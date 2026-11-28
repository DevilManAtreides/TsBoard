// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.common

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import kotlin.math.hypot

/** Names and helpers for the key styles from Settings > Appearance. */
object KeyFace {
    /** keys look like the theme says, none of the drawing below is used */
    const val THEME = "theme"
    const val FLAT = "flat"
    /** domed, glossy keys like on old phones */
    const val JELLY = "jelly"
    /** see-through keys with a bright edge */
    const val GLASS = "glass"
    /** a raised 3D cap that sinks when pressed */
    const val KEYCAP = "keycap"
    /** dark keys with a glowing edge */
    const val NEON = "neon"
    /** only an outline, see-through inside */
    const val GHOST = "ghost"
    /** a sweep from one color to another on every key */
    const val GRADIENT = "gradient"

    /** Slider value for fully round ends. Smaller values are a corner radius in dp. */
    const val PILL = 25

    @JvmStatic
    fun style(prefs: SharedPreferences): String {
        val style = prefs.getString(Settings.PREF_KEY_STYLE, Defaults.PREF_KEY_STYLE)
        return if (style in listOf(FLAT, JELLY, GLASS, KEYCAP, NEON, GHOST, GRADIENT)) style!! else THEME
    }

    const val EDGE_OUTLINE = "outline"
    const val EDGE_GLOW = "glow"
    const val EDGE_NONE = "none"

    @JvmStatic
    fun edge(prefs: SharedPreferences): String =
        when (prefs.getString(Settings.PREF_KEY_EDGE, Defaults.PREF_KEY_EDGE)) {
            EDGE_GLOW -> EDGE_GLOW
            EDGE_NONE -> EDGE_NONE
            else -> EDGE_OUTLINE
        }

    /** Corner radius of the keys in pixels if they use one of our own styles, otherwise 0. */
    @JvmStatic
    fun hintRadiusPx(context: Context, hasKeyBorders: Boolean): Float {
        if (!hasKeyBorders) return 0f
        val prefs = context.prefs()
        if (style(prefs) == THEME) return 0f
        return radiusPx(prefs, context.resources.displayMetrics.density)
    }

