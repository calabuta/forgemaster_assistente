package com.joaodegrandi.forgemasterassistente.parser

import com.joaodegrandi.forgemasterassistente.model.StatType
import com.joaodegrandi.forgemasterassistente.model.SubStat
import java.math.BigDecimal
import java.util.Locale

object StatParser {
    private val abbreviatedNumber = Regex(
        pattern = """^[+]?([0-9]+(?:[.,][0-9]+)?)([kKmMbB]?)$""",
    )
    private val statLine = Regex(
        pattern = """^[+]?([0-9]+(?:[.,][0-9]+)?)\s*%\s+(.+?)\s*$""",
        option = RegexOption.IGNORE_CASE,
    )
    private val levelPattern = Regex(
        pattern = """\bLv[.,]?\s*([0-9]+)\b""",
        option = RegexOption.IGNORE_CASE,
    )
    private val skillsBanner = Regex(
        pattern = """[+]?([0-9]+(?:[.,][0-9]+)?[kKmMbB]?)\s*Base\s+Damage\s+[+]?([0-9]+(?:[.,][0-9]+)?[kKmMbB]?)\s*Base\s+Health""",
        option = RegexOption.IGNORE_CASE,
    )

    data class SkillsValues(val damage: BigDecimal, val health: BigDecimal)

    fun parseAbbreviatedNumber(raw: String): BigDecimal? {
        val normalized = raw.trim().replace(" ", "")
        val match = abbreviatedNumber.matchEntire(normalized) ?: return null
        val number = match.groupValues[1].replace(',', '.').toBigDecimalOrNull() ?: return null
        val multiplier = when (match.groupValues[2].lowercase(Locale.ROOT)) {
            "k" -> BigDecimal("1000")
            "m" -> BigDecimal("1000000")
            "b" -> BigDecimal("1000000000")
            else -> BigDecimal.ONE
        }
        return number.multiply(multiplier).stripTrailingZeros()
    }

    fun parsePercent(raw: String): BigDecimal? = raw
        .trim()
        .removePrefix("+")
        .removeSuffix("%")
        .trim()
        .replace(',', '.')
        .toBigDecimalOrNull()

    fun parseLevel(raw: String): Int? =
        levelPattern.find(raw)?.groupValues?.get(1)?.toIntOrNull()

    fun parseSubStat(raw: String, confidence: Float): SubStat? {
        val match = statLine.matchEntire(raw.trim()) ?: return null
        val value = match.groupValues[1].replace(',', '.').toBigDecimalOrNull() ?: return null
        val type = parseStatType(match.groupValues[2]) ?: return null
        return SubStat(
            type = type,
            percentValue = value.stripTrailingZeros().toPlainString(),
            rawText = raw.trim(),
            confidence = confidence,
        )
    }

    fun parseSkillsBanner(raw: String): SkillsValues? {
        val match = skillsBanner.find(raw) ?: return null
        val damage = parseAbbreviatedNumber(match.groupValues[1]) ?: return null
        val health = parseAbbreviatedNumber(match.groupValues[2]) ?: return null
        return SkillsValues(damage, health)
    }

    fun parseStatType(raw: String): StatType? {
        val normalized = raw
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
        return when (normalized) {
            "critical chance", "crit chance" -> StatType.CRITICAL_CHANCE
            "critical damage", "crit damage" -> StatType.CRITICAL_DAMAGE
            "double chance" -> StatType.DOUBLE_CHANCE
            "damage" -> StatType.DAMAGE
            "melee damage" -> StatType.MELEE_DAMAGE
            "ranged damage" -> StatType.RANGED_DAMAGE
            "attack speed" -> StatType.ATTACK_SPEED
            "lifesteal", "life steal" -> StatType.LIFESTEAL
            "block", "block chance" -> StatType.BLOCK
            "health regen", "health regeneration" -> StatType.HEALTH_REGEN
            "skill damage" -> StatType.SKILL_DAMAGE
            "skill cooldown" -> StatType.SKILL_COOLDOWN
            "health" -> StatType.HEALTH
            else -> null
        }
    }
}
