// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.KeyFace
import helium314.keyboard.latin.common.KeyLighting
import helium314.keyboard.latin.common.KeyPressAnimation
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.getStringResourceOrName
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.preferences.ListPreference
import helium314.keyboard.settings.SettingsWithoutKey
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.KeyboardLivePreview
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.preferences.SliderPreference
import helium314.keyboard.settings.preferences.SwitchPreference
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.settings.dialogs.ColorPickerDialog
import helium314.keyboard.settings.dialogs.ListPickerDialog
import androidx.compose.material3.Text
import helium314.keyboard.settings.dialogs.PaletteEditorDialog
import helium314.keyboard.settings.dialogs.PaletteStrip
import helium314.keyboard.latin.common.KeyPalette
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import helium314.keyboard.settings.dialogs.ColorThemePickerDialog
import helium314.keyboard.settings.dialogs.CustomizeIconsDialog
import helium314.keyboard.settings.initPreview
import helium314.keyboard.settings.preferences.BackgroundImagePref
import helium314.keyboard.settings.preferences.CustomFontPreference
import helium314.keyboard.settings.preferences.KeyboardScalePreference
import helium314.keyboard.settings.preferences.TextInputPreference
import helium314.keyboard.latin.utils.previewDark
import androidx.core.content.edit
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.FoldableUtils
import helium314.keyboard.settings.dialogs.ThreeButtonAlertDialog

