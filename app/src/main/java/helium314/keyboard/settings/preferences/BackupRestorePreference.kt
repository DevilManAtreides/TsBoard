// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.preferences

import android.content.Intent
import android.content.SharedPreferences
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import helium314.keyboard.dictionarypack.DictionaryPackConstants
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.emoji.SupportedEmojis
import helium314.keyboard.latin.R
import helium314.keyboard.keyboard.KeyboardTypeface
import helium314.keyboard.latin.common.CustomSounds
import helium314.keyboard.latin.common.FileUtils
import helium314.keyboard.latin.database.Database
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import helium314.keyboard.latin.utils.ExecutorUtils
import helium314.keyboard.latin.utils.LayoutUtilsCustom
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.utils.protectedPrefs
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import helium314.keyboard.settings.dialogs.InfoDialog
import helium314.keyboard.settings.filePicker
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import androidx.core.content.edit
import helium314.keyboard.latin.checkVersionUpgrade
import helium314.keyboard.latin.transferOldPinnedClips
import helium314.keyboard.latin.utils.IntentUtils

@Composable
fun BackupRestorePreference(setting: Setting) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    val ctx = LocalContext.current
    var error: String? by rememberSaveable { mutableStateOf(null) }
    val backupLauncher = backupLauncher { error = it }
    val restoreLauncher = restoreLauncher { error = it }
    Preference(name = setting.title, onClick = { showDialog = true })
    if (showDialog) {
        ConfirmationDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.backup_restore_title)) },
            content = { Text(stringResource(R.string.backup_restore_message)) },
            confirmButtonText = stringResource(R.string.button_backup),
            neutralButtonText = stringResource(R.string.button_restore),
            onNeutral = {
                showDialog = false
                val intent = IntentUtils.getResolvableTypeIntent(ctx, "application/zip")
                    .setAction(Intent.ACTION_OPEN_DOCUMENT)
                restoreLauncher.launch(intent)
            },
            onConfirmed = {
                val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().time)
                val intent = IntentUtils.getResolvableTypeIntent(ctx, "application/zip")
                    .setAction(Intent.ACTION_CREATE_DOCUMENT)
                    .putExtra(
                        Intent.EXTRA_TITLE,
                        ctx.getString(R.string.english_ime_name)
                            .replace(" ", "_") + "_backup_$currentDate.zip"
                    )
                backupLauncher.launch(intent)
            }
        )
    }
    if (error != null) {
        InfoDialog(
            if (error!!.startsWith("b"))
                stringResource(R.string.backup_error, error!!.drop(1))
            else stringResource(R.string.restore_error, error!!.drop(1))
        ) { error = null }
    }
}

@Composable
private fun backupLauncher(onError: (String) -> Unit): ManagedActivityResultLauncher<Intent, ActivityResult> {
    val ctx = LocalContext.current
    return filePicker { uri ->
        // zip all files matching the backup patterns
        // essentially this is the typed words information, and user-added dictionaries
        val filesDir = ctx.filesDir ?: return@filePicker
        val filesPath = filesDir.path + File.separator
        val files = mutableListOf<File>()
        filesDir.walk().forEach { file ->
            val path = file.path.replace(filesPath, "")
            if (file.isFile && backupFilePatterns.any { path.matches(it) })
                files.add(file)
        }
        val protectedFilesDir = DeviceProtectedUtils.getFilesDir(ctx)
        val protectedFilesPath = protectedFilesDir.path + File.separator
        val protectedFiles = mutableListOf<File>()
        protectedFilesDir.walk().forEach { file ->
            val path = file.path.replace(protectedFilesPath, "")
            if (file.isFile && backupFilePatterns.any { path.matches(it) })
                protectedFiles.add(file)
        }
        val wait = CountDownLatch(1)
        ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute {
            try {
                ctx.getActivity()?.contentResolver?.openOutputStream(uri)?.use { os ->
                    // write files to zip
                    val zipStream = ZipOutputStream(os)
                    files.forEach {
                        val fileStream = FileInputStream(it).buffered()
                        zipStream.putNextEntry(ZipEntry(it.path.replace(filesPath, "")))
                        fileStream.copyTo(zipStream, 1024)
                        fileStream.close()
                        zipStream.closeEntry()
                    }
                    protectedFiles.forEach {
                        val fileStream = FileInputStream(it).buffered()
                        zipStream.putNextEntry(ZipEntry(it.path.replace(protectedFilesDir.path, "unprotected")))
                        fileStream.copyTo(zipStream, 1024)
                        fileStream.close()
                        zipStream.closeEntry()
                    }
                    val dbFile = ctx.getDatabasePath(Database.NAME)
                    if (dbFile.exists()) {
                        val fileStream = FileInputStream(dbFile).buffered()
                        zipStream.putNextEntry(ZipEntry(Database.NAME))
                        fileStream.copyTo(zipStream, 1024)
                        fileStream.close()
                        zipStream.closeEntry()
                    }
                    zipStream.putNextEntry(ZipEntry(PREFS_FILE_NAME))
                    settingsToJsonStream(ctx.prefs().all, zipStream)
                    zipStream.closeEntry()
                    zipStream.putNextEntry(ZipEntry(PROTECTED_PREFS_FILE_NAME))
                    settingsToJsonStream(ctx.protectedPrefs().all, zipStream)
                    zipStream.closeEntry()
                    zipStream.close()
                }
            } catch (t: Throwable) {
                onError("b" + t.message)
                Log.w("AdvancedScreen", "error during backup", t)
            } finally {
                wait.countDown()
            }
        }
        wait.await()
    }
}

