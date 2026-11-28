// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.dictionary.ExpandableDictionaryUtils
import helium314.keyboard.latin.settings.Settings
import org.json.JSONArray
import org.json.JSONObject

/**
 * App profiles: a set of changed settings (and optionally a layout/language) that is used while typing
 * in certain apps. When a profile becomes active its changed settings are written over the normal ones,
 * and the normal values are restored when another app (or none) is used. So every setting can be part of a profile.
 *
 * Editing a profile works the same way: the profile is applied, the person changes settings in the normal
 * settings screens, and on save the difference to the normal values becomes the profile.
 */
object AppProfiles {
    private const val PREFIX = "tsb_"
    private const val KEY_PROFILES = "tsb_profiles"
    private const val KEY_ACTIVE = "tsb_profile_active"
    private const val KEY_SAVED = "tsb_profile_saved"
    private const val KEY_EDIT = "tsb_profile_edit"
    private const val KEY_EDIT_BASE = "tsb_profile_edit_base"

    class Profile(
        val id: String,
        var name: String,
        var apps: List<String>,
        /** a subtype in the format of saved settings subtypes, null for "no change" */
        var subtype: String?,
        /** settings key -> encoded value, see [encode] */
        var overrides: JSONObject,
    ) {
        fun toJson() = JSONObject().apply {
            put("id", id)
            put("name", name)
            put("apps", JSONArray(apps))
            put("subtype", subtype ?: "")
            put("values", overrides)
        }
    }

    // ---- storage ----

