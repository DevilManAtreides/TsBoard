// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.content.Context
import android.view.KeyEvent
import androidx.core.content.edit
import helium314.keyboard.event.Event
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.InputStyle
import helium314.keyboard.latin.R
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.suggestions.SuggestedWordInfo
import helium314.keyboard.latin.suggestions.SuggestedWords
import org.json.JSONObject

/**
 * Suggestions for the command line in terminal apps ("coding mode").
 * The keyboard sees every key, so it keeps its own copy of the line that is being typed. From that it works
 * out the command, subcommand and flags, and suggests what usually comes next (from a built-in list and
 * from what the user typed before).
 * What the keyboard can't see (history with the arrow keys, completion with Tab, keys of the terminal's own
 * extra keys row) makes the line unknown until the next Enter, and nothing is suggested in the meantime.
 */
object CodeEngine {
    private const val USAGE_KEY = "tsb_code_usage"
    private const val MAX_USAGE = 600
    private const val MAX_SUGGESTIONS = 10
    private val separators = setOf("|", "||", "&&", ";", "&", "|&")
    private val wrappers = setOf("sudo", "time", "nohup", "exec", "env", "command", "xargs", "watch", "nice", "timeout", "tsu")
    private val envAssignment = Regex("^[A-Za-z_][A-Za-z0-9_]*=.*")
    private val commandName = Regex("^[A-Za-z0-9._+-]{2,30}$")

    private val line = StringBuilder()
    private var cursor = 0
    private var unknown = false
    private var partialLength = 0
    private var lastInfos: List<SuggestedWordInfo> = emptyList()
    private var appContext: Context? = null
    private var db: Map<String, List<String>>? = null
    private var usage: MutableMap<String, Int>? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** a new text field: start with an empty line */
    fun reset() {
        line.setLength(0)
        cursor = 0
        unknown = false
        partialLength = 0
        lastInfos = emptyList()
    }

    // ---------- following the keys ----------

    fun onEvent(event: Event) {
        val keyCode = event.keyCode
        val codePoint = event.codePoint
        when {
            keyCode == KeyCode.DELETE -> backspace()
            keyCode == KeyCode.DELETE_WORD -> deleteWord()
            keyCode == KeyCode.ARROW_LEFT -> if (cursor > 0) cursor--
            keyCode == KeyCode.ARROW_RIGHT -> if (cursor < line.length) cursor++
            keyCode == KeyCode.MOVE_START_OF_LINE -> cursor = 0
            keyCode == KeyCode.MOVE_END_OF_LINE -> cursor = line.length
            keyCode == KeyCode.SHIFT_ENTER -> enter()
            keyCode == KeyCode.ARROW_UP || keyCode == KeyCode.ARROW_DOWN || keyCode == KeyCode.TAB
                || keyCode == KeyCode.PAGE_UP || keyCode == KeyCode.PAGE_DOWN
                || keyCode == KeyCode.MOVE_START_OF_PAGE || keyCode == KeyCode.MOVE_END_OF_PAGE
                || keyCode == KeyCode.CLIPBOARD_PASTE || keyCode == KeyCode.CLIPBOARD_CUT -> unknown = true
            keyCode <= 0 && codePoint > 0 -> typed(codePoint, event.metaState)
        }
    }

    /** text that is inserted as a whole (paste, emoji, ...) */
    fun onText(text: CharSequence) {
        if (unknown) return
        if (text.contains('\n')) { enter(); return }
        line.insert(cursor, text)
        cursor += text.length
    }

    private fun typed(codePoint: Int, metaState: Int) {
        if (metaState and KeyEvent.META_ALT_ON != 0) { unknown = true; return }
        if (metaState and KeyEvent.META_CTRL_ON != 0) { control(codePoint.toChar().lowercaseChar()); return }
        when (codePoint) {
            '\n'.code -> enter()
            '\t'.code -> unknown = true
            else -> {
                if (unknown) return
                val text = String(Character.toChars(codePoint))
                line.insert(cursor, text)
                cursor += text.length
            }
        }
    }

