package com.spendly.tracker.domain.usecase

import com.spendly.tracker.data.database.entity.AccountBalanceEntity
import com.spendly.tracker.data.database.entity.NetWorthSourceEntity
import com.spendly.tracker.data.database.entity.NetWorthSourceType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class ComputeNetWorthUseCaseTest {

    private fun balance(amount: String, creditCard: Boolean = false, currency: String = "INR") =
        AccountBalanceEntity(
            bankName = "Sample Bank",
            accountLast4 = "0000",
            balance = BigDecimal(amount),
            timestamp = LocalDateTime.of(2025, 1, 1, 0, 0),
            isCreditCard = creditCard,
            currency = currency
        )

    private fun source(type: NetWorthSourceType, value: String, currency: String = "INR") =
        NetWorthSourceEntity(name = type.displayName, type = type, value = BigDecimal(value), currency = currency)

    @Test
    fun `combines balances sources and loans into assets and liabilities`() {
        val summary = ComputeNetWorthUseCase.compute(
            currency = "INR",
            balances = listOf(balance("10000"), balance("2000", creditCard = true)),
            sources = listOf(
                source(NetWorthSourceType.GOLD, "5000"),
                source(NetWorthSourceType.OTHER_LIABILITY, "1000")
            ),
            lent = BigDecimal("500"),
            borrowed = BigDecimal("300")
        )

        assertEquals(0, BigDecimal("15500").compareTo(summary.assets))
        assertEquals(0, BigDecimal("3300").compareTo(summary.liabilities))
        assertEquals(0, BigDecimal("12200").compareTo(summary.netWorth))
    }

    @Test
    fun `other currencies are skipped and overdrawn accounts count as liabilities`() {
        val summary = ComputeNetWorthUseCase.compute(
            currency = "INR",
            balances = listOf(balance("-400"), balance("9999", currency = "USD")),
            sources = listOf(source(NetWorthSourceType.CASH, "700", currency = "USD")),
            lent = BigDecimal.ZERO,
            borrowed = BigDecimal.ZERO
        )

        assertEquals(0, BigDecimal.ZERO.compareTo(summary.assets))
        assertEquals(0, BigDecimal("400").compareTo(summary.liabilities))
    }
}
