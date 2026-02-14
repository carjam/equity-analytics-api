package com.meiken.service

import com.meiken.model.BetaResponse
import com.meiken.model.CorrelationResponse
import com.meiken.model.SharpeResponse
import com.meiken.model.VolatilityResponse
import kotlinx.datetime.LocalDate

interface AnalyticsService {
    suspend fun calculateVolatility(symbol: String, fromDate: LocalDate, toDate: LocalDate): VolatilityResponse
    suspend fun calculateBeta(target: String, benchmark: String, fromDate: LocalDate, toDate: LocalDate): BetaResponse
    suspend fun calculateSharpe(symbol: String, fromDate: LocalDate, toDate: LocalDate, riskFreeRate: Double): SharpeResponse
    suspend fun calculateCorrelation(ticker1: String, ticker2: String, fromDate: LocalDate, toDate: LocalDate, window: Int): CorrelationResponse
}
