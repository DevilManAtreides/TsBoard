// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.SettingsSubtype.Companion.toSettingsSubtype
import helium314.keyboard.latin.utils.AppProfiles
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.NextScreenIcon
import helium314.keyboard.latin.utils.SubtypeLocaleUtils.displayName
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import helium314.keyboard.settings.dialogs.ListPickerDialog
import helium314.keyboard.settings.dialogs.ThreeButtonAlertDialog
import helium314.keyboard.settings.preferences.Preference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lists the app profiles and lets the person create, assign and edit them. */
@Composable
fun ProfilesScreen(onClickBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    val b = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    var refresh by remember { mutableStateOf(0) }
    if (refresh < 0) Log.v("irrelevant", "refresh")
    val profiles = AppProfiles.load(prefs)
    val editing = AppProfiles.editing(prefs)

    var showNew by rememberSaveable { mutableStateOf(false) }
    var actionsFor by rememberSaveable { mutableStateOf<String?>(null) }
    var renameId by rememberSaveable { mutableStateOf<String?>(null) }
    var appsId by rememberSaveable { mutableStateOf<String?>(null) }
    var layoutId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var editId by rememberSaveable { mutableStateOf<String?>(null) }

    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.settings_screen_profiles),
        settings = emptyList(),
    ) {
        Scaffold(contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)) { innerPadding ->
            Column(Modifier.verticalScroll(rememberScrollState()).padding(innerPadding)) {
                ProfileEditBanner()
                Text(
                    stringResource(R.string.profiles_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                profiles.forEach { profile ->
                    val appsText = if (profile.apps.isEmpty()) stringResource(R.string.profile_no_apps)
                        else stringResource(R.string.profile_apps_count, profile.apps.size)
                    val changesText = stringResource(R.string.profile_changes_count, AppProfiles.changedCount(profile))
                    Preference(
                        name = profile.name,
                        description = "$appsText · $changesText",
                        onClick = { actionsFor = profile.id },
                    ) { NextScreenIcon() }
                }
                Preference(
                    name = stringResource(R.string.profile_new),
                    onClick = { if (editing == null) showNew = true },
                )
            }
        }
    }

    if (showNew)
        ProfileNameDialog(
            title = stringResource(R.string.profile_new),
            initial = "",
            onDismissRequest = { showNew = false },
        ) { name ->
            actionsFor = AppProfiles.create(prefs, name)
            refresh++
        }

    val actionProfile = profiles.firstOrNull { it.id == actionsFor }
    if (actionProfile != null) {
        val items = listOf(
            stringResource(R.string.profile_action_apps) to "apps",
            stringResource(R.string.profile_action_layout) to "layout",
            stringResource(R.string.profile_action_settings) to "settings",
            stringResource(R.string.profile_action_rename) to "rename",
            stringResource(R.string.profile_action_delete) to "delete",
        )
        ListPickerDialog(
            onDismissRequest = { actionsFor = null },
            items = items,
            onItemSelected = {
                val id = actionProfile.id
                when (it.second) {
                    "apps" -> appsId = id
                    "layout" -> layoutId = id
                    "settings" -> editId = id
                    "rename" -> renameId = id
                    "delete" -> deleteId = id
                }
                actionsFor = null
            },
            title = { Text(actionProfile.name) },
            getItemName = { it.first },
            showRadioButtons = false,
        )
    }

    profiles.firstOrNull { it.id == renameId }?.let { profile ->
        ProfileNameDialog(
            title = stringResource(R.string.profile_action_rename),
            initial = profile.name,
            onDismissRequest = { renameId = null },
        ) { name ->
            AppProfiles.update(prefs, profile.id) { it.name = name }
            refresh++
        }
    }

    profiles.firstOrNull { it.id == appsId }?.let { profile ->
        AppPickerDialog(
            initial = profile.apps,
            title = profile.name,
            onDismissRequest = { appsId = null },
        ) { apps ->
            AppProfiles.setApps(prefs, profile.id, apps)
            refresh++
        }
    }

    profiles.firstOrNull { it.id == layoutId }?.let { profile ->
        val subtypes = SubtypeSettings.getEnabledSubtypes(true)
        val items = listOf(stringResource(R.string.profile_layout_default) to "") +
            subtypes.map { it.displayName() to it.toSettingsSubtype().toPref() }
        val selected = items.firstOrNull { it.second == (profile.subtype ?: "") }
        ListPickerDialog(
            onDismissRequest = { layoutId = null },
            items = items,
            onItemSelected = { item ->
                AppProfiles.update(prefs, profile.id) { it.subtype = item.second.ifEmpty { null } }
                refresh++
            },
            title = { Text(stringResource(R.string.profile_action_layout)) },
            selectedItem = selected,
            getItemName = { it.first },
            description = stringResource(R.string.profile_layout_summary),
        )
    }

    profiles.firstOrNull { it.id == deleteId }?.let { profile ->
        ConfirmationDialog(
            onDismissRequest = { deleteId = null },
            onConfirmed = {
                AppProfiles.delete(ctx, profile.id)
                refresh++
            },
            content = { Text(stringResource(R.string.profile_delete_confirm, profile.name)) },
        )
    }

    profiles.firstOrNull { it.id == editId }?.let { profile ->
        ConfirmationDialog(
            onDismissRequest = { editId = null },
            onConfirmed = {
                if (editing == null) AppProfiles.beginEdit(ctx, profile.id)
                refresh++
            },
            confirmButtonText = stringResource(R.string.profile_edit_start),
            content = { Text(stringResource(R.string.profile_edit_explain, profile.name)) },
        )
    }
}

/** Shown while a profile is edited: the normal settings screens change the profile until it is saved. */
@Composable
fun ProfileEditBanner() {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    val b = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    val profile = AppProfiles.editing(prefs)
    if (profile != null)
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(stringResource(R.string.profile_editing, profile.name), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.profile_editing_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { AppProfiles.endEdit(ctx, false) }) { Text(stringResource(android.R.string.cancel)) }
                    TextButton(onClick = { AppProfiles.endEdit(ctx, true) }) { Text(stringResource(R.string.profile_save)) }
                }
            }
        }
}

@Composable
private fun ProfileNameDialog(
    title: String,
    initial: String,
    onDismissRequest: () -> Unit,
    onConfirmed: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial) }
    ThreeButtonAlertDialog(
        onDismissRequest = onDismissRequest,
        onConfirmed = { onConfirmed(name.trim()) },
        checkOk = { name.isNotBlank() },
        title = { Text(title) },
        content = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
    )
}

private class AppEntry(val pkg: String, val label: String)

@Composable
private fun AppPickerDialog(
    initial: List<String>,
    title: String,
    onDismissRequest: () -> Unit,
    onConfirmed: (List<String>) -> Unit,
) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    val selected = remember { mutableStateListOf<String>().apply { addAll(initial) } }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(intent, 0)
                .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
                .distinctBy { it.pkg }
                .filter { it.pkg != ctx.packageName }
                .sortedBy { it.label.lowercase() }
        }
    }
    ThreeButtonAlertDialog(
        onDismissRequest = onDismissRequest,
        onConfirmed = { onConfirmed(selected.toList()) },
        title = { Text(stringResource(R.string.profile_choose_apps, title)) },
        content = {
            val list = apps
            if (list == null) Text(stringResource(R.string.profile_loading_apps))
            else LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(list, key = { it.pkg }) { app ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { if (app.pkg in selected) selected.remove(app.pkg) else selected.add(app.pkg) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = app.pkg in selected, onCheckedChange = null)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(app.label)
                            Text(app.pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
    )
}
