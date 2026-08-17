package com.joaodegrandi.forgemasterassistente.storage

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.joaodegrandi.forgemasterassistente.model.BuildState
import com.joaodegrandi.forgemasterassistente.model.CalibrationDraft
import com.joaodegrandi.forgemasterassistente.model.CropProfile
import com.joaodegrandi.forgemasterassistente.model.DefaultCropProfiles
import com.joaodegrandi.forgemasterassistente.model.SourceId
import com.joaodegrandi.forgemasterassistente.model.SourceRecord
import com.joaodegrandi.forgemasterassistente.model.UndoState
import com.joaodegrandi.forgemasterassistente.model.WeaponMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.forgeMasterDataStore by preferencesDataStore(name = "forgemaster_state")

class AppStorage(private val context: Context) {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    val buildFlow: Flow<BuildState> = valueFlow(Keys.BUILD, BuildState.serializer(), BuildState())
    val calibrationFlow: Flow<CalibrationDraft> = valueFlow(
        Keys.CALIBRATION,
        CalibrationDraft.serializer(),
        CalibrationDraft(),
    )
    val weaponModeFlow: Flow<WeaponMode> = context.forgeMasterDataStore.data.map { preferences ->
        preferences[Keys.WEAPON_MODE]?.let { encoded ->
            runCatching { json.decodeFromString(WeaponMode.serializer(), encoded) }
                .recoverCatching { WeaponMode.valueOf(encoded) }
                .getOrNull()
        } ?: WeaponMode.MELEE
    }
    val undoFlow: Flow<UndoState?> = nullableValueFlow(Keys.UNDO, UndoState.serializer())
    val cropProfilesFlow: Flow<List<CropProfile>> = context.forgeMasterDataStore.data.map { preferences ->
        preferences[Keys.CROPS]?.let { encoded ->
            runCatching {
                json.decodeFromString(ListSerializer(CropProfile.serializer()), encoded)
            }.getOrNull()
        } ?: DefaultCropProfiles.profiles
    }

    suspend fun saveWeaponMode(mode: WeaponMode) {
        context.forgeMasterDataStore.edit {
            it[Keys.WEAPON_MODE] = json.encodeToString(WeaponMode.serializer(), mode)
        }
    }

    suspend fun saveBuild(build: BuildState, clearCalibration: Boolean = true) {
        context.forgeMasterDataStore.edit { preferences ->
            preferences[Keys.BUILD] = json.encodeToString(BuildState.serializer(), build)
            if (clearCalibration) {
                preferences.remove(Keys.CALIBRATION)
            }
        }
    }

    suspend fun saveCalibrationSource(source: SourceRecord, selectedSources: List<SourceId>) {
        context.forgeMasterDataStore.edit { preferences ->
            val current = preferences.decodeOrDefault(
                Keys.CALIBRATION,
                CalibrationDraft.serializer(),
                CalibrationDraft(selectedSources = selectedSources),
            )
            val updated = current.copy(
                build = current.build.replace(source),
                selectedSources = selectedSources,
                currentSource = selectedSources.firstOrNull { id ->
                    current.build.replace(source).source(id) == null
                },
            )
            preferences[Keys.CALIBRATION] = json.encodeToString(
                CalibrationDraft.serializer(),
                updated,
            )
        }
    }

    suspend fun saveCalibrationDraft(draft: CalibrationDraft) {
        context.forgeMasterDataStore.edit {
            it[Keys.CALIBRATION] = json.encodeToString(CalibrationDraft.serializer(), draft)
        }
    }

    suspend fun discardCalibration() {
        context.forgeMasterDataStore.edit { it.remove(Keys.CALIBRATION) }
    }

    suspend fun confirmReplacement(sourceId: SourceId, candidate: SourceRecord): Boolean {
        var changed = false
        context.forgeMasterDataStore.edit { preferences ->
            val build = preferences.decodeOrDefault(Keys.BUILD, BuildState.serializer(), BuildState())
            val previous = build.source(sourceId) ?: return@edit
            val replacement = candidate.copy(id = sourceId)
            val next = build.replace(replacement)
            preferences[Keys.BUILD] = json.encodeToString(BuildState.serializer(), next)
            preferences[Keys.UNDO] = json.encodeToString(
                UndoState.serializer(),
                UndoState(previous),
            )
            changed = true
        }
        return changed
    }

    suspend fun undoLastReplacement(): Boolean {
        var changed = false
        context.forgeMasterDataStore.edit { preferences ->
            val undo = preferences.decodeOrNull(Keys.UNDO, UndoState.serializer()) ?: return@edit
            val build = preferences.decodeOrDefault(Keys.BUILD, BuildState.serializer(), BuildState())
            preferences[Keys.BUILD] = json.encodeToString(
                BuildState.serializer(),
                build.replace(undo.previousSource),
            )
            preferences.remove(Keys.UNDO)
            changed = true
        }
        return changed
    }

    suspend fun saveCropProfiles(profiles: List<CropProfile>) {
        context.forgeMasterDataStore.edit { preferences ->
            preferences[Keys.CROPS] = json.encodeToString(
                ListSerializer(CropProfile.serializer()),
                profiles,
            )
        }
    }

    suspend fun resetCropProfiles() {
        context.forgeMasterDataStore.edit { it.remove(Keys.CROPS) }
    }

    suspend fun currentBuild(): BuildState = buildFlow.first()
    suspend fun currentMode(): WeaponMode = weaponModeFlow.first()
    suspend fun currentCalibration(): CalibrationDraft = calibrationFlow.first()
    suspend fun currentCropProfiles(): List<CropProfile> = cropProfilesFlow.first()

    private fun <T> valueFlow(
        key: Preferences.Key<String>,
        serializer: kotlinx.serialization.KSerializer<T>,
        default: T,
    ): Flow<T> = context.forgeMasterDataStore.data.map { preferences ->
        preferences.decodeOrDefault(key, serializer, default)
    }

    private fun <T> nullableValueFlow(
        key: Preferences.Key<String>,
        serializer: kotlinx.serialization.KSerializer<T>,
    ): Flow<T?> = context.forgeMasterDataStore.data.map { preferences ->
        preferences.decodeOrNull(key, serializer)
    }

    private fun <T> Preferences.decodeOrDefault(
        key: Preferences.Key<String>,
        serializer: kotlinx.serialization.KSerializer<T>,
        default: T,
    ): T = decodeOrNull(key, serializer) ?: default

    private fun <T> Preferences.decodeOrNull(
        key: Preferences.Key<String>,
        serializer: kotlinx.serialization.KSerializer<T>,
    ): T? = this[key]?.let { encoded ->
        runCatching { json.decodeFromString(serializer, encoded) }.getOrNull()
    }

    private object Keys {
        val BUILD = stringPreferencesKey("active_build_json")
        val CALIBRATION = stringPreferencesKey("calibration_draft_json")
        val WEAPON_MODE = stringPreferencesKey("weapon_mode")
        val UNDO = stringPreferencesKey("undo_json")
        val CROPS = stringPreferencesKey("crop_profiles_json")
    }
}
