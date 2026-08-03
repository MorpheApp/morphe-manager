#!/usr/bin/env bash
set -euo pipefail

VERSION="$1"
RELEASE_JSON="app-release.json"
BRANCH="${GITHUB_REF_NAME:-$(git rev-parse --abbrev-ref HEAD)}"

read_field() {
  node -e "console.log(JSON.parse(require('fs').readFileSync('${RELEASE_JSON}','utf8'))['$1'] ?? '')"
}

# The manager polls app-release.json to decide whether an update exists, so the file must not
# reach the branch until the APK it points at can actually be downloaded. semantic-release
# builds the file during prepare, this step publishes it once the release assets are live.
JSON_VERSION=$(read_field version)

# The file is carried in the working tree between phases, so refuse to publish anything that
# does not describe the release we just made rather than committing a stale link
if [ "${JSON_VERSION}" != "${VERSION}" ]; then
  echo "ERROR: ${RELEASE_JSON} describes '${JSON_VERSION}', expected '${VERSION}'"
  exit 1
fi

DOWNLOAD_URL=$(read_field download_url)
SIGNATURE_URL=$(read_field signature_download_url)

APK_ATTEMPTS=30
SIGNATURE_ATTEMPTS=6
RETRY_DELAY=10

wait_for_asset() {
  local url="$1"
  local attempts="$2"
  local attempt

  echo "Waiting for ${url}"
  for ((attempt = 1; attempt <= attempts; attempt++)); do
    if curl -sfIL -o /dev/null "${url}"; then
      echo "Available after ${attempt} attempt(s)"
      return 0
    fi
    if [ "${attempt}" -lt "${attempts}" ]; then
      sleep "${RETRY_DELAY}"
    fi
  done

  echo "ERROR: ${url} is still unavailable after $((attempts * RETRY_DELAY))s"
  return 1
}

wait_for_asset "${DOWNLOAD_URL}" "${APK_ATTEMPTS}"

# The signature uploads alongside the APK, so it needs a far shorter budget of its own
if [ -n "${SIGNATURE_URL}" ]; then
  wait_for_asset "${SIGNATURE_URL}" "${SIGNATURE_ATTEMPTS}"
fi

git config user.name "${GIT_AUTHOR_NAME:-github-actions[bot]}"
git config user.email "${GIT_AUTHOR_EMAIL:-41898282+github-actions[bot]@users.noreply.github.com}"

git add "${RELEASE_JSON}"

if git diff --cached --quiet; then
  echo "No release JSON changes to publish"
  exit 0
fi

git commit -m "chore: Publish release v${VERSION} [skip ci]"

# The release commit was pushed moments ago, so rebase once if the branch moved underneath us
if ! git push origin "HEAD:${BRANCH}"; then
  git pull --rebase origin "${BRANCH}"
  git push origin "HEAD:${BRANCH}"
fi

echo "Published ${RELEASE_JSON} for v${VERSION}"
