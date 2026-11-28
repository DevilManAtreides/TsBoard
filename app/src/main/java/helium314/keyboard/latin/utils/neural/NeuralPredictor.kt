// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils.neural

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.net.Uri
import android.os.Build
import helium314.keyboard.latin.DictionaryFacilitator
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.suggestions.SuggestedWordInfo
import helium314.keyboard.latin.suggestions.SuggestedWords
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.prefs
import java.io.File
import java.nio.LongBuffer
import kotlin.math.ln
import kotlin.math.exp

/**
 * Next word prediction with a small language model (GPT-2 style, ONNX) that runs on the phone.
 * Nothing is included in the app: the model is imported by the user (see tools/make-lm-model.py).
 * If anything is missing or fails, the normal suggestions are used unchanged.
 */
object NeuralPredictor {
    private const val TAG = "NeuralPredictor"
    private const val MAX_TOKENS = 32
    private const val MAX_CONTEXT_CHARS = 160
    const val MODEL_FILE = "model.onnx"
    const val TOKENIZER_FILE = "tokenizer.json"

    enum class State { OFF, MISSING, LOADING, READY, ERROR }

    @Volatile var state = State.MISSING
        private set
    @Volatile var error: String? = null
        private set
    @Volatile private var runner: Runner? = null
    @Volatile private var loading = false

    // logits are cached for the last context: typing a word changes the candidates, not the context
    private var cachedKey: List<Int>? = null
    private var cachedLogProbs: FloatArray? = null

    fun modelDir(context: Context) = File(context.filesDir, "lm")

    fun isInstalled(context: Context): Boolean {
        val dir = modelDir(context)
        return File(dir, MODEL_FILE).isFile && File(dir, TOKENIZER_FILE).isFile
    }

    fun isEnabled(context: Context) =
        Build.VERSION.SDK_INT >= 24 && context.prefs().getBoolean(Settings.PREF_NEURAL_PREDICTION, Defaults.PREF_NEURAL_PREDICTION)

    /** human readable status for the settings screen */
    fun statusText(context: Context): String {
        if (Build.VERSION.SDK_INT < 24) return "Needs Android 7 or newer"
        if (!isInstalled(context)) return "No model installed"
        val mb = File(modelDir(context), MODEL_FILE).length() / (1024 * 1024)
        return when (state) {
            State.LOADING -> "Loading model ($mb MB)…"
            State.READY -> "Model ready ($mb MB)"
            State.ERROR -> "Model could not be loaded: $error"
            else -> "Model installed ($mb MB), loads when typing"
        }
    }

    /** copies the chosen files into the app: a .onnx file and tokenizer.json. Returns a message for the user. */
    fun import(context: Context, uris: List<Uri>): String {
        val dir = modelDir(context).apply { mkdirs() }
        var model = false
        var tokenizer = false
        for (uri in uris) {
            val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: continue
            val target = when {
                name.endsWith(".onnx") -> MODEL_FILE
                name == TOKENIZER_FILE || name.endsWith("tokenizer.json") -> TOKENIZER_FILE
                else -> continue
            }
            val tmp = File(dir, "$target.tmp")
            try {
                context.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                    ?: continue
                File(dir, target).delete()
                tmp.renameTo(File(dir, target))
                if (target == MODEL_FILE) model = true else tokenizer = true
            } catch (e: Exception) {
                tmp.delete()
                return "Copy failed: ${e.message}"
            }
        }
        release()
        return when {
            !model && !tokenizer -> "Nothing imported: choose model.onnx and tokenizer.json"
            isInstalled(context) -> "Model installed"
            model -> "model.onnx imported, tokenizer.json is still missing"
            else -> "tokenizer.json imported, model.onnx is still missing"
        }
    }

    fun remove(context: Context) {
        release()
        modelDir(context).deleteRecursively()
        state = State.MISSING
    }

