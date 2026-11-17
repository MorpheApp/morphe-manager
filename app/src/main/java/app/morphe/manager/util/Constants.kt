/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import app.morphe.manager.BuildConfig
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

const val tag = "Morphe Manager"

// Code hosts a source or a release can live on

const val GITHUB_HOST = "github.com"
const val GITHUB_RAW_HOST = "raw.githubusercontent.com"
const val GITHUB_API_HOST = "api.github.com"
const val GITLAB_HOST = "gitlab.com"

/** What a source link looks like, shown in a field waiting for one. */
const val REMOTE_URL_PLACEHOLDER = "$GITHUB_HOST/owner/repo"

// Morphe on GitHub

const val MORPHE_GITHUB_ORG = "MorpheApp"
const val MORPHE_GITHUB_URL = "https://$GITHUB_HOST/$MORPHE_GITHUB_ORG"
const val MANAGER_REPO_URL = "$MORPHE_GITHUB_URL/morphe-manager"
const val SOURCE_REPO_URL = "$MORPHE_GITHUB_URL/morphe-patches"

const val SOURCE_NAME = "Morphe Patches"

/** Key the releases of [SOURCE_REPO_URL] are signed with, see PatchBundleSignature. */
const val SOURCE_SIGNING_KEY = "g8Ys9leTu2yeixojeR35HrNkK6eOSLpA6nNIizRutqI="

private const val MANAGER_RAW_URL = "https://$GITHUB_RAW_HOST/$MORPHE_GITHUB_ORG/morphe-manager/refs/heads"

/** Raw GitHub URL for the stable manager release JSON (main branch). */
const val MANAGER_RELEASE_JSON_URL = "$MANAGER_RAW_URL/main/app-release.json"

/** Raw GitHub URL for the pre-release manager release JSON (dev branch). */
const val MANAGER_PRERELEASE_JSON_URL = "$MANAGER_RAW_URL/dev/app-release.json"

/**
 * Controls whether manager updates are fetched directly from JSON files in the repository instead
 * of using the GitHub API.
 */
const val USE_MANAGER_DIRECT_JSON = true

/**
 * Controls whether patches are fetched directly from JSON files in the repository instead of using
 * the Morphe API.
 */
const val USE_PATCHES_DIRECT_JSON = true

// Morphe website and API

const val MORPHE_WEBSITE_HOST = "morphe.software"
const val MORPHE_WEBSITE_URL = "https://$MORPHE_WEBSITE_HOST"
const val MORPHE_API_URL = "https://api.$MORPHE_WEBSITE_HOST"
const val COMMUNITY_PATCHES_URL = "https://morphe-patches.software"

/** Sent on every request to [MORPHE_API_URL]. */
const val MORPHE_USER_AGENT = "Morphe-Manager/${BuildConfig.VERSION_CODE}"

/** Website page behind add-source links: ?github|gitlab=owner/repo(&name=...). */
const val ADD_SOURCE_PATH = "/add-source"

const val BLOCKED_SOURCES_URL = "$MORPHE_API_URL/v2/blocked-sources"

/** Who works on Morphe and who backs it, see [app.morphe.manager.domain.repository.PeopleRepository]. */
const val PEOPLE_URL = "$MORPHE_API_URL/v2/people"

// Timings

/**
 * Timeout applied to a single uninstall step when running as part of a batch.
 * The system uninstall UI can block indefinitely if the user leaves it open;
 * this keeps the batch making forward progress.
 */
val BATCH_UNINSTALL_TIMEOUT: Duration = 2.minutes

/**
 * Window used to collect package add, remove and replace broadcasts into a single home refresh.
 * A store updating several apps emits them in bursts, and each refresh re-inspects tracked apps.
 */
const val PACKAGE_CHANGE_DEBOUNCE_MS = 400L

// Installers

const val PLAY_STORE_INSTALLER_PACKAGE = "com.android.vending"

const val AOSP_INSTALLER_PACKAGE        = "com.google.android.packageinstaller"
const val AOSP_INSTALLER_PACKAGE_LEGACY = "com.android.packageinstaller"
const val AOSP_INSTALLER_LABEL          = "Package installer"

// Attribution left behind by anything running as the shell user, which is how Shizuku installs
const val SHELL_INSTALLER_PACKAGE = "com.android.shell"

// File types

const val APK_MIMETYPE         = "application/vnd.android.package-archive"
const val JSON_MIMETYPE        = "application/json"
const val BIN_MIMETYPE         = "application/octet-stream"
const val TEXT_MIMETYPE        = "text/plain"
const val MPP_MIMETYPE         = "application/vnd.ms-project"
const val IMAGE_MIMETYPE       = "image/*"
const val APPLICATION_MIMETYPE = "application/*"
const val WILDCARD_MIMETYPE    = "*/*"

/** Extension of a patch bundle file. */
const val MPP_EXTENSION = "mpp"

/** File extensions recognized as APK-family archives that Morphe can patch. */
val APK_EXTENSIONS = setOf("apk", "apks", "xapk", "apkm")

val APK_FILE_MIME_TYPES = arrayOf(
    BIN_MIMETYPE,
    APK_MIMETYPE,
    // ApkMirror split files of "app-whatever123_apkmirror.com.apk" regularly misclassify
    // the file as an application or something incorrect. Renaming the file and
    // removing "apkmirror.com" from the file name fixes the issue, but that's not something the
    // end user will know or should have to do. Instead, show all files to ensure the user can
    // always select no matter what file naming ApkMirror uses.
    APPLICATION_MIMETYPE,
)

val MPP_FILE_MIME_TYPES = arrayOf(
    BIN_MIMETYPE,
    MPP_MIMETYPE,
    WILDCARD_MIMETYPE
)
