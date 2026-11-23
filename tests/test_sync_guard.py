"""Guard tests using fake processes: no network, WSL, or process signals."""
import importlib.util
from pathlib import Path
import signal
import subprocess
import unittest
from unittest.mock import Mock, patch

spec = importlib.util.spec_from_file_location(
    'sync_guard', Path(__file__).resolve().parents[1] / 'tools' / 'Sync-Lineage.py')
guard = importlib.util.module_from_spec(spec)
spec.loader.exec_module(guard)


class SyncGuardTests(unittest.TestCase):
    def test_exact_threshold_stops_before_another_wait(self):
        process = Mock()
        process.poll.return_value = None
        with self.assertRaises(guard.LowSpace):
            guard.monitor(process, lambda: 30 * guard.GIB)
        process.wait.assert_not_called()

    def test_space_drop_during_sync_is_detected(self):
        process = Mock()
        process.poll.return_value = None
        process.wait.side_effect = subprocess.TimeoutExpired('repo sync', 5)
        readings = iter((31 * guard.GIB, 29 * guard.GIB))
        with self.assertRaises(guard.LowSpace):
            guard.monitor(process, lambda: next(readings))
        self.assertEqual(process.wait.call_count, 1)

    def test_physical_space_read_failure_is_not_ignored(self):
        process = Mock()
        process.poll.return_value = None
        with self.assertRaises(OSError):
            guard.monitor(process, Mock(side_effect=OSError('drive unavailable')))
        process.wait.assert_not_called()

    def test_repo_failure_is_returned_as_failure(self):
        process = Mock()
        process.poll.return_value = 1
        self.assertEqual(guard.monitor(process, lambda: 100 * guard.GIB), 1)

    def test_wrong_drive_and_unmounted_directory_are_rejected(self):
        for record in (
            {'target': '/mnt/g', 'source': 'F:\\', 'fstype': '9p'},
            {'target': '/', 'source': '/dev/sdd', 'fstype': 'ext4'},
        ):
            with self.subTest(record=record), self.assertRaises(RuntimeError):
                guard.check_g_mount(record)

    def test_stop_targets_only_owned_process_group(self):
        process = Mock(pid=45678)
        process.poll.return_value = None
        process.wait.return_value = 130
        with patch.object(guard.signal, 'SIGKILL', 9, create=True), \
                patch.object(guard.os, 'killpg', create=True) as kill:
            guard.stop_owned_group(process)
        kill.assert_called_once_with(45678, signal.SIGINT)

    def test_stop_escalates_when_interrupt_is_ignored(self):
        process = Mock(pid=45678)
        process.poll.return_value = None
        process.wait.side_effect = [subprocess.TimeoutExpired('repo sync', 15), 143]
        with patch.object(guard.signal, 'SIGKILL', 9, create=True), \
                patch.object(guard.os, 'killpg', create=True) as kill:
            guard.stop_owned_group(process)
        self.assertEqual([call.args for call in kill.call_args_list],
                         [(45678, signal.SIGINT), (45678, signal.SIGTERM)])

    def test_completed_process_is_not_signalled(self):
        process = Mock(pid=45678)
        process.poll.return_value = 0
        with patch.object(guard.os, 'killpg', create=True) as kill:
            guard.stop_owned_group(process)
        kill.assert_not_called()


if __name__ == '__main__':
    unittest.main(verbosity=2)
