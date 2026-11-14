package com.spendly.tracker.connector.data.export

import com.spendly.tracker.connector.domain.AnalysisWindow
import com.spendly.tracker.connector.domain.DataQualityReport
import com.spendly.tracker.connector.domain.Essentiality
import com.spendly.tracker.connector.domain.ExpenseBehaviour
import com.spendly.tracker.connector.domain.FinancialType
import com.spendly.tracker.connector.domain.PaymentMethod
import com.spendly.tracker.connector.domain.SpendAnalysis
import com.spendly.tracker.connector.domain.TransactionBrief
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class SpendBriefExportFormatTest {

    private val analysis = SpendAnalysis(
        window = AnalysisWindow.LAST_30_DAYS,
        startDate = LocalDate.of(2026, 10, 16),
        endDate = LocalDate.of(2026, 11, 14),
        currency = "INR",
        grossExpense = BigDecimal("5000.00"),
        refundsReceived = BigDecimal("500.00"),
        netExpense = BigDecimal("4500.00"),
        totalIncome = BigDecimal("50000.00"),
        expenseCount = 12,
        incomeCount = 1,
        refundCount = 1,
        dailyAverage = BigDecimal("150.00"),
        categories = emptyList(),
        merchants = emptyList(),
        monthlyTrend = emptyList(),
        months = emptyList(),
        recurring = emptyList(),
        largestExpenses = emptyList(),
        weekdaySpend = emptyList(),
        previousExpense = null,
        previousExpenseChangePercent = null,
        tags = emptyList(),
        refunds = com.spendly.tracker.connector.domain.RefundSummary(),
        dataQuality = DataQualityReport(
            periodStart = LocalDate.of(2026, 10, 16),
            periodEnd = LocalDate.of(2026, 11, 14),
            periodDays = 30,
            periodMonths = 2,
            partialMonths = 2,
            sourceTransactions = 13,
            includedTransactions = 12,
            excludedTransactions = 1,
            duplicateTransactions = 0,
            uncategorizedTransactions = 0,
            unmatchedRefundCount = 0,
            unmatchedRefundAmount = BigDecimal("0.00"),
            unknownPaymentMethodCount = 3,
            missingMerchantCount = 0,
            summaryToTransactionsDifference = BigDecimal("0.00"),
            categoryToSummaryDifference = BigDecimal("0.00"),
            categoriesOmitted = 0,
            merchantsOmitted = 0
        )
    )

    private val transactions = listOf(
        TransactionBrief(
            transactionId = "txn_000001",
            date = LocalDate.of(2026, 11, 12),
            type = FinancialType.EXPENSE,
            amount = BigDecimal("250.00"),
            currency = "INR",
            category = "Food",
            subcategory = null,
            merchantOriginal = "Blue Tokai",
            merchantNormalized = "blue tokai",
            paymentMethod = PaymentMethod.UPI,
            accountAlias = "Account 1",
            tag = null,
            linkedTransactionId = null,
            notes = null,
            essentiality = Essentiality.DISCRETIONARY,
            behaviour = ExpenseBehaviour.VARIABLE,
            refundKind = null,
            refundMatchStatus = null
        )
    )

    @Test
    fun jsonExportCarriesTheSameNumbersAsTheTextBrief() {
        val json = Json.parseToJsonElement(
            SpendBriefJsonFormatter.format(analysis, transactions)
        ).jsonObject

        assertEquals("INR", json["currency"]!!.jsonPrimitive.content)
        val summary = json["summary"]!!.jsonObject
        assertEquals("5000.00", summary["gross_expense"]!!.jsonPrimitive.content)
        assertEquals("500.00", summary["refunds_received"]!!.jsonPrimitive.content)
        assertEquals("4500.00", summary["net_expense"]!!.jsonPrimitive.content)
        assertEquals("50000.00", summary["total_income"]!!.jsonPrimitive.content)

        val rows = json["transactions"]!!.jsonArray
        assertEquals(1, rows.size)
        val row = rows.first().jsonObject
        assertEquals("txn_000001", row["transaction_id"]!!.jsonPrimitive.content)
        assertEquals("UPI", row["payment_method"]!!.jsonPrimitive.content)
        assertEquals("Blue Tokai", row["merchant_original"]!!.jsonPrimitive.content)
        assertEquals("blue tokai", row["merchant_normalized"]!!.jsonPrimitive.content)
    }

    @Test
    fun jsonExportKeepsTheDataQualitySection() {
        val json = Json.parseToJsonElement(SpendBriefJsonFormatter.format(analysis)).jsonObject
        val quality = json["data_quality"]!!.jsonObject

        assertEquals("13", quality["source_transactions"]!!.jsonPrimitive.content)
        assertEquals("1", quality["excluded_transactions"]!!.jsonPrimitive.content)
        assertEquals("3", quality["transactions_with_unknown_payment_method"]!!.jsonPrimitive.content)
        assertEquals("0.00", quality["summary_to_transactions_difference"]!!.jsonPrimitive.content)
    }

    @Test
    fun csvExportHasAHeaderAndOneRowPerTransaction() {
        val csv = SpendBriefCsvFormatter.format(transactions)
        val lines = csv.trim().lines()

        assertEquals(2, lines.size)
        assertTrue(lines[0].startsWith("transaction_id,date,type,amount"))
        assertTrue(lines[1].contains("txn_000001"))
        assertTrue(lines[1].contains("\"Blue Tokai\""))
    }

    @Test
    fun csvExportQuotesSeparatorsInsideValues() {
        val csv = SpendBriefCsvFormatter.format(
            transactions.map { it.copy(merchantOriginal = "Cafe, Corner") }
        )
        assertTrue(csv.contains("\"Cafe, Corner\""))
    }

    @Test
    fun exportFormatsHaveTheirOwnExtension() {
        assertEquals(".txt", SpendBriefExporter.BriefFormat.TEXT.extension)
        assertEquals(".json", SpendBriefExporter.BriefFormat.JSON.extension)
        assertEquals(".csv", SpendBriefExporter.BriefFormat.CSV.extension)
    }
}
