package de.voculupo.historicalcamplayout

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import de.voculupo.historicalcamplayout.app.App
import de.voculupo.historicalcamplayout.features.planner.state.PlannerViewModel

fun main() =
    application {
        // Created here (not inside App) so the close handler can flush the last edits to disk.
        val viewModel = remember { PlannerViewModel() }
        Window(
            onCloseRequest = {
                // Autosave is debounced; this catches edits from the last second before closing.
                viewModel.saveProject()
                exitApplication()
            },
            title = "Lagerplaner",
            state =
                rememberWindowState(
                    width = 1920.dp,
                    height = 1200.dp,
                ),
        ) {
            App(viewModel = viewModel)
        }
    }
