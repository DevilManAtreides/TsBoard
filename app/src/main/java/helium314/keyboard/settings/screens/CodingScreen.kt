// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.preferences.SwitchPreference

@Composable
fun CodingScreen(
    onClickBack: () -> Unit,
) {
    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.settings_screen_coding),
        settings = listOf(Settings.PREF_CODING_MODE)
    )
}

fun createCodingSettings(context: Context) = listOf(
    Setting(context, Settings.PREF_CODING_MODE,
        R.string.coding_mode, R.string.coding_mode_summary
    ) { setting ->
        SwitchPreference(setting, Defaults.PREF_CODING_MODE)
    },
)
