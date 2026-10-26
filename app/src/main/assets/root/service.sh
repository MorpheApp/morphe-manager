#!/system/bin/sh
package_name=__PKG_NAME__
version=__VERSION__
manager_package=__MANAGER_PKG__

# Resolve the module directory from the script's own path.
# Falls back to the standard Magisk modules path if readlink is unavailable
# or the path couldn't be resolved to an absolute path.
module_dir="$(dirname "$0")"
if [ "${module_dir#"/"}" = "$module_dir" ] && command -v readlink >/dev/null 2>&1; then
  module_dir="$(dirname "$(readlink -f "$0")")"
fi
if [ "${module_dir#"/"}" = "$module_dir" ]; then
  module_dir=/data/adb/modules/__MODULE_ID__
fi

base_dir="$module_dir"
mkdir -p "$module_dir"

log="$module_dir/log.txt"

# Morphe runs the script with "watch" after it mounts the app itself and with "stop" when it unmounts
# it. Neither touches the boot's log.
if [ -z "$1" ]; then
  : > "$log"
fi

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

# Runs the command after the mount table while it lists the stock path, one layer per run. Bounded,
# as a root that hides mounts can report each umount as done and hold the script there forever.
lift_layers() {
  table="$1"
  shift
  for _ in 1 2 3 4 5 6 7 8 9 10; do
    grep -qF " $stock_path " "$table" && "$@" 2>/dev/null || return 0
  done
}

unmount_from_zygote_namespaces() {
  for zpid in $(pidof zygote64) $(pidof zygote); do
    lift_layers "/proc/$zpid/mountinfo" nsenter -t "$zpid" -m -- umount -l "$stock_path"
  done
}

# Processes started before the mount, such as System UI, keep what their namespace held, and an
# APK another root install left there makes them look up the app's resources in another version.
# A previous patched APK counts too, it shows up marked deleted once a new one replaced it.
# Hundreds of processes share a few namespaces, and forking for each of them takes seconds.
# The candidates are found in single passes instead, and one per namespace is entered.
replace_other_mounts() {
  mount_source="${base_path#/data}"
  for pid in $(grep -F " $stock_path " /proc/[0-9]*/mountinfo 2>/dev/null |
    grep -vF " $mount_source $stock_path " |
    sed 's|/mountinfo:.*|/ns/mnt|' | sort -u | xargs -r stat -Lc '%i %n' 2>/dev/null |
    sort -u -k1,1 | cut -d/ -f3); do
    mountinfo="/proc/$pid/mountinfo"
    sources="$(grep -F " $stock_path " "$mountinfo" | cut -d' ' -f4 | grep -vxF "$mount_source")" ||
      continue
    log_msg "Replacing $(echo $sources) in namespace of pid: $pid"
    lift_layers "$mountinfo" nsenter -t "$pid" -m -- umount -l "$stock_path"
    nsenter -t "$pid" -m -- mount -o bind "$base_path" "$stock_path" 2>/dev/null
  done
}

# Wait up to 180 seconds for PackageManager to report the stock APK path and version.
# This is necessary because the app may not be registered immediately after boot.
find_stock() {
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
}

# Every UID but system_server's, so PackageManager keeps seeing the stock APK it verifies, also
# after a framework restart, which leaves a redirect without a watcher
nomount_scope="--uid-range all --uid-excluded 1000-1000"
redirected=0

# Finds the NoMount CLI whose driver is loaded and speaks the protocol of the UID scoped rules.
# It prints the protocol and build count, such as "22 (605)".
find_nomount() {
  nm_bin=""
  for nm in /data/adb/modules/nomount/bin/nm /data/adb/ksu/bin/nm /data/adb/ap/bin/nm; do
    [ -x "$nm" ] || continue
    protocol="$("$nm" version 2>/dev/null)" || continue
    if [ "${protocol%% *}" -ge 22 ] 2>/dev/null; then
      nm_bin="$nm"
      return 0
    fi
  done
  return 1
}

# Set in Morphe, where NoMount only never falls back to a bind mount
nomount_only() {
  [ "$(cat "$module_dir/nomount-only" 2>/dev/null)" = 1 ]
}

# Redirects the stock path to the patched APK in the kernel, which no mount table shows and no
# root manager unmounts from an app. The stock APK post-fs-data may have mounted is lifted first.
redirect_patched() {
  if ! find_nomount; then
    nomount_only && not_mounted "NoMount is unavailable and bind mounts are off"
    return 1
  fi
  unmount_from_zygote_namespaces
  lift_layers /proc/self/mountinfo umount -l "$stock_path"
  # A rule is only deleted under the UID scope it was added with, so the unscoped one other root
  # installs add for the same path goes too
  "$nm_bin" rule del $nomount_scope "$stock_path" 2>/dev/null
  "$nm_bin" rule del "$stock_path" 2>/dev/null
  if ! "$nm_bin" rule add $nomount_scope "$stock_path" "$base_path" 2>> "$log"; then
    nomount_only && not_mounted "NoMount redirect failed and bind mounts are off, see log.txt"
    log_msg "NoMount redirect failed, bind mounting instead"
    return 1
  fi
  log_msg "Redirected with NoMount"
}

