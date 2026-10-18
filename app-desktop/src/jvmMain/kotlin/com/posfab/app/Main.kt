package com.posfab.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.LocalWindowExceptionHandlerFactory
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowExceptionHandler
import androidx.compose.ui.window.WindowExceptionHandlerFactory
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.posfab.shared.config.PosConfig
import com.posfab.shared.config.PosConfigLoader
import com.posfab.shared.config.PosEnv
import com.posfab.shared.core.diagnostics.AppBuildInfo
import com.posfab.shared.core.diagnostics.CrashRecord
import com.posfab.shared.core.diagnostics.CrashReporter
import com.posfab.shared.core.diagnostics.SupportBundle
import com.posfab.shared.core.logging.AppLogger
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.get
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities

private const val WINDOW_TITLE = "REMEALSOCAR POS"
private const val SUPPORT_LOG_LIMIT = 200
private const val PREVIOUS_CRASHES = 4

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val config = PosConfigLoader.fromMap(System.getenv())
    AppLogger.init(config.enableVerboseLogs)
    Thread.setDefaultUncaughtExceptionHandler { _, t -> CrashReporter.report(t) }

    startKoin {
        modules(desktopModule(config))
    }

    val buildInfo = get<AppBuildInfo>(AppBuildInfo::class.java)
    val home = System.getProperty("user.home")
    CrashReporter.install(FileCrashSink(Paths.get(home, ".pos-fab-fed", "errores.log")), buildInfo.version)
    val controller = get<AppStateController>(AppStateController::class.java)

    application {
        var crash by remember { mutableStateOf<CrashRecord?>(null) }
        var generation by remember { mutableStateOf(0) }
        val windowState = rememberWindowState(
            placement = if (config.fullscreen) WindowPlacement.Fullscreen else WindowPlacement.Maximized,
        )
        val exit = ::exitApplication
        val factory = remember {
            WindowExceptionHandlerFactory { _ ->
                val handled = AtomicBoolean(false)
                WindowExceptionHandler { t ->
                    if (handled.compareAndSet(false, true)) {
                        val crashScreenFailed = crash != null
                        val record = CrashReporter.report(t)
                        SwingUtilities.invokeLater {
                            if (crashScreenFailed) {
                                exit()
                            } else {
                                crash = record
                                generation++
                            }
                        }
                    }
                }
            }
        }

        CompositionLocalProvider(LocalWindowExceptionHandlerFactory provides factory) {
            key(generation) {
                var forceCrash by remember { mutableStateOf(false) }
                Window(
                    state = windowState,
                    onCloseRequest = ::exitApplication,
                    title = WINDOW_TITLE,
                    onPreviewKeyEvent = { event ->
                        val matched = crash == null && isDevCrashShortcut(config, event)
                        if (matched) forceCrash = true
                        matched
                    },
                ) {
                    val current = crash
                    if (current != null) {
                        CrashScreen(
                            record = current,
                            onBackHome = { crash = null },
                            onCopySupport = { crashSupportBundle(buildInfo, current) },
                        )
                    } else {
                        if (forceCrash) error(CrashStrings.FORCED)
                        PosApp(controller)
                    }
                }
            }
        }
    }
}

private fun isDevCrashShortcut(config: PosConfig, event: KeyEvent): Boolean =
    config.env == PosEnv.DEV &&
        event.type == KeyEventType.KeyDown &&
        event.isCtrlPressed &&
        event.isAltPressed &&
        event.isShiftPressed &&
        event.key == Key.X

private fun crashSupportBundle(buildInfo: AppBuildInfo, record: CrashRecord): String =
    SupportBundle.build(
        header = listOf("version" to buildInfo.version, "screen" to record.screen),
        logs = AppLogger.recentSupportEntries(SUPPORT_LOG_LIMIT),
        crashes = (listOf(record) + CrashReporter.recent(PREVIOUS_CRASHES + 1).filterNot { it == record })
            .take(PREVIOUS_CRASHES + 1),
    )
