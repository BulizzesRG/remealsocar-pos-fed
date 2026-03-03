package com.posfab.app.sale

import com.posfab.shared.features.sale.ui.SaleUiRecoveryState
import com.posfab.shared.features.sale.ui.SaleUiStateStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class DesktopSaleUiStateStore(
    private val stateFile: Path,
) : SaleUiStateStore {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    override suspend fun load(): SaleUiRecoveryState? = withContext(Dispatchers.IO) {
        if (!stateFile.exists()) return@withContext null
        runCatching {
            val obj = json.parseToJsonElement(stateFile.readText()).jsonObject
            SaleUiRecoveryState(
                searchQuery = obj["searchQuery"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                barcodeInput = obj["barcodeInput"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                selectedLineId = obj["selectedLineId"]?.jsonPrimitive?.contentOrNull,
                draftId = obj["draftId"]?.jsonPrimitive?.contentOrNull,
                savedAtEpochMs = obj["savedAtEpochMs"]?.jsonPrimitive?.longOrNull ?: 0L,
            )
        }.getOrNull()
    }

    override suspend fun save(state: SaleUiRecoveryState) {
        withContext(Dispatchers.IO) {
            stateFile.parent?.createDirectories()
            val payload = buildJsonObject {
                put("searchQuery", state.searchQuery)
                put("barcodeInput", state.barcodeInput)
                state.selectedLineId?.let { put("selectedLineId", it) }
                state.draftId?.let { put("draftId", it) }
                put("savedAtEpochMs", state.savedAtEpochMs)
            }
            stateFile.writeText(payload.toString())
        }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            if (stateFile.exists()) Files.deleteIfExists(stateFile)
        }
    }
}
