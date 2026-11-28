// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils.neural

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.regex.Pattern

/**
 * Byte level BPE tokenizer for GPT-2 style models, read from the tokenizer.json of the model.
 * Only what the keyboard needs: text to token ids, and the words that single tokens stand for.
 */
class BpeTokenizer private constructor(
    private val vocab: Map<String, Int>,
    private val ranks: Map<String, Int>,
    /** the id of the token that starts a text (<|endoftext|>), or -1 */
    val startId: Int,
) {
    private val byteToChar = CharArray(256)
    private val charToByte = HashMap<Char, Int>()
    private val cache = HashMap<String, IntArray>()

    /** tokens that are a space and letters: the ids and the words */
    val wordIds: IntArray
    val words: Array<String>

    init {
        val bytes = ArrayList<Int>()
        bytes.addAll(33..126); bytes.addAll(161..172); bytes.addAll(174..255)
        val chars = ArrayList(bytes)
        var n = 0
        for (b in 0..255) if (b !in bytes) { bytes.add(b); chars.add(256 + n); n++ }
        for (i in bytes.indices) {
            byteToChar[bytes[i]] = chars[i].toChar()
            charToByte[chars[i].toChar()] = bytes[i]
        }
        val ids = ArrayList<Int>()
        val list = ArrayList<String>()
        for ((token, id) in vocab) {
            val text = decode(token)
            if (text.length >= 2 && text[0] == ' ' && text.substring(1).all { it in 'a'..'z' || it in 'A'..'Z' }) {
                ids.add(id)
                list.add(text.substring(1))
            }
        }
        wordIds = ids.toIntArray()
        words = list.toTypedArray()
    }

    /** the text of a token (the characters of a token are a stand-in for bytes) */
    fun decode(token: String): String {
        val out = java.io.ByteArrayOutputStream(token.length)
        for (c in token) out.write(charToByte[c] ?: return "")
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    fun encode(text: String): IntArray {
        val result = ArrayList<Int>()
        val matcher = PRE_TOKENIZER.matcher(text)
        while (matcher.find()) {
            val piece = matcher.group()
            val mapped = StringBuilder()
            for (b in piece.toByteArray(Charsets.UTF_8)) mapped.append(byteToChar[b.toInt() and 0xFF])
            result.addAll(bpe(mapped.toString()).toList())
        }
        return result.toIntArray()
    }

    private fun bpe(word: String): IntArray {
        cache[word]?.let { return it }
        var parts: MutableList<String> = word.map { it.toString() }.toMutableList()
        while (parts.size > 1) {
            var bestRank = Int.MAX_VALUE
            var bestIndex = -1
            for (i in 0 until parts.size - 1) {
                val rank = ranks[parts[i] + " " + parts[i + 1]] ?: continue
                if (rank < bestRank) { bestRank = rank; bestIndex = i }
            }
            if (bestIndex < 0) break
            val merged = ArrayList<String>(parts.size)
            val first = parts[bestIndex]
            val second = parts[bestIndex + 1]
            var i = 0
            while (i < parts.size) {
                if (i < parts.size - 1 && parts[i] == first && parts[i + 1] == second) {
                    merged.add(first + second)
                    i += 2
                } else {
                    merged.add(parts[i])
                    i++
                }
            }
            parts = merged
        }
        val ids = parts.mapNotNull { vocab[it] }.toIntArray()
        if (cache.size > 5000) cache.clear()
        cache[word] = ids
        return ids
    }

    companion object {
        private val PRE_TOKENIZER = Pattern.compile(
            "'s|'t|'re|'ve|'m|'ll|'d| ?\\p{L}+| ?\\p{N}+| ?[^\\s\\p{L}\\p{N}]+|\\s+(?!\\S)|\\s+")

        /** @throws IllegalArgumentException if the tokenizer is not a byte level BPE tokenizer */
        fun load(file: File): BpeTokenizer {
            val json = JSONObject(file.readText())
            val model = json.getJSONObject("model")
            require(model.optString("type") == "BPE") { "the tokenizer is not a BPE tokenizer" }
            require(json.optJSONObject("pre_tokenizer")?.toString()?.contains("ByteLevel") == true) {
                "only GPT-2 style tokenizers (byte level BPE) are supported"
            }
            val vocabJson = model.getJSONObject("vocab")
            val vocab = HashMap<String, Int>(vocabJson.length() * 2)
            vocabJson.keys().forEach { vocab[it] = vocabJson.getInt(it) }
            val merges: JSONArray = model.getJSONArray("merges")
            val ranks = HashMap<String, Int>(merges.length() * 2)
            for (i in 0 until merges.length()) {
                val entry = merges.get(i)
                val pair = if (entry is JSONArray) entry.getString(0) + " " + entry.getString(1) else entry.toString()
                ranks[pair] = i
            }
            return BpeTokenizer(vocab, ranks, vocab["<|endoftext|>"] ?: -1)
        }
    }
}
