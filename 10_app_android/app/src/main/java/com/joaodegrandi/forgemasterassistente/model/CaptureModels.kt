package com.joaodegrandi.forgemasterassistente.model

import kotlinx.serialization.Serializable

@Serializable
enum class PanelType {
    EQUIPMENT_DETAIL,
    FORGE_COMPARISON,
    MOUNT_DETAIL,
    PET_DETAIL,
    SKILLS,
    TOTALS,
    UNKNOWN,
}

@Serializable
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    init {
        require(left in 0f..1f && top in 0f..1f && right in 0f..1f && bottom in 0f..1f)
        require(right > left && bottom > top)
    }
}

@Serializable
data class CropProfile(
    val panelType: PanelType,
    val content: NormalizedRect,
)

object DefaultCropProfiles {
    val profiles = listOf(
        CropProfile(
            PanelType.FORGE_COMPARISON,
            NormalizedRect(0.06f, 0.38f, 0.94f, 0.79f),
        ),
        CropProfile(
            PanelType.EQUIPMENT_DETAIL,
            NormalizedRect(0.06f, 0.49f, 0.94f, 0.76f),
        ),
        CropProfile(
            PanelType.MOUNT_DETAIL,
            NormalizedRect(0.03f, 0.34f, 0.97f, 0.68f),
        ),
        CropProfile(
            PanelType.PET_DETAIL,
            NormalizedRect(0.03f, 0.34f, 0.97f, 0.69f),
        ),
        CropProfile(
            PanelType.SKILLS,
            NormalizedRect(0.07f, 0.05f, 0.93f, 0.16f),
        ),
        CropProfile(
            PanelType.TOTALS,
            NormalizedRect(0.04f, 0.16f, 0.96f, 0.88f),
        ),
    )
}

data class OcrTextLine(
    val text: String,
    val confidence: Float,
    val centerY: Float,
)

data class OcrSourceDraft(
    val rarity: String,
    val name: String,
    val level: Int?,
    val baseDamage: NumericField?,
    val baseHealth: NumericField?,
    val subStats: List<SubStat>,
    val confidence: Float,
    val rawLines: List<String>,
) {
    fun toSourceRecord(
        id: SourceId,
        readAtEpochMillis: Long = System.currentTimeMillis(),
    ): SourceRecord {
        val expectation = id.mainStatExpectation()
        fun absentValue(field: NumericField?, expected: FieldExpectation): NumericField =
            field ?: when (expected) {
                FieldExpectation.ABSENT,
                FieldExpectation.OPTIONAL,
                -> NumericField.expectedAbsent()
                FieldExpectation.REQUIRED -> NumericField()
            }
        return SourceRecord(
            id = id,
            name = name,
            rarity = rarity,
            level = level,
            baseDamage = absentValue(baseDamage, expectation.damage),
            baseHealth = absentValue(baseHealth, expectation.health),
            subStats = subStats.take(2),
            ocrConfidence = confidence,
            readAtEpochMillis = readAtEpochMillis,
        )
    }
}

data class OcrReadResult(
    val panelType: PanelType,
    val sources: List<OcrSourceDraft>,
    val rawLines: List<OcrTextLine>,
    val confidence: Float,
    val requiresReview: Boolean,
    val message: String,
)
