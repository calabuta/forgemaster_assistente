package com.joaodegrandi.forgemasterassistente.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.joaodegrandi.forgemasterassistente.model.BuildState
import com.joaodegrandi.forgemasterassistente.model.CalibrationDraft
import com.joaodegrandi.forgemasterassistente.model.NumericField
import com.joaodegrandi.forgemasterassistente.model.SourceId
import com.joaodegrandi.forgemasterassistente.model.SourceRecord
import com.joaodegrandi.forgemasterassistente.model.WeaponMode
import java.math.BigDecimal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppStorageTest {
    @Test
    fun persistsModeCalibrationAtomicReplacementAndUndo() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storage = AppStorage(context)
        val original = glove("100")

        storage.saveBuild(BuildState(listOf(original)))
        storage.saveWeaponMode(WeaponMode.RANGED)
        storage.saveCalibrationDraft(
            CalibrationDraft(
                build = BuildState(listOf(head("250"))),
                selectedSources = listOf(SourceId.HEAD),
                currentSource = SourceId.HEAD,
            ),
        )

        assertEquals(WeaponMode.RANGED, storage.weaponModeFlow.first())
        assertEquals("250", storage.calibrationFlow.first().build.source(SourceId.HEAD)
            ?.baseHealth?.value)

        val replacement = glove("120")
        assertTrue(storage.confirmReplacement(SourceId.GLOVE, replacement))
        assertEquals("120", storage.buildFlow.first().source(SourceId.GLOVE)?.baseDamage?.value)
        assertNotNull(storage.undoFlow.first())
        assertTrue(storage.undoLastReplacement())
        assertEquals("100", storage.buildFlow.first().source(SourceId.GLOVE)?.baseDamage?.value)
        assertNull(storage.undoFlow.first())
        assertFalse(storage.undoLastReplacement())

        val pets = BuildState(
            listOf(
                pet(SourceId.PET_1, "100"),
                pet(SourceId.PET_2, "200"),
                pet(SourceId.PET_3, "300"),
            ),
        )
        storage.saveBuild(pets)
        assertTrue(storage.confirmReplacement(SourceId.PET_2, pet(SourceId.PET_2, "250")))
        val persistedPets = storage.buildFlow.first()
        assertEquals(3, persistedPets.sources.size)
        assertTrue(listOf(SourceId.PET_1, SourceId.PET_2, SourceId.PET_3).all {
            persistedPets.source(it) != null
        })
        assertEquals("250", persistedPets.source(SourceId.PET_2)?.baseDamage?.value)

        storage.learnEquipmentNames(
            listOf("Grip of Torment", "Phase Shift"),
            SourceId.GLOVE,
        )
        storage.learnEquipmentNames(listOf("Phase Shift"), SourceId.RING)
        val learnedNames = storage.learnedEquipmentNamesFlow.first()
        assertEquals(SourceId.GLOVE, learnedNames["grip of torment"])
        assertEquals(SourceId.GLOVE, learnedNames["phase shift"])
    }

    private fun glove(damage: String) = SourceRecord(
        id = SourceId.GLOVE,
        baseDamage = NumericField.recognized(BigDecimal(damage)),
        baseHealth = NumericField.expectedAbsent(),
        ocrConfidence = 1f,
    )

    private fun head(health: String) = SourceRecord(
        id = SourceId.HEAD,
        baseDamage = NumericField.expectedAbsent(),
        baseHealth = NumericField.recognized(BigDecimal(health)),
        ocrConfidence = 1f,
    )

    private fun pet(id: SourceId, damage: String) = SourceRecord(
        id = id,
        baseDamage = NumericField.recognized(BigDecimal(damage)),
        baseHealth = NumericField.recognized(BigDecimal("1")),
        ocrConfidence = 1f,
    )
}
