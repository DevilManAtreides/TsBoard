// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnLayout
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.KeyboardElement
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.keyboard.KeyboardView
import helium314.keyboard.latin.R
import helium314.keyboard.latin.RichInputMethodSubtype
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.ResourceUtils
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.prefs

/**
 * Shows the keyboard the way it looks with the current settings and rebuilds it whenever a setting changes.
 * Keys can be tapped to try press animations and the key light. Nothing is typed.
 */
@Composable
fun KeyboardLivePreview() {
    val ctx = LocalContext.current
    val b = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    val version = b?.value ?: 0
    var visible by rememberSaveable { mutableStateOf(true) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable { visible = !visible }.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.live_preview),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            Text(
                stringResource(if (visible) R.string.live_preview_hide else R.string.live_preview_show),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (visible)
            key(version) { // a new preview for every settings change
                AndroidView(factory = { createPreview(it) }, modifier = Modifier.fillMaxWidth())
            }
    }
}

private const val TAG = "KeyboardLivePreview"

private fun createPreview(context: Context): View {
    val frame = FrameLayout(context)
    try {
        Settings.getInstance().loadSettings(context) // does nothing if the settings are already loaded
        val settingsValues = Settings.getValues()
        val themeContext = ContextThemeWrapper(context, KeyboardTheme.getKeyboardTheme(context).mStyleId)
        KeyboardLayoutSet.onKeyboardThemeChanged() // forget cached keyboards, they might not match the settings any more
        val width = ResourceUtils.getKeyboardWidth(themeContext, settingsValues)
        val height = ResourceUtils.getKeyboardHeight(themeContext.resources, settingsValues)
        val subtype = RichInputMethodSubtype.get(SubtypeSettings.getSelectedSubtype(context.prefs()))
        val keyboard = KeyboardLayoutSet.Builder(themeContext, EditorInfo(), settingsValues)
            .setKeyboardGeometry(width, height)
            .setSubtype(subtype)
            .build()
            .getKeyboard(KeyboardElement.ALPHABET)

        val view = KeyboardView(themeContext, null)
        view.setHardwareAcceleratedDrawingEnabled(true)
        view.enablePreviewEffects()
        view.setKeyboard(keyboard)
        frame.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // the background is set up on its own copy of the colors, so the real keyboard is not affected
        val colors = KeyboardTheme.getColorsForCurrentTheme(context)
        frame.doOnLayout { colors.setBackground(frame, ColorType.MAIN_BACKGROUND) }

        var pressed: Key? = null
        frame.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val x = event.x.toInt() - view.paddingLeft
                    val y = event.y.toInt() - view.paddingTop
                    pressed = keyboard.sortedKeys.firstOrNull { !it.isSpacer && it.isOnKey(x, y) }
                    pressed?.let { view.previewPress(it) }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    pressed?.let { view.previewRelease(it) }
                    pressed = null
                }
            }
            true
        }
    } catch (e: Exception) {
        Log.e(TAG, "could not create keyboard preview", e)
    }
    return frame
}
