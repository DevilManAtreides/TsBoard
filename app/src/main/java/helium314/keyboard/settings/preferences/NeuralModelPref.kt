// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.preferences

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.neural.NeuralPredictor
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shows the state of the language model and imports model.onnx and tokenizer.json. */
@Composable
fun NeuralModelPref(setting: Setting) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(NeuralPredictor.statusText(ctx)) }
    var showDialog by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            status = "Copying…"
            val message = withContext(Dispatchers.IO) { NeuralPredictor.import(ctx, uris) }
            NeuralPredictor.prepare(ctx)
            Toast.makeText(ctx, message, Toast.LENGTH_LONG).show()
            status = NeuralPredictor.statusText(ctx)
        }
    }
    Preference(
        name = setting.title,
        description = status,
        onClick = { status = NeuralPredictor.statusText(ctx); showDialog = true }
    )
    if (showDialog) {
        ConfirmationDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(setting.title) },
            confirmButtonText = stringResource(R.string.neural_model_import),
            onConfirmed = { launcher.launch(arrayOf("*/*")) },
            neutralButtonText = if (NeuralPredictor.isInstalled(ctx)) stringResource(R.string.neural_model_remove) else null,
            onNeutral = { NeuralPredictor.remove(ctx); status = NeuralPredictor.statusText(ctx) },
            content = {
                Column {
                    Text(stringResource(R.string.neural_model_help))
                }
            }
        )
    }
}
