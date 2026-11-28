// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.preferences

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.CodePacks
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.dialogs.ConfirmationDialog

/** Chooses the folder where the tsboard-pack script in Termux puts the packs, and shows the command for Termux. */
@Composable
fun CodePacksFolderPref(setting: Setting) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    val ctx = LocalContext.current
    var label by remember { mutableStateOf(CodePacks.folderLabel(ctx)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            ctx.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (_: Exception) { }
        CodePacks.setFolder(ctx, uri)
        label = CodePacks.folderLabel(ctx)
    }
    Preference(
        name = setting.title,
        description = label ?: stringResource(R.string.code_folder_none),
        onClick = { label = CodePacks.folderLabel(ctx); showDialog = true }
    )
    if (showDialog) {
        val command = CodePacks.termuxCommand(ctx)
        ConfirmationDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(setting.title) },
            confirmButtonText = stringResource(R.string.code_folder_choose),
            onConfirmed = { launcher.launch(null) },
            neutralButtonText = if (label != null) stringResource(R.string.code_folder_remove) else null,
            onNeutral = { CodePacks.clearFolder(ctx); label = null },
            content = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (label == null) {
                        Text(stringResource(R.string.code_folder_steps_choose))
                    } else {
                        Text(stringResource(R.string.code_folder_steps_termux))
                        SelectionContainer {
                            Text(command ?: stringResource(R.string.code_folder_other_storage), fontFamily = FontFamily.Monospace)
                        }
                        if (command != null)
                            TextButton(onClick = { copy(ctx, command) }) { Text(stringResource(R.string.code_folder_copy)) }
                        Text(stringResource(R.string.code_folder_after))
                    }
                }
            }
        )
    }
}

/** Turns the built in packs and the packs from the folder on or off. */
@Composable
fun CodePacksPref(setting: Setting) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    val ctx = LocalContext.current
    var off by remember { mutableStateOf(CodePacks.disabled(ctx)) }
    val hasFolder = CodePacks.folderUri(ctx) != null
    val ids = CodePacks.builtin + if (hasFolder) listOf(CodePacks.FOLDER_ID) else emptyList()
    Preference(
        name = setting.title,
        description = stringResource(R.string.code_packs_count, ids.count { it !in off }, ids.size),
        onClick = { off = CodePacks.disabled(ctx); showDialog = true }
    )
    if (showDialog) {
        var selectedOff by remember { mutableStateOf(off) }
        ConfirmationDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(setting.title) },
            onConfirmed = { CodePacks.setDisabled(ctx, selectedOff); off = selectedOff },
            content = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ids.forEach { id ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                selectedOff = if (id in selectedOff) selectedOff - id else selectedOff + id
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = id !in selectedOff, onCheckedChange = null)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                if (id == CodePacks.FOLDER_ID) stringResource(R.string.code_pack_folder, CodePacks.folderPackCount)
                                else stringResource(CodePacks.builtinName(id))
                            )
                        }
                    }
                }
            }
        )
    }
}

private fun copy(ctx: Context, text: String) {
    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("tsboard-pack", text))
    Toast.makeText(ctx, android.R.string.copy, Toast.LENGTH_SHORT).show()
}
