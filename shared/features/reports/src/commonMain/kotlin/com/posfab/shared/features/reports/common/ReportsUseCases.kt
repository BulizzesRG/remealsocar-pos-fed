package com.posfab.shared.features.reports.common

import com.posfab.shared.features.reports.daily.DailyHistoryRepository
import com.posfab.shared.features.reports.manager.ManagerRepository

class ReportsUseCases(
    val dailyHistoryRepository: DailyHistoryRepository,
    val managerRepository: ManagerRepository,
)
