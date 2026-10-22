#!/usr/bin/env python3
"""Restore the large build assets from the verified v0.1.1 release APK."""
import argparse
import hashlib
import shutil
import tempfile
from pathlib import Path
from zipfile import ZipFile

APK_SHA256 = "25b58b7900102a6b782e51ceef2a57fc7571d5f3801383591ddf763f01f1d9e7"
ASSETS = {
    "catalog.bin": "44ee9a74b923d71dfc50cbdace8682160fec4c8ed8bbf6c998e9db05771d8ea0",
    "nmfp.tflite": "368f30c94d53e5300f0a17b7b96929b297d56e05903010603a355d798302c851",
}
METADATA = (
    "catalog.json",
    "model-provenance.json",
    "notices/model-download-provenance.json",
)


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path, help="Downloaded TangoWiz-0.1.1.apk")
    args = parser.parse_args()
    if digest(args.apk) != APK_SHA256:
        parser.error("APK SHA256 does not match the published v0.1.1 build")
    destination = Path(__file__).resolve().parents[1] / "app/src/main/assets"
    destination.mkdir(parents=True, exist_ok=True)
    # Read fixed allowlisted entries; never extract arbitrary archive paths.
    with tempfile.TemporaryDirectory(dir=destination) as temporary, ZipFile(args.apk) as archive:
        staged = []
        for name, expected in ASSETS.items():
            path = Path(temporary) / name
            with archive.open("assets/" + name) as source, path.open("wb") as target:
                shutil.copyfileobj(source, target, 1024 * 1024)
            if digest(path) != expected:
                raise SystemExit("Asset SHA256 mismatch: " + name)
            staged.append((path, destination / name))
        for name in METADATA:
            path = Path(temporary) / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(archive.read("assets/" + name))
            staged.append((path, destination / name))
        for source, target in staged:
            target.parent.mkdir(parents=True, exist_ok=True)
            source.replace(target)
            print("Restored", target.name)


if __name__ == "__main__":
    main()