    @JvmStatic
    fun load(prefs: SharedPreferences): List<Profile> {
        val text = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        return try {
            val array = JSONArray(text)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                val apps = o.optJSONArray("apps")
                Profile(
                    o.getString("id"),
                    o.getString("name"),
                    if (apps == null) emptyList() else (0 until apps.length()).map { apps.getString(it) },
                    o.optString("subtype").ifEmpty { null },
                    o.optJSONObject("values") ?: JSONObject(),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun save(prefs: SharedPreferences, profiles: List<Profile>) {
        val array = JSONArray()
        profiles.forEach { array.put(it.toJson()) }
        prefs.edit { putString(KEY_PROFILES, array.toString()) }
    }

    /** Changes one profile and stores all of them. */
    fun update(prefs: SharedPreferences, id: String, change: (Profile) -> Unit) {
        val profiles = load(prefs)
        profiles.firstOrNull { it.id == id }?.let(change) ?: return
        save(prefs, profiles)
    }

    fun create(prefs: SharedPreferences, name: String): String {
        val id = System.currentTimeMillis().toString(36) + (Math.random() * 1000).toInt().toString(36)
        save(prefs, load(prefs) + Profile(id, name, emptyList(), null, JSONObject()))
        return id
    }

    fun delete(context: Context, id: String) {
        val prefs = context.prefs()
        if (prefs.getString(KEY_EDIT, null) == id) endEdit(context, false)
        if (prefs.getString(KEY_ACTIVE, null) == id) revertActive(context)
        save(prefs, load(prefs).filter { it.id != id })
    }

    /** Sets the apps of a profile. An app belongs to one profile only, so it is removed from the others. */
    fun setApps(prefs: SharedPreferences, id: String, apps: List<String>) {
        val profiles = load(prefs)
        for (profile in profiles) {
            if (profile.id == id) profile.apps = apps
            else profile.apps = profile.apps.filter { app -> app !in apps }
        }
        save(prefs, profiles)
        // the active profile may not apply to the current app any more, it is checked again when typing starts
    }

    fun changedCount(profile: Profile) = profile.overrides.length()

    // ---- using profiles while typing ----

    /** The subtype (in saved settings format) that is assigned to [packageName], or null. */
    @JvmStatic
    fun subtypeFor(prefs: SharedPreferences, packageName: String?): String? {
        if (packageName.isNullOrEmpty() || prefs.getString(KEY_EDIT, null) != null) return null
        return load(prefs).firstOrNull { packageName in it.apps }?.subtype
    }

    /** Call when typing starts in an app. Switches to the profile of that app, or back to the normal settings. */
    @JvmStatic
    fun onAppStarted(context: Context, packageName: String?) {
        val prefs = context.prefs()
        if (prefs.getString(KEY_EDIT, null) != null) return // editing: keep what is shown in settings
        val pkg = packageName.orEmpty()
        val target = if (pkg.isEmpty() || pkg == context.packageName) null
            else load(prefs).firstOrNull { pkg in it.apps && it.overrides.length() > 0 }
        val activeId = prefs.getString(KEY_ACTIVE, null)
        if (target?.id == activeId) return
        revertActive(context)
        if (target != null) apply(prefs, target)
        reload()
    }

    /** Puts the normal settings back if a profile is active. */
    @JvmStatic
    fun revertActive(context: Context): Boolean {
        val prefs = context.prefs()
        val activeId = prefs.getString(KEY_ACTIVE, null)
        val savedText = prefs.getString(KEY_SAVED, null)
        if (activeId == null && savedText == null) return false
        val saved = parse(savedText)
        prefs.edit {
            for (key in saved.keys()) write(this, key, saved.getJSONObject(key))
            remove(KEY_SAVED)
            remove(KEY_ACTIVE)
        }
        reload()
        return true
    }

    private fun apply(prefs: SharedPreferences, profile: Profile) {
        val current = prefs.all
        val saved = JSONObject()
        prefs.edit {
            for (key in profile.overrides.keys()) {
                if (excluded(key)) continue
                saved.put(key, encode(current[key]))
                write(this, key, profile.overrides.getJSONObject(key))
            }
            putString(KEY_SAVED, saved.toString())
            putString(KEY_ACTIVE, profile.id)
        }
    }

    // ---- editing ----

    /** The profile that is currently being edited, or null. */
    fun editing(prefs: SharedPreferences): Profile? {
        val id = prefs.getString(KEY_EDIT, null) ?: return null
        return load(prefs).firstOrNull { it.id == id }
    }

    /** Applies the profile on top of the normal settings and remembers the normal settings. */
    fun beginEdit(context: Context, id: String): Boolean {
        val prefs = context.prefs()
        if (prefs.getString(KEY_EDIT, null) != null) return false
        val profile = load(prefs).firstOrNull { it.id == id } ?: return false
        revertActive(context)
        val base = JSONObject()
        for ((key, value) in prefs.all) if (!excluded(key)) base.put(key, encode(value))
        prefs.edit {
            putString(KEY_EDIT_BASE, base.toString())
            putString(KEY_EDIT, id)
            for (key in profile.overrides.keys()) if (!excluded(key)) write(this, key, profile.overrides.getJSONObject(key))
        }
        reload()
        return true
    }

    /** Ends editing. With [saveChanges] everything that differs from the normal settings becomes the profile. */
    fun endEdit(context: Context, saveChanges: Boolean) {
        val prefs = context.prefs()
        val id = prefs.getString(KEY_EDIT, null) ?: return
        val base = parse(prefs.getString(KEY_EDIT_BASE, null))
        val current = prefs.all
        val diff = JSONObject()
        if (saveChanges) {
            for ((key, value) in current) {
                if (excluded(key)) continue
                val encoded = encode(value)
                if (base.optJSONObject(key)?.toString() != encoded.toString()) diff.put(key, encoded)
            }
            for (key in base.keys())
                if (!current.containsKey(key)) diff.put(key, JSONObject().put("t", "x"))
        }
        prefs.edit {
            for (key in current.keys) if (!excluded(key) && !base.has(key)) remove(key)
            for (key in base.keys()) write(this, key, base.getJSONObject(key))
            remove(KEY_EDIT)
            remove(KEY_EDIT_BASE)
        }
        if (saveChanges) update(prefs, id) { it.overrides = diff }
        reload()
    }

    // ---- helpers ----

    /** settings that are not part of profiles: state, per-app data and profile data itself */
    private fun excluded(key: String) = key.startsWith(PREFIX)
        || key.startsWith(Settings.PREF_SAVED_APP_SUBTYPE_PREFIX)
        || key.startsWith("floating_pos")
        || key == Settings.PREF_SELECTED_SUBTYPE
        || key == Settings.PREF_ENABLED_SUBTYPES
        || key == Settings.PREF_ADDITIONAL_SUBTYPES
        || key == Settings.PREF_LAST_SHOWN_EMOJI_CATEGORY_ID
        || key == Settings.PREF_LAST_SHOWN_EMOJI_CATEGORY_PAGE_ID
        || key == Settings.PREF_RECENT_EMOJIS
        || key == Settings.PREF_DONT_SHOW_MISSING_DICTIONARY_DIALOG
        || key == Settings.PREF_VERSION_CODE

    private fun parse(text: String?): JSONObject = try {
        JSONObject(text ?: "{}")
    } catch (e: Exception) {
        JSONObject()
    }

    private fun encode(value: Any?): JSONObject {
        val o = JSONObject()
        when (value) {
            is Boolean -> { o.put("t", "b"); o.put("v", value) }
            is Int -> { o.put("t", "i"); o.put("v", value) }
            is Long -> { o.put("t", "l"); o.put("v", value) }
            is Float -> { o.put("t", "f"); o.put("v", value.toDouble()) }
            is String -> { o.put("t", "s"); o.put("v", value) }
            is Set<*> -> { o.put("t", "a"); o.put("v", JSONArray(value.map { it.toString() })) }
            else -> o.put("t", "x") // not set
        }
        return o
    }

    private fun write(editor: SharedPreferences.Editor, key: String, o: JSONObject) {
        when (o.optString("t")) {
            "b" -> editor.putBoolean(key, o.getBoolean("v"))
            "i" -> editor.putInt(key, o.getInt("v"))
            "l" -> editor.putLong(key, o.getLong("v"))
            "f" -> editor.putFloat(key, o.getDouble("v").toFloat())
            "s" -> editor.putString(key, o.getString("v"))
            "a" -> {
                val array = o.getJSONArray("v")
                editor.putStringSet(key, (0 until array.length()).map { array.getString(it) }.toSet())
            }
            else -> editor.remove(key)
        }
    }

    private fun reload() {
        KeyboardIconsSet.needsReload = true
        KeyboardSwitcher.getInstance().setThemeNeedsReload()
    }
}
