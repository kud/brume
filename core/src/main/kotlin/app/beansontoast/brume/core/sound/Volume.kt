package app.beansontoast.brume.core.sound

/**
 * The scene's one volume (Iris, 2026-10-31): a vertical slide moves every layer of the mix by the
 * same step, so the blend keeps its shape, and stops where the first layer reaches an end rather
 * than squashing the others. It never touches the system volume, and it only runs on a slide.
 */
object Volume {
    /** Every level shifted by [delta], clamped as a whole: the mix keeps its proportions. */
    fun shift(levels: List<Float>, delta: Float): List<Float> {
        if (levels.isEmpty()) return levels
        val down = -levels.min().coerceAtLeast(0f)
        val up = (1f - levels.max()).coerceAtLeast(0f)
        val d = delta.coerceIn(down, up)
        return levels.map { LevelRules.clampLevel(it + d) }
    }

    /** How loud, told in words, never as a number: 0 very soft .. 4 very loud, by the loudest layer. */
    fun band(levels: List<Float>): Int {
        val loudest = levels.maxOrNull() ?: return 0
        return (loudest.coerceIn(0f, 1f) * BANDS).toInt().coerceAtMost(BANDS - 1)
    }

    const val BANDS = 5
}
