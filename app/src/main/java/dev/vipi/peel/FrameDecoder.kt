package dev.vipi.peel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Movie
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Turns a GIF / animated WebP into a folder of PNG frames sampled at a fixed interval.
 *
 * Widgets can only flip frames at one constant rate (AdapterViewFlipper), so the animation is
 * resampled on a uniform timeline; variable per-frame delays are preserved that way.
 */
object FrameDecoder {
    private const val MAX_SIDE = 480
    private const val MIN_INTERVAL_MS = 40
    private const val MAX_FRAMES = 120

    /** Whole file is held in memory while decoding, so cap it. */
    const val MAX_FILE_MB = 40
    private const val MAX_FILE_BYTES = MAX_FILE_MB * 1024 * 1024

    /** Movie and the WebP compositor keep full-size frames internally; refuse absurd canvases. */
    private const val MAX_SOURCE_PIXELS = 4096 * 4096

    /** [posterIndex] is the most visible frame, shown while idle (first frames are often blank). */
    class Result(val count: Int, val intervalMs: Int, val posterIndex: Int)

    /** The file is bigger than [MAX_FILE_MB] or its canvas is unreasonably large. */
    class TooLargeException : Exception()

    fun decode(context: Context, uri: Uri, outDir: File): Result {
        val bytes = context.contentResolver.openInputStream(uri)?.use(::readCapped)
            ?: error("Cannot open $uri")
        val tmp = File(outDir.parentFile, outDir.name + ".tmp")
        tmp.deleteRecursively()
        tmp.mkdirs()
        val result = when {
            isGif(bytes) -> decodeGif(bytes, tmp)
            isWebp(bytes) -> decodeWebp(bytes, tmp)
            else -> null
        } ?: decodeStatic(bytes, tmp)
        outDir.deleteRecursively()
        check(tmp.renameTo(outDir)) { "Cannot move frames into place" }
        return result
    }

    fun frameFile(dir: File, index: Int) = File(dir, "f%03d.png".format(index))

