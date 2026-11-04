/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.manager

import app.morphe.manager.util.MORPHE_API_URL
import kotlin.test.Test
import kotlin.test.assertEquals

class ApkDownloadSourceTest {
    @Test
    fun `a link still on the API is not resolved yet`() {
        assertEquals(ApkDownloadSource.Unresolved, ApkDownloadSource.from(null))
        assertEquals(ApkDownloadSource.Unresolved, ApkDownloadSource.from("$MORPHE_API_URL/v2/web-search/x"))
    }

    @Test
    fun `a GitHub release page names its tag`() {
        assertEquals(
            ApkDownloadSource.GitHub("v1.14.2"),
            ApkDownloadSource.from("https://github.com/owner/repo/releases/tag/v1.14.2")
        )
        assertEquals(
            ApkDownloadSource.GitHub("v1.14.2"),
            ApkDownloadSource.from("https://github.com/owner/repo/releases/tag/v1.14.2/")
        )
    }

    @Test
    fun `a release anchored on the GitHub list of releases names its tag`() {
        assertEquals(
            ApkDownloadSource.GitHub("v1.14.2"),
            ApkDownloadSource.from("https://github.com/owner/repo/releases#release-v1.14.2")
        )
    }

    @Test
    fun `the GitHub list of releases names no release`() {
        assertEquals(ApkDownloadSource.GitHub(null), ApkDownloadSource.from("https://github.com/owner/repo/releases"))
        assertEquals(
            ApkDownloadSource.GitHub(null),
            ApkDownloadSource.from("https://github.com/owner/repo/releases/latest")
        )
    }

    @Test
    fun `a GitHub page other than releases is just another host`() {
        assertEquals(ApkDownloadSource.Other("github.com"), ApkDownloadSource.from("https://github.com/owner/repo"))
    }

    @Test
    fun `a file attached to a GitHub release is downloaded directly`() {
        assertEquals(
            ApkDownloadSource.DirectFile("github.com"),
            ApkDownloadSource.from("https://github.com/owner/repo/releases/download/v1.0/app.apk")
        )
    }

    @Test
    fun `the other download sites keep their own steps`() {
        assertEquals(
            ApkDownloadSource.ApkMirror(onDownloadPage = true),
            ApkDownloadSource.from("https://www.apkmirror.com/apk/a/b/b-1-0-release/b-1-0-android-apk-download/")
        )
        assertEquals(
            ApkDownloadSource.Uptodown,
            ApkDownloadSource.from("https://boost.en.uptodown.com/android/download/123-x")
        )
    }

    @Test
    fun `an APKCube download page is recognized`() {
        assertEquals(
            ApkDownloadSource.ApkCube(versionPinned = true),
            ApkDownloadSource.from("https://apkcube.com/youtube/com.google.android.youtube/download?version=20.12.46")
        )
    }

    @Test
    fun `an APKCube app page stops short of the download`() {
        assertEquals(
            ApkDownloadSource.ApkCube(versionPinned = false),
            ApkDownloadSource.from("https://www.apkcube.com/youtube/com.google.android.youtube")
        )
    }

    @Test
    fun `an APKCube download page without a version is not pinned`() {
        assertEquals(
            ApkDownloadSource.ApkCube(versionPinned = false),
            ApkDownloadSource.from("https://apkcube.com/youtube/com.google.android.youtube/download")
        )
    }

    @Test
    fun `a host only ending in apkcube is not APKCube`() {
        assertEquals(
            ApkDownloadSource.Other("notapkcube.com"),
            ApkDownloadSource.from("https://notapkcube.com/youtube/com.google.android.youtube/download")
        )
    }
}
