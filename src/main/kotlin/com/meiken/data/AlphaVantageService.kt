package com.meiken.data

import com.meiken.config.DataQualityConfig
import com.meiken.error.DataRetrievalException
import com.meiken.error.SymbolNotFoundException
import com.meiken.model.DailyPrice
import com.meiken.observability.Metrics
import com.meiken.util.MarketCalendar
import com.meiken.validator.DataValidator
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

private const val TIME_SERIES_KEY = "Time Series (Daily)"
private const val OPEN_KEY = "1. open"
private const val HIGH_KEY = "2. high"
private const val LOW_KEY = "3. low"
private const val CLOSE_KEY = "4. close"
private const val VOLUME_KEY = "5. volume"
private const val ERROR_MESSAGE_KEY = "Error Message"
private const val NOTE_KEY = "Note"
private const val INFORMATION_KEY = "Information"

/**
 * Implements [MarketDataService] using Alpha Vantage TIME_SERIES_DAILY API.
 * [baseUrl], [dataQualityConfig], [rawResponseLogLimit], and [sparseRatio] come from config.
 * [outputSize]: "compact" = last ~100 trading days (~4 months, free tier); "full" = full history (premium).
 * [useLimiterMessages]: when true (non-prod), no/insufficient data throws a message suggesting upgrade; when false (prod), generic message.
 * Throws [SymbolNotFoundException] for invalid symbol, [DataRetrievalException] for other errors.
 */
