#!/usr/bin/env python3
"""Point the LineageOS 22.2 Updater in the Android checkout at a GitHub feed."""

import argparse
from pathlib import Path
import re
import tempfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("android_tree", type=Path)
    parser.add_argument("repository", help="GitHub repository in OWNER/REPO form")
    args = parser.parse_args()

    if not re.fullmatch(r"[A-Za-z0-9-]+/[A-Za-z0-9_][A-Za-z0-9_.-]*", args.repository):
        parser.error("repository must be OWNER/REPO")

    updater = args.android_tree / "packages/apps/Updater"
    resource = updater / "app/src/main/res/values/strings.xml"
    code = updater / "app/src/main/java/org/lineageos/updater/misc/Utils.java"
    try:
        if 'getJSONArray("response")' not in code.read_text(encoding="utf-8"):
            raise ValueError("Updater does not use the LineageOS 22.2 feed schema")
        original = resource.read_bytes()
        stock = b"https://download.lineageos.org/api/v1/{device}/{type}/{incr}"
        feed = f"https://raw.githubusercontent.com/{args.repository}/main/fajita.json"
        start = b'<string name="updater_server_url" translatable="false">'
        end = b"</string>"
        current = start + stock + end
        wanted = start + feed.encode("ascii") + end
        if original.count(start) != 1:
            raise ValueError("Expected exactly one updater_server_url resource")
        if wanted not in original:
            if current not in original:
                raise ValueError("Unexpected existing updater URL; inspect strings.xml")
            with tempfile.NamedTemporaryFile(dir=resource.parent, delete=False) as handle:
                staged = Path(handle.name)
                try:
                    handle.write(original.replace(current, wanted, 1))
                    handle.close()
                    staged.chmod(resource.stat().st_mode & 0o777)
                    staged.replace(resource)
                finally:
                    staged.unlink(missing_ok=True)
    except (OSError, ValueError) as exc:
        parser.exit(1, f"OTA configuration failed: {exc}\n")
    print(feed)


if __name__ == "__main__":
    main()
