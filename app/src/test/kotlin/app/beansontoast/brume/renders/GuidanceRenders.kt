package app.beansontoast.brume.renders

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.beansontoast.brume.core.breath.BreathPlan
import app.beansontoast.brume.core.breath.Technique
import app.beansontoast.brume.core.breath.Variant
import app.beansontoast.brume.core.breath.Variants
import app.beansontoast.brume.ui.BreathPrelude
import app.beansontoast.brume.ui.BreathSession
import app.beansontoast.brume.ui.BreathSetup
import app.beansontoast.brume.ui.BreatheScreen
import app.beansontoast.brume.ui.MeditateScreen
import app.beansontoast.brume.ui.FnFrame
import app.beansontoast.brume.ui.BrumeTheme
import app.beansontoast.brume.ui.Meditation
import app.beansontoast.brume.ui.MeditationSession
import app.beansontoast.brume.ui.MeditationSetup
import app.beansontoast.brume.voice.Voice
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Breathing techniques and spoken guidance (2026-11-01), in French at 360x780, night and day, on
 * invented data: the technique picker and its options, hold phases, the note before a first hold,
 * the humming prelude, and a guided meditation's start and its sounds cue.
 * `BRUME_RENDERS=<dir> ./gradlew --offline :app:testGithubDebugUnitTest --tests '*GuidanceRenders*'`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "fr-w360dp-h780dp-xxhdpi")
class GuidanceRenders(private val shot: String) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun only() = assumeTrue(System.getenv("BRUME_RENDERS") != null)

    private val box = BreathSetup(Technique.BOX, sound = true)

    @Test fun render() {
        val night = shot.endsWith("-night")
        val dark = night || shot.endsWith("-dark")
        when (shot.removeSuffix("-night").removeSuffix("-day")) {
            "picker" -> tab(dark, night) { BreatheScreen(box, {}, {}, soundName = "Brun + Pluie", voice = Voice.State.READY, previewAt = 2.0) }
            "picker-options" -> tab(dark, night) { BreatheScreen(box, {}, {}, soundName = "Brun + Pluie", voice = Voice.State.READY, previewAt = 2.0, options = true) }
            "picker-long-hold" -> tab(dark, night) {
                BreatheScreen(BreathSetup(Technique.LONG_OUT, Variants().with(Technique.LONG_OUT, Variant.HOLD)), {}, {}, voice = Voice.State.MISSING,
                    previewAt = 2.0, options = true)
            }
            "hold-box" -> bare(dark, night) { BreathSession(BreathPlan.of(Technique.BOX.pattern(Variant.STANDARD), 5), 0L, night, haptics = false, onEnd = {}, elapsed = 6.3) }
            "hold-478" -> bare(dark, night) { BreathSession(BreathPlan.of(Technique.FOUR_SEVEN_EIGHT.pattern(), 4), 0L, night, haptics = false, onEnd = {}, elapsed = 4.2) }
            "hum-out" -> bare(dark, night) { BreathSession(BreathPlan.of(Technique.HUM.pattern(), 7), 0L, night, haptics = false, onEnd = {}, elapsed = 7.0) }
            "safety" -> bare(dark, night) { BreathPrelude(BreathSetup(Technique.FOUR_SEVEN_EIGHT), safety = true, onStart = {}, onBack = {}) }
            "prelude-hum" -> bare(dark, night) { BreathPrelude(BreathSetup(Technique.HUM), safety = false, onStart = {}, onBack = {}) }
            "meditate-setup" -> tab(dark, night) {
                MeditateScreen(MeditationSetup(Meditation.SOUNDS, 5), "Brun + Pluie", Voice.State.READY, {}, {}, previewAt = 2.0, options = true)
            }
            "meditate-setup-novoice" -> tab(dark, night) {
                MeditateScreen(MeditationSetup(Meditation.BREATH, 10), null, Voice.State.MISSING, {}, {}, previewAt = 2.0, options = true)
            }
            "meditate-start" -> bare(dark, night) { MeditationSession(MeditationSetup(Meditation.SOUNDS, 5), 0L, night, {}, elapsed = 6.0) }
            "meditate-sounds-cue" -> bare(dark, night) { MeditationSession(MeditationSetup(Meditation.SOUNDS, 5), 0L, night, {}, elapsed = 143.0) }
            "meditate-close" -> bare(dark, night) { MeditationSession(MeditationSetup(Meditation.BREATH, 5), 0L, night, {}, elapsed = 293.0) }
            else -> error(shot)
        }
    }

    private fun tab(dark: Boolean, night: Boolean, content: @Composable () -> Unit) = capture {
        BrumeTheme(dark = dark, night = night) { FnFrame(onClose = {}) { content() } }
    }

    private fun bare(dark: Boolean, night: Boolean, content: @Composable () -> Unit) = capture { BrumeTheme(dark = dark, night = night) { content() } }

    private fun capture(content: @Composable () -> Unit) {
        compose.setContent { content() }
        compose.waitForIdle()
        val bitmap = compose.runOnUiThread {
            val v = compose.activity.window.decorView
            Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }
        }
        writePng(bitmap, File(System.getenv("BRUME_RENDERS")!!, "guidance-$shot.png"))
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun shots() = listOf(
            "picker", "picker-options", "picker-long-hold", "hold-box", "hold-478", "hum-out", "safety", "prelude-hum",
            "meditate-setup", "meditate-setup-novoice", "meditate-start", "meditate-sounds-cue", "meditate-close",
        ).flatMap { listOf("$it-night", "$it-day") }.map { arrayOf<Any>(it) }
    }
}
