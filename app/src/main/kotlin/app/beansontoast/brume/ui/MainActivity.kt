package app.beansontoast.brume.ui

import app.beansontoast.brume.playback.openOutputSwitcher
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.beansontoast.brume.AppGraph
import app.beansontoast.brume.BrumeApp
import app.beansontoast.brume.BuildConfig
import app.beansontoast.brume.R
import app.beansontoast.brume.core.NightHours
import app.beansontoast.brume.core.journal.CheckIn
import app.beansontoast.brume.sync.ImportResult
import app.beansontoast.brume.sync.SyncProblem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val graph = (application as BrumeApp).graph
        // Sync pulls the other phone's changes on the way in and pushes this one's on the way out.
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) graph.sync.request()
            if (event == Lifecycle.Event.ON_STOP) graph.sync.flush()
        })
        setContent { BrumeRoot(graph) }
    }
}

@Composable
private fun BrumeRoot(graph: AppGraph) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val nightHours by graph.settings.nightHours.collectAsStateWithLifecycle()
    val night = nightHours && NightHours.contains(LocalTime.now().hour)
    // At night Brume always opens on Listen: Listen is the first tab, and nothing else asks for effort.
    var tab by rememberSaveable { mutableStateOf(Tab.LISTEN) }
    val player by graph.player.ui.collectAsStateWithLifecycle()
    val checkIns by graph.journal.checkIns.collectAsStateWithLifecycle(emptyList())
    val weekSeconds by remember { graph.journal.weekSeconds() }.collectAsStateWithLifecycle(0L)
    var journalMessage by remember { mutableStateOf<String?>(null) }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = runCatching {
                val json = graph.journal.exportJson(BuildConfig.VERSION_NAME)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray()) }
                }
            }.isSuccess
            journalMessage = context.getString(if (ok) R.string.journal_exported else R.string.journal_export_failed)
        }
    }

    val syncState by graph.sync.state.collectAsStateWithLifecycle()
    var syncMessage by remember { mutableStateOf<String?>(null) }
    val chooseFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) scope.launch {
            syncMessage = context.getString(if (graph.sync.chooseFolder(tree)) R.string.sync_done else R.string.sync_folder_failed)
        }
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            syncMessage = when (val result = graph.sync.import(uri)) {
                is ImportResult.Done ->
                    if (result.days == 0 && result.sessions == 0) context.getString(R.string.import_nothing)
                    else context.getString(R.string.import_done, result.days, result.sessions)
                is ImportResult.Failed ->
                    context.getString(if (result.problem == SyncProblem.NEWER) R.string.import_newer else R.string.import_failed)
            }
        }
    }
    val syncActions = SyncActions(
        chooseFolder = { chooseFolder.launch(null) },
        auto = graph.sync::setAuto,
        syncNow = { scope.launch { syncMessage = context.getString(if (graph.sync.syncNow()) R.string.sync_done else R.string.sync_failed) } },
        import = { importFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
    )

    var showPlaying by rememberSaveable { mutableStateOf(false) }
    // The introduction shows once, on first launch, and again from About. Finishing or skipping marks it seen.
    var showIntro by rememberSaveable { mutableStateOf(!graph.settings.introSeen) }

    val actions = ListenActions(
        noise = graph.player::selectNoise,
        ambience = graph.player::selectAmbience,
        noiseLevel = graph.player::setNoiseLevel,
        ambienceLevel = graph.player::setAmbienceLevel,
        timer = graph.player::setTimer,
        toggle = graph.player::toggle,
        tooLoud = graph.player::tooLoud,
        mode = graph.player::selectMode,
        open = { showPlaying = true },
        balance = graph.player::setBalance,
        output = { openOutputSwitcher(context) },
        tones = ToneActions(
            pitch = graph.player::setPitch,
            level = graph.player::setToneLevel,
            trim = graph.player::setTrim,
            match = graph.player::setMatch,
        ),
    )

    // The playing view opens when a sound or the tone pattern starts (not when the app opens on
    // one already playing), and stays, paused or not, until Back or its own way back.
    var wasPlaying by remember { mutableStateOf(player.playing) }
    LaunchedEffect(player.playing) { if (player.playing && !wasPlaying) showPlaying = true; wasPlaying = player.playing }

    BrumeTheme(dark = isSystemInDarkTheme(), night = night) {
        if (showIntro) {
            Onboarding(onFinish = { setPitch ->
                graph.settings.setIntroSeen()
                showIntro = false
                // Replayed from About, Later returns there; on first launch the tab is still Listen.
                if (setPitch) tab = Tab.TONES
            })
        } else if (showPlaying && player.hasSound) {
            BackHandler { showPlaying = false }
            PlayingView(player, night, onPause = graph.player::toggle, onBack = { showPlaying = false })
        } else BrumeScaffold(tab = tab, onTab = { tab = it }) {
            when (tab) {
                Tab.LISTEN -> ListenComfort(player, actions)
                Tab.TONES -> TonesScreen(player, actions)
                Tab.JOURNAL -> JournalScreen(
                    checkIns = checkIns,
                    weekSeconds = weekSeconds,
                    today = LocalDate.now(),
                    message = journalMessage,
                    onSave = { loudness, bother, note ->
                        scope.launch {
                            graph.journal.save(CheckIn.of(LocalDate.now(), loudness, bother, note))
                            journalMessage = context.getString(R.string.journal_saved)
                        }
                    },
                    onExport = { export.launch("brume-export-${LocalDate.now()}.json") },
                )
                Tab.ABOUT -> AboutScreen(nightHours = nightHours, onNightHours = graph.settings::setNightHours, version = BuildConfig.VERSION_NAME, onIntro = { showIntro = true },
                    sync = syncState, syncMessage = syncMessage, syncActions = syncActions)
            }
        }
    }
}
