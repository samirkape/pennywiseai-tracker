package com.spendly.tracker.domain.usecase

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class PrepaidAllocationDraft(
    val periodYearMonth: YearMonth,
    val amount: BigDecimal
)

/**
 * Splits a prepaid amount into whole-month allocations. Recognition is monthly-only
 * (no daily proration) — the first and last month each get a full month's share
 * regardless of what day-of-month the payment landed on.
 */
class ComputePrepaidAllocationScheduleUseCase @Inject constructor() {

    fun compute(
        totalAmount: BigDecimal,
        startDate: LocalDate,
        totalMonths: Int
    ): List<PrepaidAllocationDraft> {
        require(totalMonths > 0) { "totalMonths must be positive" }

        val base = totalAmount.divide(BigDecimal(totalMonths), 2, RoundingMode.FLOOR)
        val distributed = base.multiply(BigDecimal(totalMonths))
        val remainderCents = totalAmount.subtract(distributed)
            .movePointRight(2)
            .setScale(0, RoundingMode.HALF_UP)
            .toInt()

        val startMonth = YearMonth.from(startDate)
        return (0 until totalMonths).map { offset ->
            val month = startMonth.plusMonths(offset.toLong())
            // Distribute leftover paise one-per-month to the LAST `remainderCents` months,
            // so early months show the clean rounded figure and the schedule sums exactly.
            val extraCent = if (offset >= totalMonths - remainderCents) BigDecimal("0.01") else BigDecimal.ZERO
            PrepaidAllocationDraft(month, base.add(extraCent))
        }
    }
}
