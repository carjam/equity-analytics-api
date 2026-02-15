package com.meiken.config

/**
 * Test-only config instances so tests do not depend on application.conf.
 * Use for SymbolAnalyticsCacheService, etc.
 */
object TestConfig {
    val cacheConfig = CacheConfig(
        ttlSeconds = 3600L,
        maxSize = 1000,
        staleSeconds = 86400L,
        oneHourSeconds = 3600L,
        gapThreshold = 5,
        recentDatesDays = 5,
        sparseRatio = 0.5
    )
    val calculationsConfig = CalculationsConfig(tradingDaysPerYear = 252)
    val dataQualityConfig = DataQualityConfig(
        minPrice = 0.01,
        maxPrice = 1_000_000.0,
        maxSingleDayChangePct = 0.50,
        outlierSigma = 3.0
    )
}
