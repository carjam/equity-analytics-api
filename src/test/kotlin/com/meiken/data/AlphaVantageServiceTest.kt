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
        val service = AlphaVantageService(client, "test-key")
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
        val service = AlphaVantageService(client, "test-key")
        val ex = assertFailsWith<SymbolNotFoundException> {
            service.getHistoricalPrices("INVALID", LocalDate(2024, 1, 1), LocalDate(2024, 1, 10))
        }
        assertTrue(ex.message!!.contains("Invalid API call") || ex.message!!.contains("Invalid"))
    }

    @Test
    fun `getHistoricalPrices throws DataRetrievalException when Note in response`() = runBlocking {
        val engine = MockEngine { respond("""{"Note": "Thank you for using Alpha Vantage! Our standard API call frequency is 5 calls per minute..."}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
        val client = HttpClient(engine)
        val service = AlphaVantageService(client, "test-key")
        assertFailsWith<DataRetrievalException> {
            service.getHistoricalPrices("AAPL", LocalDate(2024, 1, 1), LocalDate(2024, 1, 10))
        }
    }

    @Test
    fun `getHistoricalPrices throws DataRetrievalException when Time Series missing`() = runBlocking {
        val engine = MockEngine { respond("""{}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
        val client = HttpClient(engine)
        val service = AlphaVantageService(client, "test-key")
        val ex = assertFailsWith<DataRetrievalException> {
            service.getHistoricalPrices("AAPL", LocalDate(2024, 1, 1), LocalDate(2024, 1, 10))
        }
        assertTrue(ex.message!!.contains("Time Series") || ex.message!!.contains("Missing"))
    }
}