    @Synchronized private fun release() {
        runner?.close()
        runner = null
        cachedKey = null
        cachedLogProbs = null
        state = State.MISSING
        error = null
    }

    /** starts loading in the background, if wanted and not yet done */
    fun prepare(context: Context) {
        if (!isEnabled(context) || loading || runner != null || !isInstalled(context)) return
        if (state == State.ERROR) return
        loading = true
        state = State.LOADING
        val dir = modelDir(context)
        Thread {
            try {
                val tokenizer = BpeTokenizer.load(File(dir, TOKENIZER_FILE))
                val r = Runner(File(dir, MODEL_FILE).absolutePath, tokenizer)
                runner = r
                state = State.READY
                error = null
            } catch (t: Throwable) {
                Log.w(TAG, "could not load model", t)
                error = t.message ?: t.javaClass.simpleName
                state = State.ERROR
            } finally {
                loading = false
            }
        }.start()
    }

    /**
     * Improves the suggestions with the language model. Called off the UI thread.
     * [textBefore] is the text before the cursor, without the word that is being typed ([typed], empty if none).
     */
    fun improve(
        context: Context, words: SuggestedWords, textBefore: String, typed: String, facilitator: DictionaryFacilitator?
    ): SuggestedWords {
        if (!isEnabled(context)) return words
        val r = runner
        if (r == null) { prepare(context); return words }
        return try {
            improveInternal(r, words, textBefore, typed, facilitator)
        } catch (t: Throwable) {
            Log.w(TAG, "prediction failed", t)
            words
        }
    }

    @Synchronized private fun logProbs(r: Runner, textBefore: String): FloatArray {
        var context = textBefore.takeLast(MAX_CONTEXT_CHARS).trimEnd()
        if (textBefore.length > MAX_CONTEXT_CHARS) context = context.substringAfter(' ', context) // do not start inside a word
        var ids = r.tokenizer.encode(context).toList()
        if (ids.isEmpty() && r.tokenizer.startId >= 0) ids = listOf(r.tokenizer.startId)
        if (ids.size > MAX_TOKENS) ids = ids.takeLast(MAX_TOKENS)
        if (ids == cachedKey) cachedLogProbs?.let { return it }
        val logits = r.run(ids.toIntArray())
        var max = Float.NEGATIVE_INFINITY
        for (v in logits) if (v > max) max = v
        var sum = 0.0
        for (v in logits) sum += exp((v - max).toDouble())
        val norm = max + ln(sum).toFloat()
        for (i in logits.indices) logits[i] -= norm
        cachedKey = ids
        cachedLogProbs = logits
        return logits
    }

    private fun improveInternal(
        r: Runner, words: SuggestedWords, textBefore: String, typed: String, facilitator: DictionaryFacilitator?
    ): SuggestedWords {
        if (words.inputStyle == helium314.keyboard.latin.InputStyle.UPDATE_BATCH
            || words.inputStyle == helium314.keyboard.latin.InputStyle.TAIL_BATCH) return words
        val lp = logProbs(r, textBefore)
        val tok = r.tokenizer
        val sentenceStart = textBefore.isBlank() || textBefore.endsWith("\n") || ((textBefore.trimEnd().lastOrNull() ?: '.') in ".!?")
        if (typed.isEmpty()) {
            // prediction: best words of the model, then what the normal engine had
            val best = ArrayList<Pair<String, Float>>()
            val order = tok.wordIds.indices.sortedByDescending { lp[tok.wordIds[it]] }.take(40)
            for (i in order) {
                var word = tok.words[i]
                if (word.length < 2 && word != "I" && word != "a" && word != "A") continue
                if (facilitator != null && !facilitator.isValidSuggestionWord(word.lowercase())) continue
                word = when {
                    word.lowercase() == "i" -> "I"
                    sentenceStart -> word.replaceFirstChar { it.uppercase() }
                    word == word.uppercase() && word.length > 1 -> continue // all caps tokens are rarely wanted
                    else -> word
                }
                if (best.any { it.first.equals(word, true) }) continue
                best.add(word to lp[tok.wordIds[i]])
                if (best.size >= 5) break
            }
            if (best.isEmpty()) return words
            val result = ArrayList<SuggestedWordInfo>()
            best.forEachIndexed { i, (w, _) -> result.add(info(w, i)) }
            for (old in words.suggestedWordInfoList) {
                if (result.size >= 8) break
                if (result.any { it.word.equals(old.word, true) }) continue
                result.add(old)
            }
            return copy(words, result)
        }
        // typing a word: keep the typed word first, order the rest by how well they fit the context
        val list = words.suggestedWordInfoList
        if (list.size < 3 || words.willAutoCorrect) return words
        val head = list.take(1)
        val rest = list.drop(1).take(8)
        val scored = rest.mapIndexed { i, info ->
            val score = wordScore(tok, lp, info.word) - i * 0.25f // the normal ranking still counts a bit
            info to score
        }
        val sorted = scored.sortedByDescending { it.second }.map { it.first }
        return copy(words, head + sorted + list.drop(1 + rest.size))
    }

