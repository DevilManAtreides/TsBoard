// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.common

import android.content.Context
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs

/** Settings for the key press animations, see Settings > Appearance. */
object KeyPressAnimation {
    const val NONE = "none"
    /** the key shrinks a little while pressed and springs back */
    const val SQUISH = "squish"
    /** a ring grows out from the key and fades */
    const val PULSE = "pulse"
    /** a soft glow spreads out around the key and fades */
    const val GLOW = "glow"

    class Config(@JvmField val style: String, @JvmField val color: Int, @JvmField val sizePx: Float, @JvmField val durationMs: Long)

    @JvmStatic
    fun style(context: Context): String {
        val style = context.prefs().getString(Settings.PREF_KEY_PRESS_ANIM, Defaults.PREF_KEY_PRESS_ANIM)
        return if (style == SQUISH || style == PULSE || style == GLOW) style!! else NONE
    }

    /** The current settings, or null if there is no animation. */
    @JvmStatic
    fun load(context: Context): Config? {
        val style = style(context)
        if (style == NONE) return null
        val prefs = context.prefs()
        val density = context.resources.displayMetrics.density
        return Config(
            style,
            prefs.getInt(Settings.PREF_KEY_ANIM_COLOR, Defaults.PREF_KEY_ANIM_COLOR),
            prefs.getInt(Settings.PREF_KEY_ANIM_SIZE, Defaults.PREF_KEY_ANIM_SIZE).coerceAtLeast(1) * density,
            prefs.getInt(Settings.PREF_KEY_ANIM_SPEED, Defaults.PREF_KEY_ANIM_SPEED).coerceIn(80, 1000).toLong()
        )
    }

    /** 0..1 in, 0..1 out; starts fast and slows down */
    @JvmStatic
    fun easeOut(t: Float): Float {
        val x = 1f - t.coerceIn(0f, 1f)
        return 1f - x * x
    }
}
