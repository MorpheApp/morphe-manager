/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.domain.bundles

import app.morphe.manager.domain.bundles.RemotePatchBundle.Companion.inferIssuesUrlFromEndpoint
import app.morphe.manager.domain.bundles.RemotePatchBundle.Companion.issuesUrlForRepoUrl
import kotlin.test.*

/**
 * The "report an issue" button targets the repository's issues page, so the endpoint must map
 * to the right host layout (GitHub /issues, GitLab /-/issues) and to nothing when unknown.
 */
class IssuesUrlInferenceTest {

    @Test
    fun `github raw endpoint maps to issues page`() {
        assertEquals(
            "https://github.com/jkennethcarino/adobo/issues",
            inferIssuesUrlFromEndpoint(
                "https://raw.githubusercontent.com/jkennethcarino/adobo/dev/patches-bundle.json"
            )
        )
    }

    @Test
    fun `github release endpoint maps to issues page`() {
        assertEquals(
            "https://github.com/crimera/piko/issues",
            inferIssuesUrlFromEndpoint(
                "https://github.com/crimera/piko/releases/download/v3.9.0-dev.7/patches-3.9.0-dev.7.mpp"
            )
        )
    }

    @Test
    fun `gitlab endpoint maps to issues page`() {
        assertEquals(
            "https://gitlab.com/owner/repo/-/issues",
            inferIssuesUrlFromEndpoint("https://gitlab.com/owner/repo/-/raw/main/patches-bundle.json")
        )
    }

    @Test
    fun `unknown host has no issues page`() {
        assertNull(
            inferIssuesUrlFromEndpoint("https://example.com/patches/patches-bundle.json")
        )
    }

    @Test
    fun `malformed endpoint has no issues page`() {
        assertNull(inferIssuesUrlFromEndpoint("not a url"))
    }

    @Test
    fun `manifest declared repository gets the host issues path`() {
        assertEquals(
            "https://github.com/owner/repo/issues",
            issuesUrlForRepoUrl("https://github.com/owner/repo")
        )
        assertEquals(
            "https://gitlab.com/owner/repo/-/issues",
            issuesUrlForRepoUrl("https://gitlab.com/owner/repo")
        )
        assertNull(issuesUrlForRepoUrl("https://patches.example.com/owner/repo"))
    }
}
