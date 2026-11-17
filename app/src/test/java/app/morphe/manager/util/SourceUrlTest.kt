/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import kotlin.test.*

/**
 * Every reading of a source link goes through [SourceUrl], so the repository, owner and branch it
 * finds decide the page, avatar, blocklist key and prerelease switch of a source alike.
 */
class SourceUrlTest {

    private fun parse(url: String) = assertNotNull(SourceUrl.parse(url))

    @Test
    fun `raw github link names its repository and branch`() {
        val source = parse("https://raw.githubusercontent.com/Owner/repo/dev/patches-bundle.json")
        assertEquals(CodeHost.GITHUB, source.codeHost)
        assertEquals("https://github.com/Owner/repo", source.repositoryUrl)
        assertEquals("github=Owner/repo", source.repositoryKey)
        assertEquals("dev", source.branch)
    }

    @Test
    fun `raw github link pinned through refs heads names no branch`() {
        val source = parse("https://raw.githubusercontent.com/owner/repo/refs/heads/main/patches-bundle.json")
        assertNull(source.branch)
        assertEquals("github=owner/repo", source.repositoryKey)
    }

    @Test
    fun `github page names its branch only through tree or blob`() {
        assertEquals("beta", parse("https://github.com/owner/repo/tree/beta/bundle").branch)
        assertEquals("main", parse("https://github.com/owner/repo/blob/main/patches-bundle.json").branch)
        assertNull(parse("https://github.com/owner/repo").branch)
    }

    @Test
    fun `github pull request keeps its repository`() {
        val source = parse("https://github.com/MorpheApp/morphe-patches/pull/4915")
        assertEquals("github=MorpheApp/morphe-patches", source.repositoryKey)
        assertNull(source.branch)
    }

    @Test
    fun `gitlab raw link names its repository and branch`() {
        val source = parse("https://gitlab.com/owner/repo/-/raw/dev/patches-bundle.json")
        assertEquals(CodeHost.GITLAB, source.codeHost)
        assertEquals("https://gitlab.com/owner/repo", source.repositoryUrl)
        assertEquals("gitlab=owner/repo", source.repositoryKey)
        assertEquals("dev", source.branch)
    }

    @Test
    fun `gitlab link without a raw file names no branch`() {
        assertNull(parse("https://gitlab.com/owner/repo/-/raw").branch)
        assertNull(parse("https://gitlab.com/owner/repo").branch)
    }

    @Test
    fun `owner is known before the repository is typed`() {
        val source = parse("https://github.com/owner")
        assertEquals("owner", source.owner)
        assertNull(source.repo)
        assertNull(source.repositoryKey)
    }

    @Test
    fun `host is lowercased`() {
        assertEquals(CodeHost.GITHUB, parse("https://GitHub.com/owner/repo").codeHost)
    }

    @Test
    fun `other hosts have no repository`() {
        val source = parse("https://example.com/owner/repo/main/patches-bundle.json")
        assertNull(source.codeHost)
        assertNull(source.owner)
        assertNull(source.repositoryKey)
        assertNull(source.branch)
    }

    @Test
    fun `link without a host is not parsed`() {
        assertNull(SourceUrl.parse("github.com/owner/repo"))
        assertNull(SourceUrl.parse("not a url"))
    }
}
