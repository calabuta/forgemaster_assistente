package com.joaodegrandi.forgemasterassistente.parser

import com.joaodegrandi.forgemasterassistente.model.StatType
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatParserTest {
    @Test
    fun parsesPlainAndAbbreviatedNumbers() {
        assertDecimal("291000", StatParser.parseAbbreviatedNumber("291k"))
        assertDecimal("8140000", StatParser.parseAbbreviatedNumber("8.14m"))
        assertDecimal("1500000000", StatParser.parseAbbreviatedNumber("1,5B"))
        assertDecimal("42", StatParser.parseAbbreviatedNumber("+42"))
        assertNull(StatParser.parseAbbreviatedNumber("8.14 trillion"))
    }

    @Test
    fun parsesPercentLevelAndKnownSubstats() {
        assertDecimal("43.3", StatParser.parsePercent("+43,3%"))
        assertEquals(105, StatParser.parseLevel("Lv. 105"))
        assertEquals(57, StatParser.parseLevel("Lv,57"))

        val subStat = StatParser.parseSubStat("+19.2% Melee Damage", 0.93f)
        assertEquals(StatType.MELEE_DAMAGE, subStat?.type)
        assertEquals("19.2", subStat?.percentValue)
        assertEquals(0.93f, subStat?.confidence)
        assertNull(StatParser.parseSubStat("+12% Power", 1f))
        assertEquals(
            StatType.BLOCK,
            StatParser.parseSubStat("+4.83% Block Chance", 1f)?.type,
        )
    }

    @Test
    fun parsesSkillsBanner() {
        val values = StatParser.parseSkillsBanner("+655k Base Damage   +1.56m Base Health")
        assertDecimal("655000", values?.damage)
        assertDecimal("1560000", values?.health)
    }

    private fun assertDecimal(expected: String, actual: BigDecimal?) {
        assertEquals(0, BigDecimal(expected).compareTo(actual))
    }
}
