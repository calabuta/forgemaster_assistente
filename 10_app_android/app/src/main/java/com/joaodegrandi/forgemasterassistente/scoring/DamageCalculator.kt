package com.joaodegrandi.forgemasterassistente.scoring

import com.joaodegrandi.forgemasterassistente.model.BuildState
import com.joaodegrandi.forgemasterassistente.model.DamageResult
import com.joaodegrandi.forgemasterassistente.model.StatType
import com.joaodegrandi.forgemasterassistente.model.WeaponMode
import java.math.BigDecimal
import java.math.MathContext

object DamageCalculator {
    private val mathContext = MathContext.DECIMAL128
    private val oneHundred = BigDecimal("100")
    private val baseCriticalDamage = BigDecimal("0.20")

    fun calculate(build: BuildState, mode: WeaponMode): DamageResult {
        val baseDamage = build.sources
            .mapNotNull { it.baseDamage.decimalOrNull() }
            .fold(BigDecimal.ZERO, BigDecimal::add)

        val damage = build.totalPercent(StatType.DAMAGE).asDecimalPercent()
        val attackSpeed = build.totalPercent(StatType.ATTACK_SPEED).asDecimalPercent()
        val doubleChance = build.totalPercent(StatType.DOUBLE_CHANCE)
            .asDecimalPercent()
            .min(BigDecimal.ONE)
        val criticalChance = build.totalPercent(StatType.CRITICAL_CHANCE)
            .asDecimalPercent()
            .min(BigDecimal.ONE)
        val criticalDamage = baseCriticalDamage.add(
            build.totalPercent(StatType.CRITICAL_DAMAGE).asDecimalPercent(),
            mathContext,
        )
        val modeDamage = build.totalPercent(
            if (mode == WeaponMode.MELEE) StatType.MELEE_DAMAGE else StatType.RANGED_DAMAGE,
        ).asDecimalPercent()

        val multiplier = listOf(
            BigDecimal.ONE.add(damage),
            BigDecimal.ONE.add(attackSpeed),
            BigDecimal.ONE.add(doubleChance),
            BigDecimal.ONE.add(criticalChance.multiply(criticalDamage, mathContext)),
            BigDecimal.ONE.add(modeDamage),
        ).fold(BigDecimal.ONE) { total, factor -> total.multiply(factor, mathContext) }

        return DamageResult(
            totalDamage = baseDamage.multiply(multiplier, mathContext),
            multiplier = multiplier,
        )
    }

    fun BuildState.totalPercent(type: StatType): BigDecimal = sources
        .flatMap { it.subStats }
        .filter { it.type == type }
        .map { it.decimalPercent() }
        .fold(BigDecimal.ZERO, BigDecimal::add)

    private fun BigDecimal.asDecimalPercent(): BigDecimal =
        divide(oneHundred, mathContext)
}
