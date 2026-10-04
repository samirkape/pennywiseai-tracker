package com.spendly.tracker.data.networth

import com.spendly.tracker.data.database.entity.NetWorthSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class CasParserTest {

    private fun row(vararg cells: String, page: Int = 3) = CasRow(page, cells.toList())

    private fun amount(h: CasHolding) = h.value.toPlainString()

    @Test
    fun `reads holdings rows and ignores transaction rows that carry a date`() {
        val holdings = CasParser.parse(
            listOf(
                row("STATEMENT OF TRANSACTIONS"),
                row("INE000A01010", "SAMPLE LTD", "01-01-2025", "100.000", "500.00"),
                row("INE000A01010", "01-01-2025", "100.000", "10.000", "--", "100.000", "9"),
                row("INE000A01010", "123456 SAMPLE 01-01-2025", "9.999", "9.999", "--", "9.999", "9"),
                row("HOLDING STATEMENT AS ON 31-03-2025"),
                row("ISIN", "Security", "Current Bal", "Market Price", "Value"),
                row("INE000A01010", "SAMPLE LTD", "10", "--", "--", "--", "10", "100.00", "1,000.00"),
                row("INF000A00001", "SAMPLE FUND", "2.500", "--", "--", "--", "2.500", "400.00", "1,000.00"),
                row("Sub Total", "2,000.00")
            )
        )

        assertEquals(2, holdings.size)
        assertEquals("1000.00", amount(holdings[0]))
        assertEquals(NetWorthSourceType.STOCKS, holdings[0].type)
        assertEquals(NetWorthSourceType.MUTUAL_FUND, holdings[1].type)
        assertEquals("SAMPLE LTD", holdings[0].name)
    }

    @Test
    fun `reads holdings even when section headings are garbled`() {
        val holdings = CasParser.parse(
            listOf(
                row("INE000A01010", "10.000", "--", "--", "--", "10.000 100.0000", "1,000.00"),
                row("SAMPLE LTD EQUITY"),
                row("INE000B01011", "SECOND LTD", "5.000", "--", "--", "--", "5.000", "200.0000", "1,000.00")
            )
        )

        assertEquals(2, holdings.size)
        // A name that wraps onto its own line is not guessed; the ISIN stands in.
        assertEquals("INE000A01010", holdings[0].name)
        assertEquals("1000.00", amount(holdings[0]))
        assertEquals("SECOND LTD", holdings[1].name)
    }

    @Test
    fun `same isin twice stays two holdings`() {
        val holdings = CasParser.parse(
            listOf(
                row("INE000A01010", "SAMPLE LTD", "10", "100.00", "1,000.00"),
                row("INE000A01010", "SAMPLE LTD", "5", "100.00", "500.00")
            )
        )

        assertEquals(2, holdings.size)
        assertEquals(BigDecimal("1500.00"), holdings.fold(BigDecimal.ZERO) { a, h -> a + h.value })
        assertEquals(2, holdings.map { it.externalKey }.toSet().size)
    }

    @Test
    fun `rows without a final numeric value are skipped`() {
        val holdings = CasParser.parse(
            listOf(
                row("INE000A01010", "SAMPLE LTD", "10", "100.00", "Pledged"),
                row("INE000A01010", "SAMPLE LTD")
            )
        )

        assertTrue(holdings.isEmpty())
    }

    @Test
    fun `reads mutual fund units table with distribution column and wrapped folio`() {
        val holdings = CasParser.parse(
            listOf(
                row("MUTUAL FUND UNITS HELD AS ON 31-03-2025"),
                row(
                    "SAMPLE EQUITY FUND DIRECT GROWTH", "INF000A00001", "910121125", "82/0", "DIRECT",
                    "100.000", "50.00", "4,000.00", "5,000.00", "0.50", "0.00", "0.00", "1,000.00", "25.00"
                ),
                row("SAMPLE DEBT FUND", "INF000A00002", "123456", "20.000", "10.00", "200.00")
            )
        )

        assertEquals(2, holdings.size)
        assertEquals("5000.00", amount(holdings[0]))
        assertEquals("910121125" + "82/0", holdings[0].folio)
        assertEquals("200.00", amount(holdings[1]))
        assertTrue(holdings.all { it.type == NetWorthSourceType.MUTUAL_FUND })
    }
}
