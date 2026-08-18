package com.joaodegrandi.forgemasterassistente.ocr

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.joaodegrandi.forgemasterassistente.model.PanelType
import com.joaodegrandi.forgemasterassistente.model.SourceId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OcrReferenceImagesTest {
    @Test
    fun recognizesAllFifteenPreservedReferenceScreens() = runBlocking {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val engine = MlKitOcrEngine()
        val failures = mutableListOf<String>()
        try {
            fixtures.forEach { fixture ->
                val bitmap = assets.open(fixture.fileName).use(BitmapFactory::decodeStream)
                    ?: error("Imagem de teste inválida: ${fixture.fileName}")
                val result = try {
                    engine.read(bitmap, fixture.sourceId)
                } finally {
                    bitmap.recycle()
                }
                val sourceSummary = result.sources.joinToString(" | ") { source ->
                    "${source.rarity}/${source.name};lv=${source.level};" +
                        "dmg=${source.baseDamage?.value};hp=${source.baseHealth?.value};" +
                        "subs=${source.subStats.joinToString { "${it.type}:${it.percentValue}" }}"
                }
                println(
                    "OCR_FIXTURE ${fixture.fileName}: panel=${result.panelType};" +
                        "review=${result.requiresReview};sources=[$sourceSummary];" +
                        "lines=${result.rawLines.joinToString(" / ") { it.text }}",
                )
                if (result.panelType != fixture.panelType) {
                    failures += "${fixture.fileName}: esperado ${fixture.panelType}, lido ${result.panelType}"
                }
                if (result.sources.size < fixture.minimumSources) {
                    failures += "${fixture.fileName}: esperado ao menos ${fixture.minimumSources} fonte(s), " +
                        "lidas ${result.sources.size}. Linhas: ${result.rawLines.joinToString(" / ") { it.text }}"
                }
                val damages = result.sources.mapNotNull { it.baseDamage?.value }
                val health = result.sources.mapNotNull { it.baseHealth?.value }
                val names = result.sources.map { it.name }
                val subStats = result.sources.flatMap { source ->
                    source.subStats.map { "${it.type}:${it.percentValue}" }
                }
                if (damages != fixture.damages) {
                    failures += "${fixture.fileName}: danos esperados ${fixture.damages}, lidos $damages"
                }
                if (health != fixture.health) {
                    failures += "${fixture.fileName}: vidas esperadas ${fixture.health}, lidas $health"
                }
                if (fixture.names.isNotEmpty() && names != fixture.names) {
                    failures += "${fixture.fileName}: nomes esperados ${fixture.names}, lidos $names"
                }
                if (subStats != fixture.subStats) {
                    failures += "${fixture.fileName}: substats esperados ${fixture.subStats}, lidos $subStats"
                }
                fixture.rawMustContain.forEach { expectedText ->
                    if (result.rawLines.none { expectedText in it.text }) {
                        failures += "${fixture.fileName}: texto obrigatório não lido: $expectedText"
                    }
                }
            }
        } finally {
            engine.close()
        }
        if (failures.isNotEmpty()) fail(failures.joinToString("\n"))
    }

    private data class Fixture(
        val fileName: String,
        val panelType: PanelType,
        val sourceId: SourceId? = null,
        val minimumSources: Int = 1,
        val damages: List<String> = emptyList(),
        val health: List<String> = emptyList(),
        val names: List<String> = emptyList(),
        val subStats: List<String> = emptyList(),
        val rawMustContain: List<String> = emptyList(),
    )

    private companion object {
        val fixtures = listOf(
            Fixture(
                "Screenshot_20260816_173240_Forge Master.jpg",
                PanelType.TOTALS,
                minimumSources = 0,
                rawMustContain = listOf("11.6m Total Damage", "15.6m Total Health"),
            ),
            Fixture(
                "Screenshot_20260816_173317_Forge Master.jpg",
                PanelType.FORGE_COMPARISON,
                SourceId.BOOT,
                minimumSources = 2,
                names = listOf("Greaves", "Antimatter Feet"),
                health = listOf("2970000", "2040000"),
                subStats = listOf(
                    "CRITICAL_DAMAGE:13.6", "LIFESTEAL:16.4",
                    "MELEE_DAMAGE:49", "CRITICAL_CHANCE:10.3",
                ),
            ),
            Fixture(
                "Screenshot_20260817_102058_Forge Master.jpg",
                PanelType.SKILLS,
                SourceId.SKILLS,
                damages = listOf("21200"),
                health = listOf("170000"),
            ),
            Fixture(
                "Screenshot_20260817_104655_Forge Master.jpg",
                PanelType.EQUIPMENT_DETAIL,
                SourceId.WEAPON,
                names = listOf("Black Sword & Shield"),
                damages = listOf("296000"),
                health = listOf("1480000"),
                subStats = listOf("CRITICAL_CHANCE:9.64"),
            ),
            Fixture(
                "Screenshot_20260817_105148_Forge Master.jpg",
                PanelType.EQUIPMENT_DETAIL,
                SourceId.HEAD,
                names = listOf("Spite Crown"),
                health = listOf("3480000"),
                subStats = listOf("DOUBLE_CHANCE:6.34", "CRITICAL_CHANCE:10.5"),
            ),
            Fixture(
                "Screenshot_20260817_105155_Forge Master.jpg",
                PanelType.EQUIPMENT_DETAIL,
                SourceId.GLOVE,
                names = listOf("Grip of Torment"),
                damages = listOf("367000"),
                subStats = listOf("DAMAGE:14", "CRITICAL_CHANCE:11.2"),
            ),
            Fixture(
                "Screenshot_20260817_105204_Forge Master.jpg",
                PanelType.MOUNT_DETAIL,
                SourceId.MOUNT,
                names = listOf("Pig"),
                damages = listOf("307000"),
                health = listOf("2460000"),
                subStats = listOf("LIFESTEAL:19.9"),
            ),
            Fixture(
                "Screenshot_20260817_105212_Forge Master.jpg",
                PanelType.MOUNT_DETAIL,
                SourceId.MOUNT,
                names = listOf("Dino"),
                damages = listOf("11000"),
                health = listOf("88000"),
                subStats = listOf("LIFESTEAL:9.44"),
            ),
            Fixture(
                "Screenshot_20260817_105330_Forge Master.jpg",
                PanelType.PET_DETAIL,
                SourceId.PET_1,
                names = listOf("Cerberus"),
                damages = listOf("190000"),
                health = listOf("1340000"),
                subStats = listOf("MELEE_DAMAGE:10.9", "DOUBLE_CHANCE:1.5"),
            ),
            Fixture(
                "Screenshot_20260817_105333_Forge Master.jpg",
                PanelType.PET_DETAIL,
                SourceId.PET_1,
                names = listOf("Kitsune"),
                damages = listOf("285000"),
                health = listOf("672000"),
                subStats = listOf("BLOCK:4.83", "MELEE_DAMAGE:45.2"),
            ),
            Fixture(
                "Screenshot_20260817_105920_Forge Master.jpg",
                PanelType.TOTALS,
                minimumSources = 0,
                rawMustContain = listOf("8.14m Total Damage", "17.2m Total Health"),
            ),
            Fixture(
                "Screenshot_20260817_105926_Forge Master.jpg",
                PanelType.TOTALS,
                minimumSources = 0,
                rawMustContain = listOf("8.14m Total Damage", "17.2m Total Health"),
            ),
            Fixture(
                "Screenshot_20260817_105939_Forge Master.jpg",
                PanelType.PET_DETAIL,
                SourceId.PET_1,
                names = listOf("Serpent"),
                damages = listOf("291000"),
                health = listOf("685000"),
                subStats = listOf("MELEE_DAMAGE:19.2", "ATTACK_SPEED:11.6"),
            ),
            Fixture(
                "Screenshot_20260817_105941_Forge Master.jpg",
                PanelType.PET_DETAIL,
                SourceId.PET_2,
                names = listOf("Kitsune"),
                damages = listOf("285000"),
                health = listOf("672000"),
                subStats = listOf("BLOCK:4.83", "MELEE_DAMAGE:45.2"),
            ),
            Fixture(
                "Screenshot_20260817_105943_Forge Master.jpg",
                PanelType.PET_DETAIL,
                SourceId.PET_3,
                names = listOf("Kitsune"),
                damages = listOf("285000"),
                health = listOf("672000"),
                subStats = listOf("ATTACK_SPEED:39.8", "MELEE_DAMAGE:24.8"),
            ),
        )
    }
}
