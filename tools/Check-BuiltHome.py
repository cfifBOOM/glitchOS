#!/usr/bin/env python3
"""Ensure the APK version matches the integrated source, including incremental builds."""
import argparse
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('tree',type=Path)
    parser.add_argument('out',type=Path)
    args=parser.parse_args()
    manifest=ET.parse(args.tree/'vendor/contour/home/AndroidManifest.xml').getroot()
    android='{http://schemas.android.com/apk/res/android}'
    expected=(manifest.attrib['package'],manifest.attrib[android+'versionCode'],manifest.attrib[android+'versionName'])
    apk=args.out/'target/product/fajita/system/product/app/ContourHome/ContourHome.apk'
    result=subprocess.run([str(args.tree/'prebuilts/sdk/tools/linux/bin/aapt2'),'dump','badging',str(apk)],capture_output=True,text=True,check=True)
    match=re.search(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'",result.stdout,re.MULTILINE)
    actual=match.groups() if match else None
    if actual!=expected:
        parser.exit(1,f'APK/source mismatch: expected {expected}, found {actual}. Do not install or release this artifact.\n')
    print(f'APK_VERSION_VERIFIED: {expected[0]} {expected[2]} (code {expected[1]})')

if __name__=='__main__':main()
