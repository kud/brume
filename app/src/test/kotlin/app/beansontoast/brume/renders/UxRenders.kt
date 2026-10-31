package app.beansontoast.brume.renders

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.beansontoast.brume.core.sound.Ambience
import app.beansontoast.brume.core.sound.NoiseColour
import app.beansontoast.brume.core.sound.SoundEngine
import app.beansontoast.brume.core.sound.ToneSettings
import app.beansontoast.brume.playback.PlayerUi
import app.beansontoast.brume.ui.BrumeScaffold
import app.beansontoast.brume.ui.BrumeScaffoldTwo
import app.beansontoast.brume.ui.BrumeTheme
import app.beansontoast.brume.ui.ListenActions
import app.beansontoast.brume.ui.ListenComfort
import app.beansontoast.brume.ui.ListenOnePage
import app.beansontoast.brume.ui.Tab
import app.beansontoast.brume.ui.TonesScreen
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The Listen UX prototypes (branch ux-listen), on invented data. Option A is one page, option B two
 * destinations. A `-full` shot is the whole scrolling page on a tall screen.
 * `BRUME_RENDERS=<dir> ./gradlew --offline :app:testDebugUnitTest --tests '*UxRenders*'`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xxhdpi")
class UxRenders(private val shot: String) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun only() = assumeTrue(System.getenv("BRUME_RENDERS") != null)

    private val idle = PlayerUi(NoiseColour.PINK, null, 0.3f, 0.45f)
    private val playing = PlayerUi(NoiseColour.BROWN, Ambience.RAIN, 0.35f, 0.25f, playing = true, timerMinutes = 45, timerRemainingMinutes = 38)
    private val tones = PlayerUi(
        mode = SoundEngine.Mode.TONES, tones = ToneSettings(pitch = 6200, trims = listOf(1f, 0.85f, 0.7f, 0.6f)), toneLevel = 0.2f,
        playing = true, timerMinutes = 90, timerRemainingMinutes = 72,
    )

    @Test fun render() {
        val (option, state, theme) = shot.removeSuffix("-full").split("-")
        if (shot.endsWith("-full")) RuntimeEnvironment.setQualifiers("+h1500dp")
        val ui = when (state) { "idle" -> idle; "playing" -> playing; else -> tones }
        val night = theme == "night"
        shoot(dark = night, night = night) {
            if (option == "a") BrumeScaffold(Tab.LISTEN, {}) { ListenOnePage(ui, ListenActions()) }
            else BrumeScaffoldTwo(tones = state == "tones") {
                if (state == "tones") TonesScreen(ui, ListenActions()) else ListenComfort(ui, ListenActions())
            }
        }
    }

    private fun shoot(dark: Boolean, night: Boolean, content: @Composable () -> Unit) {
        compose.setContent { BrumeTheme(dark = dark, night = night) { content() } }
        compose.waitForIdle()
        val bitmap = compose.runOnUiThread {
            val v = compose.activity.window.decorView
            Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }
        }
        writePng(bitmap, File(System.getenv("BRUME_RENDERS")!!, "ux-$shot.png"))
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun shots() = listOf("a", "b").flatMap { o ->
            listOf("idle-night", "playing-night", "idle-day", "playing-day", "tones-night", "tones-day", "idle-night-full", "tones-night-full").map { "$o-$it" }
        }.map { arrayOf<Any>(it) }
    }
}
