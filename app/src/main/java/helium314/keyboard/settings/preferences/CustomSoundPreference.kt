// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.preferences

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.CustomSounds
import helium314.keyboard.latin.common.FileUtils
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import helium314.keyboard.settings.dialogs.InfoDialog

/** Row to pick, replace or remove the key press sound for [slot], see [CustomSounds]. */
@Composable
fun CustomSoundPreference(setting: Setting, slot: String) {
    val ctx = LocalContext.current
    val soundFile = CustomSounds.file(ctx, slot)
    var hasSound by rememberSaveable { mutableStateOf(soundFile.isFile) }
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var showErrorDialog by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = it.data?.data ?: return@rememberLauncherForActivityResult
        CustomSounds.dir(ctx).mkdirs()
        val tempFile = java.io.File(CustomSounds.dir(ctx), "temp_file")
        try {
            FileUtils.copyContentUriToNewFile(uri, ctx, tempFile)
            if (!CustomSounds.isUsable(tempFile)) throw java.io.IOException("not a short audio file")
            soundFile.delete()
            if (!tempFile.renameTo(soundFile)) throw java.io.IOException("could not save the sound")
            hasSound = true
            CustomSounds.reloadIfNeeded(ctx)
        } catch (e: Exception) {
            tempFile.delete()
            showErrorDialog = true
        }
    }
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        .addCategory(Intent.CATEGORY_OPENABLE)
        .setType("audio/*")
    Preference(
        name = setting.title,
        description = stringResource(if (hasSound) R.string.custom_sound_set else R.string.custom_sound_not_set),
        onClick = {
            if (hasSound) showDialog = true
            else launcher.launch(intent)
        },
    )
    if (showDialog)
        ConfirmationDialog(
            onDismissRequest = { showDialog = false },
            onConfirmed = { launcher.launch(intent) },
            onNeutral = {
                showDialog = false
                soundFile.delete()
                hasSound = false
                CustomSounds.reloadIfNeeded(ctx)
            },
            neutralButtonText = stringResource(R.string.delete),
            confirmButtonText = stringResource(R.string.load),
            title = { Text(setting.title) }
        )
    if (showErrorDialog)
        InfoDialog(stringResource(R.string.custom_sound_error)) { showErrorDialog = false }
}
