package com.joaodegrandi.forgemasterassistente.scoring

import com.joaodegrandi.forgemasterassistente.model.BuildState
import com.joaodegrandi.forgemasterassistente.model.NumericField
import com.joaodegrandi.forgemasterassistente.model.Recommendation
import com.joaodegrandi.forgemasterassistente.model.SourceId
import com.joaodegrandi.forgemasterassistente.model.SourceRecord
import com.joaodegrandi.forgemasterassistente.model.StatType
import com.joaodegrandi.forgemasterassistente.model.SubStat
import com.joaodegrandi.forgemasterassistente.model.WeaponMode
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReplacementEvaluatorTest {
    @Test
    fun recommendsEquipSellOrInconclusiveFromUnroundedDamage() {
        val build = BuildState(listOf(glove("100")))

        assertEquals(
            Recommendation.EQUIPAR,
            ReplacementEvaluator.compare(build, SourceId.GLOVE, glove("100.0001"), WeaponMode.MELEE)
                .recommendation,
        )
        assertEquals(
            Recommendation.VENDER,
            ReplacementEvaluator.compare(build, SourceId.GLOVE, glove("99.9999"), WeaponMode.MELEE)
                .recommendation,
        )
        val equal = ReplacementEvaluator.compare(
            build,
            SourceId.GLOVE,
            glove("100"),
            WeaponMode.MELEE,
        )
        assertEquals(Recommendation.INCONCLUSIVO, equal.recommendation)
        assertEquals("dano equivalente", equal.reason)
        assertEquals(0, BigDecimal.ZERO.compareTo(equal.delta))
    }

    @Test
    fun rejectsMissingLowConfidenceOrZeroBaseDamage() {
        val build = BuildState(listOf(glove("100")))
        val unsafe = glove("110").copy(
            baseDamage = NumericField.recognized(BigDecimal("110"), confidence = 0.84f),
        )

        assertEquals(
            Recommendation.INCONCLUSIVO,
            ReplacementEvaluator.compare(build, SourceId.GLOVE, unsafe, WeaponMode.MELEE)
                .recommendation,
        )
        val zeroBuild = BuildState(listOf(glove("0")))
        val zeroResult = ReplacementEvaluator.compare(
            zeroBuild,
            SourceId.GLOVE,
            glove("1"),
            WeaponMode.MELEE,
        )
        assertEquals(Recommendation.INCONCLUSIVO, zeroResult.recommendation)
        assertNull(zeroResult.delta)
    }

    @Test
    fun ignoresInactiveWeaponDamageType() {
        val current = glove("100", StatType.RANGED_DAMAGE to "50")
        val candidate = glove("100", StatType.MELEE_DAMAGE to "50")
        val build = BuildState(listOf(current))

        assertEquals(
            Recommendation.EQUIPAR,
            ReplacementEvaluator.compare(build, SourceId.GLOVE, candidate, WeaponMode.MELEE)
                .recommendation,
        )
        assertEquals(
            Recommendation.VENDER,
            ReplacementEvaluator.compare(build, SourceId.GLOVE, candidate, WeaponMode.RANGED)
                .recommendation,
        )
    }

    @Test
    fun evaluatesAllThreePetReplacementScenarios() {
        val build = BuildState(
            listOf(
                pet(SourceId.PET_1, "100"),
                pet(SourceId.PET_2, "200"),
                pet(SourceId.PET_3, "300"),
            ),
        )

        val result = ReplacementEvaluator.comparePet(
            build,
            pet(SourceId.PET_1, "250"),
            WeaponMode.MELEE,
        )

        assertEquals(3, result.scenarios.size)
        assertEquals(SourceId.PET_1, result.best?.sourceId)
        assertEquals(Recommendation.EQUIPAR, result.scenarios[0].recommendation)
        assertEquals(Recommendation.EQUIPAR, result.scenarios[1].recommendation)
        assertEquals(Recommendation.VENDER, result.scenarios[2].recommendation)
    }

    private fun glove(
        damage: String,
        stat: Pair<StatType, String>? = null,
    ) = SourceRecord(
        id = SourceId.GLOVE,
        baseDamage = NumericField.recognized(BigDecimal(damage)),
        baseHealth = NumericField.expectedAbsent(),
        subStats = listOfNotNull(stat?.let { (type, value) ->
            SubStat(type, value, "+$value% ${type.name}", 1f)
        }),
        ocrConfidence = 1f,
    )

    private fun pet(id: SourceId, damage: String) = SourceRecord(
        id = id,
        baseDamage = NumericField.recognized(BigDecimal(damage)),
        baseHealth = NumericField.recognized(BigDecimal("1")),
        ocrConfidence = 1f,
    )
}
