#!/system/bin/sh
package_name="__PKG_NAME__"
version="__VERSION__"

# Resolve the module directory from the script's own path.
# Falls back to the standard Magisk modules path if readlink is unavailable
# or the path couldn't be resolved to an absolute path.
module_dir="$(dirname "$0")"
if [ "${module_dir#"/"}" = "$module_dir" ] && command -v readlink >/dev/null 2>&1; then
  module_dir="$(dirname "$(readlink -f "$0")")"
fi
if [ "${module_dir#"/"}" = "$module_dir" ]; then
  module_dir="/data/adb/modules/${package_name}-morphe"
fi

base_dir="$module_dir"
mkdir -p "$module_dir"

# Redirect all output (stdout + stderr) to the module log file.
log="$module_dir/log.txt"
rm -f "$log"
exec >> "$log" 2>&1

base_path="$base_dir/$package_name.apk"

# Wait for the system to fully boot before proceeding.
until [ "$(getprop sys.boot_completed)" = 1 ]; do sleep 3; done
# Wait briefly for external storage to be available. On direct-boot devices this
# path may stay unavailable until first unlock, so do not keep the root service
# alive indefinitely.
waited=0
max_storage_wait=60
while [ ! -d "/sdcard/Android" ] && [ "$waited" -lt "$max_storage_wait" ]; do
  waited=$((waited + 1))
  sleep 1
done
if [ ! -d "/sdcard/Android" ]; then
  echo "Not mounting as external storage did not become ready"
  exit 1
fi

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

mount_in_zygote_namespaces() {
  for zpid in $(pidof zygote64) $(pidof zygote); do
    if nsenter -t "$zpid" -m mount -o bind "$base_path" "$stock_path" 2>/dev/null; then
      echo "Mounted in zygote namespace: $zpid"
    else
      echo "Failed to mount in zygote namespace: $zpid"
    fi
  done
}

unmount_from_zygote_namespaces() {
  for zpid in $(pidof zygote64) $(pidof zygote); do
    nsenter -t "$zpid" -m umount -l "$stock_path" 2>/dev/null || true
  done
}

# Unmount any existing installation to prevent multiple mounts.
grep "$package_name" /proc/mounts | while read -r line; do
  echo "$line" | cut -d " " -f 2 | sed "s/apk.*/apk/" | xargs -r umount -l
done

# Wait up to 180 seconds for PackageManager to report the stock APK path and version.
# This is necessary because the app may not be registered immediately after boot.
waited=0
max_wait=180
stock_path=""
stock_versions=""
while [ "$waited" -lt "$max_wait" ]; do
  # Prefer the path under /data/app/ (user-installed); fall back to any base APK path;
  # last resort: use the lower-level `cmd package` if `pm` returns nothing.
  stock_path_data="$(pm path "$package_name" | grep base | grep /data/app/ | head -n 1 | sed 's/package://g')"
  stock_path_fallback="$(pm path "$package_name" | grep base | head -n 1 | sed 's/package://g')"
  if [ -z "$stock_path_data" ] && [ -z "$stock_path_fallback" ]; then
    stock_path_cmd="$(cmd package path "$package_name" 2>/dev/null | grep base | head -n 1 | sed 's/package://g')"
  else
    stock_path_cmd=""
  fi

  # Extract all versionName entries for this package from dumpsys, stopping before
  # any hidden system package section to avoid picking up OEM preinstall metadata.
  package_dump="$(dumpsys package "$package_name")"
  stock_versions="$(echo "$package_dump" | awk -v pkg="$package_name" '
    $0 ~ ("Package \\[" pkg "\\]") { in_pkg = 1 }
    $0 ~ /Hidden system package/ { in_pkg = 0 }
    in_pkg && /versionName=/ { sub(/.*versionName=/, ""); print }
  ' | tr -d '\r')"
  stock_path_dumpsys="$(echo "$package_dump" | awk -v pkg="$package_name" '
    $0 ~ ("Package \\[" pkg "\\]") { in_pkg = 1 }
    $0 ~ /Hidden system package/ { in_pkg = 0 }
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
    stock_path="$(pm path "$package_name" | grep base | head -n 1 | sed 's/package://g')"
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

echo "base_path: $base_path"
echo "stock_path: $stock_path"
echo "base_version: $version"
echo "stock_versions: $(echo "$stock_versions" | tr '\n' ' ' | xargs)"

# Abort if the patched APK version doesn't match the installed stock version.
# Mounting a mismatched APK would cause a signature or version mismatch crash.
if [ -n "$stock_versions" ] && ! echo "$stock_versions" | grep -Fxq "$version"; then
  echo "Not mounting as versions don't match"
  exit 1
fi

if [ -z "$stock_path" ] || [ -z "$stock_versions" ]; then
  echo "Not mounting as app info could not be loaded"
  exit 1
fi

if [ ! -f "$base_path" ]; then
  echo "Not mounting as patched APK is missing: $base_path"
  exit 1
fi

# Set the correct SELinux context and bind-mount the patched APK over the stock one.
chcon u:object_r:apk_data_file:s0 "$base_path"
unmount_from_zygote_namespaces
umount -l "$stock_path" 2>/dev/null || true
mount -o bind "$base_path" "$stock_path"
mount_in_zygote_namespaces
