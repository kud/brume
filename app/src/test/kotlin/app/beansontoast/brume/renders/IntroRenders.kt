package app.beansontoast.brume.renders

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.beansontoast.brume.ui.BrumeTheme
import app.beansontoast.brume.ui.INTRO_PAGES
import app.beansontoast.brume.ui.Onboarding
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale

/** The introduction, every page night and day, and three in French. Same switch as [Renders]. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xxhdpi")
class IntroRenders(private val page: Int, private val theme: String, private val lang: String) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun only() = assumeTrue(System.getenv("BRUME_RENDERS") != null)

    @Test fun render() {
        val night = theme == "night"
        compose.setContent {
            val base = LocalContext.current
            val config = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(lang)) }
            CompositionLocalProvider(LocalContext provides base.createConfigurationContext(config), LocalConfiguration provides config) {
                BrumeTheme(dark = night, night = night) { Onboarding(onFinish = {}, startPage = page) }
            }
        }
        compose.waitForIdle()
        val bitmap = compose.runOnUiThread {
            val v = compose.activity.window.decorView
            Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }
        }
        writePng(bitmap, File(System.getenv("BRUME_RENDERS")!!, "intro-${page + 1}-$theme-$lang.png"))
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}-{2}")
        fun shots() = (0 until INTRO_PAGES).flatMap { p -> listOf(arrayOf<Any>(p, "night", "en"), arrayOf<Any>(p, "day", "en")) } +
            listOf(0, 2, 3).map { arrayOf<Any>(it, "night", "fr") }
    }
}
