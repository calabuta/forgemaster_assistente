package com.joaodegrandi.forgemasterassistente.model

import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
enum class SourceId {
    HEAD,
    TORSO,
    GLOVE,
    NECKLACE,
    RING,
    WEAPON,
    BOOT,
    BELT,
    MOUNT,
    PET_1,
    PET_2,
    PET_3,
    SKILLS,
}

@Serializable
enum class WeaponMode { MELEE, RANGED }

@Serializable
enum class StatType {
    CRITICAL_CHANCE,
    CRITICAL_DAMAGE,
    DOUBLE_CHANCE,
    DAMAGE,
    MELEE_DAMAGE,
    RANGED_DAMAGE,
    ATTACK_SPEED,
    LIFESTEAL,
    BLOCK,
    HEALTH_REGEN,
    SKILL_DAMAGE,
    SKILL_COOLDOWN,
    HEALTH,
}

@Serializable
enum class ValueState { RECOGNIZED, EXPECTED_ABSENT, NEEDS_REVIEW }

@Serializable
data class NumericField(
    val state: ValueState = ValueState.NEEDS_REVIEW,
    val value: String? = null,
    val rawText: String = "",
    val confidence: Float = 0f,
) {
    fun decimalOrNull(): BigDecimal? =
        value?.toBigDecimalOrNull()?.takeIf { state == ValueState.RECOGNIZED }

    companion object {
        fun recognized(
            value: BigDecimal,
            rawText: String = value.toPlainString(),
            confidence: Float = 1f,
        ) = NumericField(
            state = ValueState.RECOGNIZED,
            value = value.stripTrailingZeros().toPlainString(),
            rawText = rawText,
            confidence = confidence,
        )

        fun expectedAbsent() = NumericField(state = ValueState.EXPECTED_ABSENT)
    }
}

@Serializable
data class SubStat(
    val type: StatType,
    val percentValue: String,
    val rawText: String,
    val confidence: Float,
) {
    fun decimalPercent(): BigDecimal = percentValue.toBigDecimal()
}

@Serializable
data class SourceRecord(
    val id: SourceId,
    val name: String = "",
    val rarity: String = "",
    val level: Int? = null,
    val baseDamage: NumericField = NumericField(),
    val baseHealth: NumericField = NumericField(),
    val subStats: List<SubStat> = emptyList(),
    val ocrConfidence: Float = 0f,
    val readAtEpochMillis: Long = 0L,
) {
    init {
        require(subStats.size <= 2 || id == SourceId.SKILLS) {
            "Cada fonte possui no máximo dois substats; Skills não possui substats."
        }
        require(id != SourceId.SKILLS || subStats.isEmpty()) {
            "Skills não pode possuir substats."
        }
    }

    fun isComplete(minConfidence: Float = 0.85f): Boolean {
        val expectation = id.mainStatExpectation()
        val damageOk = baseDamage.matches(expectation.damage, minConfidence)
        val healthOk = baseHealth.matches(expectation.health, minConfidence)
        val subStatsOk = subStats.all { it.confidence >= minConfidence }
        return damageOk && healthOk && subStatsOk && ocrConfidence >= minConfidence
    }

    private fun NumericField.matches(
        expectation: FieldExpectation,
        minConfidence: Float,
    ): Boolean = when (expectation) {
        FieldExpectation.REQUIRED ->
            state == ValueState.RECOGNIZED && decimalOrNull() != null && confidence >= minConfidence
        FieldExpectation.ABSENT -> state == ValueState.EXPECTED_ABSENT
        FieldExpectation.OPTIONAL ->
            state == ValueState.EXPECTED_ABSENT ||
                (state == ValueState.RECOGNIZED && decimalOrNull() != null && confidence >= minConfidence)
    }
}

@Serializable
data class BuildState(
    val sources: List<SourceRecord> = emptyList(),
    val updatedAtEpochMillis: Long = 0L,
) {
    fun source(id: SourceId): SourceRecord? = sources.firstOrNull { it.id == id }

    fun replace(source: SourceRecord): BuildState = copy(
        sources = sources.filterNot { it.id == source.id } + source,
        updatedAtEpochMillis = source.readAtEpochMillis,
    )

    fun isComplete(minConfidence: Float = 0.85f): Boolean =
        SourceId.entries.all { id -> source(id)?.isComplete(minConfidence) == true }
}

@Serializable
data class CalibrationDraft(
    val build: BuildState = BuildState(),
    val selectedSources: List<SourceId> = SourceId.entries,
    val currentSource: SourceId? = null,
)

@Serializable
data class UndoState(
    val previousSource: SourceRecord,
)

enum class Recommendation { EQUIPAR, VENDER, INCONCLUSIVO }

data class StatChange(
    val label: String,
    val before: BigDecimal,
    val after: BigDecimal,
    val affectsDecision: Boolean,
)

data class DamageResult(
    val totalDamage: BigDecimal,
    val multiplier: BigDecimal,
)

data class ComparisonResult(
    val recommendation: Recommendation,
    val reason: String,
    val damageBefore: BigDecimal,
    val damageAfter: BigDecimal,
    val delta: BigDecimal?,
    val changes: List<StatChange> = emptyList(),
    val sourceId: SourceId? = null,
)

data class PetComparison(
    val scenarios: List<ComparisonResult>,
    val best: ComparisonResult?,
)

enum class FieldExpectation { REQUIRED, ABSENT, OPTIONAL }

data class MainStatExpectation(
    val damage: FieldExpectation,
    val health: FieldExpectation,
)

fun SourceId.mainStatExpectation(): MainStatExpectation = when (this) {
    SourceId.HEAD,
    SourceId.TORSO,
    SourceId.BOOT,
    SourceId.BELT,
    -> MainStatExpectation(FieldExpectation.ABSENT, FieldExpectation.REQUIRED)

    SourceId.GLOVE,
    SourceId.NECKLACE,
    SourceId.RING,
    -> MainStatExpectation(FieldExpectation.REQUIRED, FieldExpectation.ABSENT)

    SourceId.WEAPON ->
        MainStatExpectation(FieldExpectation.REQUIRED, FieldExpectation.OPTIONAL)

    SourceId.MOUNT,
    SourceId.PET_1,
    SourceId.PET_2,
    SourceId.PET_3,
    SourceId.SKILLS,
    -> MainStatExpectation(FieldExpectation.REQUIRED, FieldExpectation.REQUIRED)
}
