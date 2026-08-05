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
    fun loadArrangementDraft(): List<ArrangementChange>
    fun saveArrangementDraft(changes: List<ArrangementChange>)
    fun loadArrangementPresets(): List<ArrangementPreset>
    fun saveArrangementPresets(presets: List<ArrangementPreset>)
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

    override fun loadArrangementDraft(): List<ArrangementChange> {
        val stored = preferences.getString(KEY_ARRANGEMENT_DRAFT, null) ?: return emptyList()
        return runCatching { decodeArrangement(JSONArray(stored)) }.getOrDefault(emptyList())
    }

    override fun saveArrangementDraft(changes: List<ArrangementChange>) {
        preferences.edit {
            putInt(KEY_SCHEMA_VERSION, SCHEMA_VERSION)
            putString(
                KEY_ARRANGEMENT_DRAFT,
                arrangementToJson(sanitizeArrangementChanges(changes)).toString(),
            )
        }
    }

    override fun loadArrangementPresets(): List<ArrangementPreset> {
        val stored = preferences.getString(KEY_ARRANGEMENT_PRESETS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(stored)
            buildList {
                for (index in 0 until array.length()) {
                    val value = array.optJSONObject(index) ?: continue
                    val id = value.optString(JSON_ID).trim()
                    val name = value.optString(JSON_NAME).trim()
                    if (id.isEmpty() || name.isEmpty()) continue
                    val changes = decodeArrangement(value.optJSONArray(JSON_CHANGES))
                    if (changes.isEmpty()) continue
                    add(ArrangementPreset(id, name, changes).sanitized())
                }
            }.distinctBy(ArrangementPreset::id)
        }.getOrDefault(emptyList())
    }

    override fun saveArrangementPresets(presets: List<ArrangementPreset>) {
        val array = JSONArray()
        presets
            .map(ArrangementPreset::sanitized)
            .filter { it.id.isNotBlank() && it.name.isNotBlank() && it.changes.isNotEmpty() }
            .forEach { preset ->
                array.put(
                    JSONObject()
                        .put(JSON_ID, preset.id)
                        .put(JSON_NAME, preset.name)
                        .put(JSON_CHANGES, arrangementToJson(preset.changes)),
                )
            }
        preferences.edit {
            putInt(KEY_SCHEMA_VERSION, SCHEMA_VERSION)
            putString(KEY_ARRANGEMENT_PRESETS, array.toString())
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

    private fun decodeArrangement(array: JSONArray?): List<ArrangementChange> {
        if (array == null) return emptyList()
        val changes = buildList {
            for (index in 0 until array.length()) {
                val value = array.optJSONObject(index) ?: continue
                val meter = ArrangementMeter(
                    numerator = value.optInt(JSON_NUMERATOR, 4),
                    denominator = value.optInt(JSON_DENOMINATOR, 4),
                ).sanitized()
                val beats = decodePattern(value.optJSONArray(JSON_BEATS))
                add(
                    ArrangementChange(
                        startMeasure = value.optInt(JSON_START_MEASURE, 1),
                        bpm = value.optInt(JSON_BPM, DEFAULT_BPM),
                        meter = meter,
                        beats = if (beats.isEmpty()) defaultArrangementPattern(meter) else beats,
                    ).sanitized(),
                )
            }
        }
        return sanitizeArrangementChanges(changes)
    }

    private fun arrangementToJson(changes: List<ArrangementChange>): JSONArray = JSONArray().apply {
        changes.forEach { rawChange ->
            val change = rawChange.sanitized()
            put(
                JSONObject()
                    .put(JSON_START_MEASURE, change.startMeasure)
                    .put(JSON_BPM, change.bpm)
                    .put(JSON_NUMERATOR, change.meter.numerator)
                    .put(JSON_DENOMINATOR, change.meter.denominator)
                    .put(JSON_BEATS, patternToJson(change.beats)),
            )
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "metronome_settings"
        const val SCHEMA_VERSION = 3
        const val KEY_SCHEMA_VERSION = "schema_version"
        const val KEY_BPM = "bpm"
        const val KEY_TIME_SIGNATURE = "time_signature"
        const val KEY_SUBDIVISION = "subdivision"
        const val KEY_STEP = "step"
        const val KEY_ACCENT = "accent"
        const val KEY_PLAYBACK_MODE = "playback_mode"
        const val KEY_CUSTOM_PATTERN = "custom_pattern"
        const val KEY_CUSTOM_PRESETS = "custom_presets"
        const val KEY_ARRANGEMENT_DRAFT = "arrangement_draft"
        const val KEY_ARRANGEMENT_PRESETS = "arrangement_presets"
        const val JSON_ID = "id"
        const val JSON_NAME = "name"
        const val JSON_BPM = "bpm"
        const val JSON_TIME_SIGNATURE = "time_signature"
        const val JSON_BEATS = "beats"
        const val JSON_CHANGES = "changes"
        const val JSON_START_MEASURE = "start_measure"
        const val JSON_NUMERATOR = "numerator"
        const val JSON_DENOMINATOR = "denominator"
    }
}
