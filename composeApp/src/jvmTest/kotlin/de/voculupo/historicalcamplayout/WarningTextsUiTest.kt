package de.voculupo.historicalcamplayout

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import de.voculupo.historicalcamplayout.features.planner.state.PlannerViewModel
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class WarningTextsUiTest {
    private fun warningTexts(locale: Locale): List<String> {
        val systemLocale = Locale.getDefault()
        Locale.setDefault(locale)
        try {
            var texts = emptyList<String>()
            runDesktopComposeUiTest {
                val vm =
                    PlannerViewModel(InMemoryStorage()).apply {
                        placePresetAt("round_4", Vec2(10f, 10f))
                        placePresetAt("round_4", Vec2(11f, 10f))
                    }
                setContent { texts = rememberWarningTexts(vm.uiState.value) }
                waitForIdle()
            }
            return texts
        } finally {
            Locale.setDefault(systemLocale)
        }
    }

    @Test
    fun collision_isWordedInAppLanguage_withTranslatedTentNames() {
        assertEquals(listOf("Collision between Round tent 4m and Round tent 4m."), warningTexts(Locale.ENGLISH))
        assertEquals(listOf("Kollision zwischen Rundzelt 4m und Rundzelt 4m."), warningTexts(Locale.GERMAN))
    }
}
