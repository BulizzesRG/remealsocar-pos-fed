package com.posfab.app

import com.posfab.shared.core.device.DeviceStorage
import com.posfab.shared.core.model.DeviceRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class DesktopDeviceStorage(
    private val deviceFile: Path,
) : DeviceStorage {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    override suspend fun load(): DeviceRegistration? = withContext(Dispatchers.IO) {
        if (!deviceFile.exists()) return@withContext null
        runCatching { json.decodeFromString<DeviceRegistration>(deviceFile.readText()) }.getOrNull()
    }

    override suspend fun save(registration: DeviceRegistration) {
        withContext(Dispatchers.IO) {
            deviceFile.parent?.createDirectories()
            deviceFile.writeText(json.encodeToString(DeviceRegistration.serializer(), registration))
        }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            if (deviceFile.exists()) {
                Files.deleteIfExists(deviceFile)
            }
        }
    }
}
