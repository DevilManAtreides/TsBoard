// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Blur and dim for the keyboard background image. Done in plain code so it works on every Android
 * version the app supports. The processed bitmap is cached by [helium314.keyboard.latin.settings.Settings],
 * so this runs once per change of image or setting, not while typing.
 */
object BackgroundEffects {
    /** Longest side of the decoded image. Keeps memory and processing time small; a keyboard is not a wallpaper. */
    private const val MAX_SIZE = 1280

    /** The blur runs on an image of about this width and is scaled back up, which is much faster. */
    private const val BLUR_WORK_WIDTH = 360

    /** Blur radius in pixels of the small working image at 100 % blur. */
    private const val MAX_RADIUS = 24

    /** Decodes [path], scaled down if it is large, and applies [blurPercent] (0..100) and [dimPercent] (0..100). */
    @JvmStatic
    fun load(path: String, blurPercent: Int, dimPercent: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(path, options) ?: return null
        return process(decoded, blurPercent, dimPercent)
    }

    @JvmStatic
    fun process(source: Bitmap, blurPercent: Int, dimPercent: Int): Bitmap {
        val blur = blurPercent.coerceIn(0, 100)
        val dim = dimPercent.coerceIn(0, 100)
        if (blur == 0 && dim == 0) return source

        var result = if (blur > 0) blurred(source, blur) else source
        if (dim > 0) {
            if (!result.isMutable || result === source) result = result.copy(Bitmap.Config.ARGB_8888, true)
            Canvas(result).drawColor(Color.argb((dim * 255 / 100), 0, 0, 0))
        }
        return result
    }

    /** Largest power of two that brings the longest side to at most [MAX_SIZE]. */
    internal fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        var longest = max(width, height)
        while (longest / 2 >= MAX_SIZE) {
            longest /= 2
            sample *= 2
        }
        return sample
    }

    private fun blurred(source: Bitmap, percent: Int): Bitmap {
        val scale = min(1f, BLUR_WORK_WIDTH.toFloat() / source.width)
        val w = max(1, (source.width * scale).roundToInt())
        val h = max(1, (source.height * scale).roundToInt())
        val small = Bitmap.createScaledBitmap(source, w, h, true)
        val radius = max(1, (MAX_RADIUS * percent / 100f).roundToInt())

        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)
        // three box blurs approximate a gaussian blur
        repeat(3) {
            boxBlur(pixels, w, h, radius, horizontal = true)
            boxBlur(pixels, w, h, radius, horizontal = false)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        if (small !== source) small.recycle()
        // back to the size of the original so the background can be drawn without extra scaling
        return Bitmap.createScaledBitmap(out, source.width, source.height, true)
            .also { if (it !== out) out.recycle() }
    }

    /** One box blur pass over the whole image; edges repeat the border pixel. */
    internal fun boxBlur(pixels: IntArray, w: Int, h: Int, radius: Int, horizontal: Boolean) {
        val length = if (horizontal) w else h
        val lines = if (horizontal) h else w
        val step = if (horizontal) 1 else w
        val window = radius * 2 + 1
        val line = IntArray(length)
        for (l in 0 until lines) {
            val start = if (horizontal) l * w else l
            for (i in 0 until length) line[i] = pixels[start + i * step]

            var a = 0; var r = 0; var g = 0; var b = 0
            for (i in -radius..radius) {
                val p = line[i.coerceIn(0, length - 1)]
                a += p ushr 24; r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF
            }
            for (i in 0 until length) {
                // rounded, not truncated, so repeated passes do not slowly darken the image
                val half = window / 2
                pixels[start + i * step] = (((a + half) / window) shl 24) or (((r + half) / window) shl 16) or
                    (((g + half) / window) shl 8) or ((b + half) / window)
                val out = line[(i - radius).coerceIn(0, length - 1)]
                val inn = line[(i + radius + 1).coerceIn(0, length - 1)]
                a += (inn ushr 24) - (out ushr 24)
                r += ((inn shr 16) and 0xFF) - ((out shr 16) and 0xFF)
                g += ((inn shr 8) and 0xFF) - ((out shr 8) and 0xFF)
                b += (inn and 0xFF) - (out and 0xFF)
            }
        }
    }
}
