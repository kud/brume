package app.beansontoast.brume.ui

import app.beansontoast.brume.playback.Bells
import app.beansontoast.brume.core.sound.Bell
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
import app.beansontoast.brume.core.region.Country
import app.beansontoast.brume.core.learn.Learn
import androidx.compose.ui.res.stringResource
import app.beansontoast.brume.core.journal.CheckIn
import app.beansontoast.brume.core.journal.EndedBy
import app.beansontoast.brume.core.breath.BreathPlan
import app.beansontoast.brume.core.breath.Pattern
import app.beansontoast.brume.core.sound.SoundEngine
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
    val chosenCountry by graph.settings.country.collectAsStateWithLifecycle()
    val guessedCountry = remember { guessCountry(context) }
    val chosen = chosenCountry?.let { name -> Country.entries.firstOrNull { it.name == name } }
    val country = chosen ?: guessedCountry
    // Calm and Tones are scenes inside Listen now: only Listen, Journal and Learn are tabs.
    LaunchedEffect(Unit) { if (tab == Tab.CALM || tab == Tab.TONES) tab = Tab.LISTEN }
    // Brume reopens on the scene last used (Iris, 2026-10-31): the clock never picks one for him,
    // which is what he disliked in dev.11. The first time, Settle, the scene that assumes nothing.
    var scene by rememberSaveable { mutableStateOf(Scene.entries.firstOrNull { it.name == graph.settings.scene } ?: Scene.SETTLE) }
    // A scene whose module was just switched off, here or on the other phone, falls back to Settle.
    LaunchedEffect(modules) { if (scene !in modules.scenes()) scene = Scene.SETTLE }
    var breath by remember {
        mutableStateOf(BreathSetup(
            // 5-5 left the set (2026-11-01): a stored 5-5 becomes Souffle long, and is saved so.
            pattern = Pattern.fromStored(graph.settings.breathPattern).also { graph.settings.breathPattern = it.name },
            minutes = graph.settings.breathMinutes, haptics = graph.settings.breathHaptics, sound = graph.settings.breathSound,
        ))
    }
    var meditation by remember {
        mutableStateOf(MeditationSetup(
            meditation = Meditation.fromStored(graph.settings.meditationChoice).also { graph.settings.meditationChoice = it.name },
            minutes = graph.settings.meditationMinutes, haptics = graph.settings.meditationHaptics, sound = graph.settings.meditationSound,
        ))
    }
    var practice by remember { mutableStateOf(Practice.entries.firstOrNull { it.name == graph.settings.calmPractice } ?: Practice.BREATHE) }
    // A running session: when it started (wall clock), so it survives rotation. Null when none.
    var breathStartedAt by rememberSaveable { mutableStateOf<Long?>(null) }
    var meditationStartedAt by rememberSaveable { mutableStateOf<Long?>(null) }
    var breathStartedSound by rememberSaveable { mutableStateOf(false) }
    val bells = remember { Bells() }
    val pickerHint = remember { !graph.settings.pickerHintSeen }
    val player by graph.player.ui.collectAsStateWithLifecycle()
    // Tones is a practice, not a background: leaving its scene, or Listen, pauses it. The comfort
    // sounds play on, with the mini player on the other tabs (Erwann, 2026-10-31).
    LaunchedEffect(tab, scene, player.playing, player.mode) {
        if (player.playing && player.mode == SoundEngine.Mode.TONES && (tab != Tab.LISTEN || scene != Scene.TONES)) graph.player.pause(EndedBy.USER)
    }
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

    // Pages opened inside a tab, as a "/"-joined stack: "topic:3", "card:c3_1", "settings", "about", "tones-how". Cleared on a tab change.
    var pages by rememberSaveable { mutableStateOf("") }
    val stack = pages.split('/').filter { it.isNotEmpty() }
    fun push(page: String) { pages = (stack + page).joinToString("/") }
    fun pop() { pages = stack.dropLast(1).joinToString("/") }
    // The introduction shows once, on first launch, and again from About Brume. Finishing or skipping marks it seen.
    var showIntro by rememberSaveable { mutableStateOf(!graph.settings.introSeen) }

    // A sound scene keeps its own mix: any change to its sounds or timer is saved to it at once.
    fun keepMix() { if (scene.sounds) graph.settings.setSceneMix(scene.name, SceneMix.of(graph.player.ui.value).encode()) }
    // Moving to a sound scene brings its mix back. A swipe is his own gesture, and levels stay his,
    // per headphones, so a scene never makes anything louder. Never on the first visit: the scene
    // then starts from the mix already chosen, so nothing is lost when scenes arrive.
    var swipeHint by remember { mutableStateOf(!graph.settings.scenesSwiped) }
    fun goScene(next: Scene) {
        if (next != scene && swipeHint) { swipeHint = false; graph.settings.scenesSwiped = true }
        scene = next
        graph.settings.scene = next.name
        if (!next.sounds) return
        val mix = SceneMix.decode(graph.settings.sceneMix(next.name)) ?: return keepMix()
        val now = graph.player.ui.value
        if (mix.noise != now.noise) graph.player.selectNoise(mix.noise)
        if (mix.ambience != now.ambience) graph.player.selectAmbience(mix.ambience)
        // The tone pattern playing keeps its own timer until a sound scene is played.
        if (mix.timer != now.timerMinutes && !(now.playing && now.mode == SoundEngine.Mode.TONES)) graph.player.setTimer(mix.timer)
    }
    val actions = ListenActions(
        noise = { graph.player.selectNoise(it); keepMix() },
        ambience = { graph.player.selectAmbience(it); keepMix() },
        noiseLevel = graph.player::setNoiseLevel,
        ambienceLevel = graph.player::setAmbienceLevel,
        timer = { graph.player.setTimer(it); keepMix() },
        toggle = graph.player::toggle,
        tooLoud = graph.player::tooLoud,
        mode = graph.player::selectMode,
        open = { pages = "" },
        balance = graph.player::setBalance,
        output = { openOutputSwitcher(context) },
        tones = ToneActions(
            pitch = graph.player::setPitch,
            level = graph.player::setToneLevel,
            trim = graph.player::setTrim,
            match = graph.player::setMatch,
        ),
    )


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
        bells.stop()
        breathStartedAt = null
        meditationStartedAt = null
    }

    val anchors = remember { LaunchAnchors() }
    // Read once: the launch intro lands on the introduction only on the launch that shows it.
    val firstLaunch = remember { showIntro }
    BrumeTheme(dark = isSystemInDarkTheme(), night = night) {
        CompositionLocalProvider(LocalLaunchAnchors provides anchors, LocalCountry provides country) {
            Box(Modifier.fillMaxSize()) {
                if (showIntro) {
                    Onboarding(onFinish = { setPitch ->
                        graph.settings.setIntroSeen()
                        showIntro = false
                        // Replayed from About Brume, Later returns there; on first launch the tab is still Listen.
                        // Setting the pitch from the intro turns Tones back on if it was off.
                        if (setPitch) { if (!modules.tones) graph.settings.setModules(modules.copy(tones = true)); tab = Tab.LISTEN; goScene(Scene.TONES); pages = "tones" }
                    })
                } else if (breathStartedAt != null) {
                    BackHandler { endBreath() }
                    BreathSession(BreathPlan(breath.pattern, breath.minutes), breathStartedAt!!, night, breath.haptics, onEnd = ::endBreath)
                } else if (meditationStartedAt != null) {
                    BackHandler { endBreath() }
                    // The bell follows the session's level: the sound under it, or the listening level when none plays.
                    val sessionLevel = if (player.playing && player.mode == SoundEngine.Mode.SOUNDS)
                        listOfNotNull(player.noise?.let { player.noiseLevel }, player.ambience?.let { player.ambienceLevel }).maxOrNull() ?: player.noiseLevel
                    else player.noiseLevel
                    MeditationSession(meditation, meditationStartedAt!!, night, onEnd = ::endBreath, onBell = { bells.ring(Bell.gain(sessionLevel, it.strength)) })
                } else BrumeScaffold(tab = tab, onTab = { tab = it; pages = "" }, modules = modules,
                    mini = if (tab != Tab.LISTEN && player.playing && player.mode == SoundEngine.Mode.SOUNDS) {
                        { MiniPlayer(chosenSound(context, player)) { graph.player.pause(EndedBy.USER) } }
                    } else null,
                    // Listen's scenes draw their own art, edge to edge; every other page reads over the
                    // scene last used, still and further back (Erwann, dev.19: « le fond d'écran partout »).
                    bleed = stack.isEmpty() && tab in listOf(Tab.LISTEN, Tab.CALM, Tab.TONES),
                    backdrop = scene.takeUnless { stack.isEmpty() && tab in listOf(Tab.LISTEN, Tab.CALM, Tab.TONES) },
                    night = night) {
                    val page = stack.lastOrNull()
                    if (page != null) BackHandler { pop() }
                    when {
                        page == "settings" -> SettingsScreen(nightHours = nightHours, onNightHours = graph.settings::setNightHours, onBack = ::pop,
                            sync = syncState, syncMessage = syncMessage, syncActions = syncActions,
                            modules = modules, onModules = graph.settings::setModules, onOutput = { openOutputSwitcher(context) },
                            country = CountryChoice(chosen, guessedCountry) { graph.settings.setCountry(it?.name) },
                            language = if (Build.VERSION.SDK_INT >= 33) remember { LanguageChoice(currentLanguage(context)) { applyLanguage(context, it) } } else null)
                        page?.startsWith("topic:") == true -> LearnTopicScreen(page.removePrefix("topic:").toInt(), onBack = ::pop, onCard = { push("card:$it") })
                        page == "about" -> AboutScreen(version = BuildConfig.VERSION_NAME, onIntro = { showIntro = true }, onBack = ::pop)
                        page == "tones-how" -> TonesHowScreen(onBack = ::pop)
                        page == "sounds" -> SoundsScreen(player, actions, onBack = ::pop, hint = pickerHint, onHintSeen = graph.settings::setPickerHintSeen)
                        page == "tones" -> TonesScreen(player, actions, onHow = { push("tones-how") })
                        page == "calm" -> CalmScreen(
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
                        page?.startsWith("card:") == true -> {
                            // "card:c3_1" from the library or a Journal tag; Next walks the reading order in place.
                            val card = Learn.card(page.removePrefix("card:"))
                            if (card == null) LaunchedEffect(page) { pop() }
                            else LearnCardScreen(card, backLabel = stringResource(R.string.learn_back), onBack = ::pop,
                                onNext = { pages = (stack.dropLast(1) + "card:$it").joinToString("/") })
                        }
                        else -> when (tab) {
                        Tab.LISTEN, Tab.CALM, Tab.TONES -> SceneScreen(
                            scenes = modules.scenes(), scene = scene, onScene = ::goScene,
                            ui = player, actions = actions, night = night,
                            onSounds = { push("sounds") }, onTonesSetup = { push("tones") },
                            swipeHint = swipeHint,
                            // An empty scene's suggestion, on his tap: its sounds and timer become the scene's mix, then it starts, fading in.
                            onSuggest = { mix ->
                                graph.player.selectNoise(mix.noise); graph.player.selectAmbience(mix.ambience); graph.player.setTimer(mix.timer)
                                keepMix(); graph.player.play()
                            },
                            breathe = BreatheScene(
                                practices = listOfNotNull(Practice.BREATHE.takeIf { modules.breath }, Practice.MEDITATE.takeIf { modules.meditation }),
                                practice = if (!modules.breath) Practice.MEDITATE else if (!modules.meditation) Practice.BREATHE else practice,
                                onPractice = { practice = it; graph.settings.calmPractice = it.name },
                                minutes = if (practice == Practice.MEDITATE && modules.meditation || !modules.breath) meditation.length else breath.minutes,
                                cycles = breath.pattern.cycles.takeUnless { practice == Practice.MEDITATE && modules.meditation || !modules.breath },
                                pattern = breath.pattern,
                                onPattern = { breath = breath.copy(pattern = it); graph.settings.breathPattern = it.name },
                                soundName = chosenSound(context, player).ifEmpty { null }
                                    ?.takeIf { if (practice == Practice.MEDITATE && modules.meditation || !modules.breath) meditation.sound else breath.sound },
                                onStart = { if (practice == Practice.MEDITATE && modules.meditation || !modules.breath) startMeditation() else startBreath() },
                                onSetup = { push("calm") },
                            ),
                        )
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
                        Tab.LEARN -> LearnScreen(onTopic = { push("topic:$it") }, onSettings = { push("settings") }, onAbout = { push("about") }, onCard = { push("card:$it") })
                        }
                    }
                }
                LaunchIntroOverlay(intro, firstLaunch)
            }
        }
    }
}
