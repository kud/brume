package app.beansontoast.brume.renders

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.beansontoast.brume.ui.BrumeTheme
import app.beansontoast.brume.ui.QuietSheet
import app.beansontoast.brume.ui.UpdateSheetContent
import app.beansontoast.brume.update.AppVersion
import app.beansontoast.brume.update.ReleaseAsset
import app.beansontoast.brume.update.Update
import app.beansontoast.brume.update.UpdateState
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
 * The updater in the quiet sheet (Iris, phase 4), in French at 360x780, on an invented release.
 * `BRUME_RENDERS=<dir> ./gradlew --offline :app:testGithubDebugUnitTest --tests '*UpdateRenders*'`.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "fr-w360dp-h780dp-xxhdpi")
class UpdateRenders(private val shot: String) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun only() = assumeTrue(System.getenv("BRUME_RENDERS") != null)

    @Test fun render() {
        val night = shot.endsWith("-night")
        val update = Update(AppVersion(0, 4, 0, 31), "v0.4.0-dev.31", ReleaseAsset("brume-0.4.0-dev.31-debug.apk", "https://example.invalid/a.apk", null, 1L), ByteArray(32))
        val state = when (shot.substringBeforeLast('-')) {
            "update-available" -> UpdateState.Available(update)
            else -> UpdateState.Downloading(update, 40)
        }
        compose.setContent {
            BrumeTheme(dark = night, night = night) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    QuietSheet(true, {}, "Mettre à jour Brume") { UpdateSheetContent("0.4.0-dev.30", state, {}, {}, {}, {}) }
                }
            }
        }
        compose.waitForIdle()
        val bitmap = compose.runOnUiThread {
            val v = compose.activity.window.decorView
            Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }
        }
        writePng(bitmap, File(System.getenv("BRUME_RENDERS")!!, "brume-$shot.png"))
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun shots() = listOf("update-available-day", "update-available-night", "update-downloading-day")
    }
}
