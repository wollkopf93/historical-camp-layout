package de.voculupo.historicalcamplayout

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import de.voculupo.historicalcamplayout.app.App

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Lagerplaner",
        state = rememberWindowState(
            width = 1920.dp,
            height = 1200.dp,
        ),
    ) {
        App()
    }
}