    private fun readCapped(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > MAX_FILE_BYTES) throw TooLargeException()
        }
        return out.toByteArray()
    }

    private fun checkSourceSize(width: Int, height: Int) {
        if (width.toLong() * height > MAX_SOURCE_PIXELS) throw TooLargeException()
    }

    /**
     * Output-sized drawing surface. Everything is drawn through [canvas], which is pre-scaled, so
     * a large source never needs a full-size bitmap of our own.
     */
    private class Surface(width: Int, height: Int) {
        val scale = min(1f, MAX_SIDE.toFloat() / max(width, height))
        val bitmap: Bitmap = Bitmap.createBitmap(
            max(1, (width * scale).roundToInt()),
            max(1, (height * scale).roundToInt()),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap).apply { scale(scale, scale) }
    }

    private class FrameSink(private val dir: File) {
        private val coverage = ArrayList<Int>()
        val count get() = coverage.size

        fun emit(frame: Bitmap) {
            frameFile(dir, count).outputStream().use { frame.compress(Bitmap.CompressFormat.PNG, 100, it) }
            coverage += visiblePixels(frame)
        }

        /**
         * The resting frame: the first one (usually the artist's intended pose) unless it's much
         * emptier than the fullest frame, as with animations that fade or slide in from nothing.
         */
        fun result(intervalMs: Int): Result {
            val best = coverage.maxOrNull() ?: 0
            val poster = coverage.indexOfFirst { it >= best * 0.9 }.coerceAtLeast(0)
            return Result(count, intervalMs, poster)
        }

        private fun visiblePixels(bitmap: Bitmap): Int {
            val row = IntArray(bitmap.width)
            var visible = 0
            for (y in 0 until bitmap.height step 4) {
                bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
                for (x in row.indices step 4) if (row[x] ushr 24 > 16) visible++
            }
            return visible
        }
    }

    private fun pickInterval(minDelay: Int, total: Int) = PlayingLayouts.intervalAtLeast(
        maxOf(MIN_INTERVAL_MS, minDelay, ceil(total.toDouble() / MAX_FRAMES).toInt()),
    )

    // Browsers treat tiny delays as 100ms; match that so "0 delay" GIFs don't race.
    private fun normalizeDelay(ms: Int) = if (ms <= 10) 100 else ms

    // ---- GIF -----------------------------------------------------------------------------

    private fun isGif(b: ByteArray) = b.size > 6 && String(b, 0, 3, Charsets.US_ASCII) == "GIF"

    @Suppress("DEPRECATION")
    private fun decodeGif(bytes: ByteArray, dir: File): Result? {
        val delays = gifDelays(bytes)
        if (delays.size <= 1) return null
        // Logical screen size lives at bytes 6..9; check it before Movie allocates anything.
        checkSourceSize(
            (bytes[6].toInt() and 0xff) or ((bytes[7].toInt() and 0xff) shl 8),
            (bytes[8].toInt() and 0xff) or ((bytes[9].toInt() and 0xff) shl 8),
        )
        val movie = Movie.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val total = movie.duration()
        if (total <= 0) return null
        val interval = pickInterval(delays.minOf(::normalizeDelay), total)

        val surface = Surface(movie.width(), movie.height())
        val sink = FrameSink(dir)
        var t = 0
        while (t < total) {
            surface.bitmap.eraseColor(0)
            movie.setTime(t + interval / 2) // mid-frame; frame boundaries are ambiguous
            movie.draw(surface.canvas, 0f, 0f)
            sink.emit(surface.bitmap)
            t += interval
        }
        surface.bitmap.recycle()
        return sink.result(interval)
    }

    /** Walks the GIF block structure and returns each image's delay in ms. */
    private fun gifDelays(b: ByteArray): List<Int> {
        val delays = ArrayList<Int>()
        if (b.size < 13) return delays
        fun u8(i: Int) = b[i].toInt() and 0xff
        var p = 13
        val screenFlags = u8(10)
        if (screenFlags and 0x80 != 0) p += 3 * (1 shl ((screenFlags and 7) + 1))
        var pendingDelay = 0
        fun skipSubBlocks(start: Int): Int {
            var q = start
            while (q < b.size) {
                val n = u8(q)
                q += 1 + n
                if (n == 0) break
            }
            return q
        }
        while (p < b.size) {
            when (u8(p)) {
                0x21 -> {
                    if (p + 1 >= b.size) break
                    val label = u8(p + 1)
                    p += 2
                    if (label == 0xF9 && p + 3 < b.size) {
                        pendingDelay = (u8(p + 2) or (u8(p + 3) shl 8)) * 10
                    }
                    p = skipSubBlocks(p)
                }
                0x2C -> {
                    if (p + 9 >= b.size) break
                    val flags = u8(p + 9)
                    p += 10
                    if (flags and 0x80 != 0) p += 3 * (1 shl ((flags and 7) + 1))
                    p = skipSubBlocks(p + 1) // +1 skips LZW minimum code size
                    delays += pendingDelay
                    pendingDelay = 0
                }
                else -> break // 0x3B trailer or junk
            }
        }
        return delays
    }

    // ---- Animated WebP -------------------------------------------------------------------

    private fun isWebp(b: ByteArray) = b.size > 12 &&
        String(b, 0, 4, Charsets.US_ASCII) == "RIFF" && String(b, 8, 4, Charsets.US_ASCII) == "WEBP"

    private class WebpFrame(
        val rect: Rect,
        val durationMs: Int,
        val blend: Boolean,
        val disposeToBackground: Boolean,
        val data: ByteArray, // a standalone still WebP that BitmapFactory can decode
    )

    private fun le24(b: ByteArray, i: Int) =
        (b[i].toInt() and 0xff) or ((b[i + 1].toInt() and 0xff) shl 8) or ((b[i + 2].toInt() and 0xff) shl 16)

    private fun le32(b: ByteArray, i: Int) = le24(b, i) or ((b[i + 3].toInt() and 0xff) shl 24)

    private fun fourcc(b: ByteArray, i: Int) = String(b, i, 4, Charsets.US_ASCII)

    private fun decodeWebp(bytes: ByteArray, dir: File): Result? {
        var canvasW = 0
        var canvasH = 0
        val frames = ArrayList<WebpFrame>()
        var p = 12
        while (p + 8 <= bytes.size) {
            val size = le32(bytes, p + 4)
            val body = p + 8
            if (size < 0 || body + size > bytes.size) break
            when (fourcc(bytes, p)) {
                "VP8X" -> {
                    canvasW = 1 + le24(bytes, body + 4)
                    canvasH = 1 + le24(bytes, body + 7)
                }
                "ANMF" -> parseAnmf(bytes, body, size)?.let(frames::add)
            }
            p = body + size + (size and 1)
        }
        if (frames.size <= 1 || canvasW <= 0 || canvasH <= 0) return null
        checkSourceSize(canvasW, canvasH)

        val total = frames.sumOf { normalizeDelay(it.durationMs) }
        val interval = pickInterval(frames.minOf { normalizeDelay(it.durationMs) }, total)

        val surface = Surface(canvasW, canvasH)
        val canvas = surface.canvas
        // Frames only need to be decoded at roughly output resolution.
        var sampleSize = 1
        while (sampleSize * 2 <= (1 / surface.scale).toInt()) sampleSize *= 2
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val clearPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
        val replacePaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC) }
        val sink = FrameSink(dir)

        var frameStart = 0
        var nextSample = 0
        var previous: WebpFrame? = null
        for (frame in frames) {
            previous?.takeIf { it.disposeToBackground }?.let { canvas.drawRect(it.rect, clearPaint) }
            BitmapFactory.decodeByteArray(frame.data, 0, frame.data.size, decodeOptions)?.let { bmp ->
                // Draw into the frame's rect in canvas coordinates, whatever the decoded sample size.
                canvas.drawBitmap(bmp, null, frame.rect, if (frame.blend) null else replacePaint)
                bmp.recycle()
            }
            val frameEnd = frameStart + normalizeDelay(frame.durationMs)
            while (nextSample < frameEnd) {
                sink.emit(surface.bitmap)
                nextSample += interval
            }
            frameStart = frameEnd
            previous = frame
        }
        surface.bitmap.recycle()
        return sink.result(interval)
    }

    private fun parseAnmf(b: ByteArray, start: Int, size: Int): WebpFrame? {
        if (size < 16) return null
        val x = le24(b, start) * 2
        val y = le24(b, start + 3) * 2
        val w = 1 + le24(b, start + 6)
        val h = 1 + le24(b, start + 9)
        val duration = le24(b, start + 12)
        val flags = b[start + 15].toInt()

        var alph: ByteArray? = null
        var image: ByteArray? = null
        var imageType = ""
        var q = start + 16
        val end = start + size
        while (q + 8 <= end) {
            val sz = le32(b, q + 4)
            val chunkEnd = minOf(end, q + 8 + sz + (sz and 1))
            when (val id = fourcc(b, q)) {
                "ALPH" -> alph = b.copyOfRange(q, chunkEnd)
                "VP8 ", "VP8L" -> {
                    image = b.copyOfRange(q, chunkEnd)
                    imageType = id
                }
            }
            q = chunkEnd
        }
        val img = image ?: return null

        // Lossy frames carry alpha in a separate ALPH chunk, which needs a VP8X header to be valid.
        val payload = if (alph != null && imageType == "VP8 ") {
            val vp8x = ByteArray(18)
            "VP8X".toByteArray(Charsets.US_ASCII).copyInto(vp8x)
            putLe(vp8x, 4, 10, 4)
            vp8x[8] = 0x10 // alpha flag
            putLe(vp8x, 12, w - 1, 3)
            putLe(vp8x, 15, h - 1, 3)
            vp8x + alph + img
        } else {
            img
        }
        val riff = ByteArray(12)
        "RIFF".toByteArray(Charsets.US_ASCII).copyInto(riff)
        putLe(riff, 4, 4 + payload.size, 4)
        "WEBP".toByteArray(Charsets.US_ASCII).copyInto(riff, 8)

        return WebpFrame(
            rect = Rect(x, y, x + w, y + h),
            durationMs = duration,
            blend = flags and 0x02 == 0,
            disposeToBackground = flags and 0x01 != 0,
            data = riff + payload,
        )
    }

    private fun putLe(b: ByteArray, at: Int, value: Int, bytes: Int) {
        for (i in 0 until bytes) b[at + i] = (value shr (8 * i)).toByte()
    }

    // ---- Still images --------------------------------------------------------------------

    private fun decodeStatic(bytes: ByteArray, dir: File): Result {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            // Decode straight to output size; never allocate a huge still.
            val scale = min(1f, MAX_SIDE.toFloat() / max(info.size.width, info.size.height))
            decoder.setTargetSize(
                max(1, (info.size.width * scale).roundToInt()),
                max(1, (info.size.height * scale).roundToInt()),
            )
        }
        val sink = FrameSink(dir)
        sink.emit(bitmap)
        bitmap.recycle()
        return sink.result(0)
    }
}
