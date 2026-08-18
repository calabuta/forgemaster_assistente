package com.joaodegrandi.forgemasterassistente.scoring

import com.joaodegrandi.forgemasterassistente.model.BuildState
import com.joaodegrandi.forgemasterassistente.model.ComparisonResult
import com.joaodegrandi.forgemasterassistente.model.NumericField
import com.joaodegrandi.forgemasterassistente.model.PetComparison
import com.joaodegrandi.forgemasterassistente.model.Recommendation
import com.joaodegrandi.forgemasterassistente.model.SourceId
import com.joaodegrandi.forgemasterassistente.model.SourceRecord
import com.joaodegrandi.forgemasterassistente.model.StatChange
import com.joaodegrandi.forgemasterassistente.model.StatType
import com.joaodegrandi.forgemasterassistente.model.WeaponMode
import com.joaodegrandi.forgemasterassistente.scoring.DamageCalculator.totalPercent
import java.math.BigDecimal
import java.math.MathContext

object ReplacementEvaluator {
    private val mathContext = MathContext.DECIMAL128
    private val petIds = listOf(SourceId.PET_1, SourceId.PET_2, SourceId.PET_3)
    private val petLevelFactor = BigDecimal("1.01")
    private val mountLevelFactor = BigDecimal("1.006")

    fun compare(
        build: BuildState,
        sourceId: SourceId,
        candidate: SourceRecord,
        mode: WeaponMode,
        minConfidence: Float = 0.85f,
    ): ComparisonResult {
        val currentSource = build.source(sourceId)
            ?: return inconclusive("Fonte atual não calibrada.", sourceId)
        val assignedCandidate = candidate.copy(id = sourceId)
        if (!currentSource.isComplete(minConfidence) || !assignedCandidate.isComplete(minConfidence)) {
            return inconclusive("Leitura incompleta ou insegura; revise os valores.", sourceId)
        }

        val normalized = normalizePair(currentSource, assignedCandidate)
            ?: return inconclusive(
                "Nível atual ou do candidato não reconhecido; revise os valores.",
                sourceId,
            )
        val comparisonBuild = build.replace(normalized.current)
        val nextBuild = comparisonBuild.replace(normalized.candidate)
        val before = DamageCalculator.calculate(comparisonBuild, mode).totalDamage
        val after = DamageCalculator.calculate(nextBuild, mode).totalDamage
        if (before.compareTo(BigDecimal.ZERO) <= 0) {
            return inconclusive("A build atual não possui Base Damage válido.", sourceId)
        }

        val delta = after.divide(before, mathContext).subtract(BigDecimal.ONE, mathContext)
        val comparison = after.compareTo(before)
        val recommendation = when {
            comparison > 0 -> Recommendation.EQUIPAR
            comparison < 0 -> Recommendation.VENDER
            else -> Recommendation.INCONCLUSIVO
        }
        val reason = when (recommendation) {
            Recommendation.EQUIPAR -> "O dano total aumenta."
            Recommendation.VENDER -> "O dano total diminui."
            Recommendation.INCONCLUSIVO -> "dano equivalente"
        }
        val normalizedReason = normalized.level?.let { level ->
            "Comparação normalizada no Lv. $level. $reason"
        } ?: reason
        return ComparisonResult(
            recommendation = recommendation,
            reason = normalizedReason,
            damageBefore = before,
            damageAfter = after,
            delta = delta,
            changes = changedStats(comparisonBuild, nextBuild, mode),
            sourceId = sourceId,
            normalizedLevel = normalized.level,
        )
    }

    fun normalizedCandidateForReplacement(
        build: BuildState,
        sourceId: SourceId,
        candidate: SourceRecord,
    ): SourceRecord? {
        val assignedCandidate = candidate.copy(id = sourceId)
        val currentSource = build.source(sourceId) ?: return null
        return normalizePair(currentSource, assignedCandidate)?.candidate
    }

    fun comparePet(
        build: BuildState,
        candidate: SourceRecord,
        mode: WeaponMode,
        minConfidence: Float = 0.85f,
    ): PetComparison {
        val scenarios = petIds.map { petId ->
            compare(build, petId, candidate.copy(id = petId), mode, minConfidence)
        }
        val best = scenarios
            .filter { it.delta != null }
            .maxByOrNull { it.delta!! }
        return PetComparison(scenarios = scenarios, best = best)
    }

