#!/usr/bin/env python3
"""Integrate only ContourHome into an existing fajita source tree; never build or flash."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import tempfile

SOURCE = Path(__file__).resolve().parents[1] / 'vendor/contour'
PRODUCT = Path('device/oneplus/fajita/lineage_fajita.mk')
DEST = Path('vendor/contour')
INHERIT = '$(call inherit-product, vendor/contour/contour.mk)'

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def files(path):
    if not path.exists():
        return {}
    result = {}
    for item in sorted(path.rglob('*')):
        if item.is_symlink():
            raise RuntimeError(f'Symlink is not supported in module: {item}')
        if item.is_file():
            result[item.relative_to(path).as_posix()] = sha(item)
    return result

def checked(tree, relative):
    path = tree
    for part in relative.parts:
        path = path / part
        if path.is_symlink():
            raise RuntimeError(f'Refusing symlink: {path}')
    return path

def atomic(path, data):
    handle, temporary = tempfile.mkstemp(dir=path.parent, prefix='.'+path.name+'-')
    try:
        with os.fdopen(handle, 'wb') as stream:
            stream.write(data); stream.flush(); os.fsync(stream.fileno())
        os.replace(temporary, path)
    finally:
        if Path(temporary).exists():
            Path(temporary).unlink()

def install(tree):
    tree = tree.expanduser().resolve()
    if not (tree/'build/envsetup.sh').is_file():
        raise RuntimeError('This is not an Android source tree')
    product = checked(tree, PRODUCT)
    dest = checked(tree, DEST)
    if not product.is_file():
        raise RuntimeError('fajita product file is missing; run breakfast fajita first')
    before = product.read_bytes()
    content = before.decode('utf-8')
    has_inherit = INHERIT in [line.strip() for line in content.splitlines() if not line.lstrip().startswith('#')]
    source_files = files(SOURCE)
    old_files = files(dest)
    if not source_files:
        raise RuntimeError('Source module is empty')
    if has_inherit and all(old_files.get(name)==digest for name,digest in source_files.items()):
        print('HOME_SOURCE_ALREADY_CURRENT'); return None
    if not has_inherit:
        content = content.rstrip() + '\n\n# glitchOS: native home and first local assistant skill\n' + INHERIT + '\n'
    backup_root = tree.parent/'glitchos-backups'
    backup_root.mkdir(exist_ok=True)
    backup = Path(tempfile.mkdtemp(prefix='home-0.5.0-',dir=backup_root))
    (backup/'product.mk').write_bytes(before)
    existed = dest.exists()
    if existed:
        shutil.copytree(dest,backup/'contour')
    try:
        dest.mkdir(parents=True,exist_ok=True)
        # Ninja uses mtimes. Imported sources may predate an already built output,
        # so changed contents need a fresh destination timestamp. Leave equal files alone.
        for name,digest in source_files.items():
            if old_files.get(name) != digest:
                target = dest/name
                target.parent.mkdir(parents=True,exist_ok=True)
                shutil.copy(SOURCE/name,target)
        if content.encode('utf-8') != before:
            atomic(product,content.encode('utf-8'))
        manifest = {'schema':1,'tree':str(tree),'existed':existed,'product_after':sha(product),'module_after':files(dest)}
        (backup/'restore.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
    except Exception:
        if dest.exists(): shutil.rmtree(dest)
        if existed: shutil.copytree(backup/'contour',dest,copy_function=shutil.copy)
        atomic(product,before)
        raise
    print('HOME_SOURCE_READY')
    print(f'Backup: {backup}')
    print('Next in the same build environment: m -j2 ContourHome')
    print('Only source files were changed. OTA keys, Updater and the phone were not changed.')
    return backup

def restore(backup):
    backup = backup.expanduser().resolve()
    manifest = json.loads((backup/'restore.json').read_text(encoding='utf-8'))
    if manifest['schema'] != 1: raise RuntimeError('Unknown backup schema')
    tree = Path(manifest['tree'])
    product, dest = checked(tree,PRODUCT), checked(tree,DEST)
    if sha(product) != manifest['product_after'] or files(dest) != manifest['module_after']:
        raise RuntimeError('Source changed after integration; automatic rollback would overwrite later work')
    old_product = (backup/'product.mk').read_bytes()
    old_module = backup/'contour'
    if manifest['existed'] and not old_module.is_dir(): raise RuntimeError('Incomplete backup')
    if old_module.exists(): files(old_module)
    # Keep a copy of the replaced state so even interrupted rollback can be recovered.
    current = Path(tempfile.mkdtemp(prefix='before-restore-',dir=backup.parent))
    shutil.copytree(dest,current/'contour'); shutil.copy2(product,current/'product.mk')
    shutil.rmtree(dest)
    if manifest['existed']: shutil.copytree(old_module,dest,copy_function=shutil.copy)
    atomic(product,old_product)
    print(f'HOME_SOURCE_RESTORED; previous integrated state: {current}')

if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    mode = p.add_mutually_exclusive_group()
    mode.add_argument('--tree',type=Path,default=Path.home()/'android/lineage')
    mode.add_argument('--restore',type=Path)
    args=p.parse_args()
    try:
        restore(args.restore) if args.restore else install(args.tree)
    except (OSError,ValueError,KeyError,RuntimeError) as error:
        p.exit(1,f'ERROR: {error}\n')
