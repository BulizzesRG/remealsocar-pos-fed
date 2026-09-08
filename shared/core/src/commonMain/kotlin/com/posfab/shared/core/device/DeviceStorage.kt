package com.posfab.shared.core.device

import com.posfab.shared.core.model.DeviceRegistration

interface DeviceStorage {
    suspend fun load(): DeviceRegistration?
    suspend fun save(registration: DeviceRegistration)
    suspend fun clear()
}