    private fun control(letter: Char) {
        when (letter) {
            'c', 'd', 'z' -> { line.setLength(0); cursor = 0; unknown = false } // new prompt
            'u' -> { line.delete(0, cursor); cursor = 0 }
            'k' -> line.setLength(cursor)
            'a' -> cursor = 0
            'e' -> cursor = line.length
            'w' -> deleteWord()
            'l' -> {}
            'b' -> if (cursor > 0) cursor--
            'f' -> if (cursor < line.length) cursor++
            'h' -> backspace()
            else -> unknown = true // history search and other things the shell does
        }
    }

    private fun backspace() {
        if (unknown || cursor == 0) return
        line.deleteCharAt(cursor - 1)
        cursor--
    }

    private fun deleteWord() {
        if (unknown) return
        var start = cursor
        while (start > 0 && line[start - 1].isWhitespace()) start--
        while (start > 0 && !line[start - 1].isWhitespace()) start--
        line.delete(start, cursor)
        cursor = start
    }

    private fun enter() {
        if (!unknown && line.isNotBlank()) learn(line.toString())
        line.setLength(0)
        cursor = 0
        unknown = false
    }

    // ---------- understanding the line ----------

    private class LineContext(val segment: List<String>, val partial: String)

    private fun context(): LineContext? {
        if (unknown) return null
        val before = line.substring(0, cursor)
        if (before.count { it == '"' } % 2 == 1 || before.count { it == '\'' } % 2 == 1) return null // inside quotes
        val parts = before.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val endsWithSpace = before.isEmpty() || before.last().isWhitespace()
        val complete = if (endsWithSpace) parts else parts.dropLast(1)
        val partial = if (endsWithSpace) "" else parts.last()
        if (partial.contains('/') || partial.startsWith("$") || partial.startsWith("~") || partial.startsWith(".")) return null // paths and variables
        var segment = complete.drop(complete.indexOfLast { it in separators } + 1)
        while (segment.isNotEmpty() && (segment[0] in wrappers || envAssignment.matches(segment[0])))
            segment = segment.drop(1)
        return LineContext(segment, partial)
    }

    private fun resolveKey(db: Map<String, List<String>>, segment: List<String>): String? {
        var key = segment.firstOrNull() ?: return null
        if (!db.containsKey(key)) return null
        for (token in segment.drop(1)) {
            if (token.startsWith("-")) continue
            val deeper = "$key $token"
            if (db.containsKey(deeper)) key = deeper
        }
        return key
    }

    private fun candidates(): Pair<List<String>, Int> {
        val context = context() ?: return emptyList<String>() to 0
        val db = database()
        val usage = usage()
        val partial = context.partial
        val result: List<String>
        if (context.segment.isEmpty()) { // the command itself
            val learned = usage.keys.filter { it.startsWith("|") }.map { it.substring(1) }
            val all = (db.keys.filter { !it.contains(' ') } + learned).distinct()
            result = all.filter { it.startsWith(partial, true) && it != partial }
                .sortedByDescending { usage["|$it"] ?: 0 } // stable, so the order of the list is the tie breaker
        } else {
            val key = resolveKey(db, context.segment) ?: return emptyList<String>() to 0
            val used = context.segment.toSet()
            val items = db[key].orEmpty().filter { it !in used && it != partial }
            val matching = when {
                partial.startsWith("-") -> items.filter { it.startsWith("-") && it.startsWith(partial) }
                partial.isNotEmpty() -> items.filter { !it.startsWith("-") && it.startsWith(partial, true) }
                else -> items
            }
            val (flags, words) = matching.partition { it.startsWith("-") }
            result = words.sortedByDescending { usage["$key|$it"] ?: 0 } + flags.sortedByDescending { usage["$key|$it"] ?: 0 }
        }
        return result.take(MAX_SUGGESTIONS) to partial.length
    }

    // ---------- suggestions ----------

