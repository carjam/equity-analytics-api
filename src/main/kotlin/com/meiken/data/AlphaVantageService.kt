package com.meiken.data

import com.meiken.error.DataRetrievalException
import com.meiken.error.SymbolNotFoundException
import com.meiken.model.DailyPrice
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

private const val BASE_URL = "https://www.alphavantage.co/query"
private const val TIME_SERIES_KEY = "Time Series (Daily)"
private const val OPEN_KEY = "1. open"
private const val HIGH_KEY = "2. high"
private const val LOW_KEY = "3. low"
private const val CLOSE_KEY = "4. close"
private const val VOLUME_KEY = "5. volume"
private const val ERROR_MESSAGE_KEY = "Error Message"
private const val NOTE_KEY = "Note"
private const val INFORMATION_KEY = "Information"
private const val RAW_RESPONSE_LOG_LIMIT = 500

/**
 * Implements [MarketDataService] using Alpha Vantage TIME_SERIES_DAILY API.
 * Parses OHLCV from "Time Series (Daily)" and uses "4. close" for returns.
 * Throws [SymbolNotFoundException] for invalid symbol, [DataRetrievalException] for other errors.
 */
class AlphaVantageService(
    private val client: HttpClient,
    private val apiKey: String
) : MarketDataService {

    private val json = Json { ignoreUnknownKeys = true }
    private val log = LoggerFactory.getLogger(AlphaVantageService::class.java)

    /** Calls Alpha Vantage TIME_SERIES_DAILY, filters to [fromDate, toDate], returns daily OHLCV. Throws [SymbolNotFoundException] on error message, [DataRetrievalException] on network/parse/rate limit. */
    override suspend fun getHistoricalPrices(symbol: String, fromDate: LocalDate, toDate: LocalDate): List<DailyPrice> {
        val response = try {
            client.get(BASE_URL) {
                parameter("function", "TIME_SERIES_DAILY")
                parameter("symbol", symbol)
                parameter("apikey", apiKey)
                parameter("outputsize", "full")
            }.body<String>()
        } catch (e: Exception) {
            throw DataRetrievalException("Failed to fetch data for $symbol: ${e.message}", e)
        }

        val root = try {
            json.parseToJsonElement(response).jsonObject
        } catch (e: Exception) {
            log.error("Invalid JSON response for {}: {}", symbol, e.message)
            log.debug("Raw response (first {} chars): {}", RAW_RESPONSE_LOG_LIMIT, response.take(RAW_RESPONSE_LOG_LIMIT))
            throw DataRetrievalException("Invalid JSON response for $symbol", e)
        }

        if (root.containsKey(ERROR_MESSAGE_KEY)) {
            val msg = root[ERROR_MESSAGE_KEY]?.jsonPrimitive?.content ?: "Invalid symbol"
            throw SymbolNotFoundException(msg)
        }
        if (root.containsKey(NOTE_KEY)) {
            val note = root[NOTE_KEY]?.jsonPrimitive?.content ?: "Rate limit or API notice"
            throw DataRetrievalException(note)
        }
        if (root.containsKey(INFORMATION_KEY)) {
            val info = root[INFORMATION_KEY]?.jsonPrimitive?.content ?: "API information or rate limit"
            log.warn("Alpha Vantage returned 'Information' for {}: {}", symbol, info)
            throw DataRetrievalException(info)
        }

        val timeSeries = root[TIME_SERIES_KEY]?.jsonObject
        if (timeSeries == null) {
            val topLevelKeys = root.keys.joinToString(", ")
            log.error("Missing 'Time Series (Daily)' in response for {}. Top-level keys: {}", symbol, topLevelKeys)
            log.debug("Raw response (first {} chars): {}", RAW_RESPONSE_LOG_LIMIT, response.take(RAW_RESPONSE_LOG_LIMIT))
            throw DataRetrievalException("Missing 'Time Series (Daily)' in response for $symbol. Top-level keys: $topLevelKeys")
        }

        val fromEpoch = fromDate.toEpochDays()
        val toEpoch = toDate.toEpochDays()

        return timeSeries.entries
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
    }

    /** Parses YYYY-MM-DD to [LocalDate]; returns null on parse failure. */
    private fun parseDate(s: String): LocalDate? = try {
        LocalDate.parse(s)
    } catch (_: Exception) {
        null
    }
}
