package com.meiken.service

import com.meiken.model.Alpha
import kotlinx.datetime.LocalDate

interface AlphaService {
    suspend fun calculateAlpha(
        target: String,
        benchmark: String,
        fromDate: LocalDate,
        toDate: LocalDate
    ): Alpha
}
