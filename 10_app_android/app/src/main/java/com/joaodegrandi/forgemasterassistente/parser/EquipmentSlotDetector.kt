package com.joaodegrandi.forgemasterassistente.parser

import com.joaodegrandi.forgemasterassistente.model.SourceId
import java.util.Locale

object EquipmentSlotDetector {
    private val knownNames = mapOf(
        "suit" to SourceId.TORSO,
        "crown" to SourceId.HEAD,
        "grip" to SourceId.GLOVE,
        "glove" to SourceId.GLOVE,
        "gloves" to SourceId.GLOVE,
        "impulse" to SourceId.GLOVE,
        "necklace" to SourceId.NECKLACE,
        "ring" to SourceId.RING,
        "sword" to SourceId.WEAPON,
        "greaves" to SourceId.BOOT,
        "feet" to SourceId.BOOT,
        "belt" to SourceId.BELT,
    )

    fun detect(
        itemName: String,
        learnedNames: Map<String, SourceId> = emptyMap(),
    ): SourceId? {
        val normalizedName = normalizeName(itemName)
        val words = normalizedName
            .split(Regex("\\s+"))
        return words.asReversed().firstNotNullOfOrNull(knownNames::get)
            ?: learnedNames[normalizedName]
    }

    fun detectCompatible(
        itemNames: List<String>,
        learnedNames: Map<String, SourceId> = emptyMap(),
    ): SourceId? {
        val recognizedSlots = itemNames.mapNotNull { detect(it, learnedNames) }.distinct()
        return recognizedSlots.singleOrNull()
    }

    fun conflictsWith(
        expectedSlot: SourceId,
        itemName: String,
        learnedNames: Map<String, SourceId> = emptyMap(),
    ): Boolean = detect(itemName, learnedNames)
        ?.let { detectedSlot -> detectedSlot != expectedSlot }
        ?: false

    fun normalizeName(itemName: String): String = itemName.lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    fun learnNames(
        current: Map<String, SourceId>,
        names: List<String>,
        sourceId: SourceId,
    ): Map<String, SourceId> = current.toMutableMap().apply {
        names.map(::normalizeName)
            .filter(String::isNotBlank)
            .forEach { normalizedName ->
                val existing = this[normalizedName]
                if (existing == null || existing == sourceId) {
                    this[normalizedName] = sourceId
                }
            }
    }
}