    /**
     * Moves the center [xy] of a hint at the top right corner of a key inwards, along the curve, so the
     * hint (half width [halfW], half height [halfH]) stays inside a rounded corner of [radius].
     */
    @JvmStatic
    fun fitHint(xy: FloatArray, halfW: Float, halfH: Float, keyWidth: Int, keyHeight: Int, radius: Float) {
        val r = minOf(radius, minOf(keyWidth, keyHeight) / 2f)
        val cx = keyWidth - r
        val cy = r
        val dx = xy[0] - cx
        val dy = xy[1] - cy
        if (dx <= 0f || dy >= 0f) return // not in the corner part
        val allowed = r - hypot(halfW, halfH) - 1f
        if (allowed <= 0f) return // corner too small to matter
        val distance = hypot(dx, dy)
        if (distance <= allowed) return
        val scale = allowed / distance
        xy[0] = cx + dx * scale
        xy[1] = cy + dy * scale
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
 * like they tint the normal key drawables, so the colors from Settings keep working (except for the
 * gradient style, which has colors of its own). Highlights and shading are drawn on top and are not tinted.
 */
class KeyFaceDrawable(
    private val style: String,
    private val radius: Float,
    density: Float,
    private val colorA: Int = 0xFF3DF2FF.toInt(),
    /** the colors of the gradient style, at least two */
    private val gradientColors: IntArray = KeyPalette.DEFAULT,
) : Drawable() {
    private val glass = style == KeyFace.GLASS
    private val jelly = style == KeyFace.JELLY
    private val keycap = style == KeyFace.KEYCAP
    private val neon = style == KeyFace.NEON
    private val ghost = style == KeyFace.GHOST
    private val gradient = style == KeyFace.GRADIENT

    /** how far a keycap stands out below its top */
    private val sideHeight = if (keycap) 3.5f * density else 0f

    private val body = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius
        when {
            gradient -> {
                setOrientation(GradientDrawable.Orientation.TL_BR)
                setColors(if (gradientColors.size >= 2) gradientColors else intArrayOf(gradientColors[0], gradientColors[0]))
            }
            glass -> setColor(0x73FFFFFF) // see-through before the theme color is applied
            neon -> setColor(0xD9FFFFFF.toInt())
            ghost -> setColor(0x59FFFFFF)
            else -> setColor(Color.WHITE)
        }
    }
    /** the side of a keycap */
    private val side = if (keycap) GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius
        setColor(0xB3000000.toInt())
    } else null
    /** darkens towards the bottom, or towards the top while pressed; for the gradient only while pressed */
    private val shade = if (jelly) GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x00000000, 0x59000000)).apply {
        cornerRadius = radius
    } else if (gradient) GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius
        setColor(0x40000000)
    } else null
    /** the glossy spot near the top of the key */
    private val gloss = if (jelly || glass) GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
        if (jelly) intArrayOf(0xB8FFFFFF.toInt(), 0x14FFFFFF) else intArrayOf(0x59FFFFFF, 0x05FFFFFF)).apply {
        cornerRadius = radius * 0.85f
    } else null
    /** the dish on top of a keycap */
    private val dish = if (keycap) GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius * 0.7f
        setColor(Color.TRANSPARENT)
        setStroke(maxOf(1, Math.round(density)), 0x59000000)
    } else null
    private val neonGlow = if (neon) KeyGlowDrawable(radius, (colorA and 0x00FFFFFF) or 0x99000000.toInt(), 5f * density) else null
    private val edge = if (jelly || glass || neon || ghost || keycap) GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius
        setColor(Color.TRANSPARENT)
        val width = when { glass || neon || ghost -> 1.5f; keycap -> 1f; else -> 0.6f }
        val color = when { neon -> colorA or 0xFF000000.toInt(); ghost -> 0xB3FFFFFF.toInt(); glass -> 0x99FFFFFF.toInt(); keycap -> 0x40FFFFFF; else -> 0x40FFFFFF }
        setStroke(maxOf(1, Math.round(width * density)), color)
    } else null

    private var pressed = false
    private var alphaValue = 255

    init {
        applyState()
    }

    override fun draw(canvas: Canvas) {
        side?.draw(canvas)
        body.draw(canvas)
        if (!(gradient && !pressed)) shade?.draw(canvas)
        neonGlow?.draw(canvas)
        dish?.draw(canvas)
        gloss?.draw(canvas)
        edge?.draw(canvas)
    }

    override fun onBoundsChange(bounds: Rect) = layout()

    private fun layout() {
        val b = bounds
        // a keycap stands on its side, and the top moves down a little when pressed
        val top = if (keycap && pressed) (sideHeight * 0.7f).toInt() else 0
        val bottomGap = if (keycap) (if (pressed) (sideHeight * 0.3f).toInt() else sideHeight.toInt()) else 0
        val face = Rect(b.left, b.top + top, b.right, b.bottom - bottomGap)
        body.bounds = face
        shade?.bounds = face
        neonGlow?.bounds = face
        edge?.bounds = face
        side?.setBounds(b.left, b.top + sideHeight.toInt(), b.right, b.bottom)
        dish?.setBounds(face.left + (face.width() * 0.07f).toInt(), face.top + (face.height() * 0.1f).toInt(),
            face.right - (face.width() * 0.07f).toInt(), face.bottom - (face.height() * 0.14f).toInt())
        val inset = (face.width() * 0.07f).toInt()
        val glossTop = face.top + (face.height() * 0.06f).toInt()
        gloss?.setBounds(face.left + inset, glossTop, face.right - inset, glossTop + (face.height() * 0.46f).toInt())
    }

    override fun isStateful() = true

    override fun onStateChange(state: IntArray): Boolean {
        pressed = state.contains(android.R.attr.state_pressed)
        if (!gradient) body.setState(state) // lets the tint pick the pressed color
        applyState()
        layout()
        return true
    }

    /** pressed: the dome flattens, the shine fades, see-through keys become a little more solid */
    private fun applyState() {
        shade?.orientation = if (pressed) GradientDrawable.Orientation.BOTTOM_TOP else GradientDrawable.Orientation.TOP_BOTTOM
        body.alpha = when {
            glass && pressed -> minOf(255, alphaValue * 3 / 2)
            ghost -> if (pressed) alphaValue else alphaValue / 4
            else -> alphaValue
        }
        shade?.alpha = alphaValue
        side?.alpha = alphaValue
        edge?.alpha = alphaValue
        dish?.alpha = alphaValue
        neonGlow?.alpha = alphaValue
        gloss?.alpha = alphaValue * (if (pressed) 35 else 100) / 100
    }

    override fun setTintList(tint: ColorStateList?) {
        if (!gradient) body.setTintList(tint)
        invalidateSelf()
    }

    override fun setTintMode(tintMode: PorterDuff.Mode?) {
        if (!gradient) body.setTintMode(tintMode)
        invalidateSelf()
    }

    override fun setAlpha(alpha: Int) {
        alphaValue = alpha
        applyState()
        invalidateSelf()
    }

    override fun getAlpha() = alphaValue

    override fun setColorFilter(colorFilter: ColorFilter?) {
        if (!gradient) body.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/**
 * A soft glow along the inside of the key edge: several strokes of growing width, each faint, add up to
 * a brightness that is highest at the edge and fades towards the middle of the key.
 */
class KeyGlowDrawable(private val radius: Float, private val color: Int, private val size: Float) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val clip = Path()
    private val rect = RectF()
    private var alphaValue = 255

    override fun onBoundsChange(bounds: Rect) {
        rect.set(bounds)
        val r = minOf(radius, minOf(rect.width(), rect.height()) / 2f)
        clip.reset()
        clip.addRoundRect(rect, r, r, Path.Direction.CW)
    }

    override fun draw(canvas: Canvas) {
        if (size <= 0f || rect.isEmpty) return
        val r = minOf(radius, minOf(rect.width(), rect.height()) / 2f)
        val baseAlpha = Color.alpha(color) * alphaValue / 255
        paint.color = color
        canvas.save()
        canvas.clipPath(clip)
        for (i in 1..STEPS) {
            // the stroke is centered on the edge, so only its inner half is visible: that half is [size * i / STEPS] wide
            paint.strokeWidth = 2f * size * i / STEPS
            paint.alpha = maxOf(1, baseAlpha * 2 / (STEPS + 1))
            canvas.drawRoundRect(rect, r, r, paint)
        }
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) {
        alphaValue = alpha
        invalidateSelf()
    }

    override fun getAlpha() = alphaValue
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    private companion object {
        const val STEPS = 6
    }
}
