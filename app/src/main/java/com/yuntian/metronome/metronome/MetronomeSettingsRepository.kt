package com.yuntian.metronome.metronome

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

interface MetronomeSettingsRepository {
    fun load(): MetronomeSettings
    fun save(settings: MetronomeSettings)
    fun loadPresets(): List<CustomPreset>
    fun savePresets(presets: List<CustomPreset>)
}

class SharedPreferencesMetronomeSettingsRepository(context: Context) :
    MetronomeSettingsRepository {

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun load(): MetronomeSettings {
        val timeSignature = TimeSignature.fromStored(
            preferences.getString(KEY_TIME_SIGNATURE, null),
        )
        val customPattern = preferences.getString(KEY_CUSTOM_PATTERN, null)
            ?.let(::decodePattern)
            .orEmpty()

        return MetronomeSettings(
            bpm = preferences.getInt(KEY_BPM, DEFAULT_BPM),
            timeSignature = timeSignature,
            subdivision = Subdivision.fromStored(preferences.getString(KEY_SUBDIVISION, null)),
            step = preferences.getInt(KEY_STEP, 1),
            accentEnabled = preferences.getBoolean(KEY_ACCENT, true),
            playbackMode = PlaybackMode.fromStored(preferences.getString(KEY_PLAYBACK_MODE, null)),
            customPattern = if (customPattern.isEmpty()) {
                defaultCustomPattern(timeSignature)
            } else {
                customPattern
            },
        ).sanitized()
    }

    override fun save(settings: MetronomeSettings) {
        val safeSettings = settings.sanitized()
        preferences.edit {
            putInt(KEY_SCHEMA_VERSION, SCHEMA_VERSION)
            putInt(KEY_BPM, safeSettings.bpm)
            putString(KEY_TIME_SIGNATURE, safeSettings.timeSignature.name)
            putString(KEY_SUBDIVISION, safeSettings.subdivision.name)
            putInt(KEY_STEP, safeSettings.step)
            putBoolean(KEY_ACCENT, safeSettings.accentEnabled)
            putString(KEY_PLAYBACK_MODE, safeSettings.playbackMode.name)
            putString(KEY_CUSTOM_PATTERN, patternToJson(safeSettings.customPattern).toString())
        }
    }

    override fun loadPresets(): List<CustomPreset> {
        val stored = preferences.getString(KEY_CUSTOM_PRESETS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(stored)
            buildList {
                for (index in 0 until array.length()) {
                    val value = array.optJSONObject(index) ?: continue
                    val id = value.optString(JSON_ID).trim()
                    val name = value.optString(JSON_NAME).trim()
                    if (id.isEmpty() || name.isEmpty()) continue
                    val signature = TimeSignature.fromStored(value.optString(JSON_TIME_SIGNATURE))
                    val beats = decodePattern(value.optJSONArray(JSON_BEATS))
                    add(
                        CustomPreset(
                            id = id,
                            name = name,
                            bpm = value.optInt(JSON_BPM, DEFAULT_BPM),
                            timeSignature = signature,
                            beats = if (beats.isEmpty()) defaultCustomPattern(signature) else beats,
                        ).sanitized(),
                    )
                }
            }.distinctBy(CustomPreset::id)
        }.getOrDefault(emptyList())
    }

    override fun savePresets(presets: List<CustomPreset>) {
        val array = JSONArray()
        presets
            .map(CustomPreset::sanitized)
            .filter { it.id.isNotBlank() && it.name.isNotBlank() }
            .forEach { preset ->
                array.put(
                    JSONObject()
                        .put(JSON_ID, preset.id)
                        .put(JSON_NAME, preset.name)
                        .put(JSON_BPM, preset.bpm)
                        .put(JSON_TIME_SIGNATURE, preset.timeSignature.name)
                        .put(JSON_BEATS, patternToJson(preset.beats)),
                )
            }
        preferences.edit {
            putInt(KEY_SCHEMA_VERSION, SCHEMA_VERSION)
            putString(KEY_CUSTOM_PRESETS, array.toString())
        }
    }

    private fun decodePattern(raw: String): List<BeatPattern> =
        runCatching { decodePattern(JSONArray(raw)) }.getOrDefault(emptyList())

    private fun decodePattern(array: JSONArray?): List<BeatPattern> {
        if (array == null) return emptyList()
        return buildList {
            for (beatIndex in 0 until array.length()) {
                val cellsJson = array.optJSONArray(beatIndex) ?: continue
                val cells = buildList {
                    for (cellIndex in 0 until cellsJson.length()) {
                        add(CellSound.fromStored(cellsJson.optString(cellIndex)))
                    }
                }
                add(BeatPattern(cells).sanitized())
            }
        }
    }

    private fun patternToJson(pattern: List<BeatPattern>): JSONArray = JSONArray().apply {
        pattern.forEach { beat ->
            put(JSONArray().apply { beat.cells.forEach { put(it.name) } })
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "metronome_settings"
        const val SCHEMA_VERSION = 2
        const val KEY_SCHEMA_VERSION = "schema_version"
        const val KEY_BPM = "bpm"
        const val KEY_TIME_SIGNATURE = "time_signature"
        const val KEY_SUBDIVISION = "subdivision"
        const val KEY_STEP = "step"
        const val KEY_ACCENT = "accent"
        const val KEY_PLAYBACK_MODE = "playback_mode"
        const val KEY_CUSTOM_PATTERN = "custom_pattern"
        const val KEY_CUSTOM_PRESETS = "custom_presets"
        const val JSON_ID = "id"
        const val JSON_NAME = "name"
        const val JSON_BPM = "bpm"
        const val JSON_TIME_SIGNATURE = "time_signature"
        const val JSON_BEATS = "beats"
    }
}
