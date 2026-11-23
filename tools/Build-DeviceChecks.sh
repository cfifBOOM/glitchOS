#!/bin/bash
set -euo pipefail
project_dir=$(cd "$(dirname "$0")/.." && pwd)
tree=${GLITCHOS_ANDROID_TREE:-"$(dirname "$project_dir")/lineage"}
rom_out=${OUT_DIR:-"$(dirname "$tree")/out/fajita"}
out_dir="$project_dir/build/device-tests"
export PATH="$tree/prebuilts/jdk/jdk21/linux-x86/bin:$PATH"
mkdir -p "$out_dir/classes" "$out_dir/dex"
"$tree/prebuilts/sdk/tools/linux/bin/aapt2" link -o "$out_dir/resources.apk" -I "$tree/prebuilts/sdk/35/public/android.jar" --manifest "$project_dir/tests/device/AndroidManifest.xml"
javac -source 8 -target 8 -Xlint:-options -encoding UTF-8 -cp "$tree/prebuilts/sdk/35/public/android.jar" -d "$out_dir/classes" "$project_dir/tests/device/DeviceChecks.java"
jar cf "$out_dir/classes.jar" -C "$out_dir/classes" .
java -cp "$tree/prebuilts/sdk/tools/linux/lib/d8.jar" com.android.tools.r8.D8 --lib "$tree/prebuilts/sdk/35/public/android.jar" --min-api 31 --output "$out_dir/dex" "$out_dir/classes.jar"
python3 - "$out_dir" <<'PY'
import sys,zipfile,shutil
from pathlib import Path
p=Path(sys.argv[1]);shutil.copy2(p/'resources.apk',p/'unsigned.apk')
with zipfile.ZipFile(p/'unsigned.apk','a') as z:z.write(p/'dex/classes.dex','classes.dex')
PY
java -Djava.library.path="$rom_out/host/linux-x86/lib64" -jar "$rom_out/host/linux-x86/framework/signapk.jar" "$tree/vendor/lineage-priv/keys/testkey.x509.pem" "$tree/vendor/lineage-priv/keys/testkey.pk8" "$out_dir/unsigned.apk" "$out_dir/checks.apk"
version=$(python3 -c 'import sys, xml.etree.ElementTree as ET; print(ET.parse(sys.argv[1]).getroot().attrib["{http://schemas.android.com/apk/res/android}versionName"])' "$project_dir/vendor/contour/home/AndroidManifest.xml")
if [[ -d "/mnt/g/glitchOS/dev-$version" ]]; then cp "$out_dir/checks.apk" "/mnt/g/glitchOS/dev-$version/checks.apk"; fi
printf '%s\n' "$out_dir/checks.apk"