    private fun normalizePair(
        current: SourceRecord,
        candidate: SourceRecord,
    ): NormalizedPair? {
        val factor = levelFactor(current.id)
            ?: return NormalizedPair(current, candidate, null)
        val currentLevel = current.level?.takeIf { it >= 1 } ?: return null
        val candidateLevel = candidate.level?.takeIf { it >= 1 } ?: return null
        val targetLevel = maxOf(currentLevel, candidateLevel)
        return NormalizedPair(
            current = current.projectToLevel(currentLevel, targetLevel, factor),
            candidate = candidate.projectToLevel(candidateLevel, targetLevel, factor),
            level = targetLevel,
        )
    }

    private fun levelFactor(sourceId: SourceId): BigDecimal? = when (sourceId) {
        SourceId.MOUNT -> mountLevelFactor
        SourceId.PET_1,
        SourceId.PET_2,
        SourceId.PET_3,
        -> petLevelFactor
        else -> null
    }

    private fun SourceRecord.projectToLevel(
        sourceLevel: Int,
        targetLevel: Int,
        factor: BigDecimal,
    ): SourceRecord {
        val multiplier = factor.pow(targetLevel - sourceLevel, mathContext)
        fun project(field: NumericField) =
            field.decimalOrNull()?.let { value ->
                field.copy(
                    value = value.multiply(multiplier, mathContext)
                        .stripTrailingZeros()
                        .toPlainString(),
                )
            } ?: field
        return copy(
            level = targetLevel,
            baseDamage = project(baseDamage),
            baseHealth = project(baseHealth),
        )
    }

    private fun changedStats(
        before: BuildState,
        after: BuildState,
        mode: WeaponMode,
    ): List<StatChange> {
        val changes = mutableListOf<StatChange>()
        val baseDamageBefore = before.sources.mapNotNull { it.baseDamage.decimalOrNull() }
            .fold(BigDecimal.ZERO, BigDecimal::add)
        val baseDamageAfter = after.sources.mapNotNull { it.baseDamage.decimalOrNull() }
            .fold(BigDecimal.ZERO, BigDecimal::add)
        val baseHealthBefore = before.sources.mapNotNull { it.baseHealth.decimalOrNull() }
            .fold(BigDecimal.ZERO, BigDecimal::add)
        val baseHealthAfter = after.sources.mapNotNull { it.baseHealth.decimalOrNull() }
            .fold(BigDecimal.ZERO, BigDecimal::add)
        if (baseDamageBefore.compareTo(baseDamageAfter) != 0) {
            changes += StatChange("Base Damage", baseDamageBefore, baseDamageAfter, true)
        }
        if (baseHealthBefore.compareTo(baseHealthAfter) != 0) {
            changes += StatChange("Base Health", baseHealthBefore, baseHealthAfter, false)
        }

        StatType.entries.forEach { type ->
            val valueBefore = before.totalPercent(type)
            val valueAfter = after.totalPercent(type)
            if (valueBefore.compareTo(valueAfter) != 0) {
                changes += StatChange(
                    label = type.displayName(),
                    before = valueBefore,
                    after = valueAfter,
                    affectsDecision = type.affects(mode),
                )
            }
        }
        return changes
    }

    private fun inconclusive(reason: String, sourceId: SourceId) = ComparisonResult(
        recommendation = Recommendation.INCONCLUSIVO,
        reason = reason,
        damageBefore = BigDecimal.ZERO,
        damageAfter = BigDecimal.ZERO,
        delta = null,
        sourceId = sourceId,
    )

    private fun StatType.affects(mode: WeaponMode): Boolean = when (this) {
        StatType.DAMAGE,
        StatType.ATTACK_SPEED,
        StatType.DOUBLE_CHANCE,
        StatType.CRITICAL_CHANCE,
        StatType.CRITICAL_DAMAGE,
        -> true
        StatType.MELEE_DAMAGE -> mode == WeaponMode.MELEE
        StatType.RANGED_DAMAGE -> mode == WeaponMode.RANGED
        else -> false
    }

    private fun StatType.displayName(): String = name
        .lowercase()
        .split('_')
        .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

    private data class NormalizedPair(
        val current: SourceRecord,
        val candidate: SourceRecord,
        val level: Int?,
    )
}
