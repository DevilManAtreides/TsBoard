// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.common

import android.content.Context
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import kotlin.math.PI
import kotlin.math.sin

/** The moving "RGB" light around the keys, see Settings > Appearance. */
object KeyLighting {
    const val OFF = "off"
    /** a rainbow that moves across the keyboard */
    const val WAVE = "wave"
    /** the two chosen colors slowly sliding across the keyboard */
    const val FLOW = "flow"
    /** all keys fade in and out in the first color */
    const val BREATHE = "breathe"
    /** all keys change through the rainbow together */
    const val CYCLE = "cycle"
    /** a key lights up when pressed and fades */
    const val REACTIVE = "reactive"
    /** a ring of light spreads over the keys from the one that was pressed */
    const val RIPPLE = "ripple"

    class Config(
        @JvmField val style: String,
        @JvmField val colorA: Int,
        @JvmField val colorB: Int,
        /** 1..10 */
        @JvmField val speed: Float,
        @JvmField val widthPx: Float,
        @JvmField val glowPx: Float,
    )

    @JvmStatic
    fun style(context: Context): String {
        val style = context.prefs().getString(Settings.PREF_KEY_LIGHT_PATTERN, Defaults.PREF_KEY_LIGHT_PATTERN)
        return if (style in listOf(WAVE, FLOW, BREATHE, CYCLE, REACTIVE, RIPPLE)) style!! else OFF
    }

    /** The current settings, or null if the light is off. */
    @JvmStatic
    fun load(context: Context): Config? {
        val style = style(context)
        if (style == OFF) return null
        val prefs = context.prefs()
        val density = context.resources.displayMetrics.density
        return Config(
            style,
            prefs.getInt(Settings.PREF_KEY_LIGHT_COLOR_A, Defaults.PREF_KEY_LIGHT_COLOR_A),
            prefs.getInt(Settings.PREF_KEY_LIGHT_COLOR_B, Defaults.PREF_KEY_LIGHT_COLOR_B),
            prefs.getInt(Settings.PREF_KEY_LIGHT_SPEED, Defaults.PREF_KEY_LIGHT_SPEED).coerceIn(1, 10).toFloat(),
            prefs.getInt(Settings.PREF_KEY_LIGHT_WIDTH, Defaults.PREF_KEY_LIGHT_WIDTH).coerceAtLeast(1) * 0.5f * density,
            prefs.getInt(Settings.PREF_KEY_LIGHT_GLOW, Defaults.PREF_KEY_LIGHT_GLOW).coerceAtLeast(0) * density,
        )
    }

    /** True for the patterns that keep moving without any touch. */
    @JvmStatic
    fun isContinuous(style: String) = style == WAVE || style == FLOW || style == BREATHE || style == CYCLE

    /**
     * Color of a key for the always-moving patterns. [x] is how far across the keyboard the key is (0..1),
     * [t] the time in seconds.
     */
    @JvmStatic
    fun colorAt(config: Config, t: Float, x: Float): Int = when (config.style) {
        WAVE -> rainbow(x * 360f + t * config.speed * 40f)
        CYCLE -> rainbow(t * config.speed * 30f)
        FLOW -> {
            val mix = 0.5f + 0.5f * sin((2.0 * PI * (x - t * config.speed * 0.05f)).toFloat())
            ColorUtils.blendARGB(config.colorA, config.colorB, mix)
        }
        BREATHE -> withAlpha(config.colorA, 0.2f + 0.8f * (0.5f + 0.5f * sin((2.0 * PI * t * config.speed * 0.1f).toFloat())))
        else -> config.colorA
    }

    /** Color for a key that was just pressed, from the first color to the second across the keyboard. */
    @JvmStatic
    fun colorForPress(config: Config, x: Float): Int = ColorUtils.blendARGB(config.colorA, config.colorB, x.coerceIn(0f, 1f))

    /** [color] with its opacity multiplied by [factor] (0..1). */
    @JvmStatic
    fun withAlpha(color: Int, factor: Float): Int =
        ColorUtils.setAlphaComponent(color, (Color.alpha(color) * factor.coerceIn(0f, 1f)).toInt())

    private fun rainbow(hue: Float): Int {
        val h = ((hue % 360f) + 360f) % 360f
        return Color.HSVToColor(floatArrayOf(h, 0.85f, 1f))
    }
}
