#!/system/bin/sh
package_name="__PKG_NAME__"
version="__VERSION__"
manager_package="__MANAGER_PKG__"

# Resolve the module directory from the script's own path.
# Falls back to the standard Magisk modules path if readlink is unavailable
# or the path couldn't be resolved to an absolute path.
module_dir="$(dirname "$0")"
if [ "${module_dir#"/"}" = "$module_dir" ] && command -v readlink >/dev/null 2>&1; then
  module_dir="$(dirname "$(readlink -f "$0")")"
fi
if [ "${module_dir#"/"}" = "$module_dir" ]; then
  module_dir="/data/adb/modules/__MODULE_ID__"
fi

base_dir="$module_dir"
mkdir -p "$module_dir"

log="$module_dir/log.txt"
: > "$log"

log_msg() {
  echo "$*" >> "$log"
}

# The root manager shows the description, so it carries the outcome of this boot.
set_status() {
  sed -i "s|^description=.*|description=$1|" "$module_dir/module.prop"
}

# Logs why the patched APK was not mounted, shows it on the module and stops.
not_mounted() {
  log_msg "Not mounting: $1"
  set_status "Not mounted at boot: $1"
  exit 1
}

base_path="$base_dir/$package_name.apk"

# Wait for the system to fully boot before proceeding.
until [ "$(getprop sys.boot_completed)" = 1 ]; do sleep 3; done

mkdir -p "$base_dir"

resolve_apk_from_path() {
  path="$1"
  if [ -z "$path" ]; then
    return
  fi
  if [ -f "$path" ]; then
    echo "$path"
    return
  fi
  if [ -d "$path" ]; then
    find "$path" -maxdepth 1 -name "*.apk" -type f 2>/dev/null | head -n 1
  fi
}

# The command is set apart with -- because toybox nsenter would otherwise take mount's
# and umount's options as its own.
mount_in_zygote_namespaces() {
  for zpid in $(pidof zygote64) $(pidof zygote); do
    if nsenter -t "$zpid" -m -- mount -o bind "$base_path" "$stock_path" 2>/dev/null; then
      log_msg "Mounted in zygote namespace: $zpid"
    else
      log_msg "Failed to mount in zygote namespace: $zpid"
    fi
  done
}

# A namespace can hold the patched APK more than once, so each one is unmounted until
# the stock path no longer appears in its mount table.
unmount_from_zygote_namespaces() {
  for zpid in $(pidof zygote64) $(pidof zygote); do
    while grep -qF " $stock_path " "/proc/$zpid/mountinfo" &&
      nsenter -t "$zpid" -m -- umount -l "$stock_path" 2>/dev/null; do :; done
  done
}

