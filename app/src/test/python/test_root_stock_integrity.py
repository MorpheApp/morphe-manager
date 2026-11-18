#!/usr/bin/env python3
"""Exercise the shipped root scripts with real hashes and owned mount/redirect fixtures."""

from pathlib import Path
import hashlib
import os
import shlex
import subprocess
import tempfile
import unittest

ASSETS = Path(__file__).resolve().parents[2] / "main/assets/root"
PACKAGE = "com.example.app"


class RootModuleFixture:
    def __init__(self, *, space_in_path=False):
        self.temp = tempfile.TemporaryDirectory(prefix="morphe-stock-integrity-")
        self.root = Path(self.temp.name)
        self.module = self.root / ("module with spaces" if space_in_path else "module")
        self.module.mkdir()
        self.bin = self.root / "bin"
        self.bin.mkdir()
        self.effects = self.root / "effects.txt"
        self.effects.write_text("")
        self.target = self.root / "data/app/installed/base.apk"
        self.target.parent.mkdir(parents=True)
        self.target.write_bytes(b"installed original")
        self.system_target = self.root / "product/app/original/base.apk"
        self.system_target.parent.mkdir(parents=True)
        self.system_target.write_bytes(b"installed system original")
        (self.root / "proc/42").mkdir(parents=True)
        (self.root / "proc/42/mountinfo").write_text("")
        (self.root / "proc/mounts").write_text("")
        (self.root / "proc/self").mkdir()
        (self.root / "proc/self/mountinfo").write_text("")
        (self.root / "dev/socket").mkdir(parents=True)
        self.stock = self.module / f"{PACKAGE}-stock.apk"
        self.stock.write_bytes(b"verified original base")
        self.patched = self.module / f"{PACKAGE}.apk"
        self.patched.write_bytes(b"patched application")
        (self.module / "module.prop").write_text("description=Old status\n")
        (self.module / "stock-paths.txt").write_text(f"{self.target}\n")
        (self.module / "nomount-only").write_text("0")
        for name in ("stock-integrity.sh", "post-fs-data.sh", "service.sh"):
            text = (ASSETS / name).read_text()
            text = text.replace("__PKG_NAME__", shlex.quote(PACKAGE))
            text = text.replace("__VERSION__", shlex.quote("1.0"))
            text = text.replace("__MANAGER_PKG__", shlex.quote("app.morphe.manager"))
            text = text.replace("__MODULE_ID__", shlex.quote(f"{PACKAGE}-morphe"))
            # Only absolute Android fixture paths change; the script's control flow is untouched.
            for old in ("/data/adb/", "/data/app/", "/proc/", "/dev/socket"):
                text = text.replace(old, str(self.root) + old)
            (self.module / name).write_text(text)
        self.env = dict(os.environ)
        self.env.update({
            "PATH": str(self.bin) + os.pathsep + os.environ["PATH"],
            "TEST_EFFECTS": str(self.effects),
            "TEST_STOCK_TARGET": str(self.target),
            "TEST_PID_COUNT": str(self.root / "pid-count"),
        })
        capture = '''#!/bin/sh
name="${0##*/}"
printf '%s' "$name" >> "$TEST_EFFECTS"
for arg do printf ' <%s>' "$arg" >> "$TEST_EFFECTS"; done
printf '\\n' >> "$TEST_EFFECTS"
exit 0
'''
        for name in ("mount", "umount", "chcon", "nsenter", "pkill", "am"):
            self.command(name, capture)
        self.command("getprop", "#!/bin/sh\nprintf '1\\n'\n")
        self.command("sleep", "#!/bin/sh\nexit 0\n")
        self.command("pm", "#!/bin/sh\n[ \"$1\" = path ] && printf 'package:%s\\n' \"$TEST_STOCK_TARGET\"\n")
        self.command("cmd", "#!/bin/sh\nexit 0\n")
        self.command("dumpsys", f"""#!/bin/sh
printf 'Package [{PACKAGE}]\\n  versionName=1.0\\n  codePath=%s\\n' "${{TEST_STOCK_TARGET%/base.apk}}"
""")
        self.command("pidof", """#!/bin/sh
case "$1" in
  zygote64) printf '42\\n' ;;
  system_server)
    count=0
    [ ! -f "$TEST_PID_COUNT" ] || count="$(cat "$TEST_PID_COUNT")"
    count=$((count + 1))
    printf '%s\\n' "$count" > "$TEST_PID_COUNT"
    if [ "$count" = 1 ]; then printf '1000\\n'; else printf '2000\\n'; fi
    ;;
esac
""")
        self.command("inotifyd", """#!/bin/sh
if [ -n "$TEST_TAMPER_ON_RESTART" ]; then
  printf 'changed' >> "$TEST_TAMPER_ON_RESTART"
  printf 'n socket zygote\\n'
fi
""")
        self.manifest([self.stock])

    def command(self, name, text):
        path = self.bin / name
        path.write_text(text)
        path.chmod(0o700)

    def split(self, name="split_config.en.apk"):
        path = self.module / f"{PACKAGE}-stock-{name}"
        path.write_bytes(b"verified original split " + name.encode())
        return path

    def manifest(self, files):
        (self.module / "stock-apk-sha256.txt").write_text("".join(
            f"{hashlib.sha256(file.read_bytes()).hexdigest()}  {file.name}\n"
            for file in files
        ))

    def nomount(self):
        path = self.root / "data/adb/modules/nomount/bin/nm"
        path.parent.mkdir(parents=True)
        path.write_text("""#!/bin/sh
if [ "$1" = version ]; then printf '22 (605)\\n'; exit 0; fi
printf 'nm' >> "$TEST_EFFECTS"
for arg do printf ' <%s>' "$arg" >> "$TEST_EFFECTS"; done
printf '\\n' >> "$TEST_EFFECTS"
""")
        path.chmod(0o700)

    def run(self, script, *args, relative=False):
        path = self.module / script
        cwd = self.root if relative else self.module
        source = str(path.relative_to(cwd)) if relative else str(path)
        return subprocess.run(
            ["/bin/sh", source, *args], cwd=cwd, env=self.env,
            capture_output=True, text=True, timeout=10,
        )

    def effects_for(self, *names):
        return [line for line in self.effects.read_text().splitlines()
                if line.split(" ", 1)[0] in names]

    def existing_morphe_mounts(self, *, unrelated_top=False, deleted_source=False):
        """Model root/zygote mount stacks and the contents each namespace currently sees."""
        self.zygote_target = self.root / "zygote-view.apk"
        self.original = self.root / "physical-original.apk"
        self.original.write_bytes(self.target.read_bytes())
        visible = b"other provider" if unrelated_top else self.patched.read_bytes()
        self.target.write_bytes(visible)
        self.zygote_target.write_bytes(visible)
        self.env.update({
            "TEST_FIXTURE_ROOT": str(self.root),
            "TEST_PHYSICAL_ORIGINAL": str(self.original),
            "TEST_ZYGOTE_TARGET": str(self.zygote_target),
        })
        escape = lambda value: str(value).replace(" ", "\\040")
        stock_source = escape(self.stock)
        patch_source = escape(self.patched) + ("\\040(deleted)" if deleted_source else "")
        target = escape(self.target)
        records = (f"1 0 8:1 {stock_source} {target} rw - ext4 /dev/test rw\n"
                   f"2 1 8:1 {patch_source} {target} rw - ext4 /dev/test rw\n")
        if unrelated_top:
            records += f"3 2 8:1 /other-provider/app.apk {target} rw - ext4 /dev/test rw\n"
        for namespace in ("self", "42"):
            (self.root / "proc" / namespace / "mountinfo").write_text(records)
        self.command("nsenter", """#!/usr/bin/env python3
import os, subprocess, sys
from pathlib import Path
args = sys.argv[1:]
with Path(os.environ['TEST_EFFECTS']).open('a') as out:
    out.write('nsenter' + ''.join(' <' + arg + '>' for arg in args) + '\\n')
env = dict(os.environ, TEST_MOUNT_NAMESPACE=args[args.index('-t') + 1])
raise SystemExit(subprocess.run(args[args.index('--') + 1:], env=env).returncode)
""")
        self.command("umount", """#!/usr/bin/env python3
import os, sys
from pathlib import Path
with Path(os.environ['TEST_EFFECTS']).open('a') as out:
    out.write('umount' + ''.join(' <' + arg + '>' for arg in sys.argv[1:]) + '\\n')
namespace = os.environ.get('TEST_MOUNT_NAMESPACE', 'self')
table = Path(os.environ['TEST_FIXTURE_ROOT']) / 'proc' / namespace / 'mountinfo'
records = table.read_text().splitlines()
target = os.environ['TEST_STOCK_TARGET']
matching = [line for line in records if line.split()[4].replace('\\\\040', ' ') == target]
if not matching:
    raise SystemExit(1)
top = max(matching, key=lambda line: int(line.split()[0]))
records.remove(top)
table.write_text(''.join(line + '\\n' for line in records))
view = Path(target if namespace == 'self' else os.environ['TEST_ZYGOTE_TARGET'])
remaining = [line for line in records if line.split()[4].replace('\\\\040', ' ') == target]
if remaining:
    source = max(remaining, key=lambda line: int(line.split()[0])).split()[3].replace('\\\\040', ' ')
    view.write_bytes(Path(source).read_bytes())
else:
    view.write_bytes(Path(os.environ['TEST_PHYSICAL_ORIGINAL']).read_bytes())
""")

    def close(self):
        self.temp.cleanup()


