package com.posfab.shared.features.reports.common

import com.posfab.shared.features.reports.manager.ManagerRepository
import com.posfab.shared.features.reports.sales.SalesRepository

class ReportsUseCases(
    val salesRepository: SalesRepository,
    val managerRepository: ManagerRepository,
)