# Play Store would replace the stock APK under the mount with an update. zygisk-detach keeps its
# list across reboots, so this only matters when it was installed after Morphe mounted the app.
# The marker tells Morphe to reattach the app once the module is gone, unless the user detached it.
detach_from_play_store() {
  detach_bin=/data/adb/modules/zygisk-detach/detach
  [ -x "$detach_bin" ] || return 0
  out="$("$detach_bin" detach "$package_name" 2>/dev/null)" || return 0
  case "$out" in
    *"already detached"*) ;;
    *)
      touch "$module_dir/detached"
      log_msg "Detached from Play Store with zygisk-detach"
      ;;
  esac
}

mount_patched() {
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

  # Set the correct SELinux context and redirect or bind-mount the patched APK over the stock one.
  if ! chcon u:object_r:apk_data_file:s0 "$base_path" 2>> "$log"; then
    log_msg "Failed to set SELinux context"
  fi
  if redirect_patched; then
    redirected=1
    detach_from_play_store
    set_status "Redirected the patched v$version with NoMount at boot"
    return
  fi
  unmount_from_zygote_namespaces
  lift_layers /proc/self/mountinfo umount -l "$stock_path"
  if mount -o bind "$base_path" "$stock_path" 2>> "$log"; then
    log_msg "Mounted in root namespace"
  else
    not_mounted "mounting failed, see log.txt"
  fi
  mount_in_zygote_namespaces
  replace_other_mounts
  detach_from_play_store
  set_status "Mounted the patched v$version at boot"
}

# Stops the watchers of this module that are already running: the shell running this script, and
# the reader and logcat under it. This script's own subshells are left alone.
stop_other_watchers() {
  for cmdline in $(grep -lF "$module_dir/service.sh" /proc/[0-9]*/cmdline 2>/dev/null); do
    pid="${cmdline#/proc/}"
    pid="${pid%/cmdline}"
    [ "$pid" = "$$" ] && continue
    [ "$(cut -d' ' -f4 "/proc/$pid/stat" 2>/dev/null)" = "$$" ] && continue
    pkill -P "$pid" 2>/dev/null
    kill "$pid" 2>/dev/null
  done
}

# A framework restart without a reboot keeps the patched APK mounted, and the new PackageManager
# deletes the app for not matching its stock certificates. Zygote starts seconds before that scan,
# so the APK is lifted there and mounted again once the app is reported. The event log keeps the
# watcher asleep in between.
watch_framework_restarts() {
  stop_other_watchers
  logcat -b events -v raw -s boot_progress_start -T "$(date '+%m-%d %H:%M:%S.000')" 2>/dev/null | {
    # logcat only notices the reader is gone on the next event, so it is stopped with it
    trap 'pkill -P $$ -x logcat' EXIT
    while read -r line; do
      case "$line" in
        -*) continue ;;
      esac
      if [ ! -f "$base_path" ] || [ -f "$module_dir/disable" ] || [ -f "$module_dir/remove" ]; then
        exit 0
      fi
      lift_layers /proc/self/mountinfo umount -l "$stock_path"
      log_msg "Framework restarted, lifted the patched APK before the package scan"
      find_stock
      mount_patched
      am force-stop "$package_name"
    done
  }
}

case "$1" in
  watch)
    find_stock
    watch_framework_restarts
    exit 0
    ;;
  stop)
    stop_other_watchers
    exit 0
    ;;
esac

# Wait for the system to fully boot before proceeding.
until [ "$(getprop sys.boot_completed)" = 1 ]; do sleep 3; done

# Unmount any existing installation to prevent multiple mounts.
# Matches the target field (2nd column) of /proc/mounts so unrelated mounts that happen
# to contain the package name in another field are ignored, and only paths that end in
# .apk are unmounted to avoid touching adjacent mount points.
awk -v pkg="$package_name" '$2 ~ ("/" pkg "[/-]") && $2 ~ /\.apk$/ { print $2 }' /proc/mounts \
  | sort -u \
  | xargs -r umount -l 2>/dev/null

find_stock
mount_patched

if [ "$redirected" = 1 ]; then
  stop_other_watchers
  exit 0
fi
watch_framework_restarts
