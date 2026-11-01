package app.beansontoast.brume.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.beansontoast.brume.R
import app.beansontoast.brume.core.breath.Pattern
import app.beansontoast.brume.core.scene.MiddleMotion
import app.beansontoast.brume.core.scene.SceneMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Sacha's scene art (PR #3, 2026-11-01), three layers back to front on his 1080 x 2340 canvas: an
 * opaque sky, a wide mist (1404 px, so it can drift), and the middle (the line, crescent, swell or
 * marks). The transparent layers ship cropped to the rows they paint, with [middleTop] and
 * [mistTop] saying where they sit on the canvas: a full-size layer decodes to 10 MB, the crop to 2.
 */
internal class SceneLayers(
    @DrawableRes val background: Int,
    @DrawableRes val mist: Int, val mistTop: Int,
    @DrawableRes val middle: Int, val middleTop: Int,
)

private const val CANVAS_W = 1080f
private const val CANVAS_H = 2340f
private const val MIST_OVERHANG = 162f

/**
 * Decoded layers, shared by every page. Sized for the shown scene and its neighbours (a scene is
 * 6 to 10 MB decoded), so a third scene pushes the oldest out. The sky is a soft gradient and
 * decodes at half size; the line art keeps its full resolution.
 */
internal object SceneArtCache {
    private val cache = object : LruCache<Int, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: Int, value: ImageBitmap) = value.width * value.height * 4
    }

    fun peek(@DrawableRes res: Int): ImageBitmap? = cache.get(res)

    fun load(context: Context, @DrawableRes res: Int, sample: Int = 1): ImageBitmap =
        cache.get(res) ?: BitmapFactory.decodeResource(context.resources, res, BitmapFactory.Options().apply { inSampleSize = sample })
            .asImageBitmap().also { it.prepareToDraw(); cache.put(res, it) }

    /** Decodes a scene's three layers; off the main thread in the app, directly in the renders. */
    fun loadAll(context: Context, layers: SceneLayers) {
        load(context, layers.background, sample = 2)
        load(context, layers.mist)
        load(context, layers.middle)
    }

    suspend fun preload(context: Context, layers: SceneLayers?) {
        if (layers != null) withContext(Dispatchers.IO) { loadAll(context.applicationContext, layers) }
    }
}

@Composable
private fun rememberLayer(@DrawableRes res: Int, sample: Int = 1): State<ImageBitmap?> {
    val context = LocalContext.current
    val layer = remember(res) { mutableStateOf(SceneArtCache.peek(res)) }
    LaunchedEffect(res) {
        if (layer.value == null) layer.value = withContext(Dispatchers.IO) { SceneArtCache.load(context.applicationContext, res, sample) }
    }
    return layer
}

/**
 * The scene clock in milliseconds, for the drift and the breath. It ticks at [SceneMotion.FRAME_MS]
 * and only while the app is in front: screen off or app in the background stops the activity, and
 * the clock with it, then picks up where it was, so nothing jumps. Reduced motion never starts it.
 */
@Composable
internal fun rememberSceneClock(still: Boolean): () -> Long {
    val clock = remember { mutableLongStateOf(0L) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(still, lifecycle) {
        if (still) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val base = clock.longValue
            val start = withFrameNanos { it }
            while (true) {
                withFrameNanos { clock.longValue = base + (it - start) / 1_000_000 }
                delay(SceneMotion.FRAME_MS)
            }
        }
    }
    return { clock.longValue }
}

/**
 * A scene's art behind its page, cropped from the centre like a full-bleed photo and dimmed as the
 * placeholder was (35% in night hours, 70% otherwise), so the title and the disc stay legible.
 * Sacha keeps the centre dark and paints only the lower 60%.
 *  - The mist drifts sideways (±80 px); the middle's light alone breathes, its shape never warps:
 *    Sleep and Settle barely, Breathe with the chosen rhythm, Tones' marks dissolving softly.
 *  - The sky never moves. During the swipe the mist and the middle hold back a little
 *    ([SceneMotion]'s parallax), the mist more, so the scene has depth.
 *  - Reduced motion: still layers, no drift, no parallax.
 *  - In a light theme the night sky would put dark words on dark ground, so the sky and the mist
 *    are left out and only the middle's line art is drawn, in the theme's quiet ink.
 * [clock] and [offset] are read while drawing only, so a tick or a swipe redraws without recomposing.
 */
