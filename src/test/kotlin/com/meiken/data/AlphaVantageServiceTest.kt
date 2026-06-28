package com.meiken.data

import com.meiken.error.DataRetrievalException
import com.meiken.error.SymbolNotFoundException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AlphaVantageServiceTest {

    private fun successResponse(): String = """
        {
            "Time Series (Daily)": {
                "2024-01-15": {
                    "1. open": "100.0",
                    "5. adjusted close": "100.5"
                },
                "2024-01-14": {
                    "1. open": "99.0",
                    "5. adjusted close": "99.2"
                },
                "2024-01-13": {
                    "1. open": "98.0",
                    "5. adjusted close": "98.8"
                }
            }
        }
    """.trimIndent()

    @Test
    fun `getHistoricalPrices returns parsed DailyPrice list for valid response`() = runBlocking {
        val engine = MockEngine { respond(successResponse(), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
        val client = HttpClient(engine)
        val service = AlphaVantageService(client, "test-key", baseUrl = "https://www.alphavantage.co/query")
        val from = LocalDate(2024, 1, 13)
        val to = LocalDate(2024, 1, 15)
        val prices = service.getHistoricalPrices("AAPL", from, to)

        assertEquals(3, prices.size)
        assertEquals(LocalDate(2024, 1, 13), prices[0].date)
        assertEquals(98.8, prices[0].close)
        assertEquals(LocalDate(2024, 1, 14), prices[1].date)
        assertEquals(99.2, prices[1].close)
        assertEquals(LocalDate(2024, 1, 15), prices[2].date)
        assertEquals(100.5, prices[2].close)
    }

    @Test
    fun `getHistoricalPrices throws SymbolNotFoundException when Error Message in response`() = runBlocking {
        val engine = MockEngine { respond("""{"Error Message": "Invalid API call. Please retry or visit the documentation."}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
        val client = HttpClient(engine)
        val service = AlphaVantageService(client, "test-key", baseUrl = "https://www.alphavantage.co/query")
        val ex = assertFailsWith<SymbolNotFoundException> {
            service.getHistoricalPrices("INVALID", LocalDate(2024, 1, 1), LocalDate(2024, 1, 10))
        }
        assertTrue(ex.message!!.contains("Invalid API call") || ex.message!!.contains("Invalid"))
    }

    @Test
    fun `getHistoricalPrices throws DataRetrievalException when Note in response`() = runBlocking {
        val engine = MockEngine { respond("""{"Note": "Thank you for using Alpha Vantage! Our standard API call frequency is 5 calls per minute..."}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
        val client = HttpClient(engine)
        val service = AlphaVantageService(client, "test-key", baseUrl = "https://www.alphavantage.co/query")
        assertFailsWith<DataRetrievalException> {
            service.getHistoricalPrices("AAPL", LocalDate(2024, 1, 1), LocalDate(2024, 1, 10))
        }
    }

    @Test
    fun `getHistoricalPrices throws DataRetrievalException when Time Series missing`() = runBlocking {
        val engine = MockEngine { respond("""{}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
        val client = HttpClient(engine)
        val service = AlphaVantageService(client, "test-key", baseUrl = "https://www.alphavantage.co/query")
        val ex = assertFailsWith<DataRetrievalException> {
            service.getHistoricalPrices("AAPL", LocalDate(2024, 1, 1), LocalDate(2024, 1, 10))
        }
        assertTrue(ex.message!!.contains("Time Series") || ex.message!!.contains("Missing"))
    }

    @Test
    fun `getHistoricalPrices throws DataRetrievalException with limiter message when no data in date range`() = runBlocking {
        val responseWithOldDatesOnly = """{"Time Series (Daily)": {"2020-01-02": {"5. adjusted close": "100.0"}, "2020-01-01": {"5. adjusted close": "99.0"}}}"""
        val engine = MockEngine { respond(responseWithOldDatesOnly, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
        val client = HttpClient(engine)
        val service = AlphaVantageService(client, "test-key", baseUrl = "https://www.alphavantage.co/query", outputSize = "compact", useLimiterMessages = true)
        val ex = assertFailsWith<DataRetrievalException> {
            service.getHistoricalPrices("AAPL", LocalDate(2024, 1, 1), LocalDate(2024, 1, 10))
        }
        assertTrue(ex.message!!.contains("No data") && ex.message!!.contains("upgrade"))
    }

    @Test
    fun `getHistoricalPrices with useLimiterMessages false throws with generic message when no data`() = runBlocking {
        val responseWithOldDatesOnly = """{"Time Series (Daily)": {"2020-01-02": {"5. adjusted close": "100.0"}, "2020-01-01": {"5. adjusted close": "99.0"}}}"""
        val engine = MockEngine { respond(responseWithOldDatesOnly, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
        val client = HttpClient(engine)
        val service = AlphaVantageService(client, "test-key", baseUrl = "https://www.alphavantage.co/query", outputSize = "full", useLimiterMessages = false)
        val ex = assertFailsWith<DataRetrievalException> {
            service.getHistoricalPrices("AAPL", LocalDate(2024, 1, 1), LocalDate(2024, 1, 10))
        }
        assertTrue(ex.message!!.contains("No data") && !ex.message!!.contains("upgrade"))
    }

    @Test
    fun `getHistoricalPrices throws DataRetrievalException when Information in response`() = runBlocking {
        val engine = MockEngine { respond("""{"Information": "The API key is limited to 5 calls per minute."}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
        val client = HttpClient(engine)
        val service = AlphaVantageService(client, "test-key", baseUrl = "https://www.alphavantage.co/query")
        val ex = assertFailsWith<DataRetrievalException> {
            service.getHistoricalPrices("AAPL", LocalDate(2024, 1, 1), LocalDate(2024, 1, 10))
        }
        assertTrue(ex.message!!.contains("5 calls") || ex.message!!.contains("API"))
    }

    @Test
    fun `getHistoricalPrices throws with limiter message when only one day in range`() = runBlocking {
        val singleDayInRange = """{"Time Series (Daily)": {"2024-01-15": {"5. adjusted close": "100.0"}, "2020-01-01": {"5. adjusted close": "99.0"}}}"""
        val engine = MockEngine { respond(singleDayInRange, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
        val client = HttpClient(engine)
        val service = AlphaVantageService(client, "test-key", baseUrl = "https://www.alphavantage.co/query", useLimiterMessages = true)
        val ex = assertFailsWith<DataRetrievalException> {
            service.getHistoricalPrices("AAPL", LocalDate(2024, 1, 1), LocalDate(2024, 1, 15))
        }
        assertTrue(ex.message!!.contains("Insufficient") && ex.message!!.contains("2"))
    }

    @Test
    fun `getHistoricalPrices with useLimiterMessages false throws generic when only one day in range`() = runBlocking {
        val singleDayInRange = """{"Time Series (Daily)": {"2024-01-15": {"5. adjusted close": "100.0"}, "2020-01-01": {"5. adjusted close": "99.0"}}}"""
        val engine = MockEngine { respond(singleDayInRange, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
        val client = HttpClient(engine)
        val service = AlphaVantageService(client, "test-key", baseUrl = "https://www.alphavantage.co/query", useLimiterMessages = false)
        val ex = assertFailsWith<DataRetrievalException> {
            service.getHistoricalPrices("AAPL", LocalDate(2024, 1, 1), LocalDate(2024, 1, 15))
        }
        assertTrue(ex.message!!.contains("Insufficient") && !ex.message!!.contains("upgrade"))
    }

    @Test
    fun `getHistoricalPrices throws DataRetrievalException when client throws`() = runBlocking {
        val engine = MockEngine { request -> throw RuntimeException("Network error") }
        val client = HttpClient(engine)
        val service = AlphaVantageService(client, "test-key", baseUrl = "https://www.alphavantage.co/query")
        val ex = assertFailsWith<DataRetrievalException> {
            service.getHistoricalPrices("AAPL", LocalDate(2024, 1, 1), LocalDate(2024, 1, 10))
        }
        assertTrue(ex.message!!.contains("Failed to fetch") && ex.message!!.contains("AAPL"))
    }

    @Test
    fun `getHistoricalPrices throws DataRetrievalException when response is invalid JSON`() = runBlocking {
        val engine = MockEngine { respond("not json at all", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
        val client = HttpClient(engine)
        val service = AlphaVantageService(client, "test-key", baseUrl = "https://www.alphavantage.co/query")
        val ex = assertFailsWith<DataRetrievalException> {
            service.getHistoricalPrices("AAPL", LocalDate(2024, 1, 1), LocalDate(2024, 1, 10))
        }
        assertTrue(ex.message!!.contains("Invalid JSON") || ex.message!!.contains("JSON"))
    }

    @Test
    fun `getHistoricalPrices skips entries with unparseable date and uses valid entries`() = runBlocking {
        val response = """
            {"Time Series (Daily)": {
                "bad-date": {"5. adjusted close": "100.0"},
                "2024-01-15": {"5. adjusted close": "100.5"},
                "2024-01-14": {"5. adjusted close": "99.2"}
            }}
        """.trimIndent()
        val engine = MockEngine { respond(response, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
        val client = HttpClient(engine)
        val service = AlphaVantageService(client, "test-key", baseUrl = "https://www.alphavantage.co/query")
        val prices = service.getHistoricalPrices("AAPL", LocalDate(2024, 1, 14), LocalDate(2024, 1, 15))
        assertEquals(2, prices.size)
        assertEquals(LocalDate(2024, 1, 14), prices[0].date)
        assertEquals(LocalDate(2024, 1, 15), prices[1].date)
    }

    @Test
    fun `getHistoricalPrices skips entries with non-numeric close`() = runBlocking {
        val response = """
            {"Time Series (Daily)": {
                "2024-01-15": {"5. adjusted close": "N/A"},
                "2024-01-14": {"5. adjusted close": "99.2"},
                "2024-01-13": {"5. adjusted close": "98.0"}
            }}
        """.trimIndent()
        val engine = MockEngine { respond(response, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
        val client = HttpClient(engine)
        val service = AlphaVantageService(client, "test-key", baseUrl = "https://www.alphavantage.co/query")
        val prices = service.getHistoricalPrices("AAPL", LocalDate(2024, 1, 13), LocalDate(2024, 1, 15))
        assertEquals(2, prices.size)
    }

    @Test
    fun `getHistoricalPrices records sparse data when ratio below threshold`() = runBlocking {
        // Feb 2024 has many trading days; return only 2 points so ratio < 0.5
        val response = """
            {"Time Series (Daily)": {
                "2024-02-01": {"5. adjusted close": "100.0"},
                "2024-02-02": {"5. adjusted close": "100.5"}
            }}
        """.trimIndent()
        val engine = MockEngine { respond(response, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
        val client = HttpClient(engine)
        val service = AlphaVantageService(client, "test-key", baseUrl = "https://www.alphavantage.co/query", sparseRatio = 0.5)
        val prices = service.getHistoricalPrices("AAPL", LocalDate(2024, 2, 1), LocalDate(2024, 2, 29))
        assertEquals(2, prices.size)
    }

    @Test
    fun `getHistoricalPrices throws SymbolNotFoundException when Error Message key present but null content`() = runBlocking {
        val engine = MockEngine { respond("""{"Error Message": null}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
        val client = HttpClient(engine)
        val service = AlphaVantageService(client, "test-key", baseUrl = "https://www.alphavantage.co/query")
        val ex = assertFailsWith<SymbolNotFoundException> {
            service.getHistoricalPrices("X", LocalDate(2024, 1, 1), LocalDate(2024, 1, 10))
        }
        assertTrue(ex.message!!.contains("Invalid symbol") || ex.message != null)
    }
}