class RootStockIntegrityTest(unittest.TestCase):
    def fixture(self, **kwargs):
        fixture = RootModuleFixture(**kwargs)
        self.addCleanup(fixture.close)
        return fixture

    def assert_rejected_without_effects(self, fixture, script="post-fs-data.sh", *args):
        result = fixture.run(script, *args)
        self.assertEqual([], fixture.effects_for("mount", "umount", "chcon", "nsenter", "nm", "am"),
                         (result.stdout, result.stderr, fixture.effects.read_text()))
        self.assertEqual(b"installed original", fixture.target.read_bytes())
        self.assertEqual(b"installed system original", fixture.system_target.read_bytes())
        if script == "service.sh":
            self.assertNotEqual(0, result.returncode, result.stderr)
        self.assertIn("stock verification failed", (fixture.module / "module.prop").read_text())

    def test_verified_base_and_splits_boot_mount(self):
        f = self.fixture()
        split = f.split()
        f.manifest([f.stock, split])
        result = f.run("post-fs-data.sh")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(1, len(f.effects_for("mount")))
        self.assertIn(str(f.stock), f.effects_for("mount")[0])

    def test_missing_empty_or_malformed_record_never_mounts(self):
        for record in (None, "", "0" * 63 + f"  {PACKAGE}-stock.apk\n",
                       "0" * 64 + f" {PACKAGE}-stock.apk\n"):
            with self.subTest(record=record):
                f = self.fixture()
                marker = f.module / "stock-apk-sha256.txt"
                if record is None:
                    marker.unlink()
                else:
                    marker.write_text(record)
                self.assert_rejected_without_effects(f)

    def test_wrong_checksum_never_mounts(self):
        f = self.fixture()
        (f.module / "stock-apk-sha256.txt").write_text("0" * 64 + f"  {f.stock.name}\n")
        self.assert_rejected_without_effects(f)

    def test_changed_base_never_mounts_or_redirects(self):
        for no_mount in (False, True):
            with self.subTest(no_mount=no_mount):
                f = self.fixture()
                if no_mount:
                    f.nomount()
                    (f.module / "nomount-only").write_text("1")
                    (f.module / "stock-paths.txt").write_text(f"{f.system_target}\n")
                f.stock.write_bytes(b"tampered")
                self.assert_rejected_without_effects(f)

    def test_changed_split_never_mounts(self):
        f = self.fixture()
        split = f.split()
        f.manifest([f.stock, split])
        split.write_bytes(b"tampered split")
        self.assert_rejected_without_effects(f)

    def test_duplicate_and_unsafe_record_entries_never_mount(self):
        for extra in (f"{PACKAGE}-stock.apk", "../outside.apk",
                      f"other.package-stock.apk", f"{PACKAGE}-stock-.apk"):
            with self.subTest(extra=extra):
                f = self.fixture()
                marker = f.module / "stock-apk-sha256.txt"
                marker.write_text(marker.read_text() + "0" * 64 + f"  {extra}\n")
                self.assert_rejected_without_effects(f)

    def test_base_must_be_included(self):
        f = self.fixture()
        split = f.split()
        f.manifest([split])
        self.assert_rejected_without_effects(f)

    def test_every_retained_split_must_be_included(self):
        f = self.fixture()
        f.split()
        self.assert_rejected_without_effects(f)

    def test_missing_listed_split_never_mounts(self):
        f = self.fixture()
        split = f.split()
        f.manifest([f.stock, split])
        split.unlink()
        self.assert_rejected_without_effects(f)

    def test_missing_helper_requires_restage(self):
        f = self.fixture()
        (f.module / "stock-integrity.sh").unlink()
        self.assert_rejected_without_effects(f)

    def test_relative_script_and_module_directory_with_spaces(self):
        f = self.fixture(space_in_path=True)
        result = f.run("post-fs-data.sh", relative=True)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(1, len(f.effects_for("mount")))

    def test_verified_system_stock_can_be_redirected_with_nomount(self):
        f = self.fixture()
        f.nomount()
        (f.module / "nomount-only").write_text("1")
        (f.module / "stock-paths.txt").write_text(f"{f.system_target}\n")
        result = f.run("post-fs-data.sh")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual([], f.effects_for("mount"))
        self.assertTrue(any(" <rule> <add>" in line for line in f.effects_for("nm")))

    def test_nomount_user_stock_stays_on_installed_copy(self):
        f = self.fixture()
        f.nomount()
        result = f.run("post-fs-data.sh")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual([], f.effects_for("mount", "nm"))

    def test_service_rejects_changed_stock_before_any_mount_effects(self):
        f = self.fixture()
        f.stock.write_bytes(b"tampered")
        self.assert_rejected_without_effects(f, "service.sh")

    def test_failed_watch_start_lifts_existing_patch_and_stock_layers(self):
        f = self.fixture()
        f.existing_morphe_mounts()
        f.stock.write_bytes(b"tampered stock backup")
        result = f.run("service.sh", "watch")
        self.assertNotEqual(0, result.returncode, result.stderr)
        self.assertEqual(b"installed original", f.target.read_bytes())
        self.assertEqual(b"installed original", f.zygote_target.read_bytes())
        self.assertEqual("", (f.root / "proc/self/mountinfo").read_text())
        self.assertEqual("", (f.root / "proc/42/mountinfo").read_text())
        self.assertEqual(4, len(f.effects_for("umount")))
        self.assertEqual([], f.effects_for("mount", "nm", "am"))

    def test_failed_watch_start_handles_deleted_mount_source_and_spaces(self):
        f = self.fixture(space_in_path=True)
        f.existing_morphe_mounts(deleted_source=True)
        (f.module / "stock-apk-sha256.txt").unlink()
        result = f.run("service.sh", "watch")
        self.assertNotEqual(0, result.returncode, result.stderr)
        self.assertEqual(b"installed original", f.target.read_bytes())
        self.assertEqual(b"installed original", f.zygote_target.read_bytes())

    def test_failed_watch_start_uses_recorded_path_when_package_query_fails(self):
        f = self.fixture()
        f.existing_morphe_mounts()
        f.command("pm", "#!/bin/sh\nexit 1\n")
        (f.module / "stock-integrity.sh").unlink()
        result = f.run("service.sh", "watch")
        self.assertNotEqual(0, result.returncode, result.stderr)
        self.assertEqual(b"installed original", f.target.read_bytes())
        self.assertEqual(b"installed original", f.zygote_target.read_bytes())

    def test_failed_watch_start_leaves_unrelated_upper_mount_untouched(self):
        f = self.fixture()
        f.existing_morphe_mounts(unrelated_top=True)
        f.stock.write_bytes(b"tampered stock backup")
        result = f.run("service.sh", "watch")
        self.assertNotEqual(0, result.returncode, result.stderr)
        self.assertEqual(b"other provider", f.target.read_bytes())
        self.assertEqual(b"other provider", f.zygote_target.read_bytes())
        self.assertEqual([], f.effects_for("mount", "umount", "nsenter", "nm", "am"))

    def test_service_watch_rejects_unverified_stock(self):
        f = self.fixture()
        (f.module / "stock-apk-sha256.txt").unlink()
        self.assert_rejected_without_effects(f, "service.sh", "watch")

    def test_service_stop_still_works_without_verified_stock(self):
        f = self.fixture()
        (f.module / "stock-integrity.sh").unlink()
        (f.module / "stock-apk-sha256.txt").unlink()
        result = f.run("service.sh", "stop")
        self.assertEqual(0, result.returncode, result.stderr)

    def test_service_verified_boot_mounts_root_and_zygote(self):
        f = self.fixture()
        result = f.run("service.sh")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(1, len(f.effects_for("mount")))
        self.assertTrue(any(" <mount>" in line for line in f.effects_for("nsenter")))

    def test_service_verified_boot_preserves_nomount_uid_scope(self):
        f = self.fixture()
        f.nomount()
        result = f.run("service.sh")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual([], f.effects_for("mount"))
        self.assertTrue(any(" <rule> <add> <--uid-range> <all> <--uid-excluded> <1000-1000>"
                            in line for line in f.effects_for("nm")))

    def test_framework_restart_rechecks_before_remount(self):
        f = self.fixture()
        split = f.split()
        f.manifest([f.stock, split])
        f.env["TEST_TAMPER_ON_RESTART"] = str(split)
        result = f.run("service.sh")
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(1, len(f.effects_for("mount")),
                         "Only the initial verified mount may run")
        self.assertIn("stock verification failed", (f.module / "module.prop").read_text())
        self.assertEqual([], f.effects_for("am"), "A rejected remount must not force-stop the app")


if __name__ == "__main__":
    unittest.main()
