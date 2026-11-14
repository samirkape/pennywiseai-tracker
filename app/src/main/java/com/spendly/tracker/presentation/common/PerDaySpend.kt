package com.spendly.tracker.presentation.common

import com.spendly.tracker.data.database.entity.TransactionEntity
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.temporal.ChronoUnit

/**
 * Average spend per day for [total] spread over a window of [days] days.
 *
 * Returns null when the window length is unknown or when there is nothing to spread.
 */
fun perDayAverage(total: BigDecimal, days: Int): BigDecimal? {
    if (days <= 0 || total <= BigDecimal.ZERO) return null
    return total.divide(BigDecimal(days), 2, RoundingMode.HALF_UP)
}

/**
 * Inclusive number of days between the first and the last transaction date.
 *
 * Used as the window length when the selected period has no fixed length ("All time"),
 * where the daily average is spread over the days that actually carry transactions.
 */
fun spanDays(transactions: List<TransactionEntity>): Int {
    if (transactions.isEmpty()) return 0
    var min = transactions.first().dateTime.toLocalDate()
    var max = min
    for (tx in transactions) {
        val date = tx.dateTime.toLocalDate()
        if (date.isBefore(min)) min = date
        if (date.isAfter(max)) max = date
    }
    return (ChronoUnit.DAYS.between(min, max).toInt() + 1).coerceAtLeast(1)
}
