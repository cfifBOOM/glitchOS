#!/usr/bin/env python3
"""Run repo sync in the prepared Ubuntu checkout, monitoring physical drive G:."""

import json
import os
from pathlib import Path
import platform
import shutil
import signal
import subprocess
import sys

GIB = 1024 ** 3
MIN_FREE = 30 * GIB
POLL_SECONDS = 5


class LowSpace(RuntimeError):
    pass


def free_bytes():
    return shutil.disk_usage('/mnt/g').free


def require_space(available):
    if available <= MIN_FREE:
        raise LowSpace(f'G: has {available / GIB:.1f} GiB free; stop threshold is 30 GiB')


def find_mount(path):
    result = subprocess.run(
        ['findmnt', '--json', '--target', str(path), '--output', 'TARGET,SOURCE,FSTYPE'],
        check=True, capture_output=True, text=True)
    entries = json.loads(result.stdout).get('filesystems', [])
    if len(entries) != 1:
        raise RuntimeError(f'Cannot identify filesystem for {path}')
    return entries[0]


def check_g_mount(record):
    if (record.get('target') != '/mnt/g'
            or str(record.get('source', '')).rstrip('\\/').upper() != 'G:'
            or record.get('fstype') not in ('9p', 'drvfs', 'virtiofs')):
        raise RuntimeError('/mnt/g is not the expected Windows drive G:')


def preflight():
    if platform.system() != 'Linux' or 'microsoft' not in platform.release().lower():
        raise RuntimeError('Run this command inside Ubuntu WSL, without sudo')
    if os.geteuid() == 0:
        raise RuntimeError('Use your normal Ubuntu user, without sudo')
    project = Path.home() / 'android' / 'lineage'
    launcher = Path.home() / 'bin' / 'repo'
    if not (project / '.repo' / 'manifest.xml').is_file():
        raise RuntimeError(f'repo init has not completed in {project}')
    if not launcher.is_file() or not os.access(launcher, os.X_OK):
        raise RuntimeError(f'Missing executable repo launcher: {launcher}')
    if find_mount(project).get('fstype') != 'ext4':
        raise RuntimeError('The source checkout must be on ext4')
    check_g_mount(find_mount('/mnt/g'))
    require_space(free_bytes())
    return project, launcher


def monitor(process, read_free=free_bytes):
    while True:
        result = process.poll()
        if result is not None:
            return result
        require_space(read_free())
        try:
            return process.wait(timeout=POLL_SECONDS)
        except subprocess.TimeoutExpired:
            pass


def stop_owned_group(process):
    """Only signal the new session created for this invocation of repo."""
    if process.poll() is not None:
        return
    for sig, timeout in ((signal.SIGINT, 15), (signal.SIGTERM, 5), (signal.SIGKILL, 5)):
        try:
            os.killpg(process.pid, sig)
        except ProcessLookupError:
            process.wait()
            return
        try:
            process.wait(timeout=timeout)
            return
        except subprocess.TimeoutExpired:
            pass
    raise RuntimeError('The sync process did not exit; inspect its terminal before restarting')


def interrupt(signum, frame):
    raise KeyboardInterrupt


def main():
    process = None
    lock_stream = None
    try:
        project, launcher = preflight()
        import fcntl
        lock_path = project.parent / '.contour-repo-sync.lock'
        lock_fd = os.open(lock_path, os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
        lock_stream = os.fdopen(lock_fd, 'a')
        try:
            fcntl.flock(lock_stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise RuntimeError('Another guarded repo sync is already running') from None
        signal.signal(signal.SIGTERM, interrupt)
        signal.signal(signal.SIGHUP, interrupt)
        print(f'Sync: {project}; G: {free_bytes() / GIB:.1f} GiB free', flush=True)
        print('Checking G: every 5 seconds; stopping at 30 GiB free. Ctrl+C stops sync.', flush=True)
        process = subprocess.Popen(
            [str(launcher), 'sync'], cwd=project, start_new_session=True,
            stdin=subprocess.DEVNULL,
            env={**os.environ, 'PATH': str(launcher.parent) + os.pathsep + os.environ.get('PATH', ''),
                 'GIT_TERMINAL_PROMPT': '0'})
        result = monitor(process)
        if result == 0:
            print(f'SYNC_COMPLETE. G: {free_bytes() / GIB:.1f} GiB free.', flush=True)
        else:
            print(f'SYNC_FAILED: repo exited with {result}. Keep the error output.', flush=True)
        return result if result >= 0 else 128 - result
    except LowSpace as exc:
        if process is not None:
            stop_owned_group(process)
        print(f'SYNC_PAUSED_LOW_SPACE: {exc}', flush=True)
        print('No files were deleted. Resume with this command after freeing space.', flush=True)
        return 75
    except KeyboardInterrupt:
        if process is not None:
            stop_owned_group(process)
        print('SYNC_INTERRUPTED. Run the same command to resume.', flush=True)
        return 130
    except (OSError, RuntimeError, ValueError, subprocess.CalledProcessError) as exc:
        if process is not None:
            stop_owned_group(process)
        print(f'SYNC_STOPPED: {exc}', flush=True)
        return 1
    finally:
        if process is not None and process.poll() is None:
            stop_owned_group(process)
        if lock_stream is not None:
            lock_stream.close()


if __name__ == '__main__':
    sys.exit(main())
