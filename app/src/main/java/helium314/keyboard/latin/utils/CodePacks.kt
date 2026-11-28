// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.edit
import helium314.keyboard.latin.R

/**
 * The command packs of the coding mode. A pack is a text file with lines like
 * `git commit: -m -a --amend` (a command, or a command and a subcommand, then what usually follows).
 * Some packs are built in. The others are files in a folder the user chose: the tsboard-pack script in
 * Termux puts a pack there for every tool that is installed, and the keyboard reads them from there.
 */
object CodePacks {
    private const val KEY_FOLDER = "tsb_code_packs_uri"
    private const val KEY_OFF = "tsb_code_packs_off"
    const val FOLDER_ID = "folder"
    const val SCRIPT_NAME = "tsboard-pack.sh"
    private const val CHECK_INTERVAL = 3000L

    val builtin = listOf("core", "git", "packages", "network", "dev", "containers", "termux", "media")

    fun builtinResource(id: String) = when (id) {
        "core" -> R.raw.code_pack_core
        "git" -> R.raw.code_pack_git
        "packages" -> R.raw.code_pack_packages
        "network" -> R.raw.code_pack_network
        "dev" -> R.raw.code_pack_dev
        "containers" -> R.raw.code_pack_containers
        "termux" -> R.raw.code_pack_termux
        else -> R.raw.code_pack_media
    }

    fun builtinName(id: String) = when (id) {
        "core" -> R.string.code_pack_core
        "git" -> R.string.code_pack_git
        "packages" -> R.string.code_pack_packages
        "network" -> R.string.code_pack_network
        "dev" -> R.string.code_pack_dev
        "containers" -> R.string.code_pack_containers
        "termux" -> R.string.code_pack_termux
        else -> R.string.code_pack_media
    }

    // ---------- settings ----------

    fun folderUri(context: Context): Uri? =
        context.prefs().getString(KEY_FOLDER, "")?.takeIf { it.isNotEmpty() }?.let { Uri.parse(it) }

    fun disabled(context: Context): Set<String> =
        context.prefs().getString(KEY_OFF, "")!!.split(",").filter { it.isNotEmpty() }.toSet()

    fun setDisabled(context: Context, off: Set<String>) {
        context.prefs().edit { putString(KEY_OFF, off.joinToString(",")) }
        refreshAsync(context, true)
    }

    fun setFolder(context: Context, uri: Uri) {
        context.prefs().edit { putString(KEY_FOLDER, uri.toString()) }
        val app = context.applicationContext
        Thread {
            writeScript(app, uri)
            refreshAsync(app, true)
        }.start()
    }

    fun clearFolder(context: Context) {
        context.prefs().edit { remove(KEY_FOLDER) }
        refreshAsync(context, true)
    }

    /** The folder as a path in the storage, like TsBoard or Download/TsBoard, if it is on the main storage. */
    fun folderPath(context: Context): String? {
        val uri = folderUri(context) ?: return null
        val id = try { DocumentsContract.getTreeDocumentId(uri) } catch (_: Exception) { return null }
        return if (id.startsWith("primary:")) id.removePrefix("primary:") else null
    }

    fun folderLabel(context: Context): String? {
        val uri = folderUri(context) ?: return null
        val id = try { DocumentsContract.getTreeDocumentId(uri) } catch (_: Exception) { return null }
        return id.substringAfter(':').ifEmpty { id }
    }

    /** the command to run in Termux (the folder needs to be in the main storage) */
    fun termuxCommand(context: Context): String? =
        folderPath(context)?.let { "bash ~/storage/shared/$it/$SCRIPT_NAME --install" }

    @Volatile var folderPackCount = 0
        private set

    // ---------- reading ----------

    private class FilePack(val name: String, val uri: Uri, val modified: Long)
    private class Parsed(val modified: Long, val entries: Map<String, List<String>>)

    private val fileCache = HashMap<String, Parsed>()
    private var lastSignature = ""
    private var lastCheck = 0L

    private fun parseInto(map: MutableMap<String, MutableList<String>>, lines: Sequence<String>) {
        lines.forEach { raw ->
            if (raw.isBlank() || raw.startsWith("#")) return@forEach
            val colon = raw.indexOf(':')
            if (colon <= 0) return@forEach
            val key = raw.substring(0, colon).trim().replace(Regex("\\s+"), " ")
            val list = map.getOrPut(key) { ArrayList() }
            raw.substring(colon + 1).trim().split(" ").forEach { if (it.isNotEmpty() && it !in list) list.add(it) }
        }
    }