# Processes started before the mount, such as System UI, keep what their namespace held, and an
# APK another root install left there makes them look up the app's resources in another version.
replace_other_mounts() {
  for mountinfo in $(grep -lF " $stock_path " /proc/[0-9]*/mountinfo 2>/dev/null); do
    pid="$(echo "$mountinfo" | cut -d/ -f3)"
    sources="$(grep -F " $stock_path " "$mountinfo" | cut -d' ' -f4 | grep -vF "${module_dir#/data}/")" ||
      continue
    log_msg "Replacing $(echo $sources) in namespace of pid: $pid"
    while grep -qF " $stock_path " "$mountinfo" &&
      nsenter -t "$pid" -m -- umount -l "$stock_path" 2>/dev/null; do :; done
    nsenter -t "$pid" -m -- mount -o bind "$base_path" "$stock_path" 2>/dev/null
  done
}

# Unmount any existing installation to prevent multiple mounts.
# Matches the target field (2nd column) of /proc/mounts so unrelated mounts that happen
# to contain the package name in another field are ignored, and only paths that end in
# .apk are unmounted to avoid touching adjacent mount points.
awk -v pkg="$package_name" '$2 ~ ("/" pkg "[/-]") && $2 ~ /\.apk$/ { print $2 }' /proc/mounts \
  | sort -u \
  | xargs -r umount -l 2>/dev/null

# Wait up to 180 seconds for PackageManager to report the stock APK path and version.
# This is necessary because the app may not be registered immediately after boot.
waited=0
max_wait=180
stock_path=""
stock_versions=""
while [ "$waited" -lt "$max_wait" ]; do
  # Prefer the path under /data/app/ (user-installed); fall back to any base APK path;
  # last resort: use the lower-level `cmd package` if `pm` returns nothing.
  stock_path_data="$(pm path "$package_name" 2>/dev/null | grep base | grep /data/app/ | head -n 1 | sed 's/package://g')"
  stock_path_fallback="$(pm path "$package_name" 2>/dev/null | grep base | head -n 1 | sed 's/package://g')"
  if [ -z "$stock_path_data" ] && [ -z "$stock_path_fallback" ]; then
    stock_path_cmd="$(cmd package path "$package_name" 2>/dev/null | grep base | head -n 1 | sed 's/package://g')"
  else
    stock_path_cmd=""
  fi

  # Extract all versionName entries for this package from dumpsys, stopping before
  # any hidden system package section to avoid picking up OEM preinstall metadata.
  package_dump="$(dumpsys package "$package_name" 2>/dev/null)"
  # The hidden section repeats the package header, so it ends the read rather than pausing it.
  stock_versions="$(echo "$package_dump" | awk -v pkg="$package_name" '
    $0 ~ ("Package \\[" pkg "\\]") { in_pkg = 1 }
    $0 ~ /Hidden system package/ { exit }
    in_pkg && /versionName=/ { sub(/.*versionName=/, ""); print }
  ' | tr -d '\r')"
  stock_path_dumpsys="$(echo "$package_dump" | awk -v pkg="$package_name" '
    $0 ~ ("Package \\[" pkg "\\]") { in_pkg = 1 }
    $0 ~ /Hidden system package/ { exit }
    in_pkg && /resourcePath=/ { sub(/.*resourcePath=/, ""); print; exit }
    in_pkg && /codePath=/ { sub(/.*codePath=/, ""); print; exit }
  ' | tr -d '\r')"

  stock_path="$stock_path_data"
  if [ -z "$stock_path" ]; then
    stock_path="$stock_path_fallback"
  fi
  if [ -z "$stock_path" ]; then
    stock_path="$stock_path_cmd"
  fi
  if [ -z "$stock_path" ]; then
    stock_path="$(resolve_apk_from_path "$stock_path_dumpsys")"
  fi

  # If the stock version is already known to be wrong, there is no reason to
  # keep retrying path lookups. The module cannot mount safely in this state.
  if [ -n "$stock_versions" ] && ! echo "$stock_versions" | grep -Fxq "$version"; then
    break
  fi

  # If dumpsys returned versions but pm returned no path, retry path resolution once more.
  if [ -n "$stock_versions" ] && [ -z "$stock_path" ]; then
    stock_path="$(pm path "$package_name" 2>/dev/null | grep base | head -n 1 | sed 's/package://g')"
    if [ -z "$stock_path" ]; then
      stock_path="$(cmd package path "$package_name" 2>/dev/null | grep base | head -n 1 | sed 's/package://g')"
    fi
    if [ -z "$stock_path" ]; then
      stock_path="$(resolve_apk_from_path "$stock_path_dumpsys")"
    fi
  fi

  if [ -n "$stock_path" ] && [ -f "$stock_path" ] && [ -n "$stock_versions" ]; then
    break
  fi
  waited=$((waited + 1))
  sleep 1
done

log_msg "base_path: $base_path"
log_msg "stock_path: $stock_path"
log_msg "base_version: $version"
log_msg "stock_versions: $(echo "$stock_versions" | tr '\n' ' ' | xargs)"

# Abort if the patched APK version doesn't match the installed stock version.
# Mounting a mismatched APK would cause a signature or version mismatch crash.
if [ -n "$stock_versions" ] && ! echo "$stock_versions" | grep -Fxq "$version"; then
  # Usually a store update, which Morphe can restore. Root reaches the unexported receiver,
  # -f 0x20 a Morphe not started since boot, and & keeps am from holding up the status.
  am broadcast -f 0x20 -a app.morphe.manager.action.MOUNT_REPLACED \
    -n "$manager_package/app.morphe.manager.receiver.MountReplacedReceiver" \
    --es package "$package_name" >/dev/null 2>&1 &
  not_mounted "v$stock_versions is installed but the patch is for v$version. Open Morphe to restore it"
fi

if [ -z "$stock_path" ] || [ -z "$stock_versions" ]; then
  not_mounted "the app was not found"
fi

if [ ! -f "$base_path" ]; then
  not_mounted "the patched APK is missing"
fi

# Set the correct SELinux context and bind-mount the patched APK over the stock one.
if ! chcon u:object_r:apk_data_file:s0 "$base_path" 2>> "$log"; then
  log_msg "Failed to set SELinux context"
fi
unmount_from_zygote_namespaces
while grep -qF " $stock_path " /proc/self/mountinfo && umount -l "$stock_path" 2>/dev/null; do :; done
if mount -o bind "$base_path" "$stock_path" 2>> "$log"; then
  log_msg "Mounted in root namespace"
else
  not_mounted "mounting failed, see log.txt"
fi
mount_in_zygote_namespaces
replace_other_mounts
set_status "Mounted the patched v$version at boot"
