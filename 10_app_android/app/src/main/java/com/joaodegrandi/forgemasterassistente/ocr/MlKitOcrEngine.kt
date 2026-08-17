package com.joaodegrandi.forgemasterassistente.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.joaodegrandi.forgemasterassistente.model.CropProfile
import com.joaodegrandi.forgemasterassistente.model.DefaultCropProfiles
import com.joaodegrandi.forgemasterassistente.model.NumericField
import com.joaodegrandi.forgemasterassistente.model.OcrReadResult
import com.joaodegrandi.forgemasterassistente.model.OcrSourceDraft
import com.joaodegrandi.forgemasterassistente.model.OcrTextLine
import com.joaodegrandi.forgemasterassistente.model.PanelType
import com.joaodegrandi.forgemasterassistente.model.SourceId
import com.joaodegrandi.forgemasterassistente.parser.StatParser
import kotlinx.coroutines.suspendCancellableCoroutine
import java.math.BigDecimal
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MlKitOcrEngine : AutoCloseable {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun read(
        bitmap: Bitmap,
        expectedSourceId: SourceId? = null,
        cropProfiles: List<CropProfile> = DefaultCropProfiles.profiles,
        minConfidence: Float = 0.85f,
    ): OcrReadResult {
        val fullLines = recognize(bitmap)
        val panelType = PanelClassifier.classify(fullLines)
        if (panelType == PanelType.UNKNOWN) {
            return OcrReadResult(
                panelType = panelType,
                sources = emptyList(),
                rawLines = fullLines,
                confidence = fullLines.minimumConfidence(),
                requiresReview = true,
                message = "Tipo de tela não reconhecido; ajuste o recorte ou abra um painel compatível.",
            )
        }

        val profile = cropProfiles.firstOrNull { it.panelType == panelType }
        val detailLines = if (profile == null) fullLines else {
            val cropped = bitmap.crop(profile)
            try {
                recognize(cropped)
            } finally {
                cropped.recycle()
            }
        }

        if (panelType == PanelType.SKILLS) {
            return parseSkills(detailLines, minConfidence)
        }
        if (panelType == PanelType.TOTALS) {
            val confidence = detailLines.minimumConfidence()
            return OcrReadResult(
                panelType = panelType,
                sources = emptyList(),
                rawLines = detailLines,
                confidence = confidence,
                requiresReview = confidence < minConfidence,
                message = "Tela de totais reconhecida somente para conferência.",
            )
        }

        val drafts = parseSources(detailLines)
        val expectedCount = if (panelType == PanelType.FORGE_COMPARISON) 2 else 1
        val confidence = drafts.minOfOrNull { it.confidence } ?: 0f
        val expectedRecordIsComplete = expectedSourceId?.let { id ->
            drafts.firstOrNull()?.toSourceRecord(id)?.isComplete(minConfidence)
        } ?: true
        val review = drafts.size < expectedCount || confidence < minConfidence || !expectedRecordIsComplete
        return OcrReadResult(
            panelType = panelType,
            sources = drafts,
            rawLines = detailLines,
            confidence = confidence,
            requiresReview = review,
            message = if (review) {
                "Revise os valores reconhecidos antes de usar a recomendação."
            } else {
                "Leitura concluída."
            },
        )
    }

    private suspend fun recognize(bitmap: Bitmap): List<OcrTextLine> {
        val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
        val height = max(bitmap.height, 1).toFloat()
        return result.textBlocks
            .flatMap { it.lines }
            .map { line ->
                val confidence = line.elements
                    .map { it.confidence }
                    .filter { it > 0f }
                    .minOrNull()
                    ?: 0f
                OcrTextLine(
                    text = line.text.trim(),
                    confidence = confidence,
                    centerY = (line.boundingBox?.centerY() ?: 0) / height,
                )
            }
            .filter { it.text.isNotBlank() }
            .sortedBy { it.centerY }
    }

    private fun parseSkills(lines: List<OcrTextLine>, minConfidence: Float): OcrReadResult {
        val combined = lines.joinToString(" ") { it.text }
        val values = StatParser.parseSkillsBanner(combined)
        val confidence = lines
            .filter { "Base" in it.text || "Damage" in it.text || "Health" in it.text }
            .minimumConfidence()
        val draft = values?.let {
            OcrSourceDraft(
                rarity = "",
                name = "Skills",
                level = null,
                baseDamage = NumericField.recognized(it.damage, confidence = confidence),
                baseHealth = NumericField.recognized(it.health, confidence = confidence),
                subStats = emptyList(),
                confidence = confidence,
                rawLines = lines.map(OcrTextLine::text),
            )
        }
        val review = draft == null || confidence < minConfidence
        return OcrReadResult(
            panelType = PanelType.SKILLS,
            sources = listOfNotNull(draft),
            rawLines = lines,
            confidence = confidence,
            requiresReview = review,
            message = if (review) "Banner de Skills incompleto; revise os valores." else "Skills reconhecidas.",
        )
    }

    private fun parseSources(lines: List<OcrTextLine>): List<OcrSourceDraft> {
        val sourceAnchors = lines.mapIndexedNotNull { index, line ->
            index.takeIf { parseMainStat(line) != null }
                ?.let { mainStatIndex -> findNearestNameIndex(lines, mainStatIndex) }
        }.distinct()
        return sourceAnchors.mapIndexed { groupIndex, startIndex ->
            val endIndex = sourceAnchors.getOrNull(groupIndex + 1) ?: lines.size
            val group = lines.subList(startIndex, endIndex)
            val (rarity, name) = parseSourceName(group.first().text)
            val mainStats = group.mapNotNull(::parseMainStat)
            val subStats = group.mapNotNull { line ->
                StatParser.parseSubStat(line.text, line.confidence)
            }.take(2)
            val nearestLevel = group
                .filter { StatParser.parseLevel(it.text) != null }
                .minByOrNull { abs(it.centerY - group.first().centerY) }
                ?.let { StatParser.parseLevel(it.text) }
            val invalidStatLine = group.any { line ->
                looksLikePercentStat(line.text) &&
                    StatParser.parseSubStat(line.text, line.confidence) == null
            }
            val relevantConfidence = if (invalidStatLine) {
                0f
            } else {
                group.filter { line ->
                    line == group.first() || parseMainStat(line) != null ||
                        StatParser.parseSubStat(line.text, line.confidence) != null
                }.minimumConfidence()
            }
            OcrSourceDraft(
                rarity = rarity,
                name = name,
                level = nearestLevel,
                baseDamage = mainStats.firstOrNull { it.first == "damage" }?.second,
                baseHealth = mainStats.firstOrNull { it.first == "health" }?.second,
                subStats = subStats,
                confidence = relevantConfidence,
                rawLines = group.map(OcrTextLine::text),
            )
        }
    }

    private fun findNearestNameIndex(lines: List<OcrTextLine>, mainStatIndex: Int): Int? =
        (mainStatIndex - 1 downTo 0).firstOrNull { index ->
            isSourceNameCandidate(lines[index])
        }

    private fun isSourceNameCandidate(line: OcrTextLine): Boolean {
        val text = line.text.trim()
        if (text.length < 3 || text.none(Char::isLetter)) return false
        if (parseMainStat(line) != null || StatParser.parseLevel(text) != null) return false
        if (StatParser.parseSubStat(text, line.confidence) != null || looksLikePercentStat(text)) {
            return false
        }
        val normalized = text.lowercase()
            .replace(Regex("[^a-z]+"), " ")
            .trim()
        return normalized !in setOf(
            "auto",
            "on",
            "off",
            "equipped",
            "equip",
            "sell",
            "remove",
            "upgrade",
            "new",
            "mount",
            "mounts",
            "pet",
            "pets",
            "skills",
            "forge",
        )
    }

    private fun parseSourceName(raw: String): Pair<String, String> {
        val cleaned = raw.trim().trimStart('(', '[', '{')
        val separatorIndex = cleaned.indexOfFirst { it == ']' || it == ')' || it == '}' }
        return if (separatorIndex in 1 until cleaned.lastIndex) {
            cleaned.substring(0, separatorIndex).trim() to
                cleaned.substring(separatorIndex + 1).trim()
        } else {
            "" to cleaned
        }
    }

    private fun looksLikePercentStat(raw: String): Boolean = Regex(
        pattern = """^[+]?\s*[0-9]+(?:[.,][0-9]+)?\s*%\s*\p{L}+.*$""",
        option = RegexOption.IGNORE_CASE,
    ).matches(raw.trim())

    private fun parseMainStat(line: OcrTextLine): Pair<String, NumericField>? {
        val sanitized = line.text.replace("▲", "").replace("▼", "").trim()
        val match = Regex(
            pattern = """^([0-9]+(?:[.,][0-9]+)?[kKmMbB]?)\s+(Damage|Health)\b""",
            option = RegexOption.IGNORE_CASE,
        ).find(sanitized) ?: return null
        val value = StatParser.parseAbbreviatedNumber(match.groupValues[1]) ?: return null
        return match.groupValues[2].lowercase() to NumericField.recognized(
            value = value,
            rawText = line.text,
            confidence = line.confidence,
        )
    }

    override fun close() {
        recognizer.close()
    }

    private fun Bitmap.crop(profile: CropProfile): Bitmap {
        val rect = profile.content
        val left = (rect.left * width).toInt().coerceIn(0, width - 1)
        val top = (rect.top * height).toInt().coerceIn(0, height - 1)
        val right = (rect.right * width).toInt().coerceIn(left + 1, width)
        val bottom = (rect.bottom * height).toInt().coerceIn(top + 1, height)
        return Bitmap.createBitmap(this, left, top, right - left, bottom - top)
    }

    private fun List<OcrTextLine>.minimumConfidence(): Float =
        map { it.confidence }.filter { it > 0f }.minOrNull() ?: 0f

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { continuation.resume(it) }
        addOnFailureListener { continuation.resumeWithException(it) }
        addOnCanceledListener { continuation.cancel() }
    }
}
