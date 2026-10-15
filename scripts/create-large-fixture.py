#!/usr/bin/env python3
"""Create a signed, uncompressed APK fixture for large-file transfer tests; never shipped."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import zipfile
from emulator_utils import ROOT

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--bytes", type=int, default=64 * 1024 * 1024, help="Size of the stored test asset")
args = parser.parse_args()
if args.bytes <= 0:
    parser.error("Use a positive asset size.")
source = ROOT / "qa/fixture/build/outputs/apk/debug/qaFixture-debug.apk"
if not source.exists():
    raise SystemExit("Build :qaFixture:assembleDebug first.")
sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
if not sdk:
    for line in (ROOT / "local.properties").read_text().splitlines():
        if line.startswith("sdk.dir="):
            sdk = line.removeprefix("sdk.dir=")
versions = sorted((Path(sdk) / "build-tools").glob("*/apksigner"))
if not versions:
    raise SystemExit("Install Android SDK Build Tools with apksigner.")
folder = ROOT / "app/build/qa"
folder.mkdir(parents=True, exist_ok=True)
unsigned = folder / "large-unsigned.apk"
output = folder / "large-fixture.apk"
try:
    with zipfile.ZipFile(source) as original, zipfile.ZipFile(unsigned, "w") as target:
        for entry in original.infolist():
            if entry.filename.upper().startswith("META-INF/"):
                continue
            with original.open(entry) as incoming, target.open(entry, "w") as outgoing:
                shutil.copyfileobj(incoming, outgoing, 64 * 1024)
        asset = zipfile.ZipInfo("assets/pakkit-transfer-test.bin")
        asset.compress_type = zipfile.ZIP_STORED
        asset.file_size = args.bytes
        block = bytes(64 * 1024)
        remaining = args.bytes
        with target.open(asset, "w") as stream:
            while remaining:
                chunk = min(remaining, len(block))
                stream.write(block[:chunk])
                remaining -= chunk
    subprocess.run([str(versions[-1]), "sign", "--ks", str(Path.home() / ".android/debug.keystore"),
                    "--ks-pass", "pass:android", "--key-pass", "pass:android", "--ks-key-alias", "androiddebugkey",
                    "--out", str(output), str(unsigned)], check=True)
    subprocess.run([str(versions[-1]), "verify", str(output)], check=True)
    print(f"{output} ({output.stat().st_size:,} bytes)")
finally:
    unsigned.unlink(missing_ok=True)
