@file:OptIn(ExperimentalResourceApi::class)

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import org.jetbrains.compose.resources.ExperimentalResourceApi
import ui.App

fun main() = application {
    // Initialize Koin for dependency injection
    di.initKoin()
    
    // Load icon resource
//    val icon = util.painterResourceC("icons/png_icon.png")

    Window(
        onCloseRequest = ::exitApplication,
        title = "Hujjat (AKT) To'ldiruvchi",
        state = WindowState(placement = WindowPlacement.Maximized),
//        icon = icon
    ) {
        App()
    }
}