#!/usr/bin/env python3
"""Explicitly addressed development access to the experimental OnePlus only."""
import os
import shutil
import subprocess
from pathlib import Path
_saved=Path(__file__).resolve().parents[2]/'project-notes/adb-path.txt'
ADB=os.environ.get('GLITCHOS_ADB') or (_saved.read_text().strip() if _saved.is_file() else shutil.which('adb'))
if not ADB:raise RuntimeError('Set GLITCHOS_ADB to the existing adb executable')
SERIAL='39f26a79'
def run(*args,timeout=30,binary=False):
    state=subprocess.run([ADB,'-s',SERIAL,'get-state'],capture_output=True,timeout=10)
    if state.returncode or state.stdout.strip()!=b'device':raise RuntimeError('Experimental OnePlus is not connected/authorized')
    result=subprocess.run([ADB,'-s',SERIAL,*args],capture_output=True,text=not binary,timeout=timeout)
    if result.returncode:raise RuntimeError(str(result.stderr)+str(result.stdout))
    return result.stdout
if __name__=='__main__':
    import sys
    print(run(*sys.argv[1:]))
