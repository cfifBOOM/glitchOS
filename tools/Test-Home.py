#!/usr/bin/env python3
"""Host checks; not a substitute for a device alarm/OTA test."""
import argparse
from pathlib import Path
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
MODULE=ROOT/'vendor/contour/home'
SRC=MODULE/'src/app/contouros/home'
ANDROID='{http://schemas.android.com/apk/res/android}'
PERMISSIONS={'ACCESS_NETWORK_STATE','SCHEDULE_EXACT_ALARM','POST_NOTIFICATIONS','USE_FULL_SCREEN_INTENT',
             'FOREGROUND_SERVICE','FOREGROUND_SERVICE_MEDIA_PLAYBACK','WAKE_LOCK','VIBRATE','RECEIVE_BOOT_COMPLETED'}
def run(args):
    subprocess.run([str(a) for a in args],check=True)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--android-jar',type=Path)
    parser.add_argument('--build-tools',type=Path)
    parser.add_argument('--output',type=Path)
    args=parser.parse_args()
    for path in MODULE.rglob('*.xml'): ET.parse(path)
    manifest=ET.parse(MODULE/'AndroidManifest.xml').getroot()
    permissions={n.attrib[ANDROID+'name'].removeprefix('android.permission.') for n in manifest.findall('uses-permission')}
    assert permissions==PERMISSIONS,permissions
    for n in manifest.find('application'):
        if n.tag not in ('activity','service','receiver'):continue
        name=n.attrib[ANDROID+'name'].lstrip('.')
        assert (SRC/(name+'.java')).is_file(),name
        if name not in ('HomeActivity','QuietTile'):assert n.attrib.get(ANDROID+'exported')=='false',name
    print('PASS: XML, components, declared capabilities',flush=True)
    with tempfile.TemporaryDirectory(prefix='glitchos-test-') as temp:
        test=Path(temp)
        compiler=['javac'] if shutil.which('javac') else ['java','com.sun.tools.javac.Main']
        flags=['-source','8','-target','8','-Xlint:-options','-encoding','UTF-8']
        units=[SRC/(n+'.java') for n in ['TileSpec','TilePacking','OfflineCommand','PresenceMode']]
        units+=list((ROOT/'tests/app/contouros/home').glob('*Test.java'))
        run(compiler+flags+['-d',test]+units)
        for name in ['TilePackingTest','OfflineCommandTest','PresenceModeTest']:run(['java','-cp',test,'app.contouros.home.'+name])
        run(compiler+['-encoding','UTF-8','-d',test,ROOT/'tests/SourceSyntaxCheck.java'])
        run(['java','-cp',test,'SourceSyntaxCheck']+sorted(SRC.glob('*.java')))
    run(['python3','-m','unittest','discover','-s',ROOT/'tests','-p','test_*.py'])
    if args.android_jar or args.build_tools:
        if not args.android_jar or not args.build_tools:parser.error('Supply both SDK paths')
        build=(args.output or ROOT/'build/host-check').resolve()
        for folder in ['generated','classes','dex']: (build/folder).mkdir(parents=True,exist_ok=True)
        aapt=args.build_tools/'aapt2';jar=args.android_jar
        run([aapt,'compile','--dir',MODULE/'res','-o',build/'resources.zip'])
        run([aapt,'link','-o',build/'resources.apk','-I',jar,'--manifest',MODULE/'AndroidManifest.xml','--java',build/'generated','-R',build/'resources.zip','--min-sdk-version','31','--target-sdk-version','35','--auto-add-overlay'])
        sources=sorted(SRC.glob('*.java'))+sorted((build/'generated').rglob('*.java'))
        run(compiler+flags+['-classpath',jar,'-d',build/'classes']+sources)
        run(['java','sun.tools.jar.Main','--create','--file',build/'classes.jar','-C',build/'classes','.'])
        run(['java','-cp',args.build_tools/'lib/d8.jar','com.android.tools.r8.D8','--lib',jar,'--min-api','31','--output',build/'dex',build/'classes.jar'])
        print('PASS: Android 35 resources, Java type check and D8. Device/Soong tests still required.',flush=True)

if __name__=='__main__':main()
