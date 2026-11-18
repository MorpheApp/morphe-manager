# The installer verifies APK signatures before recording these hashes. Checking the exact copies
# again keeps a changed backup from being exposed to PackageManager during its boot scan.
# The caller sets module_dir, package_name and log.
stock_integrity_failure() {
  echo "Stock APK verification failed: $1" >> "$log"
  return 1
}

verify_stock_apks() {
  stock_integrity_file="$module_dir/stock-apk-sha256.txt"
  if [ ! -s "$stock_integrity_file" ]; then
    stock_integrity_failure "the verification record is missing; reinstall the module in Morphe"
    return 1
  fi

  # Accept only the base and split backup names written by RootInstaller. In particular, a
  # checksum file for a different APK, a repeated entry or a path outside this module proves
  # nothing about the stock copy that would be mounted.
  stock_integrity_records="$(awk -v pkg="$package_name" '
    function invalid() { bad = 1; exit 1 }
    {
      hash = substr($0, 1, 64)
      separator = substr($0, 65, 2)
      name = substr($0, 67)
      base = pkg "-stock.apk"
      prefix = pkg "-stock-"
      if (length(hash) != 64 || hash ~ /[^[:xdigit:]]/ || separator != "  " ||
          name ~ /[^A-Za-z0-9_.-]/ ||
          (name != base && (index(name, prefix) != 1 || name !~ /\.apk$/ ||
                           length(name) <= length(prefix) + 4)) ||
          seen[name]++) invalid()
      if (name == base) base_seen++
      count++
    }
    END {
      if (bad || base_seen != 1) exit 1
      print count
    }
  ' "$stock_integrity_file" 2>> "$log")" || {
    stock_integrity_failure "the verification record is invalid; reinstall the module in Morphe"
    return 1
  }

  # A valid record also has to account for every retained split: silently checking only the base
  # would leave a changed or incompletely staged split unverified.
  stock_integrity_copies=0
  for stock_integrity_apk in "$module_dir/$package_name-stock.apk" "$module_dir/$package_name-stock-"*.apk; do
    [ -f "$stock_integrity_apk" ] || continue
    stock_integrity_copies=$((stock_integrity_copies + 1))
  done
  if [ "$stock_integrity_records" != "$stock_integrity_copies" ]; then
    stock_integrity_failure "the stock backup set changed; reinstall the module in Morphe"
    return 1
  fi

  if ! (cd "$module_dir" && sha256sum -c stock-apk-sha256.txt) >> "$log" 2>&1; then
    stock_integrity_failure "a stock backup changed; reinstall the original app and module in Morphe"
    return 1
  fi
}
