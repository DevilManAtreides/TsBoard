// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.KeyPalette

/** A horizontal strip showing the colors of a palette blended together. */
@Composable
fun PaletteStrip(colors: List<Int>, modifier: Modifier = Modifier) {
    val list = if (colors.size >= 2) colors.map { Color(it) } else listOf(Color(colors.firstOrNull() ?: 0), Color(colors.firstOrNull() ?: 0))
    Box(modifier.clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(list)))
}

/**
 * Edit a list of colors: tap a color to select it, then change, move or remove it. "+" adds a color,
 * presets replace the whole list, the neutral button goes back to [defaultColors].
 */
@Composable
fun PaletteEditorDialog(
    title: String,
    initial: List<Int>,
    defaultColors: List<Int>,
    onDismissRequest: () -> Unit,
    onConfirmed: (List<Int>) -> Unit,
) {
    val colors = remember { mutableStateListOf<Int>().apply { addAll(initial) } }
    var selected by remember { mutableStateOf(0) }
    // -1: no picker, -2: picker for a new color, otherwise the index being edited
    var editing by remember { mutableStateOf(-1) }
    val sel = selected.coerceIn(0, (colors.size - 1).coerceAtLeast(0))

    ThreeButtonAlertDialog(
        onDismissRequest = onDismissRequest,
        onConfirmed = { onConfirmed(colors.toList()) },
        title = { Text(title) },
        scrollContent = true,
        checkOk = { colors.isNotEmpty() },
        neutralButtonText = stringResource(R.string.palette_reset),
        onNeutral = {
            colors.clear()
            colors.addAll(defaultColors)
            selected = 0
        },
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PaletteStrip(colors.toList(), Modifier.fillMaxWidth().height(28.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    colors.forEachIndexed { i, c ->
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(
                                    if (i == sel) 3.dp else 1.dp,
                                    if (i == sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    CircleShape
                                )
                                .clickable { selected = i }
                        )
                    }
                    if (colors.size < KeyPalette.MAX_COLORS)
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                .clickable { editing = -2 },
                            contentAlignment = Alignment.Center
                        ) { Text("+", style = MaterialTheme.typography.titleLarge) }
                }
                if (colors.isNotEmpty())
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { editing = sel }) { Text(stringResource(R.string.palette_edit)) }
                        TextButton(
                            enabled = sel > 0,
                            onClick = { val c = colors.removeAt(sel); colors.add(sel - 1, c); selected = sel - 1 }
                        ) { Text("◀") }
                        TextButton(
                            enabled = sel < colors.size - 1,
                            onClick = { val c = colors.removeAt(sel); colors.add(sel + 1, c); selected = sel + 1 }
                        ) { Text("▶") }
                        TextButton(
                            enabled = colors.size > 1,
                            onClick = { colors.removeAt(sel); selected = (sel - 1).coerceAtLeast(0) }
                        ) { Text("✕") }
                    }
                Text(stringResource(R.string.palette_presets), style = MaterialTheme.typography.titleSmall)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    KeyPalette.PRESETS.forEach { preset ->
                        Column(
                            Modifier.clickable {
                                colors.clear()
                                colors.addAll(preset.colors.toList())
                                selected = 0
                            },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            PaletteStrip(preset.colors.toList(), Modifier.width(76.dp).height(28.dp))
                            Text(stringResource(preset.nameRes), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
            }
        }
    )

    if (editing != -1) {
        val index = editing
        ColorPickerDialog(
            onDismissRequest = { editing = -1 },
            initialColor = if (index >= 0 && index < colors.size) colors[index] else (colors.lastOrNull() ?: KeyPalette.DEFAULT[0]),
            title = title,
            showDefault = false,
            onDefault = { },
        ) { color ->
            if (index >= 0 && index < colors.size) colors[index] = color
            else if (colors.size < KeyPalette.MAX_COLORS) { colors.add(color); selected = colors.size - 1 }
        }
    }
}
