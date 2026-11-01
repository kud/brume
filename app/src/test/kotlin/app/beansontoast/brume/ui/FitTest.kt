package app.beansontoast.brume.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import app.beansontoast.brume.Modules
import app.beansontoast.brume.core.breath.Technique
import app.beansontoast.brume.core.journal.CheckIn
import app.beansontoast.brume.core.learn.Learn
import app.beansontoast.brume.core.sound.Ambience
import app.beansontoast.brume.core.sound.MixLayer
import app.beansontoast.brume.core.sound.NoiseColour
import app.beansontoast.brume.core.sound.Sound
import app.beansontoast.brume.core.sound.SoundEngine
import app.beansontoast.brume.core.sound.ToneSettings
import app.beansontoast.brume.playback.PlayerUi
import app.beansontoast.brume.sync.SyncState
import app.beansontoast.brume.voice.Voice
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * No screen scrolls, so every screen must fit its frame (the no-scroll pass, 2026-11-01): at
 * 360x780 dp in French, font scale 1.0, nothing on the key screens may end below the window's
 * bottom. Each case is also measured at font scale 1.3 and written to
 * `build/reports/fit/<case>.txt`, for reading, never failing. A case that walks several states
 * (every card, every finder step) checks each one. The `probe` case plants a column far taller than
 * the frame and must be caught, so the check can fail.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "fr-w360dp-h780dp-xxhdpi")
