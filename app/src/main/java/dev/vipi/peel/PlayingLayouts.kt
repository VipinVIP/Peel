package dev.vipi.peel

/**
 * flipInterval can only be set from XML (it is not a RemoteViews method), so there is one playing
 * layout per supported interval and the decoder samples frames at exactly one of these rates.
 *
 * Each interval has two identical copies (a/b). Alternating between them on every play makes the
 * launcher inflate a fresh flipper, which restarts it (autoStart only fires on attach).
 */
object PlayingLayouts {
    val byInterval = linkedMapOf(
        40 to (R.layout.widget_playing_40 to R.layout.widget_playing_40_b),
        50 to (R.layout.widget_playing_50 to R.layout.widget_playing_50_b),
        60 to (R.layout.widget_playing_60 to R.layout.widget_playing_60_b),
        70 to (R.layout.widget_playing_70 to R.layout.widget_playing_70_b),
        80 to (R.layout.widget_playing_80 to R.layout.widget_playing_80_b),
        90 to (R.layout.widget_playing_90 to R.layout.widget_playing_90_b),
        100 to (R.layout.widget_playing_100 to R.layout.widget_playing_100_b),
        120 to (R.layout.widget_playing_120 to R.layout.widget_playing_120_b),
        130 to (R.layout.widget_playing_130 to R.layout.widget_playing_130_b),
        150 to (R.layout.widget_playing_150 to R.layout.widget_playing_150_b),
        170 to (R.layout.widget_playing_170 to R.layout.widget_playing_170_b),
        200 to (R.layout.widget_playing_200 to R.layout.widget_playing_200_b),
        250 to (R.layout.widget_playing_250 to R.layout.widget_playing_250_b),
        300 to (R.layout.widget_playing_300 to R.layout.widget_playing_300_b),
        400 to (R.layout.widget_playing_400 to R.layout.widget_playing_400_b),
        500 to (R.layout.widget_playing_500 to R.layout.widget_playing_500_b),
    )

    /** Smallest supported interval >= [ms] (or the largest one). */
    fun layout(intervalMs: Int, variant: Int): Int {
        val (a, b) = byInterval.getValue(intervalAtLeast(intervalMs))
        return if (variant % 2 == 0) a else b
    }

    fun intervalAtLeast(ms: Int) = byInterval.keys.firstOrNull { it >= ms } ?: byInterval.keys.last()
}
