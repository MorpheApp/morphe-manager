#!/usr/bin/env bash
set -euo pipefail

VERSION="$1"

echo "Preparing release v${VERSION}..."

# 1. Update the version in app/gradle.properties
sed -i "s/^version\s*=.*/version = ${VERSION}/" app/gradle.properties
echo "Updated app/gradle.properties to version ${VERSION}"

# 2. Build the release APK with the correct version baked in.
#    Stop any existing daemon so a fresh one picks up the updated gradle.properties.
chmod +x ./gradlew
./gradlew --stop || true
./gradlew assembleRelease --stacktrace

# 3. Find the built APK and rename it to the expected release name
RELEASE_DIR="app/build/outputs/apk/release"
APK_SRC=$(find "${RELEASE_DIR}" -name '*-release.apk' | head -1)

if [ -z "${APK_SRC}" ]; then
  echo "ERROR: No *-release.apk found in ${RELEASE_DIR}"
  ls -la "${RELEASE_DIR}" || true
  exit 1
fi

APK_DST="${RELEASE_DIR}/morphe-manager-${VERSION}.apk"
mv "${APK_SRC}" "${APK_DST}"
echo "Renamed APK to ${APK_DST}"

# 4. GPG-sign the APK
gpg --armor --detach-sign "${APK_DST}"
echo "Signed ${APK_DST}"

echo "Release v${VERSION} prepared successfully."
