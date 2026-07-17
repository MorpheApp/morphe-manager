#!/system/bin/sh
package_name="__PKG_NAME__"

module_dir="$(dirname "$0")"
if [ "${module_dir#"/"}" = "$module_dir" ] && command -v readlink >/dev/null 2>&1; then
  module_dir="$(dirname "$(readlink -f "$0")")"
fi
if [ "${module_dir#"/"}" = "$module_dir" ]; then
  module_dir="/data/adb/modules/${package_name}-morphe"
fi

stock_apk="$module_dir/$package_name-stock.apk"
stock_paths="$module_dir/stock-paths.txt"
log="$module_dir/post-fs-data.log"
: > "$log"

log_msg() {
  echo "$*" >> "$log"
}

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
    if [ -f "$path/base.apk" ]; then
      echo "$path/base.apk"
      return
    fi
    find "$path" -maxdepth 1 -name "*.apk" -type f 2>/dev/null | head -n 1
  fi
}

mount_stock_path() {
  target="$(resolve_apk_from_path "$1")"
  if [ -z "$target" ] || [ ! -f "$target" ]; then
    log_msg "Skipping missing stock target: $1"
    return
  fi

  umount -l "$target" 2>/dev/null || true
  if mount -o bind "$stock_apk" "$target" 2>> "$log"; then
    log_msg "Mounted stock APK over $target"
  else
    log_msg "Failed to mount stock APK over $target"
  fi
}

if [ ! -f "$stock_apk" ]; then
  log_msg "Skipping stock mount; missing $stock_apk"
  exit 0
fi

chcon u:object_r:apk_data_file:s0 "$stock_apk" 2>> "$log" || true

if [ ! -f "$stock_paths" ]; then
  log_msg "Skipping stock mount; missing $stock_paths"
  exit 0
fi

while IFS= read -r path; do
  mount_stock_path "$path"
done < "$stock_paths"
