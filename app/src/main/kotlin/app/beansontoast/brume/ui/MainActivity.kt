package app.beansontoast.brume.ui

import androidx.compose.foundation.layout.statusBarsPadding
import app.beansontoast.design.Motion
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import app.beansontoast.brume.playback.Bells
import app.beansontoast.brume.core.sound.Mix
import app.beansontoast.brume.core.sound.LevelRules
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
import androidx.compose.runtime.DisposableEffect
import app.beansontoast.brume.core.sound.Sound
import app.beansontoast.brume.playback.ProbePlayer
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
import app.beansontoast.brume.core.breath.Cue
import app.beansontoast.brume.core.breath.Technique
import app.beansontoast.brume.core.guide.Line
import app.beansontoast.brume.core.breath.Variants
import app.beansontoast.brume.voice.Voice
import androidx.compose.runtime.DisposableEffect
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
    // Brume opens on home, the choice of every function (Erwann, 2026-11-01); each opens its own
    // screen and the ✕ or Back comes home. Null is home.
    var place by rememberSaveable { mutableStateOf<String?>(null) }
    val fn = place?.let { name -> Fn.entries.firstOrNull { it.name == name } }
    val modules by graph.settings.modules.collectAsStateWithLifecycle()
    val chosenCountry by graph.settings.country.collectAsStateWithLifecycle()
    val guessedCountry = remember { guessCountry(context) }
    val chosen = chosenCountry?.let { name -> Country.entries.firstOrNull { it.name == name } }
    val country = chosen ?: guessedCountry
    // Brume reopens on the scene last used (Iris, 2026-10-31): the clock never picks one for him,
    // which is what he disliked in dev.11. The first time, Settle, the scene that assumes nothing.
    var scene by rememberSaveable { mutableStateOf(Scene.entries.firstOrNull { it.name == graph.settings.scene } ?: Scene.SETTLE) }
    // A scene whose module was just switched off, here or on the other phone, falls back to Settle,
    // and a function switched off while open gives way to home.
    LaunchedEffect(modules) { if (scene !in modules.scenes()) scene = Scene.SETTLE }
    LaunchedEffect(modules, fn) { if (fn != null && !modules.allows(fn)) place = null }
    var breath by remember {
        mutableStateOf(BreathSetup(
            // The ids are the old pattern names. 5-5 left the set (2026-11-01): a stored 5-5 becomes
            // Souffle long, and is saved so. A length from the older set finds the nearest offered.
            technique = Technique.fromStored(graph.settings.breathPattern).also { graph.settings.breathPattern = it.name },
            variants = Variants.decode(graph.settings.breathVariants),
            minutes = graph.settings.breathMinutes, rounds = graph.settings.breathRounds,
            haptics = graph.settings.breathHaptics, sound = graph.settings.breathSound, voice = graph.settings.breathVoice,
        ))
    }
    var meditation by remember {
        mutableStateOf(MeditationSetup(
            meditation = Meditation.fromStored(graph.settings.meditationChoice).also { graph.settings.meditationChoice = it.name },
            minutes = graph.settings.meditationMinutes, haptics = graph.settings.meditationHaptics, sound = graph.settings.meditationSound,
            voice = graph.settings.meditationVoice,
        ))
    }
    // A running session: when it started (wall clock), so it survives rotation. Null when none.
    var breathStartedAt by rememberSaveable { mutableStateOf<Long?>(null) }
    var meditationStartedAt by rememberSaveable { mutableStateOf<Long?>(null) }
    var breathStartedSound by rememberSaveable { mutableStateOf(false) }
    // What to read before a breathing session (how to sit, the hold note the first time), shown in its place.
    var breathPrelude by rememberSaveable { mutableStateOf(false) }
    val bells = remember { Bells() }
    // The guide's voice: the phone's offline text-to-speech, woken only when Breathe or a session is near.
    val voice = remember { Voice(context) }
    DisposableEffect(voice) { onDispose { voice.shutdown() } }
    val voiceState by voice.state.collectAsStateWithLifecycle()
    val pickerHint = remember { !graph.settings.pickerHintSeen }
    val player by graph.player.ui.collectAsStateWithLifecycle()
    // Tones is a practice, not a background: leaving its screen pauses it. The comfort sounds play
    // on across screens, with the mini player on home (Erwann, 2026-10-31 and 2026-11-01).
    LaunchedEffect(fn, player.playing, player.mode) {
        if (player.playing && player.mode == SoundEngine.Mode.TONES && fn != Fn.TONES) graph.player.pause(EndedBy.USER)
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
    // The update sheet (github build only; the fdroid build's stand-ins draw nothing).
    var showUpdate by rememberSaveable { mutableStateOf(false) }

    // A sound scene keeps its own mix: any change to its sounds or timer is saved to it at once.
    fun keepMix() { if (scene.sounds) graph.settings.setSceneMix(scene.name, Mix.of(graph.player.ui.value).encode()) }
    // Opening a sound scene brings its mix back. Opening it is his own gesture, and levels stay his,
    // per headphones, so a scene never makes anything louder. Never on the first visit: the scene
    // then starts from the mix already chosen, so nothing is lost when scenes arrive.
    fun goScene(next: Scene) {
        scene = next
        graph.settings.scene = next.name
        if (!next.sounds) return
        val mix = Mix.decode(graph.settings.sceneMix(next.name)) ?: return keepMix()
        val now = graph.player.ui.value
        graph.player.setMix(mix.layers)
        // The tone pattern playing keeps its own timer until a sound scene is played.
        if (mix.timer != now.timerMinutes && !(now.playing && now.mode == SoundEngine.Mode.TONES)) graph.player.setTimer(mix.timer)
    }
    // Home and back: every function opens from home with an empty page stack.
    fun home() { pages = ""; place = null }
    fun open(next: Fn) {
        when (next) { Fn.SLEEP -> goScene(Scene.SLEEP); Fn.SETTLE -> goScene(Scene.SETTLE); Fn.TONES -> goScene(Scene.TONES); else -> Unit }
        pages = ""; place = next.name
    }
    val actions = ListenActions(
        add = { graph.player.add(it); keepMix() },
        remove = { graph.player.remove(it); keepMix() },
        level = graph.player::setLevel,
        tone = { s, t -> graph.player.setTone(s, t); keepMix() },
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
    fun beginBreath() {
        if (breath.pattern.holds) graph.settings.holdNoteSeen = true
        breathPrelude = false
        startUnderneath(breath.sound); breathStartedAt = System.currentTimeMillis()
    }
    fun startBreath() {
        if (breath.voice) voice.prepare()
        if (breath.prelude(graph.settings.holdNoteSeen)) breathPrelude = true else beginBreath()
    }
    fun startMeditation() {
        if (meditation.voice && meditation.meditation.guided != null) voice.prepare()
        startUnderneath(meditation.sound); meditationStartedAt = System.currentTimeMillis()
    }
    // « Respirer normalement », End or Back: the session gives way by the route's slow cross-fade, no question asked.
    fun endBreath() {
        if (breathStartedSound) graph.player.pause(EndedBy.USER)
        breathStartedSound = false
        bells.stop()
        voice.hush()
        breathPrelude = false
        breathStartedAt = null
        meditationStartedAt = null
    }

    // Bells and voice follow the session's level: the sound under it, or the listening level when none
    // plays, so neither is ever louder than the session.
    val sessionLevel = player.levels.values.maxOrNull() ?: LevelRules.DEFAULT_LEVEL
    // The voice wakes when its setup or the Breathe scene is in view, so its row can say if it is missing.
    val voiceNear = stack.lastOrNull() == "calm" || (stack.isEmpty() && fn == Fn.BREATHE)
    LaunchedEffect(voiceNear) { if (voiceNear) voice.prepare() }

    fun keepBreath(it: BreathSetup) {
        breath = it
        graph.settings.breathPattern = it.technique.name; graph.settings.breathMinutes = it.minutes
        graph.settings.breathRounds = it.rounds; graph.settings.breathVariants = it.variants.encode()
        graph.settings.breathHaptics = it.haptics; graph.settings.breathSound = it.sound; graph.settings.breathVoice = it.voice
    }
    fun keepMeditation(it: MeditationSetup) {
        meditation = it
        graph.settings.meditationChoice = it.meditation.name; graph.settings.meditationMinutes = it.minutes
        graph.settings.meditationHaptics = it.haptics; graph.settings.meditationSound = it.sound
        graph.settings.meditationVoice = it.voice
    }
    // Settings and Mon acouphène open from home, and from Learn and Tones as pages: one body each.
    val settingsPage: @Composable (() -> Unit) -> Unit = { onBack ->
        SettingsScreen(nightHours = nightHours, onNightHours = graph.settings::setNightHours, onBack = onBack,
            sync = syncState, syncMessage = syncMessage, syncActions = syncActions,
            modules = modules, onModules = graph.settings::setModules, onOutput = { openOutputSwitcher(context) },
            country = CountryChoice(chosen, guessedCountry) { graph.settings.setCountry(it?.name) },
            language = if (Build.VERSION.SDK_INT >= 33) remember { LanguageChoice(currentLanguage(context)) { applyLanguage(context, it) } } else null)
    }
    val finderPage: @Composable (() -> Unit) -> Unit = { onBack ->
        // The search plays on its own track; the mixer goes quiet first (start from silence).
        val probe = remember { ProbePlayer() }
        DisposableEffect(Unit) { onDispose { probe.close() } }
        val profiles by graph.journal.profiles.collectAsStateWithLifecycle(initialValue = emptyList())
        FinderScreen(profiles, onBack = onBack, actions = FinderActions(
            probe = { p -> if (p != null && p.level > 0f) graph.player.pause(EndedBy.USER); probe.set(p) },
            tooLoud = probe::tooLoud,
            save = { scope.launch { graph.journal.saveProfile(it) } },
            delete = { scope.launch { graph.journal.deleteProfile(it) } },
            // The comfort sound joins the mix as one more layer, in a sound scene, at the level in words he sets.
            comfort = { band ->
                open(Fn.SLEEP)
                graph.player.add(Sound.Band(band.centre, band.widthTenths)); keepMix()
            },
        ))
    }

    val anchors = remember { LaunchAnchors() }
    // Read once: the launch intro lands on the introduction only on the launch that shows it.
    val firstLaunch = remember { showIntro }
    BrumeTheme(dark = isSystemInDarkTheme(), night = night) {
        CompositionLocalProvider(LocalLaunchAnchors provides anchors, LocalCountry provides country) {
            Box(Modifier.fillMaxSize()) {
                // Introduction, a session and home give way to each other by a slow cross-fade, each
                // drawn from its own key so the one leaving keeps its state (docs/motion.md).
                val route = when {
                    showIntro -> Route(Route.INTRO)
                    breathStartedAt != null -> Route(Route.BREATH, breathStartedAt)
                    breathPrelude -> Route(Route.PRELUDE)
                    meditationStartedAt != null -> Route(Route.MEDITATION, meditationStartedAt)
                    else -> Route(Route.HOME)
                }
                Crossfade(route, Modifier.fillMaxSize(), animationSpec = Motion.tween(Motion.SLOW), label = "route") { r ->
                if (r.kind == Route.INTRO) {
                    Onboarding(onFinish = { setPitch ->
                        graph.settings.setIntroSeen()
                        showIntro = false
                        // Replayed from About Brume, Later returns there; on first launch, home.
                        // Setting the pitch from the intro turns Tones back on if it was off.
                        if (setPitch) { if (!modules.tones) graph.settings.setModules(modules.copy(tones = true)); open(Fn.TONES); pages = "tones" }
                    })
                } else if (r.kind == Route.PRELUDE) {
                    BackHandler { endBreath() }
                    BreathPrelude(breath, safety = breath.pattern.holds && !graph.settings.holdNoteSeen, onStart = ::beginBreath, onBack = ::endBreath)
                } else if (r.kind == Route.BREATH) {
                    BackHandler { endBreath() }
                    val gain = Bell.gain(sessionLevel, 1f)
                    BreathSession(breath.plan(), r.at!!, night, breath.haptics, onEnd = ::endBreath,
                        speak = { cue: Cue -> voice.say(context.getString(cue.spoken()), gain, flush = true) }.takeIf { breath.voice })
                } else if (r.kind == Route.MEDITATION) {
                    BackHandler { endBreath() }
                    val gain = Bell.gain(sessionLevel, 1f)
                    MeditationSession(meditation, r.at!!, night, onEnd = ::endBreath, onBell = { bells.ring(Bell.gain(sessionLevel, it.strength)) },
                        speak = { line: Line -> voice.say(context.getString(line.text()), gain, flush = false) }.takeIf { meditation.voice })
                } else {
                    // Home, a function's own screen, or a page inside it: the next one slides in from the
                    // right over the last, which moves a quarter left; ✕, ‹ and Back slide it out to the
                    // right (Erwann, 2026-11-01: « je préfère un slide »; docs/motion.md). Reduced
                    // motion cross-fades. Back pops a page, then comes home.
                    val reducedNav = app.beansontoast.design.rememberReducedMotion()
                    AnimatedContent(Place(fn, stack.lastOrNull(), stack.size), Modifier.fillMaxSize(), label = "place",
                        transitionSpec = { Motion.push(reducedNav, forward = targetState.depth >= initialState.depth) }) { (shownFn, page, _) ->
                    if (shownFn == null) {
                        HomeScreen(modules.fns(), onOpen = ::open,
                            mini = if (player.playing && player.mode == SoundEngine.Mode.SOUNDS) {
                                { MiniPlayer(chosenSound(context, player), onPause = { graph.player.pause(EndedBy.USER) },
                                    onOpen = { open(if (scene.sounds) Fn.entries.first { it.name == scene.name } else Fn.SETTLE) }) }
                            } else null)
                        return@AnimatedContent
                    }
                    BackHandler { if (page != null) pop() else home() }
                    // The frame owns the title (Iris, phase 2): ✕ at a function's root, ‹ on a page inside it,
                    // the name centred on the same row and never repeated by the screen.
                    val title = when {
                        page == null -> stringResource(shownFn.title)
                        page.startsWith("topic:") -> topicName(context, page.removePrefix("topic:").toInt())
                        page.startsWith("card:") -> Learn.card(page.removePrefix("card:"))?.let { topicName(context, it.topic) }
                        page == "about" -> stringResource(R.string.about_brume)
                        page == "tones-how" -> stringResource(R.string.tones_how)
                        page == "sounds" -> stringResource(R.string.scene_sounds)
                        page == "tones" -> stringResource(R.string.tab_tones)
                        page == "settings" -> stringResource(R.string.settings_title)
                        page == "finder" -> stringResource(R.string.finder_title)
                        else -> stringResource(shownFn.title)
                    }
                    FnFrame(onClose = if (page == null) ::home else null, onBack = if (page != null) ::pop else null, title = title,
                        land = if (shownFn == Fn.BREATHE || shownFn == Fn.SLEEP || shownFn == Fn.SETTLE) Land.QUIET else Land.SKY) {
                    when {
                        page?.startsWith("topic:") == true -> LearnTopicScreen(page.removePrefix("topic:").toInt(), onBack = ::pop, onCard = { push("card:$it") })
                        page == "about" -> AboutScreen(version = BuildConfig.VERSION_NAME, onIntro = { showIntro = true }, onBack = ::pop,
                            updates = { UpdatesAbout(onOpen = { showUpdate = true }) })
                        page == "tones-how" -> TonesHowScreen(onBack = ::pop)
                        page == "sounds" -> SoundsScreen(player, actions, onBack = ::pop, hint = pickerHint, onHintSeen = graph.settings::setPickerHintSeen)
                        page == "tones" -> TonesScreen(player, actions, onHow = { push("tones-how") }, onFinder = { push("finder") })
                        page == "settings" -> settingsPage(::pop)
                        page == "finder" -> finderPage(::pop)
                        // Under Respirer, pace, length and voice: the breathing half of the setup only.
                        page == "calm" -> CalmScreen(
                            modules = modules.copy(meditation = false), practice = Practice.BREATHE, onPractice = {},
                            breath = breath, meditation = meditation, soundName = chosenSound(context, player).ifEmpty { null },
                            onBreath = ::keepBreath, onMeditation = ::keepMeditation,
                            onStartBreath = ::startBreath, onStartMeditation = ::startMeditation, voice = voiceState,
                        )
                        page?.startsWith("card:") == true -> {
                            // "card:c3_1" from the library or a Journal tag; Next walks the reading order in place.
                            val card = Learn.card(page.removePrefix("card:"))
                            if (card == null) LaunchedEffect(page) { pop() }
                            else LearnCardScreen(card, backLabel = stringResource(R.string.learn_back), onBack = ::pop,
                                onNext = { pages = (stack.dropLast(1) + "card:$it").joinToString("/") })
                        }
                        else -> when (shownFn) {
                        Fn.SLEEP, Fn.SETTLE, Fn.TONES -> SoloScene(
                            scene = Scene.valueOf(shownFn.name), ui = player, actions = actions,
                            onSounds = { push("sounds") }, onTonesSetup = { push("tones") },
                            // An empty scene's suggestion, on his tap: its sounds and timer become the scene's mix, then it starts, fading in.
                            onSuggest = { mix ->
                                graph.player.setMix(mix.layers); graph.player.setTimer(mix.timer)
                                keepMix(); graph.player.play()
                            },
                        )
                        Fn.BREATHE -> BreatheScreen(breath, onChange = ::keepBreath,
                            onStart = ::startBreath, onMore = { push("calm") })
                        Fn.MEDITATE -> MeditateScreen(meditation, chosenSound(context, player).ifEmpty { null }, voiceState,
                            onChange = ::keepMeditation, onStart = ::startMeditation)
                        Fn.EAR -> finderPage(::home)
                        Fn.JOURNAL -> JournalScreen(
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
                        Fn.LEARN -> LearnScreen(onFinder = { open(Fn.EAR) }, onTopic = { push("topic:$it") }, onSettings = { open(Fn.SETTINGS) }, onAbout = { push("about") }, onCard = { push("card:$it") })
                        Fn.SETTINGS -> settingsPage(::home)
                        }
                    }
                    }
                    }
                }
                }
                LaunchIntroOverlay(intro, firstLaunch)
                // A newer build found on opening offers itself once, never over the introduction or a session.
                UpdatesOnOpen(canAnnounce = { !showIntro && !breathPrelude && breathStartedAt == null && meditationStartedAt == null }, onFound = { showUpdate = true })
                if (showUpdate) UpdateSheet(onDismiss = { showUpdate = false })
            }
        }
    }
}

/** Where the app stands: the introduction, a session started at [at], or home. */
private data class Route(val kind: Int, val at: Long? = null) {
    companion object { const val INTRO = 0; const val BREATH = 1; const val MEDITATION = 2; const val HOME = 3; const val PRELUDE = 4 }
}

/** Where navigation stands: home ([fn] null), a function, or a page [depth] deep inside it. */
private data class Place(val fn: Fn?, val page: String?, val pages: Int) {
    val depth get() = if (fn == null) 0 else 1 + pages
}
