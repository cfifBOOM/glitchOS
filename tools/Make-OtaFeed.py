#!/usr/bin/env python3
"""Print the LineageOS 22.2 feed for a locally signed full fajita OTA."""

import argparse
import hashlib
import json
from pathlib import Path
import re
import ssl
from urllib.parse import quote
from zipfile import BadZipFile, ZipFile


def fingerprint(pem):
    der = ssl.PEM_cert_to_DER_cert(pem.decode("ascii"))
    return hashlib.sha256(der).hexdigest()


def make_feed(package, certificate, repository, tag):
    # The official verifier checks the whole-file signature separately.
    with package.open("rb") as source:
        size = source.seek(0, 2)
        if not 0 < size < 2 * 1024**3:
            raise ValueError("GitHub release assets must be smaller than 2 GiB")
        source.seek(0)
        with ZipFile(source) as archive:
            embedded = archive.read("META-INF/com/android/otacert")
            if fingerprint(embedded) != fingerprint(certificate.read_bytes()):
                raise ValueError("OTA certificate differs from the expected glitchOS certificate")
            metadata = {}
            for line in archive.read("META-INF/com/android/metadata").decode("utf-8").splitlines():
                key, separator, value = line.partition("=")
                if not separator or key in metadata:
                    raise ValueError("Invalid OTA metadata")
                metadata[key] = value.strip()
            if (metadata.get("ota-type") != "AB"
                    or not set(metadata.get("pre-device", "").split("|")) & {"OnePlus6T", "fajita"}
                    or metadata.get("post-sdk-level") != "35"
                    or {"pre-build", "pre-build-incremental", "ota-wipe", "ota-downgrade"} & metadata.keys()
                    or "payload.bin" not in archive.namelist()):
                raise ValueError("Expected a full, non-wiping Android 15 A/B OTA for fajita")
            timestamp = int(metadata.get("post-timestamp", "0"))
            if timestamp <= 0:
                raise ValueError("Invalid post-timestamp")
        source.seek(0)
        digest = hashlib.sha256()
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return {"response": [{
        "datetime": timestamp,
        "filename": package.name,
        "id": digest.hexdigest(),
        "romtype": "unofficial",
        "size": size,
        "url": f"https://github.com/{repository}/releases/download/{quote(tag, safe='')}/{quote(package.name, safe='')}",
        "version": "22.2",
    }]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("package", type=Path)
    parser.add_argument("certificate", type=Path, help="Expected public OTA certificate in PEM format")
    parser.add_argument("repository", help="Public GitHub repository in OWNER/REPO form")
    parser.add_argument("tag", help="Release tag from which the ZIP will be served")
    args = parser.parse_args()

    if not re.fullmatch(r"[A-Za-z0-9-]+/[A-Za-z0-9_][A-Za-z0-9_.-]*", args.repository):
        parser.error("repository must be OWNER/REPO")
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]*", args.tag):
        parser.error("use a tag such as fajita-v0.1.1")
    if not re.fullmatch(r"lineage-22\.2-[A-Za-z0-9_.-]+-UNOFFICIAL(?:-[A-Za-z0-9_.-]+)?-fajita\.zip",
                        args.package.name):
        parser.error("expected the original LineageOS 22.2 UNOFFICIAL fajita ZIP name")
    try:
        result = make_feed(args.package, args.certificate, args.repository, args.tag)
    except (OSError, ValueError, KeyError, BadZipFile) as exc:
        parser.exit(1, f"OTA feed generation failed: {exc}\n")
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