    private fun parse(lines: Sequence<String>): Map<String, List<String>> {
        val map = LinkedHashMap<String, MutableList<String>>()
        parseInto(map, lines)
        return map
    }

    private fun merge(into: MutableMap<String, MutableList<String>>, from: Map<String, List<String>>) {
        from.forEach { (key, items) ->
            val list = into.getOrPut(key) { ArrayList() }
            items.forEach { if (it !in list) list.add(it) }
        }
    }

    /** the built in packs that are turned on */
    fun loadBuiltin(context: Context): Map<String, List<String>> {
        val off = disabled(context)
        val result = LinkedHashMap<String, MutableList<String>>()
        builtin.filter { it !in off }.forEach { id ->
            try {
                context.resources.openRawResource(builtinResource(id)).bufferedReader().use { merge(result, parse(it.lineSequence())) }
            } catch (e: Exception) {
                Log.e("CodePacks", "could not read pack $id", e)
            }
        }
        return result
    }

    private fun listFolder(context: Context, tree: Uri): List<FilePack> {
        val result = ArrayList<FilePack>()
        try {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            context.contentResolver.query(children, arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            ), null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(1) ?: continue
                    if (!name.endsWith(".txt")) continue
                    result.add(FilePack(name, DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)), c.getLong(2)))
                }
            }
        } catch (e: Exception) {
            Log.e("CodePacks", "could not list the packs folder", e)
        }
        return result.sortedBy { it.name }
    }

    /** Reads the packs again if something changed, and gives them to the engine. Runs in the background. */
    fun refreshAsync(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        if (!force && now - lastCheck < CHECK_INTERVAL) return
        lastCheck = now
        Thread { refresh(app, force) }.start()
    }

    @Synchronized
    private fun refresh(context: Context, force: Boolean) {
        val tree = folderUri(context)
        val off = disabled(context)
        val files = if (tree != null && FOLDER_ID !in off) listFolder(context, tree) else emptyList()
        if (tree != null) folderPackCount = if (FOLDER_ID in off) folderPackCount else files.size
        val signature = off.sorted().joinToString(",") + "|" + files.joinToString(",") { it.name + it.modified }
        if (!force && signature == lastSignature) return
        lastSignature = signature
        val result = LinkedHashMap<String, MutableList<String>>()
        merge(result, loadBuiltin(context))
        val names = files.map { it.name }.toSet()
        fileCache.keys.retainAll(names)
        files.forEach { file ->
            var parsed = fileCache[file.name]
            if (parsed == null || parsed.modified != file.modified) {
                parsed = try {
                    context.contentResolver.openInputStream(file.uri)?.bufferedReader()?.use { Parsed(file.modified, parse(it.lineSequence())) }
                } catch (e: Exception) { null } ?: Parsed(file.modified, emptyMap())
                fileCache[file.name] = parsed
            }
            merge(result, parsed.entries)
        }
        CodeEngine.setDatabase(result)
    }

    /** puts the tsboard-pack script for Termux into the packs folder */
    private fun writeScript(context: Context, tree: Uri) {
        try {
            val resolver = context.contentResolver
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val existing = listFolderAll(context, tree).firstOrNull { it.first == SCRIPT_NAME }?.second
            val target = existing ?: DocumentsContract.createDocument(resolver, parent, "application/x-sh", SCRIPT_NAME) ?: return
            resolver.openOutputStream(target, "wt")?.use { out ->
                context.resources.openRawResource(R.raw.tsboard_pack_script).use { it.copyTo(out) }
            }
        } catch (e: Exception) {
            Log.e("CodePacks", "could not write the script", e)
        }
    }

    private fun listFolderAll(context: Context, tree: Uri): List<Pair<String, Uri>> {
        val result = ArrayList<Pair<String, Uri>>()
        try {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            context.contentResolver.query(children, arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            ), null, null, null)?.use { c ->
                while (c.moveToNext())
                    result.add((c.getString(1) ?: "") to DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)))
            }
        } catch (_: Exception) { }
        return result
    }
}
