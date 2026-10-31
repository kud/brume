package app.beansontoast.brume.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.room.InvalidationTracker
import androidx.room.withTransaction
import app.beansontoast.brume.BuildConfig
import app.beansontoast.brume.Settings
import app.beansontoast.brume.Modules
import app.beansontoast.brume.core.export.ExportFile
import app.beansontoast.brume.core.export.ExportFormat
import app.beansontoast.brume.core.export.NewerSchemaException
import app.beansontoast.brume.core.export.SettingsV1
import app.beansontoast.brume.core.sync.SyncMerge
import app.beansontoast.brume.data.BrumeDatabase
import app.beansontoast.brume.data.Journal
import app.beansontoast.brume.playback.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant

/** Why the last sync or import didn't work, said in words on the screen. */
enum class SyncProblem { UNREACHABLE, NEWER, NOT_BRUME }

data class SyncState(
    val folderName: String? = null,
    val hasFolder: Boolean = false,
    val auto: Boolean = false,
    val lastSyncedAt: Long? = null,
    val problem: SyncProblem? = null,
)

/** What an import did: rows taken, or why nothing changed. */
sealed interface ImportResult {
    data class Done(val days: Int, val sessions: Int) : ImportResult
    data class Failed(val problem: SyncProblem) : ImportResult
}

/**
 * Settings › Back up and sync (atelier ADR 0001, pastille's folder sync). The person picks a folder
 * once; Brume keeps brume-sync.json there and merges with it after every change, when the app comes
 * to the front and when it leaves. The storage app does the uploading; Brume has no network.
 *
 * Triggers, no scheduler: a write to the journal's tables, a change to a synced setting, and the
 * activity's start and stop. Requests settle for [DEBOUNCE_MS], then one merge runs.
 */