    fun suggestions(): SuggestedWords {
        val (words, partial) = candidates()
        partialLength = partial
        lastInfos = words.mapIndexed { index, word ->
            SuggestedWordInfo(word, "", SuggestedWordInfo.MAX_SCORE - index, SuggestedWordInfo.KIND_PREDICTION,
                Dictionary.DICTIONARY_USER_TYPED, SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE)
        }
        return SuggestedWords(lastInfos, null, null, false, false, false, InputStyle.PREDICTION, SuggestedWords.NOT_A_SEQUENCE_NUMBER)
    }

    fun owns(info: SuggestedWordInfo) = lastInfos.any { it === info }

    /** The keys to press for a picked suggestion: delete what is typed of the word, type the suggestion and a space. */
    fun keysFor(info: SuggestedWordInfo): IntArray {
        val keys = ArrayList<Int>()
        repeat(partialLength) { keys.add(KeyCode.DELETE) }
        var i = 0
        while (i < info.word.length) {
            val cp = info.word.codePointAt(i)
            keys.add(cp)
            i += Character.charCount(cp)
        }
        if (!info.word.endsWith("=") && !info.word.endsWith("/")) keys.add(' '.code)
        return keys.toIntArray()
    }

    // ---------- data ----------

    private fun database(): Map<String, List<String>> {
        db?.let { return it }
        val map = LinkedHashMap<String, List<String>>()
        try {
            appContext?.resources?.openRawResource(R.raw.code_commands)?.bufferedReader()?.useLines { lines ->
                lines.forEach { raw ->
                    if (raw.isBlank() || raw.startsWith("#")) return@forEach
                    val colon = raw.indexOf(':')
                    if (colon <= 0) return@forEach
                    map[raw.substring(0, colon).trim()] = raw.substring(colon + 1).trim().split(" ").filter { it.isNotEmpty() }
                }
            }
        } catch (e: Exception) {
            Log.e("CodeEngine", "could not read the command list", e)
        }
        db = map
        return map
    }

    private fun usage(): MutableMap<String, Int> {
        usage?.let { return it }
        val map = HashMap<String, Int>()
        try {
            val json = appContext?.prefs()?.getString(USAGE_KEY, null)
            if (json != null) JSONObject(json).let { o -> o.keys().forEach { map[it] = o.getInt(it) } }
        } catch (_: Exception) { }
        usage = map
        return map
    }

    private fun bump(usage: MutableMap<String, Int>, key: String) { usage[key] = (usage[key] ?: 0) + 1 }

    /** remembers the commands and flags of a line that was executed, to suggest them first next time */
    private fun learn(executed: String) {
        val db = database()
        val usage = usage()
        val tokens = executed.trim().split(Regex("\\s+"))
        var segment = ArrayList<String>()
        fun finish() {
            var s: List<String> = segment
            while (s.isNotEmpty() && (s[0] in wrappers || envAssignment.matches(s[0]))) s = s.drop(1)
            segment = ArrayList()
            val command = s.firstOrNull() ?: return
            if (!db.containsKey(command) && !commandName.matches(command)) return
            bump(usage, "|$command")
            var key = command
            if (!db.containsKey(key)) return
            for (token in s.drop(1)) {
                val item = if (token.startsWith("-") && token.contains('=')) token.substringBefore('=') + "=" else token
                if (db[key]?.contains(item) == true) bump(usage, "$key|$item")
                val deeper = "$key $token"
                if (!token.startsWith("-") && db.containsKey(deeper)) key = deeper
            }
        }
        for (token in tokens) {
            if (token in separators) finish() else segment.add(token)
        }
        finish()
        if (usage.size > MAX_USAGE) {
            val keep = usage.entries.sortedByDescending { it.value }.take(MAX_USAGE * 3 / 4)
            usage.clear()
            keep.forEach { usage[it.key] = it.value }
        }
        val json = JSONObject()
        usage.forEach { (k, v) -> json.put(k, v) }
        appContext?.prefs()?.edit { putString(USAGE_KEY, json.toString()) }
    }
}
