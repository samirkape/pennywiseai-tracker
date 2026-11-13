package com.spendly.tracker.connector.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class AnalysisWindowTest {

    private val today = LocalDate.of(2026, 3, 15)

    @Test
    fun dayWindowCoversInclusiveRange() {
        val window = AnalysisWindow(WindowUnit.DAYS, 7)
        assertEquals(LocalDate.of(2026, 3, 9), window.startDate(today))
        assertEquals(today, window.endDate(today))
        assertEquals(7, window.dayCount(today))
    }

    @Test
    fun monthWindowCoversWholeMonthsBack() {
        val window = AnalysisWindow(WindowUnit.MONTHS, 3)
        assertEquals(LocalDate.of(2025, 12, 16), window.startDate(today))
        assertEquals(90, window.dayCount(today))
    }

    @Test
    fun yearWindowCoversOneYear() {
        val window = AnalysisWindow(WindowUnit.YEARS, 1)
        assertEquals(LocalDate.of(2025, 3, 16), window.startDate(today))
    }

    @Test
    fun previousPeriodSitsImmediatelyBeforeTheWindow() {
        val window = AnalysisWindow(WindowUnit.DAYS, 30)
        assertEquals(LocalDate.of(2026, 1, 15), window.startDateOfPrevious(today))
        assertEquals(LocalDate.of(2026, 2, 13), window.endDateOfPrevious(today))
    }

    @Test
    fun coerceClampsCountToTheUnitLimit() {
        assertEquals(366, AnalysisWindow.coerce(WindowUnit.DAYS, 999).count)
        assertEquals(36, AnalysisWindow.coerce(WindowUnit.MONTHS, 999).count)
        assertEquals(10, AnalysisWindow.coerce(WindowUnit.YEARS, 999).count)
        assertEquals(1, AnalysisWindow.coerce(WindowUnit.DAYS, 0).count)
    }

    @Test
    fun storedWindowFallsBackToSafeDefaults() {
        val restored = AnalysisWindow.fromStored("MONTHS", 6)
        assertEquals(WindowUnit.MONTHS, restored.unit)
        assertEquals(6, restored.count)

        val fallback = AnalysisWindow.fromStored("NOPE", null)
        assertEquals(WindowUnit.MONTHS, fallback.unit)
        assertEquals(3, fallback.count)
    }
}
