package app.beansontoast.brume.ui

import app.beansontoast.brume.playback.openOutputSwitcher
import android.os.Build
import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.beansontoast.brume.ui.launch.LaunchAnchors
import app.beansontoast.brume.ui.launch.LaunchIntro
import app.beansontoast.brume.ui.launch.LaunchIntroOverlay
import app.beansontoast.brume.ui.launch.LocalLaunchAnchors
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
import app.beansontoast.brume.core.learn.Learn
import androidx.compose.ui.res.stringResource
import app.beansontoast.brume.core.journal.CheckIn
import app.beansontoast.brume.core.journal.EndedBy
import app.beansontoast.brume.core.breath.BreathPlan
import app.beansontoast.brume.core.breath.Pattern
import app.beansontoast.brume.sync.ImportResult
import app.beansontoast.brume.sync.SyncProblem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // The system splash is only a still bridge: the intro takes over at the same place and plays
        // on the app's own frame clock, over the screen already drawn underneath (ui/launch).
        val intro = LaunchIntro(LaunchIntro.kindFor(restored = savedInstanceState != null))
        splash.setOnExitAnimationListener(intro::takeOver)
        val graph = (application as BrumeApp).graph
        // Sync pulls the other phone's changes on the way in and pushes this one's on the way out.
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) graph.sync.request()
            if (event == Lifecycle.Event.ON_STOP) graph.sync.flush()
        })
        setContent { BrumeRoot(graph, intro) }
    }
}

