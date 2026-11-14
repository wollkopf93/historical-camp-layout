package de.voculupo.historicalcamplayout

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import de.voculupo.historicalcamplayout.app.App
import de.voculupo.historicalcamplayout.features.planner.state.PlannerViewModel
import kotlinx.coroutines.runBlocking
import lagerplaner.composeapp.generated.resources.Res
import lagerplaner.composeapp.generated.resources.auto_setup_short
import org.jetbrains.compose.resources.getString
import org.junit.Assume.assumeTrue
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Renders the README screenshots of the desktop app and writes the sample camp as JSON for the Android shots.
 * Skipped unless SCREENSHOT_DIR is set: `SCREENSHOT_DIR=docs/screenshots ./gradlew :composeApp:jvmTest --tests '*ReadmeScreenshots*'`
 */
@OptIn(ExperimentalTestApi::class)
class ReadmeScreenshots {
    private val outDir = System.getenv("SCREENSHOT_DIR")?.let(::File)

    private fun sampleCamp(): PlannerViewModel =
        PlannerViewModel(InMemoryStorage()).apply {
            setProjectName("Summer camp")
            autoSetup(SAMPLE_REQUEST)
            setZoomLive(ZOOM)
        }

    private fun DesktopComposeUiTest.save(name: String) {
        waitForIdle()
        ImageIO.write(captureToImage().toAwtImage(), "png", File(outDir, "$name.png"))
    }

    private fun shoot(
        name: String,
        width: Int = 1600,
        height: Int = 1000,
        action: DesktopComposeUiTest.(PlannerViewModel) -> Unit = {},
    ) {
        assumeTrue("SCREENSHOT_DIR not set", outDir != null)
        outDir!!.mkdirs()
        // README is in English, so render the English strings whatever the machine's locale.
        val systemLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        try {
            runDesktopComposeUiTest(width = width, height = height) {
                val vm = sampleCamp()
                setContent { App(viewModel = vm) }
                waitForIdle()
                action(vm)
                save(name)
            }
        } finally {
            Locale.setDefault(systemLocale)
        }
    }

    @Test
    fun desktopSelection() =
        shoot("desktop-selection") { vm ->
            vm.setSelection(
                setOf(
                    vm.uiState.value.project.assets
                        .first()
                        .id,
                ),
            )
        }

    @Test
    fun desktopAutoSetup() =
        shoot("desktop-auto-setup") {
            onNodeWithText(runBlocking { getString(Res.string.auto_setup_short) }).performClick()
        }

    @Test
    fun sampleProjectJson() {
        assumeTrue("SCREENSHOT_DIR not set", outDir != null)
        outDir!!.mkdirs()
        val project = sampleCamp().uiState.value.project
        File(outDir, "sample-project.json").writeText(PlannerJson.json.encodeToString(project))
    }

    private companion object {
        // Pixels per meter that fit the whole sample parcel into the desktop canvas.
        const val ZOOM = 30f

        val SAMPLE_REQUEST =
            AutoSetupRequest(
                parcel = Parcel(30f, 22f),
                tentPresetIds = listOf("saxon_4x6", "round_5", "saxon_3x5", "a_tent_3x4_frame", "round_4", "saxon_2x4"),
                sailPresetIds = listOf("sun_sail_6x6_flat"),
                visitorSide = VisitorSide.BOTTOM,
                freeAreas = listOf(FreeArea(FreeAreaKind.FIRE, "Fire", 2f, 2f)),
            )
    }
}