class FitTest(private val case: String) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val mix = PlayerUi(
        mix = listOf(MixLayer(Sound.Noise(NoiseColour.PINK), -0.2f), MixLayer(Sound.Recorded(Ambience.RAIN)), MixLayer(Sound.Recorded(Ambience.CAFE))),
        levels = mapOf(Sound.Noise(NoiseColour.PINK) to 0.3f, Sound.Recorded(Ambience.RAIN) to 0.5f, Sound.Recorded(Ambience.CAFE) to 0.2f),
        playing = true, timerMinutes = 45, timerRemainingMinutes = 38,
    )
    private val tones = PlayerUi(mode = SoundEngine.Mode.TONES, tones = ToneSettings(pitch = 6200, trims = listOf(1f, 0.85f, 0.7f, 0.6f)), toneLevel = 0.2f,
        playing = true, timerMinutes = 20, timerRemainingMinutes = 12)
    private val week = listOf(
        CheckIn("2026-01-20", 5, 3, "Soirée calme, pluie basse.\n#poor-sleep"),
        CheckIn("2026-01-19", 6, 5, "Repas bruyant à midi.\n#caffeine #stress"),
        CheckIn("2026-01-18", 4, 2),
    )
    private val today = LocalDate.of(2026, 1, 20)

    /** One screen as the app frames it: [Fn]'s title and corner, or a page's ‹. */
    @Composable private fun Framed(title: String, page: Boolean = false, content: @Composable () -> Unit) =
        FnFrame(onClose = if (page) null else ({}), onBack = if (page) ({}) else null, title = title, content = content)

    private fun shot(content: @Composable () -> Unit): @Composable () -> Unit = content

    /** The states a case walks through: each is composed in turn and measured. */
    private fun states(): List<@Composable () -> Unit> = when (case) {
        "probe" -> listOf(shot { Framed("Probe") { Column(Modifier.fillMaxSize()) { repeat(60) { Text("Ligne $it") } } } })
        "home" -> listOf(
            shot { HomeScreen(Modules().fns(), onOpen = {}) },
            shot { HomeScreen(Modules().fns(), onOpen = {}, mini = { MiniPlayer("Bruit rose + Pluie + Café", onPause = {}) }) },
        )
        "scenes" -> listOf(Scene.SLEEP, Scene.SETTLE).flatMap { s ->
            listOf(
                shot { Framed(stringResource(s.title)) { SoloScene(s, mix, ListenActions()) } },
                shot { Framed(stringResource(s.title)) { SoloScene(s, PlayerUi(mix = emptyList()), ListenActions()) } },
            )
        } + listOf(shot { Framed(stringResource(Scene.TONES.title)) { SoloScene(Scene.TONES, tones, ListenActions()) } })
        "breathe" -> listOf(
            shot { Framed(stringResource(Fn.BREATHE.title)) { BreatheScreen(BreathSetup(Technique.BOX), {}, {}, previewAt = 1.0) } },
            shot { Framed(stringResource(Fn.BREATHE.title)) { BreatheScreen(BreathSetup(Technique.BOX, sound = true), {}, {}, soundName = "Bruit rose + Pluie", voice = Voice.State.MISSING, previewAt = 1.0, options = true) } },
            shot { Framed(stringResource(Fn.BREATHE.title)) { BreatheScreen(BreathSetup(Technique.LONG_OUT), {}, {}, previewAt = 1.0, options = true) } },
            shot { Framed(stringResource(Fn.BREATHE.title)) { BreatheScreen(BreathSetup(Technique.BOX, sound = true), {}, {}, soundName = "Bruit rose + Pluie", voice = Voice.State.MISSING, previewAt = 1.0, options = true, optionsPage = 1) } },
            shot { Framed(stringResource(Fn.BREATHE.title)) { BreatheScreen(BreathSetup(Technique.LONG_OUT), {}, {}, previewAt = 1.0, options = true, optionsPage = 1) } },
        )
        "prelude" -> listOf(
            shot { BreathPrelude(BreathSetup(Technique.FOUR_SEVEN_EIGHT), safety = true, onStart = {}, onBack = {}) },
            shot { BreathPrelude(BreathSetup(Technique.FOUR_SEVEN_EIGHT), safety = true, onStart = {}, onBack = {}, startStep = 1) },
            shot { BreathPrelude(BreathSetup(Technique.BOX), safety = true, onStart = {}, onBack = {}) },
            shot { BreathPrelude(BreathSetup(Technique.HUM), safety = false, onStart = {}, onBack = {}) },
        )
        "meditate" -> listOf(
            shot { Framed(stringResource(Fn.MEDITATE.title)) { MeditateScreen(MeditationSetup(), "Bruit rose", Voice.State.IDLE, {}, {}, previewAt = 1.0) } },
            shot { Framed(stringResource(Fn.MEDITATE.title)) { MeditateScreen(MeditationSetup(), "Bruit rose", Voice.State.MISSING, {}, {}, previewAt = 1.0, options = true) } },
        )
        "mixer" -> listOf(null, SoundsSheet.ADD, SoundsSheet.EARS).map { sheet ->
            shot { Framed(stringResource(app.beansontoast.brume.R.string.scene_sounds), page = true) { SoundsScreen(mix, ListenActions(), onBack = {}, sheet = sheet) } }
        }
        "tones" -> listOf(
            shot { Framed(stringResource(app.beansontoast.brume.R.string.tab_tones), page = true) { TonesScreen(tones, ListenActions()) } },
            shot { Framed(stringResource(app.beansontoast.brume.R.string.tab_tones), page = true) { TonesScreen(tones, ListenActions(), startPage = 1) } },
            shot { Framed(stringResource(app.beansontoast.brume.R.string.tab_tones), page = true) { TonesScreen(tones, ListenActions(), startPage = 2) } },
            shot { Framed(stringResource(app.beansontoast.brume.R.string.tones_how), page = true) { TonesHowScreen {} } },
        )
        "journal" -> listOf(
            shot { Framed(stringResource(Fn.JOURNAL.title)) { JournalScreen(week, 3 * 3600, today, "Enregistré", { _, _, _ -> }, {}) } },
            shot { Framed(stringResource(Fn.JOURNAL.title)) { JournalScreen(week, 3 * 3600, today, null, { _, _, _ -> }, {}, noteOpen = true) } },
            shot { Framed(stringResource(Fn.JOURNAL.title)) { JournalScreen(emptyList(), 0, today, null, { _, _, _ -> }, {}) } },
        )
        "learn" -> (0..2).map { p -> shot { Framed(stringResource(Fn.LEARN.title)) { LearnScreen(startPage = p, still = true) } } } +
            listOf(
                shot { Framed(stringResource(Fn.LEARN.title)) { LearnScreen(doctorOpen = true) } },
                // The rocket: the loudest step, unweighted, with its extra note.
                shot { Framed(stringResource(Fn.LEARN.title)) { LearnScreen(startPage = 2, still = true, loudnessAt = 136) } },
                shot { Framed(stringResource(Fn.LEARN.title)) { LearnScreen(startPage = 2, still = true, loudnessAt = 100) } },
            )
        "topics" -> (1..Learn.TOPICS).map { t -> shot { Framed("Sujet $t", page = true) { LearnTopicScreen(t, {}, {}) } } }
        "cards" -> Learn.cards.map { c -> shot { Framed("Carte", page = true) { LearnCardScreen(c, "Retour", {}, onNext = {}) } } }
        "about" -> (0..2).map { p ->
            shot {
                Framed(stringResource(app.beansontoast.brume.R.string.about_brume), page = true) {
                    // The github build's updates section, as tall as it draws there.
                    AboutScreen("0.2.0", startPage = p, updates = { SectionTitle("Mises à jour"); Text("Vérifier si une version plus récente existe."); OutlinedButtonStandIn() })
                }
            }
        }
        "settings" -> (listOf(null) + SettingsSheet.entries).map { sheet ->
            shot {
                Framed(stringResource(Fn.SETTINGS.title)) {
                    SettingsScreen(true, {}, {}, sync = SyncState(folderName = "Brume", hasFolder = true, auto = true, lastSyncedAt = 1_768_900_000_000),
                        language = LanguageChoice("fr") {}, country = CountryChoice(null, app.beansontoast.brume.core.region.Regions.supported.first()) {}, sheet = sheet)
                }
            }
        }
        "finder" -> listOf(shot { Framed(stringResource(Fn.EAR.title)) { FinderScreen(emptyList(), FinderActions(), onBack = {}) } }) +
            FinderStep.entries.map { step ->
                shot { Framed(stringResource(Fn.EAR.title)) { FinderScreen(emptyList(), FinderActions(), onBack = {}, start = FinderPlace.Journey(step)) } }
            }
        "onboarding" -> (0 until INTRO_PAGES).map { p -> shot { Onboarding(onFinish = {}, startPage = p) } }
        else -> error(case)
    }

    @Test fun fits() {
        val shown = states()
        var index by mutableIntStateOf(0)
        var scale by mutableIntStateOf(100)
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, scale / 100f)) {
                BrumeTheme(dark = false, night = false) {
                    // Each state gets fresh state of its own, so a sheet opened by one never leaks into the next.
                    androidx.compose.runtime.key(index, scale) { shown[index]() }
                }
            }
        }
        val failures = mutableListOf<String>()
        val report = StringBuilder()
        for (s in listOf(100, 130)) for (i in shown.indices) {
            scale = s; index = i
            compose.waitForIdle()
            val found = measure()
            report.append("font ${s / 100f} · state $i: ").append(if (found.isEmpty()) "fits" else found.joinToString("; ")).append('\n')
            if (s == 100) failures += found.filter { it.startsWith("below") }.map { "state $i: $it" }
        }
        File("build/reports/fit").apply { mkdirs() }.resolve("$case.txt").writeText(report.toString())
        if (case == "probe") assertTrue("the probe's 60 lines must be found below the frame", failures.isNotEmpty())
        else assertTrue("$case doesn't fit 360x780 at font 1.0:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    /** What ends below the window, and which texts are cut short, on the screen as composed now. */
    private fun measure(): List<String> {
        val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
        val bottom = root.size.height + 1
        val out = mutableListOf<String>()
        fun walk(n: SemanticsNode) {
            val end = n.positionInRoot.y + n.size.height
            val words = n.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ")
                ?: n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
            if (words != null) {
                val layouts = mutableListOf<TextLayoutResult>()
                n.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
                val layout = layouts.firstOrNull()
                // A column out of room doesn't push its last rows below the window: it measures them
                // with no height left. So a text squeezed to nothing, or cut by its box, counts too.
                if (end > bottom) out += "below the frame by ${((end - root.size.height) / root.layoutInfo.density.density).toInt()} dp: \"${words.take(40)}\""
                else if (layout != null && words.isNotBlank() && squeezed(n, layout)) out += "below the frame, squeezed: \"${words.take(40)}\""
                if (layout != null && (0 until layout.lineCount).any { layout.isLineEllipsized(it) }) out += "cut short: \"${words.take(40)}\""
            }
            n.children.forEach(::walk)
        }
        walk(root)
        return out.distinct()
    }

    /** Squeezed to nothing, or short of more than half a line of what its words need. A pixel's rounding is not. */
    private fun squeezed(n: SemanticsNode, layout: TextLayoutResult): Boolean {
        if (n.size.height == 0) return true
        if (layout.lineCount == 0) return false
        val line = layout.multiParagraph.getLineBottom(0) - layout.multiParagraph.getLineTop(0)
        return layout.multiParagraph.height - layout.size.height > line / 2
    }

    @Composable private fun OutlinedButtonStandIn() =
        androidx.compose.material3.OutlinedButton({}, Modifier.padding(top = 12.dp).heightIn(min = 48.dp)) { Text("Rechercher une mise à jour") }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases() = listOf(
            "probe", "home", "scenes", "breathe", "prelude", "meditate", "mixer", "tones", "journal", "learn", "topics", "cards",
            "about", "settings", "finder", "onboarding",
        ).map { arrayOf<Any>(it) }
    }
}
