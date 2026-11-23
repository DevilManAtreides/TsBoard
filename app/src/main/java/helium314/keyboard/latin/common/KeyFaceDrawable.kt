// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.common

import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings

/** Names and helpers for the key styles from Settings > Appearance. */
object KeyFace {
    /** keys look like the theme says, none of the drawing below is used */
    const val THEME = "theme"
    const val FLAT = "flat"
    /** domed, glossy keys like on old phones */
    const val JELLY = "jelly"
    /** see-through keys with a bright edge */
    const val GLASS = "glass"

    /** Slider value for fully round ends. Smaller values are a corner radius in dp. */
    const val PILL = 25

    @JvmStatic
    fun style(prefs: SharedPreferences): String {
        val style = prefs.getString(Settings.PREF_KEY_STYLE, Defaults.PREF_KEY_STYLE)
        return if (style == FLAT || style == JELLY || style == GLASS) style else THEME
    }

    /** Corner radius in pixels; a radius larger than the key is limited to a half circle when drawn. */
    @JvmStatic
    fun radiusPx(prefs: SharedPreferences, density: Float): Float {
        val setting = prefs.getInt(Settings.PREF_KEY_CORNER_RADIUS, Defaults.PREF_KEY_CORNER_RADIUS)
        return if (setting >= PILL) 500f * density else setting.coerceAtLeast(0) * density
    }
}

/**
 * The face of a key for the styles in [KeyFace]. The body is a plain shape that the theme colors tint,
 * like they tint the normal key drawables, so the colors from Settings keep working. Highlights and
 * shading are drawn on top and are not tinted.
 */
class KeyFaceDrawable(private val style: String, private val radius: Float, density: Float) : Drawable() {
    private val glass = style == KeyFace.GLASS
    private val jelly = style == KeyFace.JELLY

    private val body = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius
        // glass keys are see-through before the theme color is applied
        setColor(if (glass) 0x73FFFFFF else Color.WHITE)
    }
    /** darkens towards the bottom, or towards the top while pressed */
    private val shade = if (jelly) GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x00000000, 0x59000000)).apply {
        cornerRadius = radius
    } else null
    /** the glossy spot near the top of the key */
    private val gloss = if (jelly || glass) GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
        if (jelly) intArrayOf(0xB8FFFFFF.toInt(), 0x14FFFFFF) else intArrayOf(0x59FFFFFF, 0x05FFFFFF)).apply {
        cornerRadius = radius * 0.85f
    } else null
    private val edge = if (jelly || glass) GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius
        setColor(Color.TRANSPARENT)
        setStroke(maxOf(1, Math.round((if (glass) 1f else 0.6f) * density)), if (glass) 0x99FFFFFF.toInt() else 0x40FFFFFF)
    } else null

    private var pressed = false
    private var alphaValue = 255

    init {
        applyState()
    }

    override fun draw(canvas: Canvas) {
        body.draw(canvas)
        shade?.draw(canvas)
        gloss?.draw(canvas)
        edge?.draw(canvas)
    }

    override fun onBoundsChange(bounds: Rect) {
        body.bounds = bounds
        shade?.bounds = bounds
        edge?.bounds = bounds
        val inset = (bounds.width() * 0.07f).toInt()
        val top = bounds.top + (bounds.height() * 0.06f).toInt()
        gloss?.setBounds(bounds.left + inset, top, bounds.right - inset, top + (bounds.height() * 0.46f).toInt())
    }

    override fun isStateful() = true

    override fun onStateChange(state: IntArray): Boolean {
        pressed = state.contains(android.R.attr.state_pressed)
        body.setState(state) // lets the tint pick the pressed color
        applyState()
        return true
    }

    /** pressed: the dome flattens, the shine fades, glass becomes a little more solid */
    private fun applyState() {
        shade?.orientation = if (pressed) GradientDrawable.Orientation.BOTTOM_TOP else GradientDrawable.Orientation.TOP_BOTTOM
        body.alpha = if (glass && pressed) minOf(255, alphaValue * 3 / 2) else alphaValue
        shade?.alpha = alphaValue
        edge?.alpha = alphaValue
        gloss?.alpha = alphaValue * (if (pressed) 35 else 100) / 100
    }

    override fun setTintList(tint: ColorStateList?) {
        body.setTintList(tint)
        invalidateSelf()
    }

    override fun setTintMode(tintMode: PorterDuff.Mode?) {
        body.setTintMode(tintMode)
        invalidateSelf()
    }

    override fun setAlpha(alpha: Int) {
        alphaValue = alpha
        applyState()
        invalidateSelf()
    }

    override fun getAlpha() = alphaValue

    override fun setColorFilter(colorFilter: ColorFilter?) {
        body.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