class Sync(
    context: Context,
    private val database: BrumeDatabase,
    private val journal: Journal,
    private val settings: Settings,
    private val player: Player,
    private val scope: CoroutineScope,
) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("sync", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val _state = MutableStateFlow(readState())
    val state: StateFlow<SyncState> = _state

    private val folder: Uri? get() = prefs.getString(FOLDER, null)?.let(Uri::parse)
    private val active get() = folder != null && prefs.getBoolean(AUTO, false)

    @OptIn(FlowPreview::class)
    fun start() {
        database.invalidationTracker.addObserver(object : InvalidationTracker.Observer(arrayOf("check_ins", "sessions")) {
            override fun onInvalidated(tables: Set<String>) = request()
        })
        scope.launch { settings.nightHours.drop(1).collect { request() } }
        scope.launch { settings.modules.drop(1).collect { request() } }
        scope.launch { player.ui.map { it.tones.pitch to it.tones.trims }.distinctUntilChanged().drop(1).collect { request() } }
        // A sound stopping is when tones skipped while it played can come in.
        scope.launch { player.ui.map { it.playing }.distinctUntilChanged().drop(1).collect { if (!it) request() } }
        scope.launch { requests.debounce(DEBOUNCE_MS).collect { runSync() } }
    }

    /** Asks for a merge soon. Does nothing unless a folder is chosen and sync is on. */
    fun request() { if (active) requests.tryEmit(Unit) }

    /** The app left the screen: merge now rather than wait for the debounce. */
    fun flush() { if (active) scope.launch { runSync() } }

    /**
     * Starts syncing with [tree]: finds or creates the file there and merges at once. Returns false
     * when the folder can't be opened; then nothing changes and any previous folder carries on.
     */
    suspend fun chooseFolder(tree: Uri): Boolean {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val ok = runCatching {
            app.contentResolver.takePersistableUriPermission(tree, flags)
            withContext(Dispatchers.IO) { SyncFolder.resolve(app, tree) }
        }.isSuccess
        if (!ok) return false
        folder?.takeIf { it != tree }?.let { old -> runCatching { app.contentResolver.releasePersistableUriPermission(old, flags) } }
        prefs.edit().putString(FOLDER, tree.toString()).putString(FOLDER_NAME, SyncFolder.folderName(app, tree))
            .putBoolean(AUTO, true).remove(PROBLEM).apply()
        _state.value = readState()
        return runSync()
    }

    fun setAuto(on: Boolean) {
        prefs.edit().putBoolean(AUTO, on).apply()
        _state.value = readState()
        if (on) request()
    }

    /** One merge with the folder's file now. True when it worked. */
    suspend fun syncNow(): Boolean = runSync()

    /** Merges a file the person picked, once: nothing on this phone is removed, the file isn't written. */
    suspend fun import(uri: Uri): ImportResult = mutex.withLock {
        val result = try {
            val incoming = ExportFormat.read(withContext(Dispatchers.IO) { SyncFolder.read(app, uri) })
            val counts = mergeIn(local(), incoming)
            ImportResult.Done(counts.first, counts.second)
        } catch (error: Exception) {
            ImportResult.Failed(classify(error))
        }
        if (result is ImportResult.Done) request()
        result
    }

    private suspend fun runSync(): Boolean = mutex.withLock {
        val tree = folder ?: return@withLock false
        val ok = try {
            withContext(Dispatchers.IO) {
                val file = SyncFolder.resolve(app, tree)
                val text = SyncFolder.read(app, file)
                val remote = text.takeIf { it.isNotBlank() }?.let(ExportFormat::read)
                val local = local()
                val merged = if (remote == null) local else SyncMerge.merge(local, remote, Instant.now().toString(), BuildConfig.VERSION_NAME)
                mergeIn(local, merged)
                if (remote == null || !SyncMerge.sameContent(remote, merged)) SyncFolder.write(app, file, ExportFormat.encode(merged))
            }
            prefs.edit().putLong(LAST, System.currentTimeMillis()).remove(PROBLEM).apply()
            true
        } catch (error: Exception) {
            prefs.edit().putString(PROBLEM, classify(error).name).apply()
            false
        }
        _state.value = readState()
        ok
    }

    private suspend fun local(): ExportFile {
        val tones = player.ui.value.tones
        return journal.snapshot(
            BuildConfig.VERSION_NAME,
            SettingsV1(settings.nightHours.value, settings.nightHoursAt, tones.pitch, tones.trims, player.tonesAt,
                settings.modules.value.toWire(), settings.modulesAt.takeIf { it > 0 }),
        )
    }

    /** Takes what [merged] has that this phone hasn't. Returns (days, sessions) taken. */
    private suspend fun mergeIn(local: ExportFile, other: ExportFile): Pair<Int, Int> {
        val merged = SyncMerge.merge(local, other, Instant.now().toString(), BuildConfig.VERSION_NAME)
        val changes = SyncMerge.changesFor(local, merged)
        if (changes.isEmpty) return 0 to 0
        val counts = database.withTransaction { journal.take(changes.checkIns, changes.sessions) }
        withContext(Dispatchers.Main) {
            changes.nightHours?.let { (on, at) -> settings.applyNightHours(on, at) }
            changes.tones?.let { (pitch, trims, at) -> player.applySyncedTones(pitch, trims, at) }
            changes.modules?.let { (map, at) -> settings.applyModules(Modules.fromWire(map), at) }
        }
        return counts
    }

    private fun classify(error: Exception) = when (error) {
        is NewerSchemaException -> SyncProblem.NEWER
        is IOException, is SecurityException -> SyncProblem.UNREACHABLE
        is IllegalArgumentException -> SyncProblem.NOT_BRUME // includes kotlinx SerializationException
        else -> SyncProblem.UNREACHABLE
    }

    private fun readState() = SyncState(
        folderName = prefs.getString(FOLDER_NAME, null),
        hasFolder = prefs.getString(FOLDER, null) != null,
        auto = prefs.getBoolean(AUTO, false),
        lastSyncedAt = prefs.getLong(LAST, 0L).takeIf { it > 0 },
        problem = prefs.getString(PROBLEM, null)?.let { p -> SyncProblem.entries.firstOrNull { it.name == p } },
    )

    private companion object {
        const val DEBOUNCE_MS = 1_500L
        const val FOLDER = "folder"; const val FOLDER_NAME = "folder_name"; const val AUTO = "auto"
        const val LAST = "last_synced_at"; const val PROBLEM = "problem"
    }
}