@Composable
private fun BrumeRoot(graph: AppGraph, intro: LaunchIntro) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val nightHours by graph.settings.nightHours.collectAsStateWithLifecycle()
    val night = nightHours && NightHours.contains(LocalTime.now().hour)
    // Brume always opens on Listen: the sounds, chosen directly; nothing else asks for effort at night.
    var tab by rememberSaveable { mutableStateOf(Tab.LISTEN) }
    val modules by graph.settings.modules.collectAsStateWithLifecycle()
    // A tab whose module was just switched off, here or on the other phone, falls back to Listen.
    LaunchedEffect(modules) { if (!modules.shows(tab)) tab = Tab.LISTEN }
    var breath by remember {
        mutableStateOf(BreathSetup(
            pattern = Pattern.entries.firstOrNull { it.name == graph.settings.breathPattern } ?: Pattern.LONG_OUT,
            minutes = graph.settings.breathMinutes, haptics = graph.settings.breathHaptics, sound = graph.settings.breathSound,
        ))
    }
    var meditation by remember {
        mutableStateOf(MeditationSetup(
            meditation = Meditation.entries.firstOrNull { it.name == graph.settings.meditationChoice } ?: Meditation.SOUNDS,
            minutes = graph.settings.meditationMinutes, haptics = graph.settings.meditationHaptics, sound = graph.settings.meditationSound,
        ))
    }
    var practice by remember { mutableStateOf(Practice.entries.firstOrNull { it.name == graph.settings.calmPractice } ?: Practice.BREATHE) }
    // A running session: when it started (wall clock), so it survives rotation. Null when none.
    var breathStartedAt by rememberSaveable { mutableStateOf<Long?>(null) }
    var meditationStartedAt by rememberSaveable { mutableStateOf<Long?>(null) }
    var breathStartedSound by rememberSaveable { mutableStateOf(false) }
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
    // Pages opened inside a tab, as a "/"-joined stack: "topic:3", "card:c3_1", "settings", "about", "tones-how". Cleared on a tab change.
    var pages by rememberSaveable { mutableStateOf("") }
    val stack = pages.split('/').filter { it.isNotEmpty() }
    fun push(page: String) { pages = (stack + page).joinToString("/") }
    fun pop() { pages = stack.dropLast(1).joinToString("/") }
    // The introduction shows once, on first launch, and again from About Brume. Finishing or skipping marks it seen.
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

    // The sound underneath goes through the player as ever: its fade-in, its levels, Too loud.
    // Already playing, it plays on and is left playing after. Breathe and Meditation share this.
    fun startUnderneath(wanted: Boolean) {
        breathStartedSound = wanted && player.hasSound && !player.playing
        if (breathStartedSound) graph.player.play()
    }
    fun startBreath() { startUnderneath(breath.sound); breathStartedAt = System.currentTimeMillis() }
    fun startMeditation() { startUnderneath(meditation.sound); meditationStartedAt = System.currentTimeMillis() }
    fun endBreath() {
        if (breathStartedSound) graph.player.pause(EndedBy.USER)
        breathStartedSound = false
        breathStartedAt = null
        meditationStartedAt = null
        showPlaying = false
    }

    val anchors = remember { LaunchAnchors() }
    // Read once: the launch intro lands on the introduction only on the launch that shows it.
    val firstLaunch = remember { showIntro }
    BrumeTheme(dark = isSystemInDarkTheme(), night = night) {
        CompositionLocalProvider(LocalLaunchAnchors provides anchors) {
            Box(Modifier.fillMaxSize()) {
                if (showIntro) {
                    Onboarding(onFinish = { setPitch ->
                        graph.settings.setIntroSeen()
                        showIntro = false
                        // Replayed from About Brume, Later returns there; on first launch the tab is still Listen.
                        // Setting the pitch from the intro turns Tones back on if it was off.
                        if (setPitch) { if (!modules.tones) graph.settings.setModules(modules.copy(tones = true)); tab = Tab.TONES }
                    })
                } else if (breathStartedAt != null) {
                    BackHandler { endBreath() }
                    BreathSession(BreathPlan(breath.pattern, breath.minutes), breathStartedAt!!, night, breath.haptics, onEnd = ::endBreath)
                } else if (meditationStartedAt != null) {
                    BackHandler { endBreath() }
                    MeditationSession(meditation, meditationStartedAt!!, night, onEnd = ::endBreath)
                } else if (showPlaying && player.hasSound) {
                    BackHandler { showPlaying = false }
                    PlayingView(player, night, onPause = graph.player::toggle, onBack = { showPlaying = false })
                } else BrumeScaffold(tab = tab, onTab = { tab = it; pages = "" }, modules = modules) {
                    val page = stack.lastOrNull()
                    if (page != null) BackHandler { pop() }
                    when {
                        page == "settings" -> SettingsScreen(nightHours = nightHours, onNightHours = graph.settings::setNightHours, onBack = ::pop,
                            sync = syncState, syncMessage = syncMessage, syncActions = syncActions,
                            modules = modules, onModules = graph.settings::setModules, onOutput = { openOutputSwitcher(context) },
                            language = if (Build.VERSION.SDK_INT >= 33) remember { LanguageChoice(currentLanguage(context)) { applyLanguage(context, it) } } else null)
                        page?.startsWith("topic:") == true -> LearnTopicScreen(page.removePrefix("topic:").toInt(), onBack = ::pop, onCard = { push("card:$it") })
                        page == "about" -> AboutScreen(version = BuildConfig.VERSION_NAME, onIntro = { showIntro = true }, onBack = ::pop)
                        page == "tones-how" -> TonesHowScreen(onBack = ::pop)
                        page?.startsWith("card:") == true -> {
                            // "card:c3_1" from the library or a Journal tag; Next walks the reading order in place.
                            val card = Learn.card(page.removePrefix("card:"))
                            if (card == null) LaunchedEffect(page) { pop() }
                            else LearnCardScreen(card, backLabel = stringResource(R.string.learn_back), onBack = ::pop,
                                onNext = { pages = (stack.dropLast(1) + "card:$it").joinToString("/") })
                        }
                        else -> when (tab) {
                        Tab.LISTEN -> ListenComfort(player, actions)
                        Tab.CALM -> CalmScreen(
                            modules = modules,
                            practice = practice,
                            onPractice = { practice = it; graph.settings.calmPractice = it.name },
                            breath = breath,
                            meditation = meditation,
                            soundName = chosenSound(context, player).ifEmpty { null },
                            onBreath = {
                                breath = it
                                graph.settings.breathPattern = it.pattern.name; graph.settings.breathMinutes = it.minutes
                                graph.settings.breathHaptics = it.haptics; graph.settings.breathSound = it.sound
                            },
                            onMeditation = {
                                meditation = it
                                graph.settings.meditationChoice = it.meditation.name; graph.settings.meditationMinutes = it.minutes
                                graph.settings.meditationHaptics = it.haptics; graph.settings.meditationSound = it.sound
                            },
                            onStartBreath = ::startBreath,
                            onStartMeditation = ::startMeditation,
                        )
                        Tab.TONES -> TonesScreen(player, actions, onHow = { push("tones-how") })
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
                            onCard = { push("card:$it") },
                        )
                        Tab.LEARN -> LearnScreen(onTopic = { push("topic:$it") }, onSettings = { push("settings") }, onAbout = { push("about") })
                        }
                    }
                }
                LaunchIntroOverlay(intro, firstLaunch)
            }
        }
    }
}
