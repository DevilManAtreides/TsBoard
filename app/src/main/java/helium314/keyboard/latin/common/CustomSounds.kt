// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.common

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.media.SoundPool
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import java.io.File

/**
 * Key press sounds picked by the user. They are copied to the app's files, one file per kind of key,
 * and played with a [SoundPool], which is made for short sounds and has no noticeable delay.
 * A kind of key without its own sound uses the one for normal keys, and without that the system sound.
 */
object CustomSounds {
    const val KEY = "key"
    const val SPACE = "space"
    const val DELETE = "delete"
    const val ENTER = "enter"
    @JvmField
    val SLOTS = listOf(KEY, SPACE, DELETE, ENTER)

    /** Short sounds are all that is useful here, and SoundPool decodes the whole file into memory. */
    private const val MAX_BYTES = 2_000_000L

    private var pool: SoundPool? = null
    private val ids = HashMap<String, Int>()
    private var loadedSignature = ""

    @JvmStatic
    fun dir(context: Context) = File(DeviceProtectedUtils.getFilesDir(context), "custom_sounds")

    @JvmStatic
    fun file(context: Context, slot: String) = File(dir(context), slot)

    /** True if [file] is a small audio file. */
    @JvmStatic
    fun isUsable(file: File): Boolean {
        if (!file.isFile || file.length() <= 0L || file.length() > MAX_BYTES) return false
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.path)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
        } catch (e: Exception) {
            false
        } finally {
            try { retriever.release() } catch (e: Exception) { /* nothing to do */ }
        }
    }

    private fun signature(context: Context): String {
        val parts = SLOTS.map {
            val f = file(context, it)
            if (f.isFile) "${f.lastModified()}:${f.length()}" else "-"
        }
        return if (parts.all { it == "-" }) "" else parts.joinToString("|")
    }

    /** Loads the sounds again if a file was added, replaced or removed since the last call. */
    @JvmStatic
    fun reloadIfNeeded(context: Context) {
        val signature = signature(context)
        if (signature == loadedSignature) return
        loadedSignature = signature
        pool?.release()
        pool = null
        ids.clear()
        if (signature.isEmpty()) return
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val newPool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attributes).build()
        SLOTS.forEach {
            val f = file(context, it)
            if (f.isFile) ids[it] = newPool.load(f.path, 1)
        }
        pool = newPool
    }

    /** Plays the sound for [slot] at [volume] (0..1). Returns false if there is none, so the system sound can be used. */
    @JvmStatic
    fun play(slot: String, volume: Float): Boolean {
        val currentPool = pool ?: return false
        val id = ids[slot] ?: ids[KEY] ?: return false
        return currentPool.play(id, volume, volume, 1, 0, 1f) != 0
    }
}
