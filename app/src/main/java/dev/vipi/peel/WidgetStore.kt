package dev.vipi.peel

import android.content.Context
import android.os.SystemClock
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom

/** Per-widget state: where its frames live and whether it's currently playing. */
object WidgetStore {
    /** Plays per tap; [FOREVER] means "until tapped again". */
    const val FOREVER = 0
    private const val MAX_LOOPS = 50

    /**
     * Every frame of a playing widget is one ~150-byte item in a single update; keeping the total
     * small lets several widgets start at once without hitting the ~1 MB binder buffer.
     */
    private const val MAX_ITEMS = 1100

    /** Resting frames after the last play, covering a late stop timer. */
    private const val RESTING_TAIL_MS = 4_000

    class Info(
        val count: Int,
        val intervalMs: Int,
        val version: Int,
        val secret: String,
        val posterIndex: Int,
        requestedLoops: Int,
        lastFiniteLoops: Int,
    ) {
        val restingFrames get() = (RESTING_TAIL_MS + intervalMs - 1) / intervalMs.coerceAtLeast(1)

        /** Most plays that fit the update budget for this GIF. */
        val maxLoops get() = ((MAX_ITEMS - restingFrames) / count.coerceAtLeast(1)).coerceIn(1, MAX_LOOPS)

        val loops = if (requestedLoops == FOREVER) FOREVER else requestedLoops.coerceIn(1, maxLoops)
        val forever get() = loops == FOREVER

        /** The count to return to when "Forever" is switched off. */
        val finiteLoops = lastFiniteLoops.coerceIn(1, maxLoops)

        /** How long one tap plays, or null when it loops until tapped again. */
        val playDurationMs: Long? get() = if (forever) null else count.toLong() * intervalMs * loops
    }

    private fun prefs(context: Context) = context.getSharedPreferences("widgets", Context.MODE_PRIVATE)

    fun framesDir(context: Context, widgetId: Int) = File(context.filesDir, "w$widgetId")

    fun info(context: Context, widgetId: Int): Info? {
        val p = prefs(context)
        val count = p.getInt("${widgetId}_count", 0)
        if (count == 0) return null
        return Info(
            count,
            p.getInt("${widgetId}_interval", 0),
            p.getInt("${widgetId}_version", 0),
            secret(context, widgetId),
            p.getInt("${widgetId}_poster", 0),
            p.getInt("${widgetId}_loops", 1),
            p.getInt("${widgetId}_lastLoops", 1),
        )
    }

    fun save(context: Context, widgetId: Int, result: FrameDecoder.Result) {
        val p = prefs(context)
        p.edit()
            .putInt("${widgetId}_count", result.count)
            .putInt("${widgetId}_interval", result.intervalMs)
            .putInt("${widgetId}_poster", result.posterIndex)
            .putInt("${widgetId}_version", p.getInt("${widgetId}_version", 0) + 1)
            .putString("${widgetId}_secret", newSecret())
            .putBoolean("${widgetId}_playing", false)
            .commit()
    }

    private fun newSecret(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** The widget's frame-URI secret, created on first use for widgets set up before secrets existed. */
    private fun secret(context: Context, widgetId: Int): String {
        val p = prefs(context)
        p.getString("${widgetId}_secret", null)?.let { return it }
        return newSecret().also { p.edit().putString("${widgetId}_secret", it).commit() }
    }

    fun secretMatches(context: Context, widgetId: Int, candidate: String): Boolean {
        val expected = prefs(context).getString("${widgetId}_secret", null) ?: return false
        return MessageDigest.isEqual(expected.toByteArray(), candidate.toByteArray())
    }

    /** True while the playing layout is shown (it may have already reached its last frame). */
    fun isPlaying(context: Context, widgetId: Int) = prefs(context).getBoolean("${widgetId}_playing", false)

    /** True while the animation is still running, i.e. a tap should stop rather than replay. */
    fun isMidPlayback(context: Context, widgetId: Int, info: Info): Boolean {
        if (!isPlaying(context, widgetId)) return false
        val duration = info.playDurationMs ?: return true
        val started = prefs(context).getLong("${widgetId}_started", 0)
        return SystemClock.elapsedRealtime() < started + duration
    }

    /** Kept across GIF changes; stops any current playback so the new count applies next tap. */
    fun setLoops(context: Context, widgetId: Int, loops: Int) {
        val editor = prefs(context).edit()
            .putInt("${widgetId}_loops", loops)
            .putBoolean("${widgetId}_playing", false)
        if (loops != FOREVER) editor.putInt("${widgetId}_lastLoops", loops)
        editor.commit()
    }

    /** Marks the widget as playing and returns a token identifying this playback. */
    fun startPlaying(context: Context, widgetId: Int): Int {
        val p = prefs(context)
        val token = p.getInt("${widgetId}_token", 0) + 1
        p.edit()
            .putBoolean("${widgetId}_playing", true)
            .putInt("${widgetId}_token", token)
            .putLong("${widgetId}_started", SystemClock.elapsedRealtime())
            .commit()
        return token
    }

    fun playToken(context: Context, widgetId: Int) = prefs(context).getInt("${widgetId}_token", 0)

    fun stopPlaying(context: Context, widgetId: Int) {
        prefs(context).edit().putBoolean("${widgetId}_playing", false).commit()
    }

    /**
     * Removes frames and settings for widget ids that no longer exist (e.g. the launcher dropped
     * a widget without telling us), plus any half-written temp folders.
     */
    fun prune(context: Context, liveIds: Set<Int>) {
        val p = prefs(context)
        val storedIds = p.all.keys.mapNotNull { it.substringBefore('_').toIntOrNull() }.toSet()
        (storedIds - liveIds).forEach { delete(context, it) }
        context.filesDir.listFiles()?.forEach { dir ->
            val id = dir.name.removePrefix("w").removeSuffix(".tmp").toIntOrNull()
            if (dir.name.endsWith(".tmp") || (dir.name.startsWith("w") && id != null && id !in liveIds)) {
                dir.deleteRecursively()
            }
        }
    }

    fun delete(context: Context, widgetId: Int) {
        val p = prefs(context)
        val editor = p.edit()
        p.all.keys.filter { it.startsWith("${widgetId}_") }.forEach(editor::remove)
        editor.commit()
        framesDir(context, widgetId).deleteRecursively()
    }
}
