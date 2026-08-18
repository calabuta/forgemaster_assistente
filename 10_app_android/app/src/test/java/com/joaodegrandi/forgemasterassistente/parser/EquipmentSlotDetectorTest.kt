package com.joaodegrandi.forgemasterassistente.parser

import com.joaodegrandi.forgemasterassistente.model.SourceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EquipmentSlotDetectorTest {
    @Test
    fun identifiesKnownEquipmentNames() {
        val cases = mapOf(
            "Quantum Suit" to SourceId.TORSO,
            "Spite Crown" to SourceId.HEAD,
            "Power Grip" to SourceId.GLOVE,
            "Gravity Gloves" to SourceId.GLOVE,
            "Multiverse Impulse" to SourceId.GLOVE,
            "Void Necklace" to SourceId.NECKLACE,
            "Solar Ring" to SourceId.RING,
            "Ancient Sword" to SourceId.WEAPON,
            "Divine Greaves" to SourceId.BOOT,
            "Antimatter Feet" to SourceId.BOOT,
            "Modern Belt" to SourceId.BELT,
        )

        cases.forEach { (name, expected) ->
            assertEquals(name, expected, EquipmentSlotDetector.detect(name))
        }
    }

    @Test
    fun acceptsPunctuationButNotOcrGuesses() {
        assertEquals(SourceId.BOOT, EquipmentSlotDetector.detect("Astral-GREAVES!"))
        assertNull(EquipmentSlotDetector.detect("Spite Groun"))
    }

    @Test
    fun staysInconclusiveForUnknownOrConflictingNames() {
        assertNull(EquipmentSlotDetector.detect("Kitsune"))
        assertNull(EquipmentSlotDetector.detectCompatible(listOf("Spite Crown", "Solar Ring")))
        assertNull(EquipmentSlotDetector.detectCompatible(listOf("Unknown", "Spite Groun")))
        assertEquals(
            SourceId.HEAD,
            EquipmentSlotDetector.detectCompatible(listOf("Spite Crown", "Unknown Candidate")),
        )
        assertEquals(
            SourceId.GLOVE,
            EquipmentSlotDetector.detectCompatible(listOf("Grip of Torment", "Impulse")),
        )
        assertEquals(
            false,
            EquipmentSlotDetector.conflictsWith(SourceId.GLOVE, "Unknown Candidate"),
        )
        assertEquals(
            true,
            EquipmentSlotDetector.conflictsWith(SourceId.GLOVE, "Probability Ring"),
        )
    }

    @Test
    fun usesLearnedExactNamesWithoutOverridingBuiltInSlotWords() {
        val learned = EquipmentSlotDetector.learnNames(
            current = emptyMap(),
            names = listOf("Phase Shift", "Probability Ring"),
            sourceId = SourceId.GLOVE,
        )
        val afterConflictingLearning = EquipmentSlotDetector.learnNames(
            current = learned,
            names = listOf("Phase Shift"),
            sourceId = SourceId.RING,
        )

        assertEquals(SourceId.GLOVE, EquipmentSlotDetector.detect("Phase Shift", learned))
        assertEquals(SourceId.RING, EquipmentSlotDetector.detect("Probability Ring", learned))
        assertEquals(SourceId.GLOVE, afterConflictingLearning["phase shift"])
    }
}
