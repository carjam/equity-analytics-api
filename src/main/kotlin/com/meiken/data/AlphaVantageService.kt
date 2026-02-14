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

private const val BASE_URL = "https://www.alphavantage.co/query"
private const val TIME_SERIES_KEY = "Time Series (Daily)"
private const val ADJUSTED_CLOSE_KEY = "5. adjusted close"
private const val ERROR_MESSAGE_KEY = "Error Message"
private const val NOTE_KEY = "Note"

/**
 * Implements [MarketDataService] using Alpha Vantage TIME_SERIES_DAILY_ADJUSTED API.
 * Throws [SymbolNotFoundException] for invalid symbol, [DataRetrievalException] for other errors.
 */
class AlphaVantageService(
    private val client: HttpClient,
    private val apiKey: String
) : MarketDataService {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getHistoricalPrices(symbol: String, fromDate: LocalDate, toDate: LocalDate): List<DailyPrice> {
        val response = try {
            client.get(BASE_URL) {
                parameter("function", "TIME_SERIES_DAILY_ADJUSTED")
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

        val timeSeries = root[TIME_SERIES_KEY]?.jsonObject
            ?: throw DataRetrievalException("Missing 'Time Series (Daily)' in response for $symbol")

        val fromEpoch = fromDate.toEpochDays()
        val toEpoch = toDate.toEpochDays()

        return timeSeries.entries
            .mapNotNull { (dateStr, dayObj) ->
                val date = parseDate(dateStr) ?: return@mapNotNull null
                if (date.toEpochDays() !in fromEpoch..toEpoch) return@mapNotNull null
                val closeStr = (dayObj as? JsonObject)?.get(ADJUSTED_CLOSE_KEY)?.jsonPrimitive?.content
                    ?: return@mapNotNull null
                val close = closeStr.toDoubleOrNull() ?: return@mapNotNull null
                DailyPrice(date = date, close = close)
            }
            .sortedBy { it.date.toEpochDays() }
    }

    private fun parseDate(s: String): LocalDate? = try {
        LocalDate.parse(s)
    } catch (_: Exception) {
        null
    }
}
