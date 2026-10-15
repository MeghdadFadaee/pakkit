"""Small ADB helpers restricted to running emulators; never select a physical device."""
import os
from pathlib import Path
import re
import shutil
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


def adb_path():
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if sdk:
        return str(Path(sdk) / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb"))
    found = shutil.which("adb")
    if found:
        return found
    local = ROOT / "local.properties"
    if local.exists():
        for line in local.read_text().splitlines():
            if line.startswith("sdk.dir="):
                return str(Path(line.removeprefix("sdk.dir=").replace("\\:", ":").replace("\\\\", "\\")) / "platform-tools" / "adb")
    raise RuntimeError("Set ANDROID_HOME or put adb on PATH.")


def adb(serial, *args, **kwargs):
    if not serial.startswith("emulator-"):
        raise ValueError("Tests accept emulator-* serials only; physical devices are never targeted.")
    return subprocess.check_output([adb_path(), "-s", serial, *args], **kwargs)


def ui(serial):
    adb(serial, "shell", "input", "keyevent", "KEYCODE_WAKEUP")
    path = "/sdcard/pakkit-test-ui.xml"
    for _ in range(3):
        try:
            adb(serial, "shell", "uiautomator", "dump", path)
            return ET.fromstring(adb(serial, "exec-out", "cat", path))
        except (ET.ParseError, subprocess.CalledProcessError):
            time.sleep(0.2)
        finally:
            adb(serial, "shell", "rm", "-f", path)
    raise RuntimeError("No settled emulator UI was available for inspection.")


def tap_text(serial, label, attempts=6):
    for _ in range(attempts):
        tree = ui(serial)
        for node in tree.iter("node"):
            if node.get("text") == label and node.get("clickable") == "true" and node.get("enabled") == "true":
                x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
                if y2 - y1 >= 48:
                    adb(serial, "shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
                    return
        bounds = next(tree.iter("node")).get("bounds")
        _, _, width, height = map(int, re.findall(r"\d+", bounds))
        adb(serial, "shell", "input", "swipe", str(width // 2), str(height * 3 // 4), str(width // 2), str(height // 4), "300")
    raise RuntimeError(f"Visible enabled control not found: {label}")


def session_url(serial):
    tap_text(serial, "View link")
    for _ in range(6):
        tree = ui(serial)
        for node in tree.iter("node"):
            value = node.get("text", "")
            if value.startswith("http://") and "#" in value:
                tap_text(serial, "Back to code")
                return value
        bounds = next(tree.iter("node")).get("bounds")
        _, _, width, height = map(int, re.findall(r"\d+", bounds))
        adb(serial, "shell", "input", "swipe", str(width // 2), str(height * 3 // 4), str(width // 2), str(height // 4), "300")
    raise RuntimeError("No private session link found. Check source approval and emulator Wi-Fi.")
