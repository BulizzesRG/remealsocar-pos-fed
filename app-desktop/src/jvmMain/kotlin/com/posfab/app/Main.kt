package com.posfab.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.posfab.shared.config.PosConfigLoader
import com.posfab.shared.core.logging.AppLogger
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.get

fun main() {
    val config = PosConfigLoader.fromMap(System.getenv())
    AppLogger.init(config.enableVerboseLogs)

    startKoin {
        modules(desktopModule(config))
    }

    application {
        Window(onCloseRequest = ::exitApplication, title = "REMEALSOCAR POS") {
            PosApp(controller = get(AppStateController::class.java))
        }
    }
}
