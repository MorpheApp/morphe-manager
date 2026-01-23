#!/system/bin/sh
package_name="__PKG_NAME__"
version="__VERSION__"
base_dir="/data/adb/revanced/$package_name"
log="$base_dir/log"
base_path="$base_dir/$package_name.apk"

rm -f "$log"

{

until [ "$(getprop sys.boot_completed)" = 1 ]; do sleep 3; done
until [ -d "/sdcard/Android" ]; do sleep 1; done

mkdir -p "$base_dir"

# Unmount any existing installation to prevent multiple mounts.
grep "$package_name" /proc/mounts | while read -r line; do
  echo "$line" | cut -d " " -f 2 | sed "s/apk.*/apk/" | xargs -r umount -l
done

waited=0
max_wait=60
stock_path=""
stock_versions=""
while [ "$waited" -lt "$max_wait" ]; do
  stock_path_data="$(pm path "$package_name" | grep base | grep /data/app/ | head -n 1 | sed 's/package://g')"
  stock_path_fallback="$(pm path "$package_name" | grep base | head -n 1 | sed 's/package://g')"
  stock_path="${stock_path_data:-$stock_path_fallback}"
  stock_versions="$(dumpsys package "$package_name" | awk -v pkg="$package_name" '
    $0 ~ ("Package \\[" pkg "\\]") { in_pkg = 1 }
    $0 ~ /Hidden system package/ { in_pkg = 0 }
    in_pkg && /versionName=/ { sub(/.*versionName=/, ""); print }
  ' | tr -d '\r')"
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

chcon u:object_r:apk_data_file:s0 "$base_path"
mount -o bind "$base_path" "$stock_path"

# Kill the app to force it to restart the mounted APK in case it is already running.
am force-stop "$package_name"

} >> "$log"
