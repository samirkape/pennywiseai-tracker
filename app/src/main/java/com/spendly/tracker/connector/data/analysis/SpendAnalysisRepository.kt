package com.spendly.tracker.connector.data.analysis

import com.spendly.tracker.connector.domain.AnalysisWindow
import com.spendly.tracker.data.preferences.UserPreferencesRepository
import com.spendly.tracker.data.repository.TransactionRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns raw transactions into the privacy-safe brief that is handed to a provider.
 *
 * Only aggregate figures leave this class — descriptions, SMS bodies, account
 * numbers and bank names are never read.
 */
@Singleton
class SpendAnalysisRepository @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) {

    suspend fun buildAnalysis(
        window: AnalysisWindow,
        today: LocalDate = LocalDate.now()
    ): com.spendly.tracker.connector.domain.SpendAnalysis {
        val start = window.startDate(today)
        val end = window.endDate(today)

        val transactions = transactionRepository.getNetTransactionsBetweenDates(
            start.atStartOfDay(),
            end.atTime(23, 59, 59)
        ).first()

        val previousTransactions = transactionRepository.getNetTransactionsBetweenDates(
            window.startDateOfPrevious(today).atStartOfDay(),
            window.endDateOfPrevious(today).atTime(23, 59, 59)
        ).first()

        return SpendAnalysisBuilder.build(
            transactions = transactions,
            previousTransactions = previousTransactions,
            window = window,
            startDate = start,
            endDate = end,
            currency = userPreferencesRepository.baseCurrency.first(),
            today = today
        )
    }
}
