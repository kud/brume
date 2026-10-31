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
import app.beansontoast.brume.core.home.LastSound
import app.beansontoast.brume.core.home.Suggest
import app.beansontoast.brume.core.home.Suggestion
import app.beansontoast.brume.core.sound.SoundEngine.Mode
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
    // Brume always opens on the home tab: one suggestion, nothing that asks for effort at night.
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    val modules by graph.settings.modules.collectAsStateWithLifecycle()
    // A tab whose module was just switched off, here or on the other phone, falls back to the home tab.
    LaunchedEffect(modules) { if (!modules.shows(tab)) tab = Tab.HOME }
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
    // Pages opened inside a tab, as a "/"-joined stack: "topic:3", "card:c3_1", "settings". Cleared on a tab change.
    var pages by rememberSaveable { mutableStateOf("") }
    val stack = pages.split('/').filter { it.isNotEmpty() }
    fun push(page: String) { pages = (stack + page).joinToString("/") }
    fun pop() { pages = stack.dropLast(1).joinToString("/") }
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

    // The home's suggestion plays through the player as any start does: the person's levels, the fade-in.
    // The non-empty layer goes first so the player never sees silence and pauses in between.
    fun playSuggestion(s: Suggestion) {
        if (player.mode != Mode.SOUNDS) graph.player.selectMode(Mode.SOUNDS)
        if (s.noise != null) { graph.player.selectNoise(s.noise); graph.player.selectAmbience(s.ambience) }
        else { graph.player.selectAmbience(s.ambience); graph.player.selectNoise(null) }
        graph.player.setTimer(s.timerMinutes)
        if (!graph.player.ui.value.playing) graph.player.play()
    }
    val learnStep = remember { Learn.step(graph.settings.learnStartDay(LocalDate.now().toEpochDay()), LocalDate.now().toEpochDay()) }
    var tonesStep by remember { mutableStateOf(graph.settings.tonesStep) }

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
                        // Replayed from About, Later returns there; on first launch the tab is still the home tab.
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
                } else BrumeScaffold(tab = tab, onTab = { tab = it; pages = "" }, modules = modules, night = night) {
                    val page = stack.lastOrNull()
                    if (page != null) BackHandler { pop() }
                    when {
                        page == "settings" -> SettingsScreen(nightHours = nightHours, onNightHours = graph.settings::setNightHours, onBack = ::pop,
                            sync = syncState, syncMessage = syncMessage, syncActions = syncActions,
                            modules = modules, onModules = graph.settings::setModules,
                            language = if (Build.VERSION.SDK_INT >= 33) remember { LanguageChoice(currentLanguage(context)) { applyLanguage(context, it) } } else null)
                        page?.startsWith("topic:") == true -> LearnTopicScreen(page.removePrefix("topic:").toInt(), onBack = ::pop, onCard = { push("card:$it") })
                        page == "controls" -> ListenComfort(player, actions, onBack = ::pop)
                        page?.startsWith("card:") == true -> {
                            // "card:c3_1" from the library or a tag walks on; "card:c3_1@4" is the home's step 4 of the path.
                            val (id, step) = page.removePrefix("card:").split('@').let { it[0] to it.getOrNull(1)?.toIntOrNull() }
                            val card = Learn.card(id)
                            val walkOn: (String) -> Unit = { next -> pages = (stack.dropLast(1) + "card:$next").joinToString("/") }
                            if (card == null) LaunchedEffect(page) { pop() }
                            else LearnCardScreen(card, backLabel = stringResource(R.string.learn_back), onBack = ::pop, step = step,
                                onNext = walkOn.takeIf { step == null })
                        }
                        else -> when (tab) {
                        Tab.HOME -> HomeScreen(
                            night = night, ui = player, suggestion = Suggest.of(night, LastSound(player.noise, player.ambience, player.timerMinutes)),
                            step = learnStep, actions = actions,
                            onPlay = { playSuggestion(Suggest.of(night, LastSound(player.noise, player.ambience, player.timerMinutes))) },
                            onOtherSound = { push("controls") },
                            onCard = { id, step -> push(if (step != null) "card:$id@$step" else "card:$id") },
                            modules = modules, tonesStep = tonesStep, onTones = { tab = Tab.TONES; pages = "" },
                        )
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
                        Tab.TONES -> TonesScreen(player, actions, step = tonesStep, onStep = { tonesStep = it; graph.settings.tonesStep = it }, night = night)
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
                        Tab.ABOUT -> AboutScreen(version = BuildConfig.VERSION_NAME, onIntro = { showIntro = true },
                            onTopic = { push("topic:$it") }, onSettings = { push("settings") })
                        }
                    }
                }
                LaunchIntroOverlay(intro, firstLaunch)
            }
        }
    }
}
