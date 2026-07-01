package com.spendly.tracker.presentation.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendly.tracker.data.currency.CurrencyConversionService
import com.spendly.tracker.data.currency.CurrencyConversionService.TransactionData
import com.spendly.tracker.data.database.entity.AccountBalanceEntity
import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.preferences.UserPreferencesRepository
import com.spendly.tracker.data.repository.AccountBalanceRepository
import com.spendly.tracker.data.repository.TransactionRepository
import com.spendly.tracker.ui.components.BalancePoint
import com.spendly.tracker.utils.CurrencyFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AccountDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactionRepository: TransactionRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val currencyConversionService: CurrencyConversionService,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {
    
    private val bankName: String = savedStateHandle.get<String>("bankName") ?: ""
    private val accountLast4: String = savedStateHandle.get<String>("accountLast4") ?: ""
    
    private val _uiState = MutableStateFlow(AccountDetailUiState())
    val uiState: StateFlow<AccountDetailUiState> = _uiState.asStateFlow()
    
    private val _selectedDateRange = MutableStateFlow(DateRange.LAST_30_DAYS)
    val selectedDateRange: StateFlow<DateRange> = _selectedDateRange.asStateFlow()
    
    init {
        loadAccountData()
        observeTransactions()
        observeBalanceHistory()
        observeSpendChartData()
    }
    
    private fun loadAccountData() {
        _uiState.update { it.copy(
            bankName = bankName,
            accountLast4 = accountLast4,
            isLoading = true
        ) }
    }
    
    private fun observeTransactions() {
        viewModelScope.launch {
            combine(
                selectedDateRange,
                transactionRepository.getTransactionsByAccount(bankName, accountLast4),
                userPreferencesRepository.unifiedCurrencyMode,
                userPreferencesRepository.displayCurrency
            ) { dateRange, allTransactions, isUnified, displayCurrency ->
                val (startDate, endDate) = getDateRangeValues(dateRange)

                val filteredTransactions = if (dateRange == DateRange.ALL_TIME) {
                    allTransactions
                } else {
                    allTransactions.filter { transaction ->
                        transaction.dateTime.isAfter(startDate) &&
                        transaction.dateTime.isBefore(endDate)
                    }
                }

                // Use display currency when unified, otherwise account's primary currency
                val targetCurrency = if (isUnified) displayCurrency else getPrimaryCurrencyForAccount(bankName)
                val hasMultipleCurrencies = filteredTransactions
                    .map { it.currency }
                    .distinct()
                    .size > 1

                // Refresh exchange rates if we have multiple currencies
                if (hasMultipleCurrencies || isUnified) {
                    val accountCurrencies = filteredTransactions.map { it.currency }.distinct()
                    currencyConversionService.refreshExchangeRatesForAccount(accountCurrencies)
                }

                // Calculate total income and expenses with currency conversion
                var totalIncome = BigDecimal.ZERO
                var totalExpenses = BigDecimal.ZERO

                filteredTransactions.forEach { transaction ->
                    val convertedAmount = if (transaction.currency != targetCurrency) {
                        currencyConversionService.convertAmount(
                            amount = transaction.amount,
                            fromCurrency = transaction.currency,
                            toCurrency = targetCurrency
                        ) ?: transaction.amount
                    } else {
                        transaction.amount
                    }

                    when (transaction.transactionType) {
                        com.spendly.tracker.data.database.entity.TransactionType.INCOME -> totalIncome += convertedAmount
                        com.spendly.tracker.data.database.entity.TransactionType.EXPENSE,
                        com.spendly.tracker.data.database.entity.TransactionType.CREDIT -> totalExpenses += convertedAmount
                        else -> { /* TRANSFER, INVESTMENT are not counted */ }
                    }
                }

                // Compute billed/unbilled outstanding for credit cards with a statement day
                val statementDay = _uiState.value.currentBalance?.statementDay
                val billedUnbilled = if (statementDay != null) {
                    computeBilledUnbilled(allTransactions, statementDay)
                } else null

                _uiState.update { state ->
                    state.copy(
                        transactions = filteredTransactions,
                        totalIncome = totalIncome,
                        totalExpenses = totalExpenses,
                        netBalance = totalIncome - totalExpenses,
                        primaryCurrency = targetCurrency,
                        hasMultipleCurrencies = hasMultipleCurrencies,
                        isLoading = false,
                        billedOutstanding = billedUnbilled?.first,
                        unbilledOutstanding = billedUnbilled?.second
                    )
                }
            }.collect()
        }
    }
    
    private fun observeBalanceHistory() {
        viewModelScope.launch {
            accountBalanceRepository.getLatestBalanceFlow(bankName, accountLast4)
                .collect { latestBalance ->
                    _uiState.update { state ->
                        state.copy(currentBalance = latestBalance)
                    }
                }
        }
        
        viewModelScope.launch {
            selectedDateRange.flatMapLatest { dateRange ->
                val (startDate, endDate) = getDateRangeValues(dateRange)
                accountBalanceRepository.getBalanceHistory(
                    bankName, 
                    accountLast4,
                    startDate,
                    endDate
                )
            }.collect { balanceHistory ->
                _uiState.update { state ->
                    state.copy(balanceHistory = balanceHistory)
                }
            }
        }
    }
    
    private fun observeSpendChartData() {
        viewModelScope.launch {
            combine(
                selectedDateRange,
                transactionRepository.getTransactionsByAccount(bankName, accountLast4)
            ) { dateRange, allTransactions ->
                val (startDateTime, endDateTime) = getDateRangeValues(dateRange)
                val startDate = startDateTime.toLocalDate()
                val endDate = endDateTime.toLocalDate()

                val spendTransactions = allTransactions.filter { tx ->
                    val txDate = tx.dateTime.toLocalDate()
                    val inRange = !txDate.isBefore(startDate) && !txDate.isAfter(endDate)
                    val isSpend = tx.transactionType == com.spendly.tracker.data.database.entity.TransactionType.EXPENSE ||
                                  tx.transactionType == com.spendly.tracker.data.database.entity.TransactionType.CREDIT
                    inRange && isSpend
                }

                val currency = spendTransactions.firstOrNull()?.currency ?: _uiState.value.primaryCurrency
                computeSpendTrend(spendTransactions, startDate, endDate, currency, dateRange)
            }.collect { spendTrend ->
                _uiState.update { it.copy(spendChartData = spendTrend) }
            }
        }
    }

    private fun computeSpendTrend(
        transactions: List<TransactionEntity>,
        startDate: LocalDate,
        endDate: LocalDate,
        currency: String,
        dateRange: DateRange
    ): List<BalancePoint> {
        val trend = mutableListOf<BalancePoint>()
        val rangeDays = ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1
        val byDate = transactions.groupBy { it.dateTime.toLocalDate() }

        when {
            dateRange == DateRange.ALL_TIME || rangeDays > 60 -> {
                var month = startDate.withDayOfMonth(1)
                val lastMonth = endDate.withDayOfMonth(1)
                while (!month.isAfter(lastMonth) && !month.isAfter(LocalDate.now().withDayOfMonth(1))) {
                    val endOfMonth = month.withDayOfMonth(month.lengthOfMonth())
                    val total = transactions
                        .filter { !it.dateTime.toLocalDate().isBefore(month) && !it.dateTime.toLocalDate().isAfter(endOfMonth) }
                        .sumOf { it.amount.toDouble() }.toBigDecimal()
                    trend.add(BalancePoint(timestamp = month.atStartOfDay(), balance = total, currency = currency))
                    month = month.plusMonths(1)
                }
            }
            rangeDays > 14 -> {
                val weekStart = startDate.minusDays(startDate.dayOfWeek.value.toLong() - 1)
                var week = weekStart
                while (!week.isAfter(endDate) && !week.isAfter(LocalDate.now())) {
                    val weekEnd = week.plusDays(6).coerceAtMost(endDate).coerceAtMost(LocalDate.now())
                    var d = week.coerceAtLeast(startDate)
                    var weekTotal = BigDecimal.ZERO
                    while (!d.isAfter(weekEnd)) {
                        weekTotal += (byDate[d] ?: emptyList()).sumOf { it.amount.toDouble() }.toBigDecimal()
                        d = d.plusDays(1)
                    }
                    trend.add(BalancePoint(timestamp = week.coerceAtLeast(startDate).atStartOfDay(), balance = weekTotal, currency = currency))
                    week = week.plusWeeks(1)
                }
            }
            else -> {
                var day = startDate
                while (!day.isAfter(endDate) && !day.isAfter(LocalDate.now())) {
                    val total = (byDate[day] ?: emptyList()).sumOf { it.amount.toDouble() }.toBigDecimal()
                    trend.add(BalancePoint(timestamp = day.atStartOfDay(), balance = total, currency = currency))
                    day = day.plusDays(1)
                }
            }
        }
        return trend
    }
    
    fun selectDateRange(dateRange: DateRange) {
        _selectedDateRange.value = dateRange
    }
    
    private fun getDateRangeValues(dateRange: DateRange): Pair<LocalDateTime, LocalDateTime> {
        val endDate = LocalDateTime.now()
        val startDate = when (dateRange) {
            DateRange.LAST_7_DAYS -> endDate.minusDays(7)
            DateRange.LAST_30_DAYS -> endDate.minusDays(30)
            DateRange.LAST_3_MONTHS -> endDate.minusMonths(3)
            DateRange.LAST_6_MONTHS -> endDate.minusMonths(6)
            DateRange.LAST_YEAR -> endDate.minusYears(1)
            DateRange.ALL_TIME -> LocalDateTime.of(2000, 1, 1, 0, 0)
        }
        return startDate to endDate
    }

    /**
     * Splits CREDIT transactions into billed and unbilled based on [statementDay].
     * Returns (billed, unbilled) where:
     *   - billed = CREDIT transactions from the previous statement close to the most recent close
     *   - unbilled = CREDIT transactions from the most recent close to today
     */
    private fun computeBilledUnbilled(
        allTransactions: List<TransactionEntity>,
        statementDay: Int
    ): Pair<BigDecimal, BigDecimal> {
        val today = LocalDate.now()
        val day = statementDay.coerceIn(1, 28)

        // Most recent statement close date
        val lastClose = if (today.dayOfMonth > day) {
            today.withDayOfMonth(day)
        } else {
            today.minusMonths(1).withDayOfMonth(day)
        }
        val prevClose = lastClose.minusMonths(1)

        val creditTxs = allTransactions.filter {
            it.transactionType == com.spendly.tracker.data.database.entity.TransactionType.CREDIT
        }

        val billed = creditTxs
            .filter { it.dateTime.toLocalDate().isAfter(prevClose) && !it.dateTime.toLocalDate().isAfter(lastClose) }
            .fold(BigDecimal.ZERO) { acc, tx -> acc + tx.amount }

        val unbilled = creditTxs
            .filter { it.dateTime.toLocalDate().isAfter(lastClose) }
            .fold(BigDecimal.ZERO) { acc, tx -> acc + tx.amount }

        return billed to unbilled
    }

    private fun getPrimaryCurrencyForAccount(bankName: String): String {
        return CurrencyFormatter.getBankBaseCurrency(bankName)
    }
}

data class AccountDetailUiState(
    val bankName: String = "",
    val accountLast4: String = "",
    val currentBalance: AccountBalanceEntity? = null,
    val balanceHistory: List<AccountBalanceEntity> = emptyList(),
    val spendChartData: List<BalancePoint> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
    val totalIncome: BigDecimal = BigDecimal.ZERO,
    val totalExpenses: BigDecimal = BigDecimal.ZERO,
    val netBalance: BigDecimal = BigDecimal.ZERO,
    val primaryCurrency: String = "INR",
    val hasMultipleCurrencies: Boolean = false,
    val isLoading: Boolean = true,
    val billedOutstanding: BigDecimal? = null,
    val unbilledOutstanding: BigDecimal? = null
)

enum class DateRange(val label: String) {
    LAST_7_DAYS("Last 7 Days"),
    LAST_30_DAYS("Last 30 Days"),
    LAST_3_MONTHS("Last 3 Months"),
    LAST_6_MONTHS("Last 6 Months"),
    LAST_YEAR("Last Year"),
    ALL_TIME("All Time")
}