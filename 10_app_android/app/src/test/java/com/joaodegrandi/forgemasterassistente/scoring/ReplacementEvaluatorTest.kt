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
import java.math.MathContext
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

    @Test
    fun normalizesCandidateForEachPetLevelUsingItsOwnBaseValues() {
        val candidate = pet(
            SourceId.PET_1,
            damage = "28.5",
            level = 1,
            name = "Candidate With Different Base",
            health = "67.2",
        )
        val build = BuildState(
            listOf(
                pet(SourceId.PET_1, "29.7", level = 5, name = "Pet A", health = "69.9"),
                pet(SourceId.PET_2, "76.4", level = 100, name = "Pet B", health = "179.9"),
            ),
        )

        val forPet1 = ReplacementEvaluator.normalizedCandidateForReplacement(
            build,
            SourceId.PET_1,
            candidate,
        )!!
        val forPet2 = ReplacementEvaluator.normalizedCandidateForReplacement(
            build,
            SourceId.PET_2,
            candidate,
        )!!

        assertEquals(5, forPet1.level)
        assertEquals(100, forPet2.level)
        assertEquals("Candidate With Different Base", forPet1.name)
        assertDecimalEquals(
            BigDecimal("28.5").multiply(
                BigDecimal("1.01").pow(4, MathContext.DECIMAL128),
                MathContext.DECIMAL128,
            ),
            forPet1.baseDamage.decimalOrNull()!!,
        )
        assertDecimalEquals(
            BigDecimal("28.5").multiply(
                BigDecimal("1.01").pow(99, MathContext.DECIMAL128),
                MathContext.DECIMAL128,
            ),
            forPet2.baseDamage.decimalOrNull()!!,
        )
    }

    @Test
    fun keepsHigherLevelBagCandidateAndProjectsCurrentSourceUp() {
        val current = pet(
            SourceId.PET_1,
            damage = "29.7",
            level = 5,
            name = "Current Pet",
            health = "69.9",
        )
        val candidate = pet(
            SourceId.PET_1,
            damage = "76.4",
            level = 100,
            name = "Higher Bag Pet",
            health = "179.9",
        )
        val result = ReplacementEvaluator.compare(
            BuildState(listOf(current)),
            SourceId.PET_1,
            candidate,
            WeaponMode.MELEE,
        )

        assertEquals(100, result.normalizedLevel)
        assertDecimalEquals(
            BigDecimal("29.7").multiply(
                BigDecimal("1.01").pow(95, MathContext.DECIMAL128),
                MathContext.DECIMAL128,
            ),
            result.damageBefore,
        )
        assertDecimalEquals(BigDecimal("76.4"), result.damageAfter)
    }

    @Test
    fun normalizesMountWithPointSixPercentCompoundGrowth() {
        val current = SourceRecord(
            id = SourceId.MOUNT,
            name = "Current Mount",
            level = 57,
            baseDamage = NumericField.recognized(BigDecimal("307")),
            baseHealth = NumericField.recognized(BigDecimal("2460")),
            ocrConfidence = 1f,
        )
        val candidate = SourceRecord(
            id = SourceId.MOUNT,
            name = "Different Mount",
            level = 1,
            baseDamage = NumericField.recognized(BigDecimal("220")),
            baseHealth = NumericField.recognized(BigDecimal("1760")),
            ocrConfidence = 1f,
        )

        val normalized = ReplacementEvaluator.normalizedCandidateForReplacement(
            BuildState(listOf(current)),
            SourceId.MOUNT,
            candidate,
        )!!

        assertEquals(57, normalized.level)
        assertEquals("Different Mount", normalized.name)
        assertDecimalEquals(
            BigDecimal("220").multiply(
                BigDecimal("1.006").pow(56, MathContext.DECIMAL128),
                MathContext.DECIMAL128,
            ),
            normalized.baseDamage.decimalOrNull()!!,
        )
        assertDecimalEquals(
            BigDecimal("1760").multiply(
                BigDecimal("1.006").pow(56, MathContext.DECIMAL128),
                MathContext.DECIMAL128,
            ),
            normalized.baseHealth.decimalOrNull()!!,
        )
    }

    @Test
    fun requiresLevelsForPetAndMountNormalization() {
        val current = pet(SourceId.PET_1, "100", level = null)
        val candidate = pet(SourceId.PET_1, "110", level = 1)

        val result = ReplacementEvaluator.compare(
            BuildState(listOf(current)),
            SourceId.PET_1,
            candidate,
            WeaponMode.MELEE,
        )

        assertEquals(Recommendation.INCONCLUSIVO, result.recommendation)
        assertNull(result.delta)
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

    private fun pet(
        id: SourceId,
        damage: String,
        level: Int? = 1,
        name: String = "Pet",
        health: String = "1",
    ) = SourceRecord(
        id = id,
        name = name,
        level = level,
        baseDamage = NumericField.recognized(BigDecimal(damage)),
        baseHealth = NumericField.recognized(BigDecimal(health)),
        ocrConfidence = 1f,
    )

    private fun assertDecimalEquals(expected: BigDecimal, actual: BigDecimal) {
        assertEquals(0, expected.compareTo(actual))
    }
}