@Composable
fun AppearanceScreen(
    onClickBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    val b = (LocalContext.current.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    val dayNightMode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && prefs.getBoolean(Settings.PREF_THEME_DAY_NIGHT, Defaults.PREF_THEME_DAY_NIGHT)
    val borders = prefs.getBoolean(Settings.PREF_THEME_KEY_BORDERS, Defaults.PREF_THEME_KEY_BORDERS)
    val items = listOf(
        R.string.appearance_cat_style,
        Settings.PREF_THEME_STYLE,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            Settings.PREF_THEME_DAY_NIGHT else null,
        Settings.PREF_THEME_COLORS,
        if (dayNightMode) Settings.PREF_THEME_COLORS_NIGHT else null,
        Settings.PREF_ICON_STYLE,
        Settings.PREF_ACCENT_SHIFTED_ICON,
        Settings.PREF_CUSTOM_ICON_NAMES,
        R.string.appearance_cat_keys,
        Settings.PREF_THEME_KEY_BORDERS,
        if (borders && prefs.getString(Settings.PREF_KEY_STYLE, Defaults.PREF_KEY_STYLE) != KeyFace.THEME) Settings.PREF_KEY_CORNER_RADIUS else null,
        if (borders && KeyFace.style(prefs) == KeyFace.NEON) Settings.PREF_KEY_STYLE_COLOR_A else null,
        if (borders && KeyFace.style(prefs) == KeyFace.GRADIENT) Settings.PREF_KEY_GRADIENT_PALETTE else null,
        if (borders) Settings.PREF_KEY_EDGE else null,
        if (borders && KeyFace.edge(prefs) == KeyFace.EDGE_OUTLINE) Settings.PREF_KEY_OUTLINE_WIDTH else null,
        if (borders && KeyFace.edge(prefs) == KeyFace.EDGE_OUTLINE) Settings.PREF_KEY_OUTLINE_COLOR else null,
        if (borders && KeyFace.edge(prefs) == KeyFace.EDGE_GLOW) Settings.PREF_KEY_GLOW_SIZE else null,
        if (borders && KeyFace.edge(prefs) == KeyFace.EDGE_GLOW) Settings.PREF_KEY_GLOW_COLOR else null,
        if (borders) Settings.PREF_KEY_GAP_SCALE_PREFIX else null,
        R.string.appearance_cat_light,
        Settings.PREF_KEY_LIGHT_PATTERN,
        if (KeyLighting.style(ctx) != KeyLighting.OFF) Settings.PREF_KEY_LIGHT_SPEED else null,
        if (KeyLighting.style(ctx) != KeyLighting.OFF) Settings.PREF_KEY_LIGHT_PALETTE else null,
        if (KeyLighting.style(ctx).let { it == KeyLighting.REACTIVE || it == KeyLighting.RIPPLE }) Settings.PREF_KEY_LIGHT_RANDOM else null,
        if (KeyLighting.style(ctx) != KeyLighting.OFF) Settings.PREF_KEY_LIGHT_WIDTH else null,
        if (KeyLighting.style(ctx) != KeyLighting.OFF) Settings.PREF_KEY_LIGHT_GLOW else null,
        R.string.appearance_cat_effects,
        Settings.PREF_KEY_PRESS_ANIM,
        if (KeyPressAnimation.style(ctx) == KeyPressAnimation.PULSE || KeyPressAnimation.style(ctx) == KeyPressAnimation.GLOW)
            Settings.PREF_KEY_ANIM_COLOR else null,
        if (KeyPressAnimation.style(ctx) == KeyPressAnimation.PULSE || KeyPressAnimation.style(ctx) == KeyPressAnimation.GLOW)
            Settings.PREF_KEY_ANIM_SIZE else null,
        if (KeyPressAnimation.style(ctx) != KeyPressAnimation.NONE) Settings.PREF_KEY_ANIM_SPEED else null,
        R.string.appearance_cat_background,
        SettingsWithoutKey.BACKGROUND_IMAGE,
        SettingsWithoutKey.BACKGROUND_IMAGE_LANDSCAPE,
        Settings.PREF_BACKGROUND_BLUR,
        Settings.PREF_BACKGROUND_DIM,
        Settings.PREF_BACKGROUND_KEY_OPACITY,
        Settings.PREF_NAVBAR_COLOR,
        Settings.PREF_NAVBAR_BLACK,
        R.string.appearance_cat_layout,
        Settings.PREF_ENABLE_SPLIT_KEYBOARD,
        if (prefs.getBoolean(Settings.PREF_ENABLE_SPLIT_KEYBOARD_LANDSCAPE, Defaults.PREF_ENABLE_SPLIT_KEYBOARD)
            || prefs.getBoolean(Settings.PREF_ENABLE_SPLIT_KEYBOARD, Defaults.PREF_ENABLE_SPLIT_KEYBOARD)
            || prefs.getBoolean(Settings.PREF_ENABLE_SPLIT_KEYBOARD_FOLDED, Defaults.PREF_ENABLE_SPLIT_KEYBOARD)
            || prefs.getBoolean(Settings.PREF_ENABLE_SPLIT_KEYBOARD_FOLDED_LANDSCAPE, Defaults.PREF_ENABLE_SPLIT_KEYBOARD)
            )
            Settings.PREF_SPLIT_SPACER_SCALE_PREFIX else null,
        Settings.PREF_KEYBOARD_HEIGHT_SCALE_PREFIX,
        Settings.PREF_BOTTOM_ROW_SCALE_PREFIX,
        Settings.PREF_BOTTOM_PADDING_SCALE_PREFIX,
        Settings.PREF_SIDE_PADDING_SCALE_PREFIX,
        R.string.appearance_cat_text,
        Settings.PREF_SPACE_BAR_TEXT,
        SettingsWithoutKey.CUSTOM_FONT,
        Settings.PREF_FONT_SCALE,
        if (prefs.getBoolean(Settings.PREF_SHOW_HINTS, Defaults.PREF_SHOW_HINTS)) Settings.PREF_HINT_FONT_SCALE else null,
        SettingsWithoutKey.CUSTOM_EMOJI_FONT,
        Settings.PREF_EMOJI_FONT_SCALE,
        if (prefs.getFloat(Settings.PREF_EMOJI_FONT_SCALE, Defaults.PREF_EMOJI_FONT_SCALE) != 1f)
            Settings.PREF_EMOJI_KEY_FIT else null,
        if (prefs.getInt(Settings.PREF_EMOJI_MAX_SDK, 0) >= 24)
            Settings.PREF_EMOJI_SKIN_TONE else null,
    )
    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.settings_screen_appearance),
        settings = items,
        stickyContent = { KeyboardLivePreview() }
    )
}