@Composable
private fun restoreLauncher(onError: (String) -> Unit): ManagedActivityResultLauncher<Intent, ActivityResult> {
    val ctx = LocalContext.current
    return filePicker { uri ->
        val wait = CountDownLatch(1)
        ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute {
            val restoredDb = ctx.getDatabasePath(Database.NAME + "_restored")
            val oldPrefs = ctx.prefs().all.toMap()
            val oldProtectedPrefs = ctx.protectedPrefs().all.toMap()
            val filesDir = ctx.filesDir!!
            val deviceProtectedFilesDir = DeviceProtectedUtils.getFilesDir(ctx)
            val filesDir2 = File(filesDir.absolutePath + "2")
            val deviceProtectedFilesDir2 = File(deviceProtectedFilesDir.absolutePath + "2")
            filesDir.renameTo(filesDir2)
            deviceProtectedFilesDir2.renameTo(deviceProtectedFilesDir2)
            try {
                var anyMatch = false
                ctx.getActivity()?.contentResolver?.openInputStream(uri)?.use { inputStream ->
                    ZipInputStream(inputStream).use { zip ->
                        var entry: ZipEntry? = zip.nextEntry
                        LayoutUtilsCustom.onLayoutFileChanged()
                        Settings.getInstance().stopListener()
                        while (entry != null) {
                            if (entry.name.startsWith("unprotected${File.separator}")) {
                                val adjustedName = entry.name.substringAfter("unprotected${File.separator}")
                                if (backupFilePatterns.any { adjustedName.matches(it) }) {
                                    if (!restoreEntryToDir(zip, deviceProtectedFilesDir, adjustedName)) {
                                        Log.w("AdvancedScreen", "skipping unsafe backup entry $adjustedName")
                                    }
                                }
                                anyMatch = true
                            } else if (backupFilePatterns.any { entry.name.matches(it) }) {
                                if (!restoreEntryToDir(zip, filesDir, entry.name)) {
                                    Log.w("AdvancedScreen", "skipping unsafe backup entry ${entry.name}")
                                }
                                anyMatch = true
                            } else if (entry.name == Database.NAME) {
                                anyMatch = true
                                FileUtils.copyStreamToNewFile(zip, restoredDb)
                            } else if (entry.name == PREFS_FILE_NAME) {
                                val prefLines = String(zip.readBytes()).split("\n")
                                val prefs = ctx.prefs()
                                prefs.edit { clear() }
                                anyMatch = true
                                readJsonLinesToSettings(prefLines, prefs)
                            } else if (entry.name == PROTECTED_PREFS_FILE_NAME) {
                                val prefLines = String(zip.readBytes()).split("\n")
                                val protectedPrefs = ctx.protectedPrefs()
                                protectedPrefs.edit { clear() }
                                readJsonLinesToSettings(prefLines, protectedPrefs)
                                anyMatch = true
                            }
                            zip.closeEntry()
                            entry = zip.nextEntry
                        }
                    }
                }
                if (!anyMatch)
                    throw Exception("nothing to restore in the given file")

                Database.copyFromDb(restoredDb, ctx)
                filesDir2.deleteRecursively()
                deviceProtectedFilesDir2.deleteRecursively()
                if (Looper.myLooper() == null)
                    Looper.prepare()
                Toast.makeText(ctx, ctx.getString(R.string.backup_restored), Toast.LENGTH_LONG).show()
            } catch (t: Throwable) {
                filesDir.deleteRecursively()
                filesDir2.renameTo(filesDir)
                deviceProtectedFilesDir.deleteRecursively()
                deviceProtectedFilesDir2.renameTo(deviceProtectedFilesDir)
                ctx.prefs().edit {
                    clear()
                    oldPrefs.forEach { (key, value) ->
                        when (value) {
                            is String -> putString(key, value)
                            is Int -> putInt(key, value)
                            is Long -> putLong(key, value)
                            is Float -> putFloat(key, value)
                            is Boolean -> putBoolean(key, value)
                            is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                        }
                    }
                }
                ctx.protectedPrefs().edit {
                    clear()
                    oldProtectedPrefs.forEach { (key, value) ->
                        when (value) {
                            is String -> putString(key, value)
                            is Int -> putInt(key, value)
                            is Long -> putLong(key, value)
                            is Float -> putFloat(key, value)
                            is Boolean -> putBoolean(key, value)
                            is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                        }
                    }
                }
                onError("r" + t.message)
                Log.w("AdvancedScreen", "error during restore", t)
            } finally {
                wait.countDown()
            }
        }
        wait.await()
        checkVersionUpgrade(ctx)
        transferOldPinnedClips(ctx)
        Settings.getInstance().startListener()
        SubtypeSettings.reloadEnabledSubtypes(ctx)
        val newDictBroadcast = Intent(DictionaryPackConstants.NEW_DICTIONARY_INTENT_ACTION)
        ctx.getActivity()?.sendBroadcast(newDictBroadcast)
        LayoutUtilsCustom.onLayoutFileChanged()
        LayoutUtilsCustom.removeMissingLayouts(ctx)
        (ctx.getActivity() as? SettingsActivity)?.prefChanged()
        SupportedEmojis.load(ctx)
        KeyboardSwitcher.getInstance().setThemeNeedsReload()
    }
}

