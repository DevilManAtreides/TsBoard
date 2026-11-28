// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.edit
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.dictionary.ExpandableDictionaryUtils
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import java.io.InputStream

/**
 * Terminal completions the user imported, made by the tsboard-sync script in Termux (all installed commands and their flags).
 * The file stays linked, so running the script again is enough: the keyboard notices that the file changed.
 */
object TerminalCompletions {
    const val SYNC_COMMAND = "curl -sL https://raw.githubusercontent.com/DevilManAtreides/TsBoard/main/tools/tsboard-sync.sh | bash"

    fun uri(context: Context): Uri? =
        context.prefs().getString(Settings.PREF_TERMINAL_IMPORT_URI, "")?.takeIf { it.isNotEmpty() }?.let { Uri.parse(it) }

    fun open(context: Context): InputStream? {
        val uri = uri(context) ?: return null
        return try { context.contentResolver.openInputStream(uri) } catch (_: Exception) { null }
    }

    private fun lastModified(context: Context, uri: Uri): Long = try {
        context.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED), null, null, null)
            ?.use { if (it.moveToFirst()) it.getLong(0) else 0L } ?: 0L
    } catch (_: Exception) { 0L }

    /** Links a file picked by the user. */
    fun setFile(context: Context, uri: Uri) {
        val modified = lastModified(context, uri)
        context.prefs().edit {
            putString(Settings.PREF_TERMINAL_IMPORT_URI, uri.toString())
            putLong(Settings.PREF_TERMINAL_IMPORT_MODIFIED, modified)
        }
        ExpandableDictionaryUtils.setNeedsUpdate(Dictionary.TYPE_APPS)
    }

    fun clear(context: Context) {
        context.prefs().edit {
            remove(Settings.PREF_TERMINAL_IMPORT_URI)
            remove(Settings.PREF_TERMINAL_IMPORT_MODIFIED)
        }
        ExpandableDictionaryUtils.setNeedsUpdate(Dictionary.TYPE_APPS)
    }

    /** Reloads the words if the file was changed since they were loaded. */
    fun checkForUpdate(context: Context) {
        val prefs = context.prefs()
        if (!prefs.getBoolean(Settings.PREF_TERMINAL_WORDS, Defaults.PREF_TERMINAL_WORDS)) return
        val uri = uri(context) ?: return
        val app = context.applicationContext
        Thread {
            val modified = lastModified(app, uri)
            if (modified != 0L && modified != prefs.getLong(Settings.PREF_TERMINAL_IMPORT_MODIFIED, 0L)) {
                prefs.edit { putLong(Settings.PREF_TERMINAL_IMPORT_MODIFIED, modified) }
                ExpandableDictionaryUtils.setNeedsUpdate(Dictionary.TYPE_APPS)
            }
        }.start()
    }
}
