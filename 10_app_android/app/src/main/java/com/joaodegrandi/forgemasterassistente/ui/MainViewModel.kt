package com.joaodegrandi.forgemasterassistente.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.joaodegrandi.forgemasterassistente.model.BuildState
import com.joaodegrandi.forgemasterassistente.model.CalibrationDraft
import com.joaodegrandi.forgemasterassistente.model.CropProfile
import com.joaodegrandi.forgemasterassistente.model.SourceId
import com.joaodegrandi.forgemasterassistente.model.SourceRecord
import com.joaodegrandi.forgemasterassistente.model.UndoState
import com.joaodegrandi.forgemasterassistente.model.WeaponMode
import com.joaodegrandi.forgemasterassistente.storage.AppStorage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val build: BuildState = BuildState(),
    val calibration: CalibrationDraft = CalibrationDraft(),
    val mode: WeaponMode = WeaponMode.MELEE,
    val undo: UndoState? = null,
    val crops: List<CropProfile> = emptyList(),
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val storage = AppStorage(application)

    val uiState: StateFlow<MainUiState> = combine(
        storage.buildFlow,
        storage.calibrationFlow,
        storage.weaponModeFlow,
        storage.undoFlow,
        storage.cropProfilesFlow,
    ) { build, calibration, mode, undo, crops ->
        MainUiState(build, calibration, mode, undo, crops)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(),
    )

    fun setMode(mode: WeaponMode) = viewModelScope.launch {
        storage.saveWeaponMode(mode)
    }

    fun beginFullCalibration() = viewModelScope.launch {
        storage.saveCalibrationDraft(
            CalibrationDraft(
                selectedSources = SourceId.entries,
                currentSource = SourceId.entries.first(),
            ),
        )
    }

    fun beginPartialCalibration(id: SourceId) = viewModelScope.launch {
        storage.saveCalibrationDraft(
            CalibrationDraft(
                selectedSources = listOf(id),
                currentSource = id,
            ),
        )
    }

    fun selectCalibrationSource(id: SourceId) = viewModelScope.launch {
        val current = storage.currentCalibration()
        val selected = current.selectedSources.ifEmpty { SourceId.entries }
        storage.saveCalibrationDraft(current.copy(selectedSources = selected, currentSource = id))
    }

    fun saveManualSource(source: SourceRecord) = viewModelScope.launch {
        val current = storage.currentCalibration()
        storage.saveCalibrationSource(
            source = source,
            selectedSources = current.selectedSources.ifEmpty { SourceId.entries },
        )
    }

    fun confirmCalibration() = viewModelScope.launch {
        val draft = storage.currentCalibration()
        val selected = draft.selectedSources.ifEmpty { SourceId.entries }
        val allReady = selected.all { id -> draft.build.source(id)?.isComplete() == true }
        if (!allReady) return@launch
        var merged = storage.currentBuild()
        selected.forEach { id ->
            draft.build.source(id)?.let { merged = merged.replace(it) }
        }
        storage.saveBuild(merged)
    }

    fun discardCalibration() = viewModelScope.launch {
        storage.discardCalibration()
    }

    fun undo() = viewModelScope.launch {
        storage.undoLastReplacement()
    }

    fun saveCropProfiles(profiles: List<CropProfile>) = viewModelScope.launch {
        storage.saveCropProfiles(profiles)
    }

    fun resetCrops() = viewModelScope.launch {
        storage.resetCropProfiles()
    }
}
