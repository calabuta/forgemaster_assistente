package com.joaodegrandi.forgemasterassistente.ocr

import com.joaodegrandi.forgemasterassistente.model.OcrTextLine
import com.joaodegrandi.forgemasterassistente.model.PanelType

object PanelClassifier {
    fun classify(lines: List<OcrTextLine>): PanelType {
        val text = lines.joinToString("\n") { it.text }.lowercase()
        return when {
            "base damage" in text && "base health" in text && "skills" in text ->
                PanelType.SKILLS
            "equipped" in text &&
                (("sell" in text && "equip" in text) || "new!" in text) ->
                PanelType.FORGE_COMPARISON
            Regex("\\bmounts?\\b").containsMatchIn(text) -> PanelType.MOUNT_DETAIL
            Regex("\\bpets?\\b").containsMatchIn(text) -> PanelType.PET_DETAIL
            "total damage" in text || "total health" in text -> PanelType.TOTALS
            "equipped" in text -> PanelType.EQUIPMENT_DETAIL
            else -> PanelType.UNKNOWN
        }
    }
}
