#!/system/bin/sh
package_name=__PKG_NAME__

module_dir="$(dirname "$(readlink -f "$0" 2>/dev/null || echo "$0")")"
if [ ! -f "$module_dir/module.prop" ]; then
  module_dir=/data/adb/modules/__MODULE_ID__
fi

# The root manager shows what the action prints. The description holds how the last boot went,
# see set_status in service.sh, which tells whether the app opens patched. echo ends the line,
# which set_status leaves open as the last one of the file.
echo "$(sed -n 's/^description=//p' "$module_dir/module.prop")"

# TV apps only have a launcher activity for the leanback launcher
for category in android.intent.category.LAUNCHER android.intent.category.LEANBACK_LAUNCHER; do
  activity="$(cmd package resolve-activity --brief --user current -a android.intent.action.MAIN \
    -c "$category" "$package_name" 2>/dev/null | tail -n 1)"
  case "$activity" in
    */*) ;;
    *) continue ;;
  esac
  # am exits with 0 on most errors, so they are told by what it prints
  out="$(am start --user current -a android.intent.action.MAIN -c "$category" -n "$activity" 2>&1)"
  case "$out" in
    *Error*)
      echo "$out"
      exit 1
      ;;
  esac
  echo "Opened $package_name"
  exit 0
done

echo "$package_name is not installed for this user or has no launcher activity"
exit 1
