#!/usr/bin/env python3
"""Fetch pinned speech binaries before Gradle; never downloads at app runtime."""
import argparse
import hashlib
import json
from pathlib import Path
import urllib.request

ROOT = Path(__file__).resolve().parents[1]


def digest(path):
    checksum = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            checksum.update(chunk)
    return checksum.hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--verify-only", action="store_true")
    args = parser.parse_args()
    for item in json.loads((ROOT / "docs/asr-assets.json").read_text()):
        target = ROOT / item["path"]
        if not target.resolve().is_relative_to(ROOT):
            raise ValueError("Asset path escapes project")
        if target.is_file() and digest(target) == item["sha256"]:
            print(f"Verified: {item['path']}")
            continue
        if args.verify_only:
            raise RuntimeError(f"Missing or invalid asset: {item['path']}")
        target.parent.mkdir(parents=True, exist_ok=True)
        partial = target.with_suffix(target.suffix + ".part")
        try:
            print(f"Downloading: {item['path']}", flush=True)
            with urllib.request.urlopen(item["url"], timeout=60) as response, partial.open("wb") as output:
                for chunk in iter(lambda: response.read(1024 * 1024), b""):
                    output.write(chunk)
            if digest(partial) != item["sha256"]:
                raise RuntimeError(f"Checksum mismatch: {item['path']}")
            partial.replace(target)
        finally:
            partial.unlink(missing_ok=True)
        print(f"Verified: {item['path']}")


if __name__ == "__main__":
    main()