@Suppress("UNCHECKED_CAST") // it is checked... but whatever (except string set, because can't check for that))
private fun settingsToJsonStream(settings: Map<String?, Any?>, out: OutputStream) {
    val booleans = settings.filter { it.key is String && it.value is Boolean } as Map<String, Boolean>
    val ints = settings.filter { it.key is String && it.value is Int } as Map<String, Int>
    val longs = settings.filter { it.key is String && it.value is Long } as Map<String, Long>
    val floats = settings.filter { it.key is String && it.value is Float } as Map<String, Float>
    val strings = settings.filter { it.key is String && it.value is String } as Map<String, String>
    val stringSets = settings.filter { it.key is String && it.value is Set<*> } as Map<String, Set<String>>
    // now write
    out.write("boolean settings\n".toByteArray())
    out.write(Json.encodeToString(booleans).toByteArray())
    out.write("\nint settings\n".toByteArray())
    out.write(Json.encodeToString(ints).toByteArray())
    out.write("\nlong settings\n".toByteArray())
    out.write(Json.encodeToString(longs).toByteArray())
    out.write("\nfloat settings\n".toByteArray())
    out.write(Json.encodeToString(floats).toByteArray())
    out.write("\nstring settings\n".toByteArray())
    out.write(Json.encodeToString(strings).toByteArray())
    out.write("\nstring set settings\n".toByteArray())
    out.write(Json.encodeToString(stringSets).toByteArray())
}