    private fun wordScore(tok: BpeTokenizer, lp: FloatArray, word: String): Float {
        var best = Float.NEGATIVE_INFINITY
        for (variant in listOf(word, word.lowercase())) {
            val ids = tok.encode(" $variant")
            if (ids.isEmpty()) continue
            val s = lp[ids[0]] - if (ids.size > 1) 1.5f else 0f
            if (s > best) best = s
        }
        return if (best == Float.NEGATIVE_INFINITY) -30f else best
    }

    private fun info(word: String, index: Int) =
        SuggestedWordInfo(word, "", SuggestedWordInfo.MAX_SCORE - index, SuggestedWordInfo.KIND_PREDICTION,
            Dictionary.DICTIONARY_USER_TYPED, SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE)

    private fun copy(old: SuggestedWords, infos: List<SuggestedWordInfo>) = SuggestedWords(
        infos, old.rawSuggestions, old.typedWordInfo, old.typedWordValid, old.willAutoCorrect,
        old.isObsoleteSuggestions, old.inputStyle, old.sequenceNumber)

    /** the part that touches ONNX Runtime; only created on Android 7+ */
    private class Runner(modelPath: String, val tokenizer: BpeTokenizer) {
        private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
        private val session: OrtSession
        private val inputNames: Set<String>

        init {
            val options = OrtSession.SessionOptions()
            options.setIntraOpNumThreads(2)
            session = env.createSession(modelPath, options)
            inputNames = session.inputNames
        }

        /** the logits for the token after [ids] */
        fun run(ids: IntArray): FloatArray {
            val n = ids.size
            val longs = LongArray(n) { ids[it].toLong() }
            val tensors = ArrayList<OnnxTensor>()
            try {
                val inputs = HashMap<String, OnnxTensor>()
                fun add(name: String, data: LongArray) {
                    if (name !in inputNames) return
                    val t = OnnxTensor.createTensor(env, LongBuffer.wrap(data), longArrayOf(1, n.toLong()))
                    tensors.add(t); inputs[name] = t
                }
                add("input_ids", longs)
                add("attention_mask", LongArray(n) { 1L })
                add("position_ids", LongArray(n) { it.toLong() })
                session.run(inputs).use { result ->
                    val tensor = result[0] as OnnxTensor
                    val shape = tensor.info.shape
                    val vocab = shape[shape.size - 1].toInt()
                    val buffer = tensor.floatBuffer
                    val out = FloatArray(vocab)
                    buffer.position((n - 1) * vocab)
                    buffer.get(out)
                    return out
                }
            } finally {
                tensors.forEach { it.close() }
            }
        }

        fun close() {
            try { session.close() } catch (_: Throwable) { }
        }
    }
}
