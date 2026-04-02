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
# Wait for external storage to be available (ensures PackageManager is ready).
until [ -d "/sdcard/Android" ]; do sleep 1; done

mkdir -p "$base_dir"

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
  stock_path="${stock_path_data:-${stock_path_fallback:-$stock_path_cmd}}"

  # Extract all versionName entries for this package from dumpsys, stopping before
  # any hidden system package section to avoid picking up OEM preinstall metadata.
  stock_versions="$(dumpsys package "$package_name" | awk -v pkg="$package_name" '
    $0 ~ ("Package \\[" pkg "\\]") { in_pkg = 1 }
    $0 ~ /Hidden system package/ { in_pkg = 0 }
    in_pkg && /versionName=/ { sub(/.*versionName=/, ""); print }
  ' | tr -d '\r')"

  # If dumpsys returned versions but pm returned no path, retry path resolution once more.
  if [ -n "$stock_versions" ] && [ -z "$stock_path" ]; then
    stock_path="$(pm path "$package_name" | grep base | head -n 1 | sed 's/package://g')"
    if [ -z "$stock_path" ]; then
      stock_path="$(cmd package path "$package_name" 2>/dev/null | grep base | head -n 1 | sed 's/package://g')"
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
if ! echo "$stock_versions" | grep -Fxq "$version"; then
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
mount -o bind "$base_path" "$stock_path"
