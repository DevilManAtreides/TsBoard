// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.common

import android.content.SharedPreferences
import androidx.core.graphics.ColorUtils
import helium314.keyboard.latin.R

/** A list of colors chosen by the user, stored as one string like "FF3DF2FF,FFFF4FD8". */
object KeyPalette {
    const val MAX_COLORS = 12

    val DEFAULT = intArrayOf(0xFF3DF2FF.toInt(), 0xFFFF4FD8.toInt())

    class Preset(val nameRes: Int, val colors: IntArray)

    val PRESETS = listOf(
        Preset(R.string.palette_rainbow, intArrayOf(0xFFFF3B3B.toInt(), 0xFFFFD23B.toInt(), 0xFF3BFF6B.toInt(), 0xFF3BC8FF.toInt(), 0xFF7B3BFF.toInt(), 0xFFFF3BD8.toInt())),
        Preset(R.string.palette_sunset, intArrayOf(0xFFFFD36B.toInt(), 0xFFFF7A3D.toInt(), 0xFFE8336D.toInt(), 0xFF6B2FA8.toInt())),
        Preset(R.string.palette_ocean, intArrayOf(0xFF3DF2FF.toInt(), 0xFF2F8CFF.toInt(), 0xFF2F3DD6.toInt(), 0xFF12B5A6.toInt())),
        Preset(R.string.palette_cyberpunk, intArrayOf(0xFFFF2BD6.toInt(), 0xFF7B2BFF.toInt(), 0xFF2BE6FF.toInt())),
        Preset(R.string.palette_fire, intArrayOf(0xFFFFF04D.toInt(), 0xFFFF9A1F.toInt(), 0xFFFF3B1F.toInt(), 0xFF9C1212.toInt())),
        Preset(R.string.palette_aurora, intArrayOf(0xFF3BFF9A.toInt(), 0xFF2FD6C8.toInt(), 0xFF4F7BFF.toInt(), 0xFFB04FFF.toInt())),
    )

    fun encode(colors: List<Int>): String = colors.joinToString(",") { String.format("%08X", it) }

    /** The stored colors, or null if nothing valid is stored. */
    fun parse(text: String?): IntArray? {
        if (text.isNullOrBlank()) return null
        val result = ArrayList<Int>()
        for (part in text.split(',')) {
            val value = part.trim().toLongOrNull(16) ?: continue
            result.add(value.toInt())
            if (result.size >= MAX_COLORS) break
        }
        return if (result.isEmpty()) null else result.toIntArray()
    }

    @JvmStatic
    fun load(prefs: SharedPreferences, key: String): IntArray = parse(prefs.getString(key, "")) ?: DEFAULT

    fun isCustom(prefs: SharedPreferences, key: String): Boolean = parse(prefs.getString(key, "")) != null

    /**
     * Color at [pos] (0..1) along the palette, blending between neighbouring colors.
     * With [wrap] the last color blends back into the first and [pos] may be any number.
     */
    @JvmStatic
    fun at(colors: IntArray, pos: Float, wrap: Boolean): Int {
        val n = colors.size
        if (n == 0) return 0
        if (n == 1) return colors[0]
        if (wrap) {
            val p = (((pos % 1f) + 1f) % 1f) * n
            val i = p.toInt().coerceIn(0, n - 1)
            return ColorUtils.blendARGB(colors[i], colors[(i + 1) % n], p - i)
        }
        val p = pos.coerceIn(0f, 1f) * (n - 1)
        val i = p.toInt().coerceIn(0, n - 2)
        return ColorUtils.blendARGB(colors[i], colors[i + 1], p - i)
    }
}
