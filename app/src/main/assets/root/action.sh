#!/system/bin/sh
package_name=__PKG_NAME__
manager_package=__MANAGER_PKG__

user_id="$(am get-current-user 2>/dev/null | tr -d '\r')"
case "$user_id" in
  ''|*[!0-9]*) user_id=0 ;;
esac

if ! pm path --user "$user_id" "$package_name" >/dev/null 2>&1; then
  echo "App not installed: $package_name"
  am start --user "$user_id" -n "$manager_package/app.morphe.manager.MainActivity" >/dev/null 2>&1
  exit 1
fi

activity="$(cmd package resolve-activity --brief --user "$user_id" \
  -a android.intent.action.MAIN -c android.intent.category.LAUNCHER "$package_name" 2>/dev/null \
  | tr -d '\r' | tail -n 1)"

case "$activity" in
  */*) ;;
  *) activity="" ;;
esac

if [ -n "$activity" ] && am start --user "$user_id" -n "$activity" >/dev/null 2>&1; then
  echo "Launched $activity"
  exit 0
fi

if monkey -p "$package_name" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; then
  echo "Launched $package_name"
  exit 0
fi

echo "Failed to launch $package_name"
exit 1
