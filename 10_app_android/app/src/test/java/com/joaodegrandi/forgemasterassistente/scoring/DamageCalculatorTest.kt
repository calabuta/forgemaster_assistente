package com.joaodegrandi.forgemasterassistente.scoring

import com.joaodegrandi.forgemasterassistente.model.BuildState
import com.joaodegrandi.forgemasterassistente.model.NumericField
import com.joaodegrandi.forgemasterassistente.model.SourceId
import com.joaodegrandi.forgemasterassistente.model.SourceRecord
import com.joaodegrandi.forgemasterassistente.model.StatType
import com.joaodegrandi.forgemasterassistente.model.SubStat
import com.joaodegrandi.forgemasterassistente.model.WeaponMode
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class DamageCalculatorTest {
    @Test
    fun appliesOnlyCriticalAndDoubleCapsAndUsesActiveWeaponMode() {
        val build = BuildState(
            listOf(
                damageSource(
                    SourceId.GLOVE,
                    "100",
                    listOf(StatType.DAMAGE to "50", StatType.ATTACK_SPEED to "100"),
                ),
                damageSource(
                    SourceId.NECKLACE,
                    "0",
                    listOf(StatType.DOUBLE_CHANCE to "150", StatType.CRITICAL_CHANCE to "150"),
                ),
                damageSource(
                    SourceId.RING,
                    "0",
                    listOf(StatType.CRITICAL_DAMAGE to "80", StatType.MELEE_DAMAGE to "200"),
                ),
                damageSource(
                    SourceId.WEAPON,
                    "0",
                    listOf(StatType.RANGED_DAMAGE to "50"),
                ),
            ),
        )

        assertDecimal("3600", DamageCalculator.calculate(build, WeaponMode.MELEE).totalDamage)
        assertDecimal("1800", DamageCalculator.calculate(build, WeaponMode.RANGED).totalDamage)
    }

    @Test
    fun sumsBaseDamageFromEveryApplicableSource() {
        val build = BuildState(
            listOf(
                damageSource(SourceId.GLOVE, "100"),
                damageSource(SourceId.RING, "250"),
            ),
        )

        assertDecimal("350", DamageCalculator.calculate(build, WeaponMode.MELEE).totalDamage)
    }

    private fun damageSource(
        id: SourceId,
        damage: String,
        stats: List<Pair<StatType, String>> = emptyList(),
    ) = SourceRecord(
        id = id,
        baseDamage = NumericField.recognized(BigDecimal(damage)),
        baseHealth = NumericField.expectedAbsent(),
        subStats = stats.map { (type, value) ->
            SubStat(type, value, "+$value% ${type.name}", 1f)
        },
        ocrConfidence = 1f,
    )

    private fun assertDecimal(expected: String, actual: BigDecimal) {
        assertEquals(0, BigDecimal(expected).compareTo(actual))
    }
}
