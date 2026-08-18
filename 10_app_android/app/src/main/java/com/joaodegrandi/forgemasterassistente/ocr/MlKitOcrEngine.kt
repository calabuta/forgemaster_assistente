package com.joaodegrandi.forgemasterassistente.ocr

import android.graphics.Bitmap
import android.graphics.Color
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.joaodegrandi.forgemasterassistente.model.CropProfile
import com.joaodegrandi.forgemasterassistente.model.DefaultCropProfiles
import com.joaodegrandi.forgemasterassistente.model.NumericField
import com.joaodegrandi.forgemasterassistente.model.NormalizedRect
import com.joaodegrandi.forgemasterassistente.model.OcrReadResult
import com.joaodegrandi.forgemasterassistente.model.OcrSourceDraft
import com.joaodegrandi.forgemasterassistente.model.OcrTextLine
import com.joaodegrandi.forgemasterassistente.model.PanelType
import com.joaodegrandi.forgemasterassistente.model.SourceId
import com.joaodegrandi.forgemasterassistente.parser.EquipmentSlotDetector
import com.joaodegrandi.forgemasterassistente.parser.StatParser
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
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
        learnedEquipmentNames: Map<String, SourceId> = emptyMap(),
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

        val broadDrafts = if (panelType == PanelType.FORGE_COMPARISON) {
            parseForgeSources(detailLines)
        } else {
            parseSources(detailLines)
        }
        val focusedNames = readFocusedNames(bitmap, panelType)
        val safeFocusedNames = validateFocusedEquipmentNames(
            panelType,
            focusedNames,
            learnedEquipmentNames,
        )
        val drafts = mergeFocusedNames(broadDrafts, safeFocusedNames)
        val expectedCount = if (panelType == PanelType.FORGE_COMPARISON) 2 else 1
        val confidence = drafts.minOfOrNull { it.confidence } ?: 0f
        val expectedRecordIsComplete = expectedSourceId?.let { id ->
            drafts.firstOrNull()?.toSourceRecord(id)?.isComplete(minConfidence)
        } ?: true
        val focusedNamesComplete = safeFocusedNames.size >= expectedCount &&
            safeFocusedNames.take(expectedCount).all { it != null }
        val review = drafts.size < expectedCount || !focusedNamesComplete ||
            confidence < minConfidence || !expectedRecordIsComplete
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

    private suspend fun readFocusedNames(
        bitmap: Bitmap,
        panelType: PanelType,
    ): List<FocusedNameRead?> = focusedNameRects[panelType].orEmpty().map { rect ->
        val cropped = bitmap.crop(rect)
        try {
            val variants = listOf(
                cropped.upscaled(2),
                cropped.upscaled(4),
                cropped.colorFillMaskAndUpscale(3),
                cropped.colorFillMaskAndUpscale(4),
            )
            try {
                val candidates = variants.mapNotNull { variant ->
                    val lines = recognize(variant)
                    val raw = lines.joinToString(" ") { it.text }.trim()
                    raw.takeIf { it.any(Char::isLetter) }?.let {
                        FocusedNameCandidate(
                            raw = raw,
                            normalized = raw.normalizedForAgreement(),
                            confidence = lines.minimumConfidence(),
                        )
                    }
                }.filter { it.normalized.isNotBlank() }
                val agreeing = candidates.groupBy(FocusedNameCandidate::normalized)
                    .values
                    .filter { it.size >= 2 }
                    .maxWithOrNull(
                        compareBy<List<FocusedNameCandidate>>(
                            { it.size },
                            { group -> group.maxOf(FocusedNameCandidate::confidence) },
                        ),
                    )
                    ?: return@map null
                val preferred = agreeing.maxWithOrNull(
                    compareBy<FocusedNameCandidate>(
                        { it.hasCompleteRarityBrackets() },
                        { it.confidence },
                    ),
                ) ?: return@map null
                FocusedNameRead(
                    raw = preferred.raw,
                    confidence = agreeing.map { it.confidence }
                        .filter { it > 0f }
                        .minOrNull()
                        ?: 0f,
                )
            } finally {
                variants.forEach(Bitmap::recycle)
            }
        } finally {
            cropped.recycle()
        }
    }

    private fun validateFocusedEquipmentNames(
        panelType: PanelType,
        reads: List<FocusedNameRead?>,
        learnedEquipmentNames: Map<String, SourceId>,
    ): List<FocusedNameRead?> = when (panelType) {
        PanelType.EQUIPMENT_DETAIL -> reads.map { read ->
            read?.takeIf {
                EquipmentSlotDetector.detect(
                    parseSourceName(it.raw).second,
                    learnedEquipmentNames,
                ) != null
            }
        }
        PanelType.FORGE_COMPARISON -> {
            val names = reads.mapNotNull { it?.let { read -> parseSourceName(read.raw).second } }
            if (
                names.size == reads.size &&
                EquipmentSlotDetector.detectCompatible(names, learnedEquipmentNames) != null
            ) {
                reads
            } else {
                List(reads.size) { null }
            }
        }
        else -> reads
    }

    private fun mergeFocusedNames(
        drafts: List<OcrSourceDraft>,
        focusedNames: List<FocusedNameRead?>,
    ): List<OcrSourceDraft> = drafts.mapIndexed { index, draft ->
        val focused = focusedNames.getOrNull(index)
        if (focused == null) {
            draft.copy(rarity = "", name = "", confidence = 0f)
        } else {
            val (rarity, name) = parseSourceName(focused.raw)
            draft.copy(
                rarity = rarity,
                name = name,
                confidence = minOf(draft.confidence, focused.confidence),
                rawLines = listOf(focused.raw) + draft.rawLines,
            )
        }
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
        return sourceAnchors.mapIndexedNotNull { groupIndex, startIndex ->
            val endIndex = sourceAnchors.getOrNull(groupIndex + 1) ?: lines.size
            parseSourceGroup(lines.subList(startIndex, endIndex))
        }
    }

    private fun parseForgeSources(lines: List<OcrTextLine>): List<OcrSourceDraft> = listOf(
        lines.filter { it.centerY < FORGE_GROUP_SPLIT_Y },
        lines.filter { it.centerY >= FORGE_GROUP_SPLIT_Y },
    ).mapNotNull(::parseSourceGroup)

    private fun parseSourceGroup(group: List<OcrTextLine>): OcrSourceDraft? {
        val mainStatLines = group.filter { parseMainStat(it) != null }
        val firstMainStatLine = mainStatLines.firstOrNull() ?: return null
        val nameLine = group
            .filter { it.centerY < firstMainStatLine.centerY && isSourceNameCandidate(it) }
            .maxByOrNull(OcrTextLine::centerY)
            ?: group.filter(::isSourceNameCandidate)
                .minByOrNull { abs(it.centerY - firstMainStatLine.centerY) }
        val (rarity, name) = nameLine?.let { parseSourceName(it.text) } ?: ("" to "")
        val mainStats = mainStatLines.mapNotNull(::parseMainStat)
        val subStats = group.mapNotNull { line ->
            StatParser.parseSubStat(line.text, line.confidence)
        }.take(2)
        val levelReferenceY = nameLine?.centerY ?: firstMainStatLine.centerY
        val nearestLevel = group
            .filter { StatParser.parseLevel(it.text) != null }
            .minByOrNull { abs(it.centerY - levelReferenceY) }
            ?.let { StatParser.parseLevel(it.text) }
        val invalidStatLine = group.any { line ->
            looksLikePercentStat(line.text) &&
                StatParser.parseSubStat(line.text, line.confidence) == null
        }
        val relevantConfidence = if (invalidStatLine) {
            0f
        } else {
            group.filter { line ->
                line == nameLine || parseMainStat(line) != null ||
                    StatParser.parseSubStat(line.text, line.confidence) != null
            }.minimumConfidence()
        }
        return OcrSourceDraft(
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
        return crop(profile.content)
    }

    private fun Bitmap.crop(rect: NormalizedRect): Bitmap {
        val left = (rect.left * width).toInt().coerceIn(0, width - 1)
        val top = (rect.top * height).toInt().coerceIn(0, height - 1)
        val right = (rect.right * width).toInt().coerceIn(left + 1, width)
        val bottom = (rect.bottom * height).toInt().coerceIn(top + 1, height)
        return Bitmap.createBitmap(this, left, top, right - left, bottom - top)
    }

    private fun Bitmap.upscaled(scaleFactor: Int): Bitmap = Bitmap.createScaledBitmap(
        this,
        width * scaleFactor,
        height * scaleFactor,
        true,
    )

    private fun Bitmap.colorFillMaskAndUpscale(scaleFactor: Int): Bitmap {
        val masked = toColorFillMask()
        return try {
            masked.upscaled(scaleFactor)
        } finally {
            masked.recycle()
        }
    }

    private fun Bitmap.toColorFillMask(): Bitmap {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        pixels.indices.forEach { index ->
            val pixel = pixels[index]
            val red = Color.red(pixel)
            val green = Color.green(pixel)
            val blue = Color.blue(pixel)
            val maximum = maxOf(red, green, blue)
            val minimum = minOf(red, green, blue)
            val isText = maximum - minimum >= 24 && maximum >= 70
            pixels[index] = if (isText) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun String.normalizedForAgreement(): String = lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    private fun FocusedNameCandidate.hasCompleteRarityBrackets(): Boolean =
        raw.indexOfFirst { it == '[' || it == '(' || it == '{' } >= 0 &&
            raw.indexOfFirst { it == ']' || it == ')' || it == '}' } > 0

    private fun List<OcrTextLine>.minimumConfidence(): Float =
        map { it.confidence }.filter { it > 0f }.minOrNull() ?: 0f

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { continuation.resume(it) }
        addOnFailureListener { continuation.resumeWithException(it) }
        addOnCanceledListener { continuation.cancel() }
    }

    private data class FocusedNameCandidate(
        val raw: String,
        val normalized: String,
        val confidence: Float,
    )

    private data class FocusedNameRead(
        val raw: String,
        val confidence: Float,
    )

    private companion object {
        const val FORGE_GROUP_SPLIT_Y = 0.55f

        val focusedNameRects = mapOf(
            PanelType.EQUIPMENT_DETAIL to listOf(
                NormalizedRect(0.296f, 0.645f, 0.900f, 0.692f),
            ),
            PanelType.FORGE_COMPARISON to listOf(
                NormalizedRect(0.296f, 0.485f, 0.900f, 0.528f),
                NormalizedRect(0.296f, 0.620f, 0.900f, 0.662f),
            ),
            PanelType.MOUNT_DETAIL to listOf(
                NormalizedRect(0.259f, 0.423f, 0.889f, 0.466f),
            ),
            PanelType.PET_DETAIL to listOf(
                NormalizedRect(0.259f, 0.419f, 0.889f, 0.462f),
            ),
        )
    }
}
