package app.beansontoast.brume.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import app.beansontoast.brume.R

/*
 * The landscape behind the screens (Iris, 2026-11-01, the misty-landscape direction). Four layers,
 * back to front: sky, far ridges, mist, near ground, each a full-frame image of the same 9:19.5
 * canvas (Sacha's PNGs are 1440x3120), cropped to fill and pinned to the bottom so the layers stay
 * registered on any phone.
 *
 * The seam for Sacha's art is the resource name, nothing else: `land_home_{sky,far,mist,near}_{day,night}`.
 * Today they are quiet vector placeholders in res/drawable. To drop in the art, delete
 * res/drawable/land_home_<layer>_<time>.xml and put land_home_<layer>_<time>.png in
 * res/drawable-nodpi/: no code changes. Night follows the theme (the dusk scheme), not the clock.
 */

/** Which layers a screen shows. Home has them all; a working screen keeps the sky and the far haze, so text never sits on a hill. */
enum class Land(val far: Boolean, val mist: Boolean, val near: Boolean) {
    HOME(far = true, mist = true, near = true),
    QUIET(far = true, mist = true, near = false),
    SKY(far = false, mist = false, near = false),
}

@Composable
internal fun duskTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

@Composable
fun Landscape(land: Land = Land.HOME, modifier: Modifier = Modifier) {
    val night = duskTheme()
    Box(modifier.fillMaxSize()) {
        Layer(if (night) R.drawable.land_home_sky_night else R.drawable.land_home_sky_day)
        if (land.far) Layer(if (night) R.drawable.land_home_far_night else R.drawable.land_home_far_day)
        if (land.mist) Layer(if (night) R.drawable.land_home_mist_night else R.drawable.land_home_mist_day)
        if (land.near) Layer(if (night) R.drawable.land_home_near_night else R.drawable.land_home_near_day)
    }
}

@Composable
private fun Layer(@DrawableRes id: Int) {
    // Decoration only: nothing to read, so nothing for TalkBack.
    Image(painterResource(id), contentDescription = null, contentScale = ContentScale.Crop, alignment = Alignment.BottomCenter,
        modifier = Modifier.fillMaxSize())
}
