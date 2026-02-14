package com.meiken.service

import com.meiken.model.Returns
import kotlinx.datetime.LocalDate

interface ReturnsService {
    suspend fun calculateReturns(symbol: String, fromDate: LocalDate, toDate: LocalDate): Returns
}