private fun readJsonLinesToSettings(list: List<String>, prefs: SharedPreferences): Boolean {
    val i = list.iterator()
    val e = prefs.edit()
    try {
        while (i.hasNext()) {
            when (i.next()) {
                "boolean settings" -> Json.decodeFromString<Map<String, Boolean>>(i.next()).forEach { e.putBoolean(it.key, it.value) }
                "int settings" -> Json.decodeFromString<Map<String, Int>>(i.next()).forEach { e.putInt(it.key, it.value) }
                "long settings" -> Json.decodeFromString<Map<String, Long>>(i.next()).forEach { e.putLong(it.key, it.value) }
                "float settings" -> Json.decodeFromString<Map<String, Float>>(i.next()).forEach { e.putFloat(it.key, it.value) }
                "string settings" -> Json.decodeFromString<Map<String, String>>(i.next()).forEach { e.putString(it.key, it.value) }
                "string set settings" -> Json.decodeFromString<Map<String, Set<String>>>(i.next()).forEach { e.putStringSet(it.key, it.value) }
            }
        }
        e.apply()
        return true
    } catch (e: Exception) {
        return false
    }
}

private fun restoreEntryToDir(zip: ZipInputStream, baseDir: File, entryName: String): Boolean {
    val file = File(baseDir, entryName)
    val canonicalBase = baseDir.canonicalFile
    val canonicalTarget = file.canonicalFile
    if (canonicalTarget.path != canonicalBase.path
        && !canonicalTarget.path.startsWith(canonicalBase.path + File.separator)
    ) return false
    FileUtils.copyStreamToNewFile(zip, file)
    return true
}

private const val PREFS_FILE_NAME = "preferences.json"
private const val PROTECTED_PREFS_FILE_NAME = "protected_preferences.json"

private val backupFilePatterns by lazy { listOf(
    "blacklists${File.separator}.*\\.txt".toRegex(),
    "layouts${File.separator}.*${LayoutUtilsCustom.CUSTOM_LAYOUT_PREFIX}+\\..{0,4}".toRegex(), // can't expect a period at the end, as this would break restoring older backups
    "dicts${File.separator}.*${File.separator}.*user\\.dict".toRegex(),
    "UserHistoryDictionary.*${File.separator}UserHistoryDictionary.*\\.(body|header)".toRegex(),
    "custom_background_image.*".toRegex(),
    "custom_font".toRegex(),
    "custom_emoji_font".toRegex(),
    "custom_sounds${File.separator}.*".toRegex(),
    "clipboard/.*".toRegex(),
) }

// ---- export / import of the look only: colors, key styles, animations, background, fonts, sounds ----

private const val LOOK_PREFS_ENTRY = "look_preferences.json"
private const val LOOK_FILES_PREFIX = "files/"

private val lookKeys by lazy { setOf(
    Settings.PREF_THEME_STYLE, Settings.PREF_ICON_STYLE, Settings.PREF_ACCENT_SHIFTED_ICON, Settings.PREF_CUSTOM_ICON_NAMES,
    Settings.PREF_THEME_KEY_BORDERS, Settings.PREF_THEME_DAY_NIGHT, Settings.PREF_THEME_COLORS, Settings.PREF_THEME_COLORS_NIGHT,
    Settings.PREF_NAVBAR_COLOR, Settings.PREF_NAVBAR_BLACK,
    Settings.PREF_BACKGROUND_BLUR, Settings.PREF_BACKGROUND_DIM, Settings.PREF_BACKGROUND_KEY_OPACITY,
    Settings.PREF_SPACE_BAR_TEXT, Settings.PREF_FONT_SCALE, Settings.PREF_HINT_FONT_SCALE,
    Settings.PREF_EMOJI_FONT_SCALE, Settings.PREF_EMOJI_KEY_FIT,
    Settings.PREF_KEY_OUTLINE_WIDTH, Settings.PREF_KEY_OUTLINE_COLOR, Settings.PREF_KEY_STYLE, Settings.PREF_KEY_EDGE,
    Settings.PREF_KEY_CORNER_RADIUS, Settings.PREF_KEY_GLOW_SIZE, Settings.PREF_KEY_GLOW_COLOR,
    Settings.PREF_KEY_PRESS_ANIM, Settings.PREF_KEY_ANIM_COLOR, Settings.PREF_KEY_ANIM_SIZE, Settings.PREF_KEY_ANIM_SPEED,
    Settings.PREF_KEY_STYLE_COLOR_A, Settings.PREF_KEY_GRADIENT_PALETTE,
    Settings.PREF_KEY_LIGHT_PATTERN, Settings.PREF_KEY_LIGHT_SPEED, Settings.PREF_KEY_LIGHT_PALETTE, Settings.PREF_KEY_LIGHT_RANDOM,
    Settings.PREF_KEY_LIGHT_WIDTH, Settings.PREF_KEY_LIGHT_GLOW,
    Settings.PREF_SOUND_ON, Settings.PREF_KEYPRESS_SOUND_VOLUME,
) }

