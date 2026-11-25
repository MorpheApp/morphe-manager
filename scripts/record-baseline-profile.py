#!/usr/bin/env python3
# Copyright 2026 Morphe.
# https://github.com/MorpheApp/morphe-manager
"""
Records the baseline profile of the app: the classes and methods ART compiles ahead of time.

Builds a release APK without the current profile, installs it on a device, and while you use the app
collects what ART finds worth compiling. The result replaces
app/src/main/baselineProfiles/baseline-prof.txt, which the next release build compiles and packages.

What you do while it records is what becomes faster, so cover at least:
  1. Open every tab and scroll through it.
  2. Turn off Settings > Patcher > Use embedded VM and patch an app, the larger the better. Only then
     does the patcher run on ART.
  3. Turn it back on and patch another app.

Needs Python 3.10, adb, and a device or emulator on Android 14 or later, best a recent emulator. The
APK is signed with the debug key unless app/keystore.jks is there, and cannot replace a Morphe Manager
signed with another key. It does replace one signed with the same key, keeping its data.

Usage:
  scripts/record-baseline-profile.py -s SERIAL [-o OUTPUT] [--apk APK]
"""

import argparse
import re
import subprocess
import sys
import threading
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
APP = ROOT / "app"
PACKAGE = "app.morphe.manager"
PROFILE = APP / "src/main/baselineProfiles/baseline-prof.txt"
LIBRARY_RULES = APP / "build/intermediates/combined_art_profile/release/compileReleaseArtProfile/baseline-prof.txt"
DUMP = f"/data/misc/profman/{PACKAGE}-primary.prof.txt"
MIN_SDK = 34
DUMP_INTERVAL_SECONDS = 10

RULE = re.compile(r"([HSP]*)([L\[].*)")


def adb(serial, *args, check=True):
    result = subprocess.run(["adb", "-s", serial, *args], capture_output=True, text=True)
    if check and result.returncode != 0:
        sys.exit(f"adb {' '.join(args)} failed:\n{result.stdout}{result.stderr}")
    return result.stdout


def check_device(serial):
    sdk = int(adb(serial, "shell", "getprop", "ro.build.version.sdk"))
    if sdk < MIN_SDK:
        sys.exit(f"{serial} runs API {sdk}, recording needs API {MIN_SDK} or later")


def build():
    print("Building the app without its profile")
    subprocess.run([ROOT / "gradlew", "-p", ROOT, ":app:assembleRelease", "-PrecordProfile", "-q"], check=True)
    return next((APP / "build/outputs/apk/release").glob("*-release.apk"))


def install(serial, apk):
    with zipfile.ZipFile(apk) as entries:
        if "assets/art-profile/baseline.prof" in entries.namelist():
            sys.exit(f"{apk} was built with its profile, build it with -PrecordProfile")
    print(f"Installing {apk.name}")
    adb(serial, "install", "-r", "--no-incremental", "--no-streaming", str(apk))
    # Code compiled ahead of time is never seen as hot, and an update keeps the profile of before
    adb(serial, "shell", "am", "force-stop", PACKAGE)
    adb(serial, "shell", "pm", "art", "clear-app-profiles", PACKAGE)
    adb(serial, "shell", "pm", "compile", "-m", "verify", "-f", PACKAGE)


def dump(serial, check=True):
    return adb(serial, "shell", "pm", "dump-profiles", "--dump-classes-and-methods", PACKAGE, check=check)


def record(serial):
    # The launcher activity is an alias that depends on the chosen app icon
    resolved = adb(serial, "shell", "cmd", "package", "resolve-activity", "--brief", "-c", "android.intent.category.LAUNCHER", PACKAGE)
    adb(serial, "shell", "am", "start", "-W", "-n", resolved.split()[-1])
    # Dumping makes every process write what it has, and that of a patch run ends with the run
    done = threading.Event()

    def keep_dumping():
        while not done.wait(DUMP_INTERVAL_SECONDS):
            output = dump(serial, check=False)
            if "saved" not in output:
                print(f"Profile not written: {output.strip()}", file=sys.stderr)

    dumper = threading.Thread(target=keep_dumping, daemon=True)
    dumper.start()
    print("Recording. Use the app and patch with the embedded VM off and on, see --help. Press Enter when done.")
    finished = sys.stdin.readline()
    done.set()
    dumper.join()
    if not finished:
        sys.exit("Input ended before the recording did, nothing was written")
    dump(serial)
    return adb(serial, "exec-out", "cat", DUMP).splitlines()


def parse(lines):
    rules = {}
    for line in lines:
        match = RULE.fullmatch(line.strip())
        if match:
            rules.setdefault(match[2], set()).update(match[1])
    return rules


def select(recorded, library):
    """The recorded rules worth shipping, sorted by what they name."""
    selected = []
    for name, flags in recorded.items():
        # R8 names these anew in each build, and derives rules for them from the methods they come from
        if "$$ExternalSynthetic" in name or "$r8$" in name:
            continue
        # Only hot methods are compiled, while most of what a start runs is too short lived to get hot
        if "S" in flags:
            flags = flags | {"H", "P"}
        if flags == {"P"}:
            continue
        # The build adds the rules the libraries come with on its own
        if name in library and flags <= library[name]:
            continue
        selected.append((name, "".join(flag for flag in "HSP" if flag in flags)))
    return [flags + name for name, flags in sorted(selected)]


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    # Never a default, as the app on the device is replaced and loses its profiles
    parser.add_argument("-s", "--serial", required=True, help="device to record on, as adb devices lists it")
    parser.add_argument("-o", "--output", type=Path, default=PROFILE, help="file to write the profile to")
    parser.add_argument("--apk", type=Path, help="APK of the last -PrecordProfile build, to not build again")
    args = parser.parse_args()

    check_device(args.serial)
    install(args.serial, args.apk or build())
    recorded = parse(record(args.serial))
    library = parse(LIBRARY_RULES.read_text().splitlines())

    rules = select(recorded, library)
    patcher = sum("Lapp/morphe/patcher/" in rule and rule.startswith("H") for rule in rules)
    if patcher == 0:
        sys.exit("Nothing of the patcher was recorded, patch an app with the embedded VM off")
    args.output.write_text("\n".join(rules) + "\n", newline="\n")
    hot = sum(rule.startswith("H") for rule in rules)
    print(f"Wrote {len(rules)} rules to {args.output}, {hot} of them compiled, {patcher} in the patcher")


if __name__ == "__main__":
    main()