class AlphaVantageService(
    private val client: HttpClient,
    private val apiKey: String,
    private val baseUrl: String,
    private val outputSize: String = "compact",
    private val useLimiterMessages: Boolean = true,
    private val dataQualityConfig: DataQualityConfig? = null,
    private val rawResponseLogLimit: Int = 500,
    private val sparseRatio: Double = 0.5
) : MarketDataService {

    private val json = Json { ignoreUnknownKeys = true }
    private val log = LoggerFactory.getLogger(AlphaVantageService::class.java)

    /** Fetches close-of-day prices only: one bar per calendar day from TIME_SERIES_DAILY; uses "4. close". outputSize (compact/full) and limiter messages are config-driven. */
    override suspend fun getHistoricalPrices(symbol: String, fromDate: LocalDate, toDate: LocalDate): List<DailyPrice> {
        val startNanos = System.nanoTime()
        return try {
            val response = try {
                client.get(baseUrl) {
                    parameter("function", "TIME_SERIES_DAILY")
                    parameter("symbol", symbol)
                    parameter("apikey", apiKey)
                    parameter("outputsize", outputSize)
                }.body<String>()
            } catch (e: Exception) {
                Metrics.recordAlphaVantageCall(symbol, "error")
                Metrics.recordAlphaVantageDuration((System.nanoTime() - startNanos) / 1e9)
                throw DataRetrievalException("Failed to fetch data for $symbol: ${e.message}", e)
            }

        val root = try {
            json.parseToJsonElement(response).jsonObject
        } catch (e: Exception) {
            log.error("Invalid JSON response for {}: {}", symbol, e.message)
            log.debug("Raw response (first {} chars): {}", rawResponseLogLimit, response.take(rawResponseLogLimit))
            Metrics.recordAlphaVantageCall(symbol, "error")
            Metrics.recordAlphaVantageDuration((System.nanoTime() - startNanos) / 1e9)
            throw DataRetrievalException("Invalid JSON response for $symbol", e)
        }

        if (root.containsKey(ERROR_MESSAGE_KEY)) {
            val msg = root[ERROR_MESSAGE_KEY]?.jsonPrimitive?.content ?: "Invalid symbol"
            Metrics.recordAlphaVantageCall(symbol, "symbol_not_found")
            Metrics.recordAlphaVantageDuration((System.nanoTime() - startNanos) / 1e9)
            throw SymbolNotFoundException(msg)
        }
        if (root.containsKey(NOTE_KEY)) {
            val note = root[NOTE_KEY]?.jsonPrimitive?.content ?: "Rate limit or API notice"
            Metrics.recordAlphaVantageCall(symbol, "rate_limit")
            Metrics.recordAlphaVantageDuration((System.nanoTime() - startNanos) / 1e9)
            throw DataRetrievalException(note)
        }
        if (root.containsKey(INFORMATION_KEY)) {
            val info = root[INFORMATION_KEY]?.jsonPrimitive?.content ?: "API information or rate limit"
            log.warn("Alpha Vantage returned 'Information' for {}: {}", symbol, info)
            Metrics.recordAlphaVantageCall(symbol, "rate_limit")
            Metrics.recordAlphaVantageDuration((System.nanoTime() - startNanos) / 1e9)
            throw DataRetrievalException(info)
        }

        val timeSeries = root[TIME_SERIES_KEY]?.jsonObject
        if (timeSeries == null) {
            val topLevelKeys = root.keys.joinToString(", ")
            log.error("Missing 'Time Series (Daily)' in response for {}. Top-level keys: {}", symbol, topLevelKeys)
            log.debug("Raw response (first {} chars): {}", rawResponseLogLimit, response.take(rawResponseLogLimit))
            Metrics.recordAlphaVantageCall(symbol, "error")
            Metrics.recordAlphaVantageDuration((System.nanoTime() - startNanos) / 1e9)
            throw DataRetrievalException("Missing 'Time Series (Daily)' in response for $symbol. Top-level keys: $topLevelKeys")
        }

        val fromEpoch = fromDate.toEpochDays()
        val toEpoch = toDate.toEpochDays()

        val rawPrices = timeSeries.entries
            .mapNotNull { (dateStr, dayObj) ->
                val date = parseDate(dateStr) ?: return@mapNotNull null
                if (date.toEpochDays() !in fromEpoch..toEpoch) return@mapNotNull null
                val obj = dayObj as? JsonObject ?: return@mapNotNull null
                val closeStr = obj[CLOSE_KEY]?.jsonPrimitive?.content ?: return@mapNotNull null
                val close = closeStr.toDoubleOrNull() ?: return@mapNotNull null
                val open = obj[OPEN_KEY]?.jsonPrimitive?.content?.toDoubleOrNull()
                val high = obj[HIGH_KEY]?.jsonPrimitive?.content?.toDoubleOrNull()
                val low = obj[LOW_KEY]?.jsonPrimitive?.content?.toDoubleOrNull()
                val volume = obj[VOLUME_KEY]?.jsonPrimitive?.content?.toLongOrNull()
                DailyPrice(date = date, close = close, open = open, high = high, low = low, volume = volume)
            }
            .sortedBy { it.date.toEpochDays() }

        // Validate and clean prices (unrealistic values, duplicates, negative volume); record data-quality metrics.
        val validationResult = DataValidator.validatePriceData(rawPrices, symbol, dataQualityConfig)
        val prices = validationResult.cleanedPrices
        for (issue in validationResult.issues) {
            Metrics.recordDataQualityIssue(issue.type, symbol)
        }
        // Compare actual points to expected US trading days; warn and record metric if data is sparse.
        val expectedTradingDays = MarketCalendar.getTradingDays(fromDate, toDate)
        if (expectedTradingDays > 0 && prices.isNotEmpty() && prices.size.toDouble() / expectedTradingDays < sparseRatio) {
            log.warn("Sparse data for {}: {} points vs {} expected trading days", symbol, prices.size, expectedTradingDays)
            Metrics.recordDataQualityIssue("sparse_data", symbol)
        }

        if (prices.isEmpty()) {
            Metrics.recordAlphaVantageCall(symbol, "no_data")
            Metrics.recordAlphaVantageDuration((System.nanoTime() - startNanos) / 1e9)
            throw DataRetrievalException(
                if (useLimiterMessages)
                    "No data in the requested date range for $symbol. The free-tier API (outputsize=compact) returns only the last ~100 trading days (~4 months). For longer history, upgrade to a premium API key that supports outputsize=full."
                else
                    "No data in the requested date range for $symbol. Check the symbol and date range."
            )
        }
        if (prices.size < 2) {
            Metrics.recordAlphaVantageCall(symbol, "insufficient_data")
            Metrics.recordAlphaVantageDuration((System.nanoTime() - startNanos) / 1e9)
            throw DataRetrievalException(
                if (useLimiterMessages)
                    "Insufficient data for $symbol in the requested range (need at least 2 days). Free tier returns the last ~100 trading days; for a wider range, upgrade to a premium API key."
                else
                    "Insufficient data for $symbol in the requested range (need at least 2 days)."
            )
        }
        Metrics.recordAlphaVantageCall(symbol, "success")
        Metrics.recordAlphaVantageDuration((System.nanoTime() - startNanos) / 1e9)
        return prices
    } finally {
        // try block may return or throw; metrics already recorded on each path
    }
    }

    /** Parses YYYY-MM-DD to [LocalDate]; returns null on parse failure. */
    private fun parseDate(s: String): LocalDate? = try {
        LocalDate.parse(s)
    } catch (_: Exception) {
        null
    }
}
