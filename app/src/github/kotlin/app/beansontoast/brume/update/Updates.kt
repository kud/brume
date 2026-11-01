// SYNC: the shared in-app updater rules, copied from kud/pastille app/src/github/.../update/Updates.kt
// at 49975a8, apart from this header and the package line. It is meant to move to Gamme's
// `kit/updates` unchanged (atelier, knowledge/projects/android-kit); until then, change it in
// pastille first and copy it here, or record why Brume's differs (docs/updates.md, "Drift").
package app.beansontoast.brume.update

import java.net.URI
import java.security.MessageDigest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The in-app updater's rules, with no Android in them. The app asks GitHub's releases API, picks
 * the newest release with an APK for its own build, downloads it only from GitHub's hosts, and
 * installs it only once its SHA-256 matches the digest GitHub reported.
 */

/** `MAJOR.MINOR.PATCH` or `MAJOR.MINOR.PATCH-dev.N`, as the release workflow derives it from the tag. */
data class AppVersion(val major: Int, val minor: Int, val patch: Int, val dev: Int?) : Comparable<AppVersion> {
    /** A dev build sorts below its final release: 0.2.0-dev.25 < 0.2.0-dev.26 < 0.2.0. */
    override fun compareTo(other: AppVersion): Int = compareValuesBy(
        this,
        other,
        { it.major },
        { it.minor },
        { it.patch },
        { it.dev ?: Int.MAX_VALUE },
    )

    val isDev: Boolean get() = dev != null

    override fun toString(): String = "$major.$minor.$patch" + (dev?.let { "-dev.$it" } ?: "")

    companion object {
        private val PATTERN = Regex("""^v?(\d{1,6})\.(\d{1,6})\.(\d{1,6})(-dev(?:\.(\d{1,6}))?)?$""")

        /** A tag (`v0.2.0-dev.26`) or a versionName; a bare `-dev` reads as dev 0. Null otherwise. */
        fun parse(text: String): AppVersion? {
            val match = PATTERN.matchEntire(text.trim()) ?: return null
            val (major, minor, patch, devPart, dev) = match.destructured
            return AppVersion(
                major.toInt(),
                minor.toInt(),
                patch.toInt(),
                if (devPart.isEmpty()) null else dev.ifEmpty { "0" }.toInt(),
            )
        }
    }
}

/** Which releases count: [STABLE] final releases only, [DEV] pre-releases too. */
enum class Channel { STABLE, DEV }

/**
 * What differs between apps: the repository, and which asset in a release is this build's APK.
 * [apkFor] gets an asset name and says whether it is the APK this build updates from.
 */
class UpdateSource(val ownerRepo: String, val apkFor: (String) -> Boolean) {
    val feedUrl: String get() = "https://api.github.com/repos/$ownerRepo/releases?per_page=30"
}

data class ReleaseAsset(val name: String, val url: String, val digest: String?, val size: Long)

data class Release(val tag: String, val prerelease: Boolean, val draft: Boolean, val assets: List<ReleaseAsset>)

/** What the updater offers: a newer [version], its APK and the SHA-256 GitHub reported for it. */
class Update(val version: AppVersion, val tag: String, val asset: ReleaseAsset, val sha256: ByteArray) {
    override fun toString(): String = "Update($version, ${asset.name})"
}

object ReleaseFeed {
    @Serializable
    private data class WireAsset(
        val name: String,
        @SerialName("browser_download_url") val url: String,
        val digest: String? = null,
        val size: Long = 0,
    )

    @Serializable
    private data class WireRelease(
        @SerialName("tag_name") val tag: String,
        val prerelease: Boolean = false,
        val draft: Boolean = false,
        val assets: List<WireAsset> = emptyList(),
    )

    private val json = Json { ignoreUnknownKeys = true }

    /** GitHub's `/releases` list (pre-releases included), or null when the body is not that list. */
    fun parse(body: String): List<Release>? = runCatching {
        json.decodeFromString<List<WireRelease>>(body).map { release ->
            Release(
                release.tag,
                release.prerelease,
                release.draft,
                release.assets.map { ReleaseAsset(it.name, it.url, it.digest, it.size) },
            )
        }
    }.getOrNull()

    /** This build's APK in [release]: named for it, served from the repository's download path, with a SHA-256. */
    fun assetFor(release: Release, source: UpdateSource): ReleaseAsset? = release.assets.firstOrNull { asset ->
        source.apkFor(asset.name) &&
            isReleaseDownload(asset.url, source) &&
            Sha256.parse(asset.digest) != null
    }

