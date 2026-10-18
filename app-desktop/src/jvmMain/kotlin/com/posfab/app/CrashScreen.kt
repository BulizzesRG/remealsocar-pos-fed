package com.posfab.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.posfab.shared.core.diagnostics.CrashRecord
import com.posfab.shared.ui.components.PosBrandMark
import com.posfab.shared.ui.components.PosNoticeRow
import com.posfab.shared.ui.components.PosPrimaryButton
import com.posfab.shared.ui.components.PosSecondaryButton
import com.posfab.shared.ui.theme.PosSpacing
import com.posfab.shared.ui.theme.PosTheme
import java.time.Instant
import java.time.ZoneId

object CrashStrings {
    const val TITLE = "Algo salió mal"
    const val BODY = "La pantalla tuvo un error. Tus cuentas y tus ventas están guardadas en el servidor."
    const val BACK_HOME = "Volver al inicio"
    const val COPY_SUPPORT = "Copiar información para soporte"
    const val COPIED = "Información copiada"
    const val OCCURRED_AT = "Ocurrió a las"
    const val OCCURRED_IN = "en"
    const val FORCED = "Error de prueba provocado con Ctrl+Alt+Shift+X"

    fun occurred(time: String, screen: String): String = "$OCCURRED_AT $time $OCCURRED_IN $screen"
}

@Composable
fun CrashScreen(
    record: CrashRecord?,
    onBackHome: () -> Unit,
    onCopySupport: () -> String,
) {
    val clipboardManager = LocalClipboardManager.current
    var notice by remember(record) { mutableStateOf<String?>(null) }

    PosTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.fillMaxSize().padding(PosSpacing.lg), contentAlignment = Alignment.Center) {
                Column(
                    modifier = Modifier.widthIn(max = 520.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(PosSpacing.md),
                ) {
                    PosBrandMark()
                    Text(
                        CrashStrings.TITLE,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        CrashStrings.BODY,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                    )
                    PosPrimaryButton(text = CrashStrings.BACK_HOME, onClick = onBackHome)
                    PosSecondaryButton(
                        text = CrashStrings.COPY_SUPPORT,
                        onClick = {
                            clipboardManager.setText(AnnotatedString(onCopySupport()))
                            notice = CrashStrings.COPIED
                        },
                        icon = Icons.Filled.ContentCopy,
                    )
                    PosNoticeRow(notice = notice, errorMessage = null)
                    record?.let {
                        Text(
                            CrashStrings.occurred(crashTimeLabel(it.at), it.screen),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

internal fun crashTimeLabel(iso: String, zone: ZoneId = ZoneId.systemDefault()): String =
    runCatching {
        val local = Instant.parse(iso).atZone(zone)
        "${local.hour}:${local.minute.toString().padStart(2, '0')}"
    }.getOrDefault(iso)
