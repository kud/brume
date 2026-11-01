package app.beansontoast.brume.renders

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import app.beansontoast.brume.R
import app.beansontoast.brume.core.sound.Ambience
import app.beansontoast.brume.core.sound.NoiseColour
import app.beansontoast.brume.playback.PlayerUi
import app.beansontoast.brume.ui.BrumeScaffold
import app.beansontoast.brume.ui.BrumeTheme
import app.beansontoast.brume.ui.ListenActions
import app.beansontoast.brume.ui.SceneScreen
import app.beansontoast.brume.ui.Scene
import app.beansontoast.brume.ui.scenes
import app.beansontoast.brume.Modules
import app.beansontoast.brume.ui.Onboarding
import app.beansontoast.brume.ui.Tab
import app.beansontoast.brume.ui.launch.Choreographies
import app.beansontoast.brume.ui.launch.Choreography
import app.beansontoast.brume.ui.launch.LaunchAnchors
import app.beansontoast.brume.ui.launch.LaunchCanvas
import app.beansontoast.brume.ui.launch.LocalLaunchAnchors
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
 * The launch intro: the system splash's lockup as the vector draws it, then the overlay's frames over
 * the real screen underneath, night and day, and the frames of the cold-start GIF. Same switch as [Renders].
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xxhdpi")
class LaunchRenders(private val shot: String) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun only() = assumeTrue(System.getenv("BRUME_RENDERS") != null)

    private val idle = PlayerUi(NoiseColour.PINK, Ambience.RAIN, 0.3f, 0.45f)

    @Test fun render() {
        val parts = shot.split("-")
        val night = "night" in parts
        when {
            shot.startsWith("splash") -> capture(night) { SplashStill() }
            shot.startsWith("first") -> capture(night) { Over(Choreographies.welcome, parts.last().toFloat()) { Onboarding(onFinish = {}) } }
            shot.startsWith("reduced") -> capture(night) { Over(Choreographies.fade, parts.last().toFloat()) { Listen() } }
            shot.startsWith("warm") -> capture(night) { Over(Choreographies.warm, parts.last().toFloat()) { Listen() } }
            else -> capture(night) { Over(Choreographies.rise, parts.last().toFloat()) { Listen() } }
        }
    }

    @Composable private fun Listen() = BrumeScaffold(Tab.LISTEN, {}, Modules(), bleed = true) { SceneScreen(Modules().scenes(), Scene.SETTLE, {}, idle, ListenActions()) }

    /** What the system splash shows: splash_lockup at 288 dp, centred, on splash_ground. */
    @Composable private fun SplashStill() {
        Box(Modifier.fillMaxSize().background(colorResource(R.color.splash_ground)), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.splash_lockup), null, Modifier.size(288.dp))
        }
    }

    @Composable private fun Over(script: Choreography, ms: Float, screen: @Composable () -> Unit) {
        val anchors = remember { LaunchAnchors() }
        CompositionLocalProvider(LocalLaunchAnchors provides anchors) {
            Box(Modifier.fillMaxSize()) {
                screen()
                LaunchCanvas(script, ms, icon = null)
            }
        }
    }

    private fun capture(night: Boolean, content: @Composable () -> Unit) {
        compose.setContent {
            val base = LocalContext.current
            val config = Configuration(base.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    (if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
            }
            CompositionLocalProvider(LocalContext provides base.createConfigurationContext(config), LocalConfiguration provides config) {
                BrumeTheme(dark = night, night = night) { content() }
            }
        }
        compose.waitForIdle()
        // Anchors are laid out on the first pass; one more lets the overlay read them.
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        val bitmap = compose.runOnUiThread {
            val v = compose.activity.window.decorView
            Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }
        }
        val dir = File(System.getenv("BRUME_RENDERS")!!, if (shot.startsWith("gif")) "gif" else ".").apply { mkdirs() }
        writePng(bitmap, File(dir, "$shot.png"))
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun shots() = (
            listOf("splash-night", "splash-day") +
                listOf("night", "day").flatMap { t -> listOf(0, 200, 400, 600, 800).map { "cold-$t-%03d".format(it) } } +
                listOf("night", "day").flatMap { t -> listOf(0, 300, 600, 800).map { "first-$t-%03d".format(it) } } +
                listOf("reduced-night-100", "warm-night-150") +
                (0..800 step 40).map { "gif-night-%03d".format(it) }
            ).map { arrayOf<Any>(it) }
    }
}