    /** The newest release above [current] on [channel] with an APK for this build; drafts never count. */
    fun pickUpdate(current: AppVersion, releases: List<Release>, source: UpdateSource, channel: Channel): Update? = releases
        .asSequence()
        .filter { !it.draft }
        .mapNotNull { release ->
            val version = AppVersion.parse(release.tag) ?: return@mapNotNull null
            if (version <= current) return@mapNotNull null
            if (channel == Channel.STABLE && (version.isDev || release.prerelease)) return@mapNotNull null
            val asset = assetFor(release, source) ?: return@mapNotNull null
            Update(version, release.tag, asset, Sha256.parse(asset.digest)!!)
        }
        .maxByOrNull { it.version }

    private fun isReleaseDownload(url: String, source: UpdateSource): Boolean {
        val uri = Hosts.parse(url) ?: return false
        return uri.host.equals("github.com", ignoreCase = true) &&
            uri.rawPath.startsWith("/${source.ownerRepo}/releases/download/")
    }
}

/**
 * The downloaded file is handed to the installer only if it is this very app, at [archiveVersion]
 * the version GitHub named, and not an older build. A dev build keeps the shipped versionCode, so an
 * equal code is allowed (Android installs it over the same code) and the name decides.
 */
object ArchiveCheck {
    fun accepts(
        installedPackage: String,
        installedCode: Long,
        installedVersion: AppVersion,
        archivePackage: String?,
        archiveCode: Long,
        archiveVersionName: String?,
        expected: AppVersion,
    ): Boolean {
        val archiveVersion = archiveVersionName?.let(AppVersion::parse) ?: return false
        return archivePackage == installedPackage &&
            archiveCode >= installedCode &&
            archiveVersion == expected &&
            archiveVersion > installedVersion
    }
}

object Sha256 {
    private val PATTERN = Regex("^sha256:([0-9a-fA-F]{64})$")

    /** GitHub's `digest` field (`sha256:<64 hex>`) as bytes, or null for anything else. */
    fun parse(digest: String?): ByteArray? {
        val hex = PATTERN.matchEntire(digest?.trim() ?: return null)?.groupValues?.get(1) ?: return null
        return ByteArray(32) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }

    fun matches(expected: ByteArray, actual: ByteArray): Boolean =
        expected.size == 32 && MessageDigest.isEqual(expected, actual)
}

/** The only places the updater talks to: HTTPS, default port, GitHub's API, site and asset hosts. */
object Hosts {
    val ALLOWED = setOf(
        "api.github.com",
        "github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
    )

    /** Redirects followed by hand, each hop checked; GitHub needs two at most. */
    const val MAX_REDIRECTS = 5

    fun isAllowed(url: String): Boolean = parse(url) != null

    /** Where a redirect from [from] to [location] leads, or null if that is not an allowed host. */
    fun nextHop(from: String, location: String?): String? {
        if (location.isNullOrBlank()) return null
        val resolved = runCatching { URI(from).resolve(location.trim()).toString() }.getOrNull() ?: return null
        return resolved.takeIf { isAllowed(it) }
    }

    internal fun parse(url: String): URI? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val ok = uri.scheme == "https" &&
            uri.rawUserInfo == null &&
            (uri.port == -1 || uri.port == 443) &&
            host in ALLOWED
        return uri.takeIf { ok }
    }
}

object CheckSchedule {
    const val MINUTE_MS = 60L * 1000
    const val HOUR_MS = 60 * MINUTE_MS
    const val DAY_MS = 24 * HOUR_MS

    /** How often a dev build may ask: 4 requests an hour per app, against GitHub's 60 per IP unauthenticated. */
    const val DEV_INTERVAL_MS = 15 * MINUTE_MS

    /**
     * A dev build, replaced several times a day and often minutes after the last one, is checked at
     * most every 15 minutes (Maya, 2026-10-30; it was hourly, which hid a build published right after
     * the app last asked); a final release daily.
     */
    fun intervalFor(installed: AppVersion): Long = if (installed.isDev) DEV_INTERVAL_MS else DAY_MS

    /** The automatic check runs at most once per [intervalMs]; a clock moved backwards counts as due. */
    fun isDue(lastCheckMs: Long?, nowMs: Long, intervalMs: Long = DAY_MS): Boolean =
        lastCheckMs == null || nowMs < lastCheckMs || nowMs - lastCheckMs >= intervalMs
}

/**
 * How loudly a newer build makes itself known. [NONE]: nothing newer is known. [QUIET]: a newer
 * build was set aside with "Later", so only About says it. [NOTICE]: also the list's ⋮ menu.
 * A newer build than the one set aside notices again.
 */
enum class UpdateCue {
    NONE, QUIET, NOTICE;

    companion object {
        fun of(installed: AppVersion, found: AppVersion?, setAside: AppVersion?): UpdateCue = when {
            found == null || found <= installed -> NONE
            setAside != null && found <= setAside -> QUIET
            else -> NOTICE
        }

        /** The once-per-build message: only a build newer than any found before announces itself. */
        fun announces(found: AppVersion, previouslyFound: AppVersion?): Boolean =
            previouslyFound == null || found > previouslyFound
    }
}
