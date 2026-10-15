#!/usr/bin/env python3
"""Build and test Pakkit on an explicitly selected Wear OS Emulator."""
import argparse
import os
import re
import subprocess
import sys
from pathlib import Path
from emulator_utils import ROOT, adb, tap_text, session_url

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--watch", required=True, help="Wear OS emulator serial, e.g. emulator-5554")
parser.add_argument("--browser", help="Optional Android emulator serial with Chrome initialized")
parser.add_argument("--small", action="store_true", help="Run round UI tests at 192dp and 1.3x font size")
parser.add_argument("--skip-build", action="store_true")
parser.add_argument("--restart", action="store_true", help="After browser receipt, kill the background debug process and verify review recovery")
parser.add_argument("--large", action="store_true", help="Use app/build/qa/large-fixture.apk for the real browser upload")
args = parser.parse_args()
if args.restart and not args.browser:
    parser.error("--restart requires --browser.")
if args.browser == args.watch:
    parser.error("Use distinct Wear OS and Android browser emulator serials.")
for serial in filter(None, [args.watch, args.browser]):
    if not serial.startswith("emulator-"):
        parser.error("Only emulator-* devices are accepted.")
    adb(serial, "get-state")
if b"android.hardware.type.watch" not in adb(args.watch, "shell", "pm", "list", "features"):
    parser.error("--watch must be a Wear OS emulator.")

report_dir = ROOT / "app/build/reports/validation"
report_dir.mkdir(parents=True, exist_ok=True)
if not args.skip_build:
    wrapper = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    subprocess.run([str(wrapper), ":app:assembleDebug", ":app:assembleDebugAndroidTest", ":app:testDebugUnitTest"], cwd=ROOT, check=True)
for apk in ["app/build/outputs/apk/debug/app-debug.apk", "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"]:
    adb(args.watch, "install", "-r", str(ROOT / apk))
for serial in filter(None, [args.watch, args.browser]):
    adb(serial, "shell", "cmd", "wifi", "set-wifi-enabled", "enabled")
    adb(serial, "shell", "cmd", "wifi", "connect-network", "AndroidWifi", "open")
adb(args.watch, "shell", "input", "keyevent", "KEYCODE_WAKEUP")

original_size = adb(args.watch, "shell", "wm", "size").decode()
original_density = adb(args.watch, "shell", "wm", "density").decode()
original_font = adb(args.watch, "shell", "settings", "get", "system", "font_scale").decode().strip()
try:
    test_args = []
    if args.small:
        adb(args.watch, "shell", "wm", "size", "384x384")
        adb(args.watch, "shell", "wm", "density", "320")
        adb(args.watch, "shell", "settings", "put", "system", "font_scale", "1.3")
        test_args = ["-e", "class", "meghdad.pakkit.WatchReviewTest,meghdad.pakkit.WatchSessionTest,meghdad.pakkit.WatchExperienceTest"]
    output = adb(args.watch, "shell", "am", "instrument", "-w", "-r", *test_args,
                 "meghdad.pakkit.test/androidx.test.runner.AndroidJUnitRunner").decode()
    (report_dir / ("watch-small.txt" if args.small else "watch.txt")).write_text(output)
    print(output, flush=True)
    screenshots = report_dir / ("watch-product-small" if args.small else "watch-product")
    screenshots.mkdir(exist_ok=True)
    for name in ["home", "privacy", "scan", "browser-approval", "sending", "checking", "ready", "installing", "confirm", "failed", "installed"]:
        image = adb(args.watch, "exec-out", "cat", f"/sdcard/Android/data/meghdad.pakkit/files/product-review/{name}.png")
        (screenshots / f"{name}.png").write_bytes(image)

    if re.search(r"INSTRUMENTATION_STATUS_CODE: -[34]", output):
        raise RuntimeError("Tests skipped: allow Pakkit as an install source through the emulator’s system UI, then rerun.")
    if not re.search(r"OK \(\d+ tests?\)", output) or "FAILURES" in output:
        raise RuntimeError("Instrumentation failed; see app/build/reports/validation.")
finally:
    if args.small:
        size = re.search(r"Override size: (\d+x\d+)", original_size)
        density = re.search(r"Override density: (\d+)", original_density)
        adb(args.watch, "shell", "wm", "size", size.group(1) if size else "reset")
        adb(args.watch, "shell", "wm", "density", density.group(1) if density else "reset")
        if original_font == "null":
            adb(args.watch, "shell", "settings", "delete", "system", "font_scale")
        else:
            adb(args.watch, "shell", "settings", "put", "system", "font_scale", original_font)

if args.browser:
    # Browser tests use the emulators' real shared Wi-Fi; no production debug endpoint is added.
    endpoint = adb(args.browser, "forward", "tcp:0", "localabstract:chrome_devtools_remote").decode().strip()
    try:
        adb(args.watch, "shell", "am", "start", "-W", "-n", "meghdad.pakkit/.ui.WatchActivity")
        tap_text(args.watch, "Start receiving")
        url = session_url(args.watch)
        env = dict(os.environ, PAKKIT_SESSION_URL=url, PAKKIT_CDP_URL=f"http://127.0.0.1:{endpoint}")
        remote_fixture = "/sdcard/Android/data/com.android.chrome/files/pakkit-large.apk"
        if args.large:
            fixture = ROOT / "app/build/qa/large-fixture.apk"
            if not fixture.exists():
                raise RuntimeError("Run scripts/create-large-fixture.py first.")
            adb(args.browser, "shell", "mkdir", "-p", "/sdcard/Android/data/com.android.chrome/files")
            adb(args.browser, "push", str(fixture), remote_fixture)
            env.update(PAKKIT_TEST_APK=str(fixture), PAKKIT_BROWSER_FILE=remote_fixture)
        browser_test = subprocess.Popen(["node", str(ROOT / "web-tests/emulator.test.cjs")], cwd=ROOT, env=env)
        try:
            tap_text(args.watch, "Allow browser", attempts=12)
            if browser_test.wait(timeout=180) != 0:
                raise RuntimeError("Android browser transfer failed.")
        finally:
            if browser_test.poll() is None:
                browser_test.terminate()
                browser_test.wait(timeout=10)
        if args.restart:
            # Kill only this debug app, in the background, on the explicitly selected emulator.
            adb(args.watch, "shell", "input", "keyevent", "KEYCODE_HOME")
            pid = adb(args.watch, "shell", "pidof", "meghdad.pakkit").decode().strip()
            if not pid.isdecimal():
                raise RuntimeError("Expected one Pakkit debug process.")
            adb(args.watch, "shell", "run-as", "meghdad.pakkit", "kill", "-9", pid)
        adb(args.watch, "shell", "am", "start", "-W", "-n", "meghdad.pakkit/.ui.WatchActivity")
        tap_text(args.watch, "Remove file")
        if args.restart:
            tap_text(args.watch, "Start receiving")
            assert session_url(args.watch).endswith(":8787/")
            (report_dir / "process-recovery.txt").write_text("PASS: background process killed after signed APK receipt; private review recovered, receiving required an explicit restart, fixed port reused.\n")
        tap_text(args.watch, "Stop receiving")
    finally:
        if args.large:
            adb(args.browser, "shell", "rm", "-f", "/sdcard/Android/data/com.android.chrome/files/pakkit-large.apk")
        adb(args.browser, "forward", "--remove", f"tcp:{endpoint}")
        adb(args.watch, "shell", "am", "force-stop", "meghdad.pakkit")
