package com.joaodegrandi.forgemasterassistente.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun showsEssentialMvpControls() {
        composeRule.onNodeWithText("ForgeMaster Assistente").assertIsDisplayed()
        composeRule.onNodeWithText("Modo de cálculo").assertIsDisplayed()
        composeRule.onNodeWithText("Iniciar bolha sobre o jogo").assertIsDisplayed()
        composeRule.onNodeWithText("Calibração").assertIsDisplayed()

        composeRule.onNodeWithText("Recortes").performClick()
        composeRule.onNodeWithText("Ajuste simples dos recortes").assertIsDisplayed()
        composeRule.onNodeWithText(
            "A prévia real fica disponível ao abrir este ajuste pelo toque longo na bolha.",
        ).assertIsDisplayed()
    }
}
