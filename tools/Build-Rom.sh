#!/bin/bash
# Existing fajita checkout only. This script builds files and never flashes a device.
set -euo pipefail
project_dir=$(cd "$(dirname "$0")/.." && pwd)
tree=${GLITCHOS_ANDROID_TREE:-"$(dirname "$project_dir")/lineage"}
export OUT_DIR=${OUT_DIR:-"$(dirname "$tree")/out/fajita"}
export TARGET_PRODUCT=lineage_fajita TARGET_BUILD_VARIANT=userdebug TARGET_RELEASE=bp1a
export LINEAGE_BUILD=fajita INLINE_KERNEL_BUILDING=true
export SOONG_GOMEMLIMIT=${SOONG_GOMEMLIMIT:-10GiB} SOONG_GOGC=${SOONG_GOGC:-40}
# Match Lineage's envsetup: kernel.mk uses this separately from Soong's Clang.
export LLVM_AOSP_PREBUILTS_VERSION
LLVM_AOSP_PREBUILTS_VERSION=$(python3 "$tree/build/soong/scripts/get_clang_version.py" "$tree/build/soong/cc/config/global.go")
test -x "$tree/prebuilts/clang/host/linux-x86/$LLVM_AOSP_PREBUILTS_VERSION/bin/clang"

case "${1:-modules}" in
  modules) targets=(ContourHome TrebuchetQuickStep) ;;
  ota)
    export TARGET_UNOFFICIAL_BUILD_ID=glitchOS-0.5.0
    export LINEAGE_VERSION_APPEND_TIME_OF_DAY=true
    targets=(bacon)
    ;;
  *) echo "Usage: $0 [modules|ota]" >&2; exit 2 ;;
esac

test -f "$tree/device/oneplus/fajita/lineage_fajita.mk"
test -f "$tree/vendor/lineage-priv/keys/testkey.x509.pem"
test -f "$tree/vendor/lineage-priv/keys/testkey.pk8"
git -C "$tree/build/soong" apply --reverse --check "$project_dir/patches/soong-optional-memory-limit.patch"
git -C "$tree/packages/apps/Trebuchet" apply --reverse --check "$project_dir/patches/lineage-22.2-sole-home.patch"
python3 "$project_dir/tools/Configure-Home.py" --tree "$tree"
cd "$tree"
build/soong/soong_ui.bash --make-mode -j2 "${targets[@]}"
python3 "$project_dir/tools/Check-BuiltHome.py" "$tree" "$OUT_DIR"
