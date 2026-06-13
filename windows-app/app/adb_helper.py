import subprocess
import re


def get_device_screen_size() -> tuple[int, int]:
    """
    Returns (width, height) of the connected Android device screen.
    Raises RuntimeError with a human-readable message on failure.
    """
    try:
        proc = subprocess.run(
            ["adb", "devices"],
            capture_output=True, text=True, timeout=5
        )
    except FileNotFoundError:
        raise RuntimeError(
            "ADB not found in PATH.\n"
            "Install Android Platform Tools and add the folder to your system PATH."
        )
    except subprocess.TimeoutExpired:
        raise RuntimeError("ADB timed out. Check that the ADB server is running.")

    lines = proc.stdout.strip().splitlines()
    devices = [l for l in lines[1:] if "\tdevice" in l]
    if not devices:
        raise RuntimeError(
            "No Android device detected.\n"
            "Connect a phone via USB with USB Debugging enabled, then try again."
        )

    try:
        proc = subprocess.run(
            ["adb", "shell", "wm", "size"],
            capture_output=True, text=True, timeout=5
        )
    except subprocess.TimeoutExpired:
        raise RuntimeError("ADB timed out reading screen size.")

    text = proc.stdout.strip()
    # Prefer "Override size" (set by developer options); fall back to "Physical size"
    match = re.search(r"(?:Override|Physical) size:\s*(\d+)x(\d+)", text)
    if not match:
        match = re.search(r"(\d+)x(\d+)", text)
    if not match:
        raise RuntimeError(f"Could not parse screen size.\nADB output: {text!r}")

    w, h = int(match.group(1)), int(match.group(2))
    return w, h