fun createAppearanceSettings(context: Context) = listOf(
    Setting(context, Settings.PREF_THEME_STYLE, R.string.keyboard_style) { setting ->
        val ctx = LocalContext.current
        val prefs = ctx.prefs()
        val b = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
        if ((b?.value ?: 0) < 0)
            Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
        // one list for the built-in looks (prefix "t:") and the key styles (prefix "k:")
        val items = KeyboardTheme.STYLES.map { it.getStringResourceOrName("style_name_", ctx) to "t:$it" } + listOf(
            ctx.getString(R.string.key_style_flat) to "k:${KeyFace.FLAT}",
            ctx.getString(R.string.key_style_jelly) to "k:${KeyFace.JELLY}",
            ctx.getString(R.string.key_style_glass) to "k:${KeyFace.GLASS}",
            ctx.getString(R.string.key_style_keycap) to "k:${KeyFace.KEYCAP}",
            ctx.getString(R.string.key_style_neon) to "k:${KeyFace.NEON}",
            ctx.getString(R.string.key_style_ghost) to "k:${KeyFace.GHOST}",
            ctx.getString(R.string.key_style_gradient) to "k:${KeyFace.GRADIENT}",
        )
        val keyStyle = KeyFace.style(prefs)
        val current = if (keyStyle != KeyFace.THEME) "k:$keyStyle"
            else "t:" + prefs.getString(Settings.PREF_THEME_STYLE, Defaults.PREF_THEME_STYLE)
        val selected = items.firstOrNull { it.second == current }
        var showDialog by rememberSaveable { mutableStateOf(false) }
        Preference(name = setting.title, description = selected?.first, onClick = { showDialog = true })
        if (showDialog)
            ListPickerDialog(
                onDismissRequest = { showDialog = false },
                items = items,
                onItemSelected = { item ->
                    if (item.second == current) return@ListPickerDialog
                    if (item.second.startsWith("k:")) {
                        prefs.edit { putString(Settings.PREF_KEY_STYLE, item.second.substring(2)) }
                    } else {
                        val style = item.second.substring(2)
                        prefs.edit {
                            putString(Settings.PREF_THEME_STYLE, style)
                            remove(Settings.PREF_KEY_STYLE)
                        }
                        if (style != KeyboardTheme.STYLE_HOLO) {
                            if (prefs.getString(Settings.PREF_THEME_COLORS, Defaults.PREF_THEME_COLORS) == KeyboardTheme.THEME_HOLO_WHITE)
                                prefs.edit { remove(Settings.PREF_THEME_COLORS) }
                            if (prefs.getString(Settings.PREF_THEME_COLORS_NIGHT, Defaults.PREF_THEME_COLORS_NIGHT) == KeyboardTheme.THEME_HOLO_WHITE)
                                prefs.edit { remove(Settings.PREF_THEME_COLORS_NIGHT) }
                        }
                    }
                    KeyboardIconsSet.needsReload = true // only relevant for Settings.PREF_CUSTOM_ICON_NAMES
                    KeyboardSwitcher.getInstance().setThemeNeedsReload()
                },
                title = { Text(setting.title) },
                selectedItem = selected,
                getItemName = { it.first },
                description = ctx.getString(R.string.keyboard_style_summary),
            )
    },
    Setting(context, Settings.PREF_ICON_STYLE, R.string.icon_style) { setting ->
        val ctx = LocalContext.current
        val b = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
        if ((b?.value ?: 0) < 0)
            Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
        val items = KeyboardTheme.STYLES.map { it.getStringResourceOrName("style_name_", ctx) to it }
        ListPreference(
            setting,
            items,
            Defaults.PREF_ICON_STYLE(ctx.prefs()),
            { ctx.prefs().edit { remove(Settings.PREF_ICON_STYLE) } }
        ) {
            KeyboardIconsSet.needsReload = true // only relevant for Settings.PREF_CUSTOM_ICON_NAMES
            KeyboardSwitcher.getInstance().setThemeNeedsReload()
        }
    },
    Setting(context, Settings.PREF_ACCENT_SHIFTED_ICON, R.string.accent_shifted_icon) {
        SwitchPreference(it, Defaults.PREF_ACCENT_SHIFTED_ICON) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_CUSTOM_ICON_NAMES, R.string.customize_icons) { setting ->
        var showDialog by rememberSaveable { mutableStateOf(false) }
        Preference(
            name = setting.title,
            onClick = { showDialog = true }
        )
        if (showDialog) {
            KeyboardIconsSet.instance.loadIcons(LocalContext.current)
            CustomizeIconsDialog(setting.key) { showDialog = false }
        }
    },
    Setting(context, Settings.PREF_THEME_COLORS, R.string.theme_colors) { setting ->
        val ctx = LocalContext.current
        val prefs = ctx.prefs()
        val b = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
        if ((b?.value ?: 0) < 0)
            Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
        var showDialog by rememberSaveable { mutableStateOf(false) }
        Preference(
            name = setting.title,
            description = prefs.getString(setting.key, Defaults.PREF_THEME_COLORS)!!.getStringResourceOrName("theme_name_", ctx),
            onClick = { showDialog = true }
        )
        if (showDialog)
            ColorThemePickerDialog(
                onDismissRequest = { showDialog = false },
                setting = setting,
                isNight = false,
                default = Defaults.PREF_THEME_COLORS
            )
    },
    Setting(context, Settings.PREF_THEME_COLORS_NIGHT, R.string.theme_colors_night) { setting ->
        val ctx = LocalContext.current
        val b = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
        val prefs = ctx.prefs()
        if ((b?.value ?: 0) < 0)
            Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
        var showDialog by rememberSaveable { mutableStateOf(false) }
        Preference(
            name = setting.title,
            description = prefs.getString(setting.key, Defaults.PREF_THEME_COLORS_NIGHT)!!.getStringResourceOrName("theme_name_", ctx),
            onClick = { showDialog = true }
        )
        if (showDialog)
            ColorThemePickerDialog(
                onDismissRequest = { showDialog = false },
                setting = setting,
                isNight = true,
                default = Defaults.PREF_THEME_COLORS_NIGHT
            )
    },
    Setting(context, Settings.PREF_THEME_KEY_BORDERS, R.string.key_borders) {
        SwitchPreference(it, Defaults.PREF_THEME_KEY_BORDERS) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_KEY_OUTLINE_WIDTH, R.string.key_outline_width, R.string.key_outline_width_summary) { setting ->
        SliderPreference(
            name = setting.title,
            key = setting.key,
            default = Defaults.PREF_KEY_OUTLINE_WIDTH,
            description = { if (it == 0) "-" else "${it / 2f} dp" },
            range = 0f..8f,
            stepSize = 1,
        ) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_KEY_STYLE_COLOR_A, R.string.key_style_color_a) {
        KeyColorRow(it, Settings.PREF_KEY_STYLE_COLOR_A, Defaults.PREF_KEY_STYLE_COLOR_A)
    },
    Setting(context, Settings.PREF_KEY_GRADIENT_PALETTE, R.string.key_gradient_palette) {
        PaletteRow(it, Settings.PREF_KEY_GRADIENT_PALETTE)
    },
    Setting(context, Settings.PREF_KEY_LIGHT_PATTERN, R.string.key_light_pattern, R.string.key_light_pattern_summary) { setting ->
        val ctx = LocalContext.current
        val b = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
        if ((b?.value ?: 0) < 0)
            Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
        val items = listOf(
            ctx.getString(R.string.key_light_off) to KeyLighting.OFF,
            ctx.getString(R.string.key_light_wave) to KeyLighting.WAVE,
            ctx.getString(R.string.key_light_flow) to KeyLighting.FLOW,
            ctx.getString(R.string.key_light_breathe) to KeyLighting.BREATHE,
            ctx.getString(R.string.key_light_cycle) to KeyLighting.CYCLE,
            ctx.getString(R.string.key_light_reactive) to KeyLighting.REACTIVE,
            ctx.getString(R.string.key_light_ripple) to KeyLighting.RIPPLE,
        )
        ListPreference(setting, items, Defaults.PREF_KEY_LIGHT_PATTERN, { ctx.prefs().edit { remove(Settings.PREF_KEY_LIGHT_PATTERN) } }) {
            KeyboardSwitcher.getInstance().setThemeNeedsReload()
        }
    },
    Setting(context, Settings.PREF_KEY_LIGHT_SPEED, R.string.key_light_speed) { setting ->
        SliderPreference(
            name = setting.title,
            key = setting.key,
            default = Defaults.PREF_KEY_LIGHT_SPEED,
            description = { "$it" },
            range = 1f..10f,
            stepSize = 1,
        ) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_KEY_LIGHT_PALETTE, R.string.key_light_palette, R.string.key_light_palette_summary) {
        PaletteRow(it, Settings.PREF_KEY_LIGHT_PALETTE)
    },
    Setting(context, Settings.PREF_KEY_LIGHT_RANDOM, R.string.key_light_random, R.string.key_light_random_summary) {
        SwitchPreference(it, Defaults.PREF_KEY_LIGHT_RANDOM) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_KEY_LIGHT_WIDTH, R.string.key_light_width) { setting ->
        SliderPreference(
            name = setting.title,
            key = setting.key,
            default = Defaults.PREF_KEY_LIGHT_WIDTH,
            description = { "${it / 2f} dp" },
            range = 1f..10f,
            stepSize = 1,
        ) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_KEY_LIGHT_GLOW, R.string.key_light_glow) { setting ->
        SliderPreference(
            name = setting.title,
            key = setting.key,
            default = Defaults.PREF_KEY_LIGHT_GLOW,
            description = { if (it <= 0) "-" else "$it dp" },
            range = 0f..12f,
            stepSize = 1,
        ) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_KEY_PRESS_ANIM, R.string.key_press_anim) { setting ->
        val ctx = LocalContext.current
        val b = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
        if ((b?.value ?: 0) < 0)
            Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
        val items = listOf(
            ctx.getString(R.string.key_press_anim_none) to KeyPressAnimation.NONE,
            ctx.getString(R.string.key_press_anim_squish) to KeyPressAnimation.SQUISH,
            ctx.getString(R.string.key_press_anim_pulse) to KeyPressAnimation.PULSE,
            ctx.getString(R.string.key_press_anim_glow) to KeyPressAnimation.GLOW,
        )
        ListPreference(setting, items, Defaults.PREF_KEY_PRESS_ANIM, { ctx.prefs().edit { remove(Settings.PREF_KEY_PRESS_ANIM) } }) {
            KeyboardSwitcher.getInstance().setThemeNeedsReload()
        }
    },
    Setting(context, Settings.PREF_KEY_ANIM_COLOR, R.string.key_anim_color, R.string.key_anim_color_summary) { setting ->
        val prefs = LocalContext.current.prefs()
        var show by rememberSaveable { mutableStateOf(false) }
        Preference(name = setting.title, description = setting.description, onClick = { show = true })
        if (show)
            ColorPickerDialog(
                onDismissRequest = { show = false },
                initialColor = prefs.getInt(Settings.PREF_KEY_ANIM_COLOR, Defaults.PREF_KEY_ANIM_COLOR),
                title = setting.title,
                showDefault = prefs.contains(Settings.PREF_KEY_ANIM_COLOR),
                onDefault = {
                    prefs.edit { remove(Settings.PREF_KEY_ANIM_COLOR) }
                    KeyboardSwitcher.getInstance().setThemeNeedsReload()
                },
            ) { color ->
                prefs.edit { putInt(Settings.PREF_KEY_ANIM_COLOR, color) }
                KeyboardSwitcher.getInstance().setThemeNeedsReload()
            }
    },
    Setting(context, Settings.PREF_KEY_ANIM_SIZE, R.string.key_anim_size) { setting ->
        SliderPreference(
            name = setting.title,
            key = setting.key,
            default = Defaults.PREF_KEY_ANIM_SIZE,
            description = { "$it dp" },
            range = 2f..30f,
            stepSize = 1,
        ) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_KEY_ANIM_SPEED, R.string.key_anim_speed) { setting ->
        SliderPreference(
            name = setting.title,
            key = setting.key,
            default = Defaults.PREF_KEY_ANIM_SPEED,
            description = { "$it ms" },
            range = 100f..800f,
            stepSize = 20,
        ) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_KEY_EDGE, R.string.key_edge) { setting ->
        val ctx = LocalContext.current
        val b = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
        if ((b?.value ?: 0) < 0)
            Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
        val items = listOf(
            ctx.getString(R.string.key_edge_outline) to KeyFace.EDGE_OUTLINE,
            ctx.getString(R.string.key_edge_glow) to KeyFace.EDGE_GLOW,
        )
        ListPreference(setting, items, Defaults.PREF_KEY_EDGE, { ctx.prefs().edit { remove(Settings.PREF_KEY_EDGE) } }) {
            KeyboardSwitcher.getInstance().setThemeNeedsReload()
        }
    },
    Setting(context, Settings.PREF_KEY_GLOW_SIZE, R.string.key_glow_size) { setting ->
        SliderPreference(
            name = setting.title,
            key = setting.key,
            default = Defaults.PREF_KEY_GLOW_SIZE,
            description = { if (it <= 0) "-" else "$it dp" },
            range = 0f..16f,
            stepSize = 1,
        ) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_KEY_GLOW_COLOR, R.string.key_glow_color, R.string.key_glow_color_summary) { setting ->
        val prefs = LocalContext.current.prefs()
        var show by rememberSaveable { mutableStateOf(false) }
        Preference(name = setting.title, description = setting.description, onClick = { show = true })
        if (show)
            ColorPickerDialog(
                onDismissRequest = { show = false },
                initialColor = prefs.getInt(Settings.PREF_KEY_GLOW_COLOR, Defaults.PREF_KEY_GLOW_COLOR),
                title = setting.title,
                showDefault = prefs.contains(Settings.PREF_KEY_GLOW_COLOR),
                onDefault = {
                    prefs.edit { remove(Settings.PREF_KEY_GLOW_COLOR) }
                    KeyboardSwitcher.getInstance().setThemeNeedsReload()
                },
            ) { color ->
                prefs.edit { putInt(Settings.PREF_KEY_GLOW_COLOR, color) }
                KeyboardSwitcher.getInstance().setThemeNeedsReload()
            }
    },
    Setting(context, Settings.PREF_KEY_STYLE, R.string.key_style) { setting ->
        val ctx = LocalContext.current
        val b = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
        if ((b?.value ?: 0) < 0)
            Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
        val items = listOf(
            ctx.getString(R.string.key_style_theme) to KeyFace.THEME,
            ctx.getString(R.string.key_style_flat) to KeyFace.FLAT,
            ctx.getString(R.string.key_style_jelly) to KeyFace.JELLY,
            ctx.getString(R.string.key_style_glass) to KeyFace.GLASS,
            ctx.getString(R.string.key_style_keycap) to KeyFace.KEYCAP,
            ctx.getString(R.string.key_style_neon) to KeyFace.NEON,
            ctx.getString(R.string.key_style_ghost) to KeyFace.GHOST,
            ctx.getString(R.string.key_style_gradient) to KeyFace.GRADIENT,
        )
        ListPreference(setting, items, Defaults.PREF_KEY_STYLE, { ctx.prefs().edit { remove(Settings.PREF_KEY_STYLE) } }) {
            KeyboardSwitcher.getInstance().setThemeNeedsReload()
        }
    },
    Setting(context, Settings.PREF_KEY_CORNER_RADIUS, R.string.key_corner_radius) { setting ->
        SliderPreference(
            name = setting.title,
            key = setting.key,
            default = Defaults.PREF_KEY_CORNER_RADIUS,
            description = { if (it <= 0) "Square" else if (it >= KeyFace.PILL) "Pill" else "$it dp" },
            range = 0f..KeyFace.PILL.toFloat(),
            stepSize = 1,
        ) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_KEY_OUTLINE_COLOR, R.string.key_outline_color) { setting ->
        val prefs = LocalContext.current.prefs()
        var show by rememberSaveable { mutableStateOf(false) }
        Preference(name = setting.title, onClick = { show = true })
        if (show)
            ColorPickerDialog(
                onDismissRequest = { show = false },
                initialColor = prefs.getInt(Settings.PREF_KEY_OUTLINE_COLOR, Defaults.PREF_KEY_OUTLINE_COLOR),
                title = setting.title,
                showDefault = prefs.contains(Settings.PREF_KEY_OUTLINE_COLOR),
                onDefault = {
                    prefs.edit { remove(Settings.PREF_KEY_OUTLINE_COLOR) }
                    KeyboardSwitcher.getInstance().setThemeNeedsReload()
                },
            ) { color ->
                prefs.edit { putInt(Settings.PREF_KEY_OUTLINE_COLOR, color) }
                KeyboardSwitcher.getInstance().setThemeNeedsReload()
            }
    },
    Setting(context, Settings.PREF_THEME_DAY_NIGHT, R.string.day_night_mode, R.string.day_night_mode_summary) {
        SwitchPreference(it, Defaults.PREF_THEME_DAY_NIGHT) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_NAVBAR_COLOR, R.string.theme_navbar, R.string.day_night_mode_summary) {
        SwitchPreference(it, Defaults.PREF_NAVBAR_COLOR)
    },
    Setting(context, Settings.PREF_NAVBAR_BLACK, R.string.theme_navbar_black, R.string.theme_navbar_black_summary) {
        SwitchPreference(it, Defaults.PREF_NAVBAR_BLACK)
    },
    Setting(context, SettingsWithoutKey.BACKGROUND_IMAGE, R.string.customize_background_image) {
        BackgroundImagePref(it, false)
    },
    Setting(context, SettingsWithoutKey.BACKGROUND_IMAGE_LANDSCAPE,
        R.string.customize_background_image_landscape, R.string.summary_customize_background_image_landscape)
    {
        BackgroundImagePref(it, true)
    },
    Setting(context, Settings.PREF_BACKGROUND_BLUR, R.string.background_blur, R.string.background_blur_summary) { setting ->
        SliderPreference(
            name = setting.title,
            key = setting.key,
            default = Defaults.PREF_BACKGROUND_BLUR,
            description = { "$it %" },
            range = 0f..100f,
            stepSize = 5,
        ) { reloadBackgroundImage() }
    },
    Setting(context, Settings.PREF_BACKGROUND_DIM, R.string.background_dim, R.string.background_dim_summary) { setting ->
        SliderPreference(
            name = setting.title,
            key = setting.key,
            default = Defaults.PREF_BACKGROUND_DIM,
            description = { "$it %" },
            range = 0f..80f,
            stepSize = 5,
        ) { reloadBackgroundImage() }
    },
    Setting(context, Settings.PREF_BACKGROUND_KEY_OPACITY, R.string.background_key_opacity, R.string.background_key_opacity_summary) { setting ->
        SliderPreference(
            name = setting.title,
            key = setting.key,
            default = Defaults.PREF_BACKGROUND_KEY_OPACITY,
            description = { "$it %" },
            range = 0f..100f,
            stepSize = 5,
        ) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_ENABLE_SPLIT_KEYBOARD, R.string.enable_split_keyboard) {
        var show by remember { mutableStateOf(false) }
        val prefAndName = listOfNotNull(
            Settings.PREF_ENABLE_SPLIT_KEYBOARD to stringResource(R.string.button_default),
            Settings.PREF_ENABLE_SPLIT_KEYBOARD_LANDSCAPE to stringResource(R.string.landscape),
            if (!FoldableUtils.isFoldable) null else
                Settings.PREF_ENABLE_SPLIT_KEYBOARD_FOLDED to stringResource(R.string.folded),
            if (!FoldableUtils.isFoldable) null else
                Settings.PREF_ENABLE_SPLIT_KEYBOARD_FOLDED_LANDSCAPE to stringResource(R.string.folded) + " / " + stringResource(R.string.landscape)
        )
        Preference(
            name = stringResource(R.string.enable_split_keyboard),
            onClick = { show = true },
            description = prefAndName.filter { LocalContext.current.prefs().getBoolean(it.first, Defaults.PREF_ENABLE_SPLIT_KEYBOARD) }
                .joinToString(", ") { it.second }.takeIf { it.isNotEmpty() }
        )
        if (show) {
            ThreeButtonAlertDialog(
                onDismissRequest = { show = false },
                onConfirmed = {},
                confirmButtonText = null,
                cancelButtonText = stringResource(R.string.dialog_close),
                content = {
                    Column {
                        prefAndName.forEach {
                            SwitchPreference(name = it.second, key = it.first, default = Defaults.PREF_ENABLE_SPLIT_KEYBOARD)
                        }
                    }
                }
            )
        }
    },
    Setting(context, Settings.PREF_SPLIT_SPACER_SCALE_PREFIX, R.string.split_spacer_scale) { setting ->
        KeyboardScalePreference(
            name = setting.title,
            baseKey = setting.key,
            dimensions = listOf(stringResource(R.string.landscape), stringResource(R.string.folded)),
            defaults = Defaults.PREF_SPLIT_SPACER_SCALE,
            range = 0.5f..2f,
            description = { "${(100 * it).toInt()}%" }
        ) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_KEY_GAP_SCALE_PREFIX, R.string.prefs_key_gap_scale) { setting ->
        KeyboardScalePreference(
            name = setting.title,
            baseKey = setting.key,
            dimensions = listOf(stringResource(R.string.landscape), stringResource(R.string.folded)),
            defaults = Defaults.PREF_KEY_GAP_SCALE,
            range = 0.5f..2.5f,
            description = { "${(100 * it).toInt()}%" }
        ) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_KEYBOARD_HEIGHT_SCALE_PREFIX, R.string.prefs_keyboard_height_scale) { setting ->
        KeyboardScalePreference(
            name = setting.title,
            baseKey = setting.key,
            dimensions = listOf(stringResource(R.string.landscape), stringResource(R.string.folded)),
            defaults = Defaults.PREF_KEYBOARD_HEIGHT_SCALE,
            range = 0.3f..1.5f,
            description = { "${(100 * it).toInt()}%" }
        ) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_BOTTOM_ROW_SCALE_PREFIX, R.string.prefs_bottom_row_scale) { setting ->
        KeyboardScalePreference(
            name = setting.title,
            baseKey = setting.key,
            dimensions = listOf(stringResource(R.string.landscape), stringResource(R.string.folded)),
            defaults = Defaults.PREF_BOTTOM_ROW_SCALE,
            range = 0.5f..2f,
            description = { "${(100 * it).toInt()}%" }
        ) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_BOTTOM_PADDING_SCALE_PREFIX, R.string.prefs_bottom_padding_scale) { setting ->
        KeyboardScalePreference(
            name = setting.title,
            baseKey = setting.key,
            dimensions = listOf(stringResource(R.string.landscape), stringResource(R.string.folded)),
            defaults = Defaults.PREF_BOTTOM_PADDING_SCALE,
            range = 0f..5f,
            description = { "${(100 * it).toInt()}%" }
        ) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_SIDE_PADDING_SCALE_PREFIX, R.string.prefs_side_padding_scale) { setting ->
        KeyboardScalePreference(
            name = setting.title,
            baseKey = setting.key,
            dimensions = listOf(stringResource(R.string.landscape), stringResource(R.string.split), stringResource(R.string.folded)),
            defaults = Defaults.PREF_SIDE_PADDING_SCALE,
            range = 0f..3f,
            description = { "${(100 * it).toInt()}%" }
        ) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_SPACE_BAR_TEXT, R.string.prefs_space_bar_text) {
        TextInputPreference(it, Defaults.PREF_SPACE_BAR_TEXT)
    },
    Setting(context, SettingsWithoutKey.CUSTOM_FONT, R.string.custom_font) {
        CustomFontPreference(it, Settings.getCustomFontFile(LocalContext.current), R.string.custom_font)
    },
    Setting(context, Settings.PREF_FONT_SCALE, R.string.prefs_font_scale) { setting ->
        SliderPreference(
            name = setting.title,
            key = setting.key,
            default = Defaults.PREF_FONT_SCALE,
            range = 0.5f..1.5f,
            description = { "${(100 * it).toInt()}%" }
        ) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_HINT_FONT_SCALE, R.string.prefs_hint_font_scale) { setting ->
        SliderPreference(
            name = setting.title,
            key = setting.key,
            default = Defaults.PREF_HINT_FONT_SCALE,
            range = 0.5f..1.5f,
            description = { "${(100 * it).toInt()}%" }
        ) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, SettingsWithoutKey.CUSTOM_EMOJI_FONT, R.string.custom_emoji_font) {
        CustomFontPreference(it, Settings.getCustomEmojiFontFile(LocalContext.current), R.string.custom_emoji_font)
    },
    Setting(context, Settings.PREF_EMOJI_FONT_SCALE, R.string.prefs_emoji_font_scale) { setting ->
        SliderPreference(
            name = setting.title,
            key = setting.key,
            default = Defaults.PREF_EMOJI_FONT_SCALE,
            range = 0.5f..1.5f,
            description = { "${(100 * it).toInt()}%" }
        ) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_EMOJI_KEY_FIT, R.string.prefs_emoji_key_fit) {
        SwitchPreference(it, Defaults.PREF_EMOJI_KEY_FIT) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_EMOJI_SKIN_TONE, R.string.prefs_emoji_skin_tone) { setting ->
        val items = listOf(
            stringResource(R.string.prefs_emoji_skin_tone_neutral) to "",
            "\uD83C\uDFFB" to "\uD83C\uDFFB",
            "\uD83C\uDFFC" to "\uD83C\uDFFC",
            "\uD83C\uDFFD" to "\uD83C\uDFFD",
            "\uD83C\uDFFE" to "\uD83C\uDFFE",
            "\uD83C\uDFFF" to "\uD83C\uDFFF"
        )
        ListPreference(setting, items, Defaults.PREF_EMOJI_SKIN_TONE) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
)

@Preview
@Composable
private fun Preview() {
    initPreview(LocalContext.current)
    Theme(previewDark) {
        Surface {
            AppearanceScreen { }
        }
    }
}

/** The processed background image is cached, so it has to be rebuilt when blur or dim change. */
private fun reloadBackgroundImage() {
    Settings.clearCachedBackgroundImages()
    KeyboardSwitcher.getInstance().setThemeNeedsReload()
}

/** A row that opens the color picker and stores the chosen color (with opacity) under [key]. */
@Composable
private fun KeyColorRow(setting: Setting, key: String, default: Int) {
    val prefs = LocalContext.current.prefs()
    var show by rememberSaveable { mutableStateOf(false) }
    Preference(name = setting.title, description = setting.description, onClick = { show = true })
    if (show)
        ColorPickerDialog(
            onDismissRequest = { show = false },
            initialColor = prefs.getInt(key, default),
            title = setting.title,
            showDefault = prefs.contains(key),
            onDefault = {
                prefs.edit { remove(key) }
                KeyboardSwitcher.getInstance().setThemeNeedsReload()
            },
        ) { color ->
            prefs.edit { putInt(key, color) }
            KeyboardSwitcher.getInstance().setThemeNeedsReload()
        }
}

/** A row showing the palette stored under [key] as a color strip; opens the palette editor. */
@Composable
private fun PaletteRow(setting: Setting, key: String) {
    val prefs = LocalContext.current.prefs()
    var show by rememberSaveable { mutableStateOf(false) }
    val colors = KeyPalette.load(prefs, key).toList()
    Preference(
        name = setting.title,
        description = setting.description,
        onClick = { show = true },
        value = { PaletteStrip(colors, Modifier.width(96.dp).height(20.dp)) }
    )
    if (show)
        PaletteEditorDialog(
            onDismissRequest = { show = false },
            title = setting.title,
            initial = colors,
            defaultColors = KeyPalette.DEFAULT.toList(),
        ) { result ->
            prefs.edit {
                if (result == KeyPalette.DEFAULT.toList()) remove(key) else putString(key, KeyPalette.encode(result))
            }
            KeyboardSwitcher.getInstance().setThemeNeedsReload()
        }
}