@Composable
internal fun SceneBackdrop(
    scene: Scene,
    layers: SceneLayers,
    night: Boolean,
    still: Boolean,
    clock: () -> Long,
    offset: () -> Float,
    breathe: Pattern?,
    modifier: Modifier = Modifier,
) {
    val sky by rememberLayer(layers.background, sample = 2)
    val mist by rememberLayer(layers.mist)
    val middle by rememberLayer(layers.middle)
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val tint = if (dark) null else remember(scheme.onSurfaceVariant) { ColorFilter.tint(scheme.onSurfaceVariant, BlendMode.SrcIn) }
    val ready = sky != null && mist != null && middle != null
    // A scene decoded late fades in rather than popping.
    val shown by animateFloatAsState(if (ready) 1f else 0f, if (still) snap() else tween(600), label = "art")
    val dim = if (night) 0.35f else 0.7f
    val seed = scene.ordinal
    val motion = when (scene) {
        Scene.BREATHE -> MiddleMotion.BREATH
        Scene.TONES -> MiddleMotion.DISSOLVE
        else -> MiddleMotion.STILL
    }

    Canvas(modifier.clipToBounds()) {
        val s = max(size.width / CANVAS_W, size.height / CANVAS_H)
        val left = (size.width - CANVAS_W * s) / 2
        val top = (size.height - CANVAS_H * s) / 2
        val travel = if (still) 0f else offset() * size.width
        val t = if (still) 0.0 else clock() / 1000.0
        val alpha = dim * shown

        if (dark) sky?.let { layer(it, left, top, CANVAS_W * s, CANVAS_H * s, alpha, null) }

        // In a light theme the mist reads as a grey smudge on paper, so only the line art stays.
        if (dark) mist?.let {
            val drift = if (still) 0f else SceneMotion.drift(t, seed) * SceneMotion.DRIFT_PX
            layer(it, left + (drift - MIST_OVERHANG) * s + travel * SceneMotion.MIST_PARALLAX, top + layers.mistTop * s,
                it.width * s, it.height * s, alpha, tint)
        }

        middle?.let {
            val light = if (still) 1f else SceneMotion.middleAlpha(t, motion, seed, breathe)
            layer(it, left + travel * SceneMotion.MIDDLE_PARALLAX, top + layers.middleTop * s, it.width * s, it.height * s, alpha * light, tint)
        }
    }
}

private fun DrawScope.layer(image: ImageBitmap, x: Float, y: Float, w: Float, h: Float, alpha: Float, tint: ColorFilter?) {
    drawImage(
        image,
        dstOffset = IntOffset(x.roundToInt(), y.roundToInt()),
        dstSize = IntSize(w.roundToInt(), h.roundToInt()),
        alpha = alpha,
        colorFilter = tint,
        filterQuality = FilterQuality.Low,
    )
}

/** Where each scene's layers sit on Sacha's canvas (rows from the top of 2340). */
internal val sceneArt = mapOf(
    Scene.SLEEP to SceneLayers(R.drawable.scene_sleep_bg, R.drawable.scene_sleep_mist, 1603, R.drawable.scene_sleep_mid, 1450),
    Scene.SETTLE to SceneLayers(R.drawable.scene_settle_bg, R.drawable.scene_settle_mist, 1272, R.drawable.scene_settle_mid, 1768),
    Scene.BREATHE to SceneLayers(R.drawable.scene_breathe_bg, R.drawable.scene_breathe_mist, 1693, R.drawable.scene_breathe_mid, 1449),
    Scene.TONES to SceneLayers(R.drawable.scene_tones_bg, R.drawable.scene_tones_mist, 1695, R.drawable.scene_tones_mid, 1467),
)
