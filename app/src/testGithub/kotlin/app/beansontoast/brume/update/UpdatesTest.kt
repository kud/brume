package app.beansontoast.brume.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatesTest {
    private val release = brumeSource(debug = false)
    private val debug = brumeSource(debug = true)
    private val digest = "sha256:" + "ab".repeat(32)

    private fun asset(name: String, repo: String = "kud/brume", tag: String = "v0.1.6", d: String? = digest) =
        ReleaseAsset(name, "https://github.com/$repo/releases/download/$tag/$name", d, 1000)

    private fun rel(tag: String, vararg names: String, prerelease: Boolean = tag.contains("-dev"), draft: Boolean = false) =
        Release(tag, prerelease, draft, names.map { asset(it, tag = tag) })

    private fun v(text: String) = AppVersion.parse(text)!!

    @Test fun `versions order dev builds below their final release`() {
        assertTrue(v("0.1.6-dev.2") < v("0.1.6-dev.10"))
        assertTrue(v("0.1.6-dev.10") < v("0.1.6"))
        assertTrue(v("0.1.5") < v("0.1.6-dev.1"))
        assertEquals("0.1.6-dev.3", v("v0.1.6-dev.3").toString())
        assertNull(AppVersion.parse("0.1"))
        assertNull(AppVersion.parse("0.1.6-beta"))
    }

    @Test fun `each variant follows its own asset, never the fdroid one`() {
        val r = rel("v0.1.6", "brume-0.1.6-fdroid.apk", "brume-0.1.6-debug.apk", "brume-0.1.6.apk")
        assertEquals("brume-0.1.6.apk", ReleaseFeed.assetFor(r, release)?.name)
        assertEquals("brume-0.1.6-debug.apk", ReleaseFeed.assetFor(r, debug)?.name)
        assertNull(ReleaseFeed.assetFor(rel("v0.1.6", "brume-0.1.6-fdroid.apk"), release))
        assertNull(ReleaseFeed.assetFor(rel("v0.1.6", "brume-0.1.6-fdroid.apk"), debug))
        // A dev release today carries only the debug build: the release install skips it, the debug one takes it.
        val dev = listOf(rel("v0.1.0-dev.21", "brume-0.1.0-dev.21-debug.apk"))
        assertEquals(v("0.1.0-dev.21"), ReleaseFeed.pickUpdate(v("0.1.0-dev.20"), dev, debug, Channel.DEV)?.version)
        assertNull(ReleaseFeed.pickUpdate(v("0.1.0-dev.20"), dev, release, Channel.DEV))
        assertNull(ReleaseFeed.pickUpdate(v("0.1.0-dev.20"), dev, debug, Channel.STABLE))
    }

    @Test fun `an asset without a digest or outside the repository's downloads is never offered`() {
        val noDigest = Release("v0.1.6", false, false, listOf(asset("brume-0.1.6.apk", d = null)))
        assertNull(ReleaseFeed.assetFor(noDigest, release))
        val elsewhere = Release("v0.1.6", false, false, listOf(asset("brume-0.1.6.apk", repo = "someone/brume")))
        assertNull(ReleaseFeed.assetFor(elsewhere, release))
    }

    @Test fun `the dev channel takes the newest build, the stable channel only final releases`() {
        val releases = listOf(
            rel("v0.1.7-dev.2", "brume-0.1.7-dev.2.apk"),
            rel("v0.1.6", "brume-0.1.6.apk"),
            rel("v0.1.6-dev.9", "brume-0.1.6-dev.9.apk"),
            rel("v0.1.8", "brume-0.1.8.apk", draft = true),
        )
        assertEquals(v("0.1.7-dev.2"), ReleaseFeed.pickUpdate(v("0.1.5"), releases, release, Channel.DEV)?.version)
        assertEquals(v("0.1.6"), ReleaseFeed.pickUpdate(v("0.1.5"), releases, release, Channel.STABLE)?.version)
        assertNull(ReleaseFeed.pickUpdate(v("0.1.7-dev.2"), releases, release, Channel.DEV))
        assertNull(ReleaseFeed.pickUpdate(v("0.1.6"), releases, release, Channel.STABLE))
    }

    @Test fun `GitHub's release list parses, unknown fields ignored, and junk is null`() {
        val body = """[{"tag_name":"v0.1.6-dev.1","prerelease":true,"draft":false,"id":1,
            "assets":[{"name":"brume-0.1.6-dev.1.apk","browser_download_url":"https://github.com/kud/brume/releases/download/v0.1.6-dev.1/brume-0.1.6-dev.1.apk","digest":"$digest","size":5}]}]"""
        val releases = ReleaseFeed.parse(body)
        assertNotNull(releases)
        assertEquals(v("0.1.6-dev.1"), ReleaseFeed.pickUpdate(v("0.1.5"), releases!!, release, Channel.DEV)?.version)
        assertNull(ReleaseFeed.parse("{\"message\":\"rate limited\"}"))
    }

    @Test fun `only GitHub's hosts over plain https are allowed, redirects included`() {
        assertTrue(Hosts.isAllowed("https://api.github.com/repos/kud/brume/releases"))
        assertFalse(Hosts.isAllowed("http://github.com/kud/brume"))
        assertFalse(Hosts.isAllowed("https://github.com:8443/kud/brume"))
        assertFalse(Hosts.isAllowed("https://user@github.com/kud/brume"))
        assertFalse(Hosts.isAllowed("https://github.com.evil.example/x"))
        assertEquals(
            "https://release-assets.githubusercontent.com/a",
            Hosts.nextHop("https://github.com/kud/brume/releases/download/v1/x.apk", "https://release-assets.githubusercontent.com/a"),
        )
        assertNull(Hosts.nextHop("https://github.com/x", "https://example.com/a"))
        assertNull(Hosts.nextHop("https://github.com/x", null))
    }

    @Test fun `digests parse only as sha256 and compare exactly`() {
        val bytes = Sha256.parse(digest)!!
        assertEquals(32, bytes.size)
        assertTrue(Sha256.matches(bytes, bytes.copyOf()))
        assertFalse(Sha256.matches(bytes, ByteArray(32)))
        assertNull(Sha256.parse("md5:abc"))
        assertNull(Sha256.parse("sha256:abc"))
    }

    @Test fun `a dev build with the shipped versionCode is accepted, anything else is refused`() {
        fun accepts(pkg: String = "app.beansontoast.brume", code: Long = 16, name: String? = "0.1.6-dev.2", expected: String = "0.1.6-dev.2") =
            ArchiveCheck.accepts("app.beansontoast.brume", 16, v("0.1.6-dev.1"), pkg, code, name, v(expected))
        assertTrue(accepts())
        assertTrue(accepts(code = 17))
        assertFalse(accepts(pkg = "app.beansontoast.brume.debug"))
        assertFalse(accepts(code = 15))
        assertFalse(accepts(name = "0.1.6-dev.1", expected = "0.1.6-dev.1"))
        assertFalse(accepts(name = "0.1.6-dev.3"))
        assertFalse(accepts(name = null))
    }

    @Test fun `the automatic check is every 15 minutes on a test build, daily on a final release`() {
        assertEquals(15 * 60 * 1000L, CheckSchedule.intervalFor(v("0.1.6-dev.1")))
        // dev.10 published minutes after dev.9 last asked is seen within the quarter hour.
        assertTrue(CheckSchedule.isDue(0, 15 * 60 * 1000L, CheckSchedule.intervalFor(v("0.1.6-dev.9"))))
        assertFalse(CheckSchedule.isDue(0, 14 * 60 * 1000L, CheckSchedule.intervalFor(v("0.1.6-dev.9"))))
        assertEquals(CheckSchedule.DAY_MS, CheckSchedule.intervalFor(v("0.1.6")))
        assertTrue(CheckSchedule.isDue(null, 0))
        assertFalse(CheckSchedule.isDue(1000, 2000, CheckSchedule.HOUR_MS))
        assertTrue(CheckSchedule.isDue(5000, 1000))
    }

    @Test fun `a set-aside build goes quiet until a newer one is found`() {
        assertEquals(UpdateCue.NONE, UpdateCue.of(v("0.1.6"), null, null))
        assertEquals(UpdateCue.NOTICE, UpdateCue.of(v("0.1.5"), v("0.1.6"), null))
        assertEquals(UpdateCue.QUIET, UpdateCue.of(v("0.1.5"), v("0.1.6"), v("0.1.6")))
        assertEquals(UpdateCue.NOTICE, UpdateCue.of(v("0.1.5"), v("0.1.7"), v("0.1.6")))
    }

    @Test fun `a build announces itself once, the first time it is found`() {
        assertTrue(UpdateCue.announces(v("0.1.6-dev.10"), null))
        assertTrue(UpdateCue.announces(v("0.1.6-dev.10"), v("0.1.6-dev.9")))
        assertFalse(UpdateCue.announces(v("0.1.6-dev.10"), v("0.1.6-dev.10")))
        assertFalse(UpdateCue.announces(v("0.1.6-dev.10"), v("0.1.6-dev.11")))
    }
}
