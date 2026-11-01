package app.beansontoast.brume.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import app.beansontoast.brume.BuildConfig
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Why a step failed, each said in words by Settings › About and the update sheet (never colour alone). */
enum class UpdateProblem { NETWORK, FEED, DOWNLOAD, DIGEST, PACKAGE, INSTALL }

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val update: Update) : UpdateState
    /** [percent] is null while GitHub has not said how big the file is. */
    data class Downloading(val update: Update, val percent: Int?) : UpdateState
    /** Downloaded and verified; Android must first let Brume install packages. */
    data class NeedsPermission(val update: Update) : UpdateState
    data class Installing(val update: Update) : UpdateState
    data class Failed(val problem: UpdateProblem, val update: Update?, val detail: String? = null) : UpdateState
}

/**
 * The github build's updater (docs/updates.md; pastille's docs/adr/0001-in-app-updates.md). Asks GitHub's releases API on a
 * tap, and when the app opens at most every 15 minutes (dev build) or daily (final release);
 * downloads only on a tap, only from GitHub's hosts; installs only a file whose SHA-256 matches GitHub's digest
 * and that is a newer build of this very app, through PackageInstaller, which asks for
 * confirmation. It sends nothing but the request itself: no snippet, no clipboard, no identifier.
 */
class Updater private constructor(private val context: Context) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val busy = Mutex()
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val current: AppVersion = AppVersion.parse(BuildConfig.VERSION_NAME) ?: AppVersion(0, 0, 0, 0)

    /**
     * Brume's drift from pastille: every github build updates, each following its own variant
     * ([brumeSource]): a `.debug` install takes the next `-debug.apk`, the release install the
     * release asset. Dev releases ship the debug build until the release key exists.
     */
    val isAvailable: Boolean get() = true

    private val source = brumeSource(debug = BuildConfig.DEBUG)

    /** Dev pre-releases: offered to a dev install, never to a final one (no switch yet; developer mode will carry it). */
    private val _channel = MutableStateFlow(readChannel())
    val channel: StateFlow<Channel> = _channel.asStateFlow()

    private val _found = MutableStateFlow(readVersion(KEY_FOUND))
    private val _setAside = MutableStateFlow(readVersion(KEY_SET_ASIDE))
    private val _cue = MutableStateFlow(UpdateCue.of(current, _found.value, _setAside.value))
    val cue: StateFlow<UpdateCue> = _cue.asStateFlow()
    val found: StateFlow<AppVersion?> = _found.asStateFlow()

    private val _lastCheck = MutableStateFlow(prefs.getLong(KEY_LAST_CHECK, -1L).takeIf { it >= 0 })
    val lastCheck: StateFlow<Long?> = _lastCheck.asStateFlow()

    fun setChannel(channel: Channel) {
        prefs.edit().putString(KEY_CHANNEL, channel.name).apply()
        _channel.value = channel
    }

    /** "Later": the sheet stops opening by itself for this build; About still offers it. A newer build notices again. */
    fun setAside(version: AppVersion) {
        prefs.edit().putString(KEY_SET_ASIDE, version.toString()).apply()
        _setAside.value = version
        refreshCue()
    }

    /**
     * The automatic check, when Brume comes up: at most every 15 minutes on a dev build, daily
     * otherwise ([CheckSchedule.intervalFor]). Returns a newer build only the first time it is found
     * ([UpdateCue.announces]), so the sheet opens by itself once; after that About carries it.
     */
    suspend fun checkIfDue(): Update? {
        if (!isAvailable) return null
        if (!CheckSchedule.isDue(_lastCheck.value, System.currentTimeMillis(), CheckSchedule.intervalFor(current))) return null
        val before = _found.value
        val update = check() ?: return null
        return update.takeIf { UpdateCue.announces(it.version, before) }
    }

    /** Outlives any screen, so closing the update sheet never abandons a download halfway. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * One tap means install (the sheet's Download, or About's Update): downloads
     * the known build, asking GitHub first if this run doesn't hold one, then installs; Android
     * still asks to confirm. After a failure it resumes at the step that failed.
     */
    fun startInstall() {
        scope.launch { installNow() }
    }

    internal suspend fun installNow() {
        val update = when (val now = _state.value) {
            is UpdateState.Available -> now.update
            is UpdateState.NeedsPermission -> return install(now.update)
            is UpdateState.Failed -> when {
                now.update != null && now.problem == UpdateProblem.INSTALL -> return install(now.update)
                now.update != null -> now.update
                else -> check()
            }
            UpdateState.Idle, UpdateState.UpToDate -> check()
            UpdateState.Checking, is UpdateState.Downloading, is UpdateState.Installing -> null
        }
        update?.let { downloadAndInstall(it) }
    }

    /** Asks GitHub now. Leaves a download or install in progress alone. */
    suspend fun check(): Update? {
        if (!isAvailable || busy.isLocked) return null
        return busy.withLock {
            _state.value = UpdateState.Checking
            val outcome = withContext(Dispatchers.IO) { runCatching { fetchText(source.feedUrl) } }
            val now = System.currentTimeMillis()
            prefs.edit().putLong(KEY_LAST_CHECK, now).apply()
            _lastCheck.value = now
            val body = outcome.getOrElse {
                _state.value = UpdateState.Failed(UpdateProblem.NETWORK, null)
                return@withLock null
            }
            val releases = ReleaseFeed.parse(body) ?: run {
                _state.value = UpdateState.Failed(UpdateProblem.FEED, null)
                return@withLock null
            }
            val update = ReleaseFeed.pickUpdate(current, releases, source, _channel.value)
            _state.value = if (update == null) UpdateState.UpToDate else UpdateState.Available(update)
            rememberFound(update?.version)
            if (update == null) withContext(Dispatchers.IO) { apkFile().delete() }
            update
        }
    }

    /** Downloads [update], verifies it, then installs it (or asks for the install permission first). */
    suspend fun downloadAndInstall(update: Update) {
        if (busy.isLocked) return
        busy.withLock {
            val file = apkFile()
            val problem = withContext(Dispatchers.IO) { download(update, file) }
            if (problem != null) {
                file.delete()
                _state.value = UpdateState.Failed(problem, update)
                return
            }
        }
        install(update)
    }

    /** Installs the verified file, or says Android's permission is needed first. */
    fun install(update: Update) {
        // The sheet and About may both come back from Android's permission page: one session only.
        if (_state.value is UpdateState.Installing) return
        val file = apkFile()
        if (!file.isFile) {
            _state.value = UpdateState.Failed(UpdateProblem.DOWNLOAD, update)
            return
        }
        if (!context.packageManager.canRequestPackageInstalls()) {
            _state.value = UpdateState.NeedsPermission(update)
            return
        }
        _state.value = UpdateState.Installing(update)
        runCatching { commit(file) }.onFailure {
            _state.value = UpdateState.Failed(UpdateProblem.INSTALL, update, it.message)
        }
    }

    /** Android's "Install unknown apps" page for Brume. */
    fun permissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    internal fun onInstallFinished(status: Int, message: String?) {
        val update = (state.value as? UpdateState.Installing)?.update
        _state.value = when (status) {
            PackageInstaller.STATUS_SUCCESS -> UpdateState.UpToDate
            PackageInstaller.STATUS_FAILURE_ABORTED -> update?.let { UpdateState.Available(it) } ?: UpdateState.Idle
            else -> UpdateState.Failed(UpdateProblem.INSTALL, update, message)
        }
    }

    private fun rememberFound(found: AppVersion?) {
        prefs.edit().apply { if (found == null) remove(KEY_FOUND) else putString(KEY_FOUND, found.toString()) }.apply()
        _found.value = found
        refreshCue()
    }

    private fun refreshCue() {
        _cue.value = UpdateCue.of(current, _found.value, _setAside.value)
    }

    private fun readVersion(key: String): AppVersion? = prefs.getString(key, null)?.let { AppVersion.parse(it) }

    private fun readChannel(): Channel =
        prefs.getString(KEY_CHANNEL, null)?.let { runCatching { Channel.valueOf(it) }.getOrNull() }
            ?: if (current.isDev) Channel.DEV else Channel.STABLE

    private fun fetchText(url: String): String = open(url, api = true).let { connection ->
        try {
            connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            connection.disconnect()
        }
    }

    private fun download(update: Update, file: File): UpdateProblem? {
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            val connection = open(update.asset.url, api = false)
            try {
                val total = connection.contentLengthLong.takeIf { it > 0 } ?: update.asset.size.takeIf { it > 0 }
                if (total != null && total > MAX_APK_BYTES) return UpdateProblem.PACKAGE
                file.parentFile?.mkdirs()
                var read = 0L
                var shown = -1
                _state.value = UpdateState.Downloading(update, total?.let { 0 })
                connection.inputStream.use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            read += n
                            if (read > MAX_APK_BYTES) return UpdateProblem.PACKAGE
                            digest.update(buffer, 0, n)
                            output.write(buffer, 0, n)
                            val percent = total?.let { ((read * 100) / it).toInt().coerceIn(0, 100) }
                            if (percent != null && percent != shown) {
                                shown = percent
                                _state.value = UpdateState.Downloading(update, percent)
                            }
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }
        } catch (_: IOException) {
            return UpdateProblem.DOWNLOAD
        }
        if (!Sha256.matches(update.sha256, digest.digest())) return UpdateProblem.DIGEST
        return if (isThisAppAt(file, update.version)) null else UpdateProblem.PACKAGE
    }

    private fun isThisAppAt(file: File, expected: AppVersion): Boolean {
        val pm = context.packageManager
        val archive: PackageInfo = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(file.path, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(file.path, 0)
        }) ?: return false
        return ArchiveCheck.accepts(
            installedPackage = context.packageName,
            installedCode = BuildConfig.VERSION_CODE.toLong(),
            installedVersion = current,
            archivePackage = archive.packageName,
            archiveCode = archive.longVersionCode,
            archiveVersionName = archive.versionName,
            expected = expected,
        )
    }

    /** GET [url] over HTTPS with the system trust store, following redirects by hand, each to an allowed host. */
    private fun open(url: String, api: Boolean): HttpURLConnection {
        var target = url
        repeat(Hosts.MAX_REDIRECTS + 1) {
            if (!Hosts.isAllowed(target)) throw IOException("host not allowed")
            val connection = URL(target).openConnection() as HttpsURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.useCaches = false
            connection.setRequestProperty("User-Agent", "brume/${BuildConfig.VERSION_NAME}")
            if (api) {
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            }
            val code = connection.responseCode
            if (code in 300..399) {
                val next = Hosts.nextHop(target, connection.getHeaderField("Location"))
                connection.disconnect()
                target = next ?: throw IOException("redirect not allowed")
                return@repeat
            }
            if (code !in 200..299) {
                connection.disconnect()
                throw IOException("HTTP $code")
            }
            return connection
        }
        throw IOException("too many redirects")
    }

    private fun commit(file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
        }
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { session ->
                session.openWrite("brume.apk", 0, file.length()).use { out ->
                    file.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    // The installer fills in the status extras, so the intent must stay mutable; it is explicit.
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                val callback = PendingIntent.getBroadcast(
                    context,
                    id,
                    Intent(context, UpdateInstallReceiver::class.java).setPackage(context.packageName),
                    flags,
                )
                session.commit(callback.intentSender)
            }
        } catch (e: Exception) {
            runCatching { installer.abandonSession(id) }
            throw e
        }
    }

    private fun apkFile(): File = File(File(context.cacheDir, "updates"), "brume-update.apk")

    companion object {
        private const val PREFS = "updates"
        private const val KEY_LAST_CHECK = "last_check_ms"
        private const val KEY_FOUND = "found_version"
        private const val KEY_SET_ASIDE = "set_aside_version"
        private const val KEY_CHANNEL = "channel"
        private const val MAX_APK_BYTES = 100L * 1024 * 1024

        @Volatile private var instance: Updater? = null

        fun forContext(context: Context): Updater =
            instance ?: synchronized(this) { instance ?: Updater(context.applicationContext).also { instance = it } }
    }
}
