// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.preferences

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.TerminalCompletions
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.dialogs.ConfirmationDialog

/** Links the file made by the tsboard-sync script in Termux, and shows the command that makes it. */
@Composable
fun TerminalCompletionsPref(setting: Setting) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var linked by rememberSaveable { mutableStateOf(false) }
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        try {
            ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) { }
        TerminalCompletions.setFile(ctx, uri)
        linked = true
    }
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        .addCategory(Intent.CATEGORY_OPENABLE)
        .setType("*/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
    Preference(
        name = setting.title,
        description = setting.description,
        onClick = { linked = TerminalCompletions.uri(ctx) != null; showDialog = true }
    )
    if (showDialog) {
        ConfirmationDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(setting.title) },
            confirmButtonText = stringResource(R.string.terminal_import_choose),
            onConfirmed = { launcher.launch(intent) },
            neutralButtonText = if (linked) stringResource(R.string.terminal_import_remove) else null,
            onNeutral = { TerminalCompletions.clear(ctx); linked = false },
            content = {
                Column {
                    Text(stringResource(if (linked) R.string.terminal_import_linked else R.string.terminal_import_steps))
                    SelectionContainer {
                        Text(TerminalCompletions.SYNC_COMMAND, fontFamily = FontFamily.Monospace)
                    }
                    TextButton(onClick = { copy(ctx, TerminalCompletions.SYNC_COMMAND) }) {
                        Text(stringResource(R.string.terminal_import_copy))
                    }
                }
            }
        )
    }
}

private fun copy(ctx: Context, text: String) {
    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("tsboard-sync", text))
    Toast.makeText(ctx, android.R.string.copy, Toast.LENGTH_SHORT).show()
}
