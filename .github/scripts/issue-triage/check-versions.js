// Checks the Morphe version of a bug report.
// Bug reports for an outdated Morphe version are closed with a comment by apply.js, after the duplicate check.
// The version field is free text that also has the version of the app being patched, so every version
// in it is compared with the Morphe releases. Reports where no Morphe release version is found are left
// open, and the analyze job can ask for the version.

const { parseForm } = require('./common.js');

const VERSION_FIELD = 'Version of Morphe (Manager) and the app you are patching';

// A stable version must be the latest stable release.
// Pre-releases are allowed if they are one of the newest ones, or were published recently.
const ALLOWED_PRE_RELEASE_COUNT = 4;
const ALLOWED_PRE_RELEASE_DAYS = 4;

const VERSION = /(\d+\.\d+\.\d+)(?:[\s-]*dev[\s.-]*(\d+))?/gi;

/**
 * Finds all versions such as "v2.7.0-dev.5", "2.7.0 dev 5", "2.6.0" or "21.46.522" in a text,
 * normalized to "2.7.0-dev.5", "2.6.0" or "21.46.522".
 */
function parseVersions(text) {
  return [...(text || '').matchAll(VERSION)].map((match) =>
    match[2] ? `${match[1]}-dev.${match[2]}` : match[1]
  );
}

function releaseVersion(release) {
  return release.tag_name.replace(/^v/, '');
}

/**
 * Returns the latest stable version, the pre-releases that bug reports are accepted for, and all release versions.
 */
function acceptedVersions(releases, now) {
  const published = releases.filter((release) => !release.draft);

  const latestStable = published.find((release) => !release.prerelease);

  const cutoff = now - ALLOWED_PRE_RELEASE_DAYS * 24 * 60 * 60 * 1000;
  const preReleases = new Set(
    published
      .filter((release) => release.prerelease)
      .filter((release, index) => index < ALLOWED_PRE_RELEASE_COUNT || Date.parse(release.published_at) >= cutoff)
      .map(releaseVersion)
  );

  return {
    latestStable: latestStable ? releaseVersion(latestStable) : null,
    preReleases,
    known: new Set(published.map(releaseVersion)),
  };
}

/**
 * Returns "ok", "outdated" or "unknown" for the version field of a bug report.
 * Only versions of Morphe releases are considered, as the field also has the version of the patched app.
 * It is "outdated" only if the field has Morphe release versions and none of them is accepted.
 * Exported separately so it can be tested without the GitHub API.
 */
function versionStatus(text, versions) {
  const { latestStable, preReleases, known } = versions;
  const releases = parseVersions(text).filter((version) => known.has(version));
  if (releases.length === 0) return 'unknown';

  const accepted = releases.some((version) => version === latestStable || preReleases.has(version));
  return accepted ? 'ok' : 'outdated';
}

/**
 * Returns the problems found with the Morphe version of the issue, as comment lines.
 * The list is empty if the version is supported or not known, or if the issue is not a bug report.
 */
async function checkVersions({ github, core, owner, repo, issue }) {
  const fields = parseForm(issue.body);
  // Only bug reports have a version field.
  if (!(VERSION_FIELD in fields)) return [];

  const releases = (await github.rest.repos.listReleases({ owner, repo, per_page: 50 })).data;
  const status = versionStatus(fields[VERSION_FIELD], acceptedVersions(releases, Date.now()));
  if (status !== 'outdated') {
    core.info(`#${issue.number} Morphe version: ${status}`);
    return [];
  }

  core.info(`#${issue.number} is for an outdated Morphe version`);
  return [
    'You are using an outdated version of Morphe.',
    `Please update to the latest version from https://github.com/${owner}/${repo}/releases ` +
      'and open a new issue if the bug still happens.',
  ];
}

module.exports = {
  checkVersions,
  versionStatus,
  acceptedVersions,
  parseVersions,
};