private val lookKeyPrefixes by lazy { listOf(
    Settings.PREF_USER_COLORS_PREFIX, Settings.PREF_USER_ALL_COLORS_PREFIX, Settings.PREF_USER_MORE_COLORS_PREFIX,
    Settings.PREF_KEY_GAP_SCALE_PREFIX, Settings.PREF_KEYBOARD_HEIGHT_SCALE_PREFIX, Settings.PREF_BOTTOM_ROW_SCALE_PREFIX,
    Settings.PREF_BOTTOM_PADDING_SCALE_PREFIX, Settings.PREF_SIDE_PADDING_SCALE_PREFIX, Settings.PREF_SPLIT_SPACER_SCALE_PREFIX,
) }

private val lookFilePatterns by lazy { listOf(
    "custom_background_image.*".toRegex(),
    "custom_font".toRegex(),
    "custom_emoji_font".toRegex(),
    "custom_sounds/.*".toRegex(),
) }

private fun isLookKey(key: String?) = key != null && (key in lookKeys || lookKeyPrefixes.any { key.startsWith(it) })

/** Reads settings like [readJsonLinesToSettings], but only the ones that belong to the look. */
private fun readLookJsonToSettings(list: List<String>, prefs: SharedPreferences): Boolean {
    val i = list.iterator()
    val e = prefs.edit()
    try {
        while (i.hasNext()) {
            when (i.next()) {
                "boolean settings" -> Json.decodeFromString<Map<String, Boolean>>(i.next()).filter { isLookKey(it.key) }.forEach { e.putBoolean(it.key, it.value) }
                "int settings" -> Json.decodeFromString<Map<String, Int>>(i.next()).filter { isLookKey(it.key) }.forEach { e.putInt(it.key, it.value) }
                "long settings" -> Json.decodeFromString<Map<String, Long>>(i.next()).filter { isLookKey(it.key) }.forEach { e.putLong(it.key, it.value) }
                "float settings" -> Json.decodeFromString<Map<String, Float>>(i.next()).filter { isLookKey(it.key) }.forEach { e.putFloat(it.key, it.value) }
                "string settings" -> Json.decodeFromString<Map<String, String>>(i.next()).filter { isLookKey(it.key) }.forEach { e.putString(it.key, it.value) }
                "string set settings" -> Json.decodeFromString<Map<String, Set<String>>>(i.next()).filter { isLookKey(it.key) }.forEach { e.putStringSet(it.key, it.value) }
            }
        }
        e.apply()
        return true
    } catch (e: Exception) {
        return false
    }
}

@Composable
fun LookTransferPreference(setting: Setting) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    val ctx = LocalContext.current
    var error: String? by rememberSaveable { mutableStateOf(null) }
    val exportLauncher = lookExportLauncher { error = it }
    val importLauncher = lookImportLauncher { error = it }
    Preference(name = setting.title, onClick = { showDialog = true })
    if (showDialog) {
        ConfirmationDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.look_transfer_title)) },
            content = { Text(stringResource(R.string.look_transfer_message)) },
            confirmButtonText = stringResource(R.string.button_export),
            neutralButtonText = stringResource(R.string.button_import),
            onNeutral = {
                showDialog = false
                val intent = IntentUtils.getResolvableTypeIntent(ctx, "application/zip")
                    .setAction(Intent.ACTION_OPEN_DOCUMENT)
                importLauncher.launch(intent)
            },
            onConfirmed = {
                val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().time)
                val intent = IntentUtils.getResolvableTypeIntent(ctx, "application/zip")
                    .setAction(Intent.ACTION_CREATE_DOCUMENT)
                    .putExtra(Intent.EXTRA_TITLE, ctx.getString(R.string.english_ime_name).replace(" ", "_") + "_look_$currentDate.zip")
                exportLauncher.launch(intent)
            }
        )
    }
    if (error != null) {
        InfoDialog(
            if (error!!.startsWith("e")) stringResource(R.string.look_export_error, error!!.drop(1))
            else stringResource(R.string.look_import_error, error!!.drop(1))
        ) { error = null }
    }
}

