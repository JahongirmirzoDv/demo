@file:OptIn(ExperimentalResourceApi::class)

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.jetbrains.compose.resources.ExperimentalResourceApi
import ui.App
import util.ConfigLoader

// Initialize logger
private val logger: Logger = LogManager.getLogger("Main")

fun main() = application {
    // Load configuration
    ConfigLoader.loadConfig()

    // Test logging
    logger.info("Application starting...")
    logger.debug("Debug logging is enabled")
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