@Composable
private fun lookExportLauncher(onError: (String) -> Unit): ManagedActivityResultLauncher<Intent, ActivityResult> {
    val ctx = LocalContext.current
    return filePicker { uri ->
        val filesDir = DeviceProtectedUtils.getFilesDir(ctx)
        val basePath = filesDir.path + File.separator
        val files = filesDir.walk().filter { it.isFile && lookFilePatterns.any { p -> it.path.replace(basePath, "").matches(p) } }.toList()
        val lookPrefs = HashMap<String?, Any?>()
        for ((key, value) in ctx.prefs().all) {
            if (isLookKey(key)) lookPrefs[key] = value
        }
        val wait = CountDownLatch(1)
        ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute {
            try {
                ctx.getActivity()?.contentResolver?.openOutputStream(uri)?.use { os ->
                    val zip = ZipOutputStream(os)
                    zip.putNextEntry(ZipEntry(LOOK_PREFS_ENTRY))
                    settingsToJsonStream(lookPrefs, zip)
                    zip.closeEntry()
                    files.forEach {
                        zip.putNextEntry(ZipEntry(LOOK_FILES_PREFIX + it.path.replace(basePath, "")))
                        FileInputStream(it).buffered().use { input -> input.copyTo(zip, 1024) }
                        zip.closeEntry()
                    }
                    zip.close()
                }
            } catch (t: Throwable) {
                onError("e" + t.message)
                Log.w("AdvancedScreen", "error during look export", t)
            } finally {
                wait.countDown()
            }
        }
        wait.await()
    }
}

@Composable
private fun lookImportLauncher(onError: (String) -> Unit): ManagedActivityResultLauncher<Intent, ActivityResult> {
    val ctx = LocalContext.current
    return filePicker { uri ->
        val wait = CountDownLatch(1)
        ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute {
            try {
                var anyMatch = false
                val filesDir = DeviceProtectedUtils.getFilesDir(ctx)
                ctx.getActivity()?.contentResolver?.openInputStream(uri)?.use { inputStream ->
                    ZipInputStream(inputStream).use { zip ->
                        var entry: ZipEntry? = zip.nextEntry
                        while (entry != null) {
                            if (entry.name == LOOK_PREFS_ENTRY) {
                                val lines = String(zip.readBytes()).split("\n")
                                if (!readLookJsonToSettings(lines, ctx.prefs())) throw Exception("settings in the file are not readable")
                                anyMatch = true
                            } else if (entry.name.startsWith(LOOK_FILES_PREFIX)) {
                                val name = entry.name.removePrefix(LOOK_FILES_PREFIX)
                                if (lookFilePatterns.any { name.matches(it) }) {
                                    if (!restoreEntryToDir(zip, filesDir, name))
                                        Log.w("AdvancedScreen", "skipping unsafe entry $name")
                                    anyMatch = true
                                }
                            }
                            zip.closeEntry()
                            entry = zip.nextEntry
                        }
                    }
                }
                if (!anyMatch) throw Exception("nothing to import in the given file")
                if (Looper.myLooper() == null)
                    Looper.prepare()
                Toast.makeText(ctx, ctx.getString(R.string.look_imported), Toast.LENGTH_LONG).show()
            } catch (t: Throwable) {
                onError("i" + t.message)
                Log.w("AdvancedScreen", "error during look import", t)
            } finally {
                wait.countDown()
            }
        }
        wait.await()
        CustomSounds.reloadIfNeeded(ctx)
        KeyboardTypeface.clearCache()
        (ctx.getActivity() as? SettingsActivity)?.prefChanged()
        KeyboardSwitcher.getInstance().setThemeNeedsReload()
    }
}
