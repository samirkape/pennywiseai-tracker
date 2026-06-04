package com.pennywiseai.tracker.presentation.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import androidx.work.WorkInfo
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.manager.InAppUpdateManager
import com.pennywiseai.tracker.data.manager.InAppReviewManager
import com.pennywiseai.tracker.data.currency.CurrencyConversionService
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.presentation.common.buildProfileAccountKeys
import com.pennywiseai.tracker.presentation.common.filterAccountsByProfile
import com.pennywiseai.tracker.presentation.common.filterTransactionsByProfile
import com.pennywiseai.tracker.presentation.common.matchesAnalyticsSpendingFilter
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.ProfileRepository
import com.pennywiseai.tracker.data.repository.LlmRepository
import com.pennywiseai.tracker.data.database.entity.LoanEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.repository.LoanRepository
import com.pennywiseai.tracker.data.repository.SubscriptionRepository
import com.pennywiseai.tracker.data.repository.TransactionGroupRepository
import com.pennywiseai.tracker.data.repository.SalaryMonthOverrideRepository
import com.pennywiseai.tracker.data.manager.SmsScanManager
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.worker.OptimizedSmsReaderWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.domain.service.SalaryPayPeriodDetector
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.utils.DateRangeUtils
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val transactionGroupRepository: TransactionGroupRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val loanRepository: LoanRepository,
    private val llmRepository: LlmRepository,
    private val currencyConversionService: CurrencyConversionService,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val salaryMonthOverrideRepository: SalaryMonthOverrideRepository,
    private val profileRepository: ProfileRepository,
    private val inAppUpdateManager: InAppUpdateManager,
    private val inAppReviewManager: InAppReviewManager,
    private val smsScanManager: SmsScanManager,
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    private val sharedPrefs = context.getSharedPreferences("account_prefs", Context.MODE_PRIVATE)
    private val _uiState = MutableStateFlow(HomeUiState())

    companion object {
        private const val TAG = "BreakdownCalc"
    }
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // Selected date for the "daily spends" section — defaults to today
    private val _selectedDate = MutableStateFlow(LocalDate.now())

    // Tracks the current financial month start day (1–28); updated when preference changes
    private var currentMonthStartDay: Int = 1
    private var currentMonthOverrides: Map<String, Int> = emptyMap()
    private var currentUseFixedBudgetPeriodEnd: Boolean = false
    private var currentBudgetPeriodEndDay: Int = 31
    private var dataLoadingJob: Job? = null
    
    private val _deletedTransaction = MutableStateFlow<TransactionEntity?>(null)
    val deletedTransaction: StateFlow<TransactionEntity?> = _deletedTransaction.asStateFlow()

    // SMS scanning work progress tracking
    private val _smsScanWorkInfo = MutableStateFlow<WorkInfo?>(null)
    val smsScanWorkInfo: StateFlow<WorkInfo?> = _smsScanWorkInfo.asStateFlow()

    // Store per-currency rollups for the pay/calendar period (quick access when switching currencies)
    private var currentMonthRollupMap: Map<String, PeriodRollup> = emptyMap()
    private var lastMonthRollupMap: Map<String, PeriodRollup> = emptyMap()

    private data class PeriodRollup(
        val income: BigDecimal,
        val spending: BigDecimal,
        val transfer: BigDecimal,
        val investment: BigDecimal,
    ) {
        fun toMonthlyBreakdown(): TransactionRepository.MonthlyBreakdown =
            TransactionRepository.MonthlyBreakdown(
                total = income - spending,
                income = income,
                expenses = spending,
            )

        companion object {
            val ZERO = PeriodRollup(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
            )
        }
    }

    // Track if user has manually selected a currency to prevent auto-reset
    private var hasUserSelectedCurrency = false

    // Cached base currency for use in sort comparators (updated from preferences)
    private var baseCurrency = ""

    // Cache the latest account balances as a StateFlow so that combine blocks
    // re-emit when account profiles change (e.g. via Manage Accounts).
    // null = not loaded yet (avoids emitting stale empty data on cold launch)
    private val _cachedAccountBalances = MutableStateFlow<List<AccountBalanceEntity>?>(null)
    private val cachedAccountBalances: List<AccountBalanceEntity> get() = _cachedAccountBalances.value ?: emptyList()

    init {
        loadUnifiedModePreferences()
        loadUserName()
        // Load base currency FIRST so selectedCurrency is set before data loads
        viewModelScope.launch {
            val base = userPreferencesRepository.baseCurrency.first()
            baseCurrency = base
            _uiState.value = _uiState.value.copy(
                selectedCurrency = base,
                availableCurrencies = listOf(base)
            )
            loadHomeData()
        }
        // Re-run when pay-period prefs change together (single DataStore write updates several
        // keys; separate collectors could call loadHomeData with a stale mix of cached fields).
        combine(
            userPreferencesRepository.monthStartDay,
            userPreferencesRepository.useFinancialMonth,
            userPreferencesRepository.useFixedBudgetPeriodEnd,
            userPreferencesRepository.budgetPeriodEndDay,
        ) { _, _, _, _ ->
            loadHomeData()
        }.launchIn(viewModelScope)
        salaryMonthOverrideRepository.overridesMap
            .onEach { overrides ->
                currentMonthOverrides = overrides
                loadHomeData()
            }
            .launchIn(viewModelScope)
        // Keep listening for base currency changes
        loadBaseCurrency()
        observeSelectedProfile()
        observeProfiles()
        observePayPeriodSuggestion()
    }

    fun acceptPayPeriodSuggestion() {
        val suggestion = _uiState.value.payPeriodSuggestion ?: return
        viewModelScope.launch {
            salaryMonthOverrideRepository.setOverride(
                suggestion.yearMonth.toString(),
                suggestion.suggestedDay,
            )
            _uiState.value = _uiState.value.copy(payPeriodSuggestion = null)
        }
    }

    fun dismissPayPeriodSuggestion() {
        val suggestion = _uiState.value.payPeriodSuggestion ?: return
        viewModelScope.launch {
            userPreferencesRepository.dismissSalarySuggestion(suggestion.dismissToken)
            _uiState.value = _uiState.value.copy(payPeriodSuggestion = null)
        }
    }

    fun toggleSpendingMonthMode() {
        viewModelScope.launch {
            userPreferencesRepository.updateUseFinancialMonth(!_uiState.value.useFinancialMonth)
        }
    }

    private fun observePayPeriodSuggestion() {
        combine(
            userPreferencesRepository.useFinancialMonth,
            userPreferencesRepository.monthStartDay,
            userPreferencesRepository.useFixedBudgetPeriodEnd,
            salaryMonthOverrideRepository.overridesMap,
            userPreferencesRepository.dismissedSalarySuggestions,
        ) { useFinancial, startDay, useFixedEnd, overrides, dismissed ->
            PayPeriodSuggestionInputs(useFinancial, startDay, useFixedEnd, overrides, dismissed)
        }.flatMapLatest { inputs ->
            if (!inputs.useFinancialMonth) {
                flowOf(null)
            } else {
                val today = LocalDate.now()
                val monthStart = YearMonth.from(today).atDay(1)
                transactionRepository.getTransactionsBetweenDates(monthStart, today)
                    .map { transactions ->
                        SalaryPayPeriodDetector.findSuggestion(
                            transactions = transactions,
                            today = today,
                            useFinancialMonth = inputs.useFinancialMonth,
                            useFixedBudgetPeriodEnd = inputs.useFixedEnd,
                            defaultStartDay = inputs.defaultStartDay,
                            overrides = inputs.overrides,
                            dismissedTokens = inputs.dismissed,
                        )
                    }
            }
        }.onEach { suggestion ->
            _uiState.value = _uiState.value.copy(payPeriodSuggestion = suggestion)
        }.launchIn(viewModelScope)
    }

    private data class PayPeriodSuggestionInputs(
        val useFinancialMonth: Boolean,
        val defaultStartDay: Int,
        val useFixedEnd: Boolean,
        val overrides: Map<String, Int>,
        val dismissed: Set<String>,
    )

    private fun loadUserName() {
        userPreferencesRepository.userPreferences
            .onEach { prefs ->
                _uiState.value = _uiState.value.copy(
                    userName = prefs.userName,
                    profileImageUri = prefs.profileImageUri,
                    profileBackgroundColor = prefs.profileBackgroundColor
                )
            }
            .launchIn(viewModelScope)
    }

    private fun loadBaseCurrency() {
        var previousBaseCurrency: String? = null
        userPreferencesRepository.baseCurrency
            .onEach { newBaseCurrency ->
                // Only update if the baseCurrency ACTUALLY CHANGED (not just re-emitted)
                if (newBaseCurrency == previousBaseCurrency) return@onEach
                previousBaseCurrency = newBaseCurrency
                this@HomeViewModel.baseCurrency = newBaseCurrency

                val currentSelected = _uiState.value.selectedCurrency
                val availableCurrencies = _uiState.value.availableCurrencies
                if (baseCurrency != currentSelected && !hasUserSelectedCurrency) {
                    if (availableCurrencies.isEmpty() || availableCurrencies.contains(baseCurrency)) {
                        selectCurrency(baseCurrency)
                        hasUserSelectedCurrency = false  // Reset since this was auto-selection
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeSelectedProfile() {
        userPreferencesRepository.selectedProfileId
            .onEach { profileId ->
                _uiState.value = _uiState.value.copy(selectedProfileId = profileId)
                // Guard against cold-launch race: if balances aren't cached yet, the combine
                // blocks that include _cachedAccountBalances will apply the filter automatically
                // once balances load. Only call refreshAccountBalances() eagerly when the cache
                // is already populated (i.e. on user-driven profile switches after launch).
                if (_cachedAccountBalances.value != null) {
                    refreshAccountBalances()
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeProfiles() {
        profileRepository.observeAllProfiles()
            .onEach { profiles ->
                _uiState.value = _uiState.value.copy(profiles = profiles)
            }
            .launchIn(viewModelScope)
    }

    fun updateSelectedProfile(profileId: Long?) {
        viewModelScope.launch {
            userPreferencesRepository.updateSelectedProfileId(profileId)
        }
    }

    private fun filterTransactions(transactions: List<TransactionEntity>): List<TransactionEntity> {
        return filterTransactionsByProfile(
            transactions,
            _uiState.value.selectedProfileId,
            buildProfileAccountKeys(cachedAccountBalances)
        )
    }

    private fun filterVisibleBalances(
        allBalances: List<AccountBalanceEntity>,
        hiddenAccounts: Set<String>
    ): List<AccountBalanceEntity> {
        return filterAccountsByProfile(allBalances, hiddenAccounts, _uiState.value.selectedProfileId)
    }

    private fun computePeriodRollupsByCurrency(
        transactions: List<TransactionEntity>
    ): Map<String, PeriodRollup> {
        Log.d(TAG, "computePeriodRollupsByCurrency: totalTx=${transactions.size}")
        val excludedCount = transactions.count { it.isExcludedFromTracking }
        Log.d(TAG, "  excludedRows=$excludedCount, active=${transactions.size - excludedCount}")
        return transactions.groupBy { it.currency }.mapValues { (currency, txs) ->
            var income = BigDecimal.ZERO
            var spending = BigDecimal.ZERO
            var transfer = BigDecimal.ZERO
            var investment = BigDecimal.ZERO

            for (tx in txs) {
                if (tx.isExcludedFromTracking) continue
                if (tx.loanId != null) continue

                when (tx.transactionType) {
                    TransactionType.INCOME -> income += tx.amount
                    TransactionType.INVESTMENT -> investment += tx.amount
                    TransactionType.TRANSFER -> transfer += tx.amount
                    else -> Unit
                }
                if (tx.matchesAnalyticsSpendingFilter()) {
                    spending += tx.amount
                }
            }

            val incomeTxs = txs.count { !it.isExcludedFromTracking && it.transactionType == TransactionType.INCOME }
            val expenseTxs = txs.count { !it.isExcludedFromTracking && it.matchesAnalyticsSpendingFilter() }
            Log.d(TAG, "  [$currency] incomeTxCount=$incomeTxs income=$income | expenseTxCount=$expenseTxs expenses=$spending | net=${income - spending} | inv=$investment")

            PeriodRollup(
                income = income,
                spending = spending,
                transfer = transfer,
                investment = investment,
            )
        }
    }

    private fun loadUnifiedModePreferences() {
        viewModelScope.launch {
            combine(
                userPreferencesRepository.unifiedCurrencyMode,
                userPreferencesRepository.displayCurrency
            ) { unifiedMode, displayCurrency ->
                unifiedMode to displayCurrency
            }.collect { (unifiedMode, displayCurrency) ->
                val previousMode = _uiState.value.isUnifiedMode
                val previousCurrency = _uiState.value.selectedCurrency

                _uiState.value = _uiState.value.copy(isUnifiedMode = unifiedMode)

                if (unifiedMode && (previousMode != unifiedMode || previousCurrency != displayCurrency)) {
                    // Switch to unified mode: aggregate all currencies
                    selectCurrency(displayCurrency)
                }
            }
        }
    }

    private fun budgetPeriodRange(today: LocalDate): Pair<LocalDate, LocalDate> =
        DateRangeUtils.calculateBudgetPeriodRange(
            today,
            currentMonthStartDay,
            currentUseFixedBudgetPeriodEnd,
            currentBudgetPeriodEndDay,
            currentMonthOverrides
        )

    private suspend fun refreshPayPeriodPrefsCache() {
        currentMonthStartDay = userPreferencesRepository.monthStartDay.first()
        currentUseFixedBudgetPeriodEnd = userPreferencesRepository.useFixedBudgetPeriodEnd.first()
        currentBudgetPeriodEndDay = userPreferencesRepository.budgetPeriodEndDay.first()
    }

    private fun loadHomeData() {
        dataLoadingJob?.cancel()
        dataLoadingJob = viewModelScope.launch {
            refreshPayPeriodPrefsCache()
            val now = LocalDate.now()
            val useFinancial = userPreferencesRepository.useFinancialMonth.first()
            val (financialStart, financialEnd) = budgetPeriodRange(now)
            val calendarStart = now.withDayOfMonth(1)
            val spendingStart = if (useFinancial) financialStart else calendarStart
            val spendingPeriodLabel = if (useFinancial) {
                DateRangeUtils.formatDateRange(financialStart, financialEnd)
            } else {
                YearMonth.from(now).format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy"))
            }
            val periodEnd = if (useFinancial) financialEnd else now.withDayOfMonth(now.lengthOfMonth())
            val periodDayLabel = formatPeriodDayLabel(spendingStart, periodEnd, now)
            _uiState.value = _uiState.value.copy(
                spendingPeriodLabel = spendingPeriodLabel,
                useFinancialMonth = useFinancial,
                periodDayLabel = periodDayLabel,
                payPeriodStartEpochDay = spendingStart.toEpochDay(),
                payPeriodEndEpochDay = periodEnd.toEpochDay(),
            )
            val prevFinancialEnd = financialStart.minusDays(1)
            val (prevFinancialStart, _) = budgetPeriodRange(prevFinancialEnd)

            Log.d(TAG, "=== Breakdown date ranges ===")
            Log.d(TAG, "useFinancial=$useFinancial, now=$now")
            Log.d(TAG, "currentPeriod: $spendingStart → $now")
            Log.d(TAG, "prevPeriod:    $prevFinancialStart → $prevFinancialEnd")

        launch {
            // Load current month breakdown by currency (filtered by business/personal)
            combine(
                transactionRepository.getTransactionsBetweenDates(spendingStart, now),
                userPreferencesRepository.selectedProfileId,
                _cachedAccountBalances.filterNotNull()
            ) { transactions, profileId, balances ->
                computePeriodRollupsByCurrency(filterTransactionsByProfile(transactions, profileId, buildProfileAccountKeys(balances)))
            }.collect { rollupByCurrency ->
                updateBreakdownForSelectedCurrency(rollupByCurrency, isCurrentMonth = true)
            }
        }

        launch {
            // Load account balances — combined with unified mode preferences so that
            // individual account entities are pre-converted when unified mode is on.
            combine(
                accountBalanceRepository.getAllLatestBalances(),
                userPreferencesRepository.unifiedCurrencyMode,
                userPreferencesRepository.displayCurrency
            ) { allBalances, isUnified, displayCurrency ->
                Triple(allBalances, isUnified, displayCurrency)
            }.collect { (allBalances, isUnified, displayCurrency) ->
                // Cache the raw (unfiltered) balances for refreshAccountBalances/refreshHiddenAccounts
                _cachedAccountBalances.value = allBalances

                // Get hidden accounts from SharedPreferences
                val hiddenAccounts = sharedPrefs.getStringSet("hidden_accounts", emptySet()) ?: emptySet()

                // Filter out hidden accounts and apply business filter
                val balances = filterVisibleBalances(allBalances, hiddenAccounts)
                // Separate credit cards from regular accounts (hide zero balance accounts)
                val rawRegularAccounts = balances.filter { !it.isCreditCard && it.balance != BigDecimal.ZERO }
                val rawCreditCards = balances.filter { it.isCreditCard }

                // Check if we have multiple currencies and refresh exchange rates if needed
                val accountCurrencies = rawRegularAccounts.map { it.currency }.distinct()
                val creditCardCurrencies = rawCreditCards.map { it.currency }.distinct()
                val allAccountCurrencies = (accountCurrencies + creditCardCurrencies).distinct()

                if (allAccountCurrencies.size > 1 && allAccountCurrencies.isNotEmpty()) {
                    currencyConversionService.refreshExchangeRatesForAccount(allAccountCurrencies)
                }

                val selectedCurrency = if (isUnified) displayCurrency else _uiState.value.selectedCurrency

                // Pre-convert individual account entities when unified mode is on
                val regularAccounts = convertAccountEntities(rawRegularAccounts, selectedCurrency, isUnified)
                val creditCards = convertAccountEntities(rawCreditCards, selectedCurrency, isUnified)

                // Calculate totals from (possibly converted) entities
                var totalBalanceInSelectedCurrency = BigDecimal.ZERO
                for (account in regularAccounts) {
                    if (account.currency == selectedCurrency) {
                        totalBalanceInSelectedCurrency += account.balance
                    } else if (currencyConversionService.hasValidRate(account.currency, selectedCurrency)) {
                        totalBalanceInSelectedCurrency += currencyConversionService.convertAmount(
                            amount = account.balance,
                            fromCurrency = account.currency,
                            toCurrency = selectedCurrency
                        )
                    }
                }

                var totalAvailableCreditInSelectedCurrency = BigDecimal.ZERO
                for (card in creditCards) {
                    val availableInCardCurrency = (card.creditLimit ?: BigDecimal.ZERO) - card.balance
                    if (card.currency == selectedCurrency) {
                        totalAvailableCreditInSelectedCurrency += availableInCardCurrency
                    } else if (currencyConversionService.hasValidRate(card.currency, selectedCurrency)) {
                        totalAvailableCreditInSelectedCurrency += currencyConversionService.convertAmount(
                            amount = availableInCardCurrency,
                            fromCurrency = card.currency,
                            toCurrency = selectedCurrency
                        )
                    }
                }

                // Update available currencies to include account currencies
                val currentAvailableCurrencies = _uiState.value.availableCurrencies.toSet()
                val updatedAvailableCurrencies = (currentAvailableCurrencies + allAccountCurrencies)
                    .sortedWith { a, b ->
                        when {
                            a == baseCurrency -> -1
                            b == baseCurrency -> 1
                            else -> a.compareTo(b)
                        }
                    }

                // Determine if balance is ready (all conversions successful)
                val needsConversion = allAccountCurrencies.size > 1 &&
                    allAccountCurrencies.any { it != selectedCurrency }
                val balanceReady = if (needsConversion) {
                    allAccountCurrencies
                        .filter { it != selectedCurrency }
                        .all { currency ->
                            currencyConversionService.hasValidRate(currency, selectedCurrency)
                        }
                } else {
                    true
                }

                _uiState.value = _uiState.value.copy(
                    accountBalances = regularAccounts,  // Pre-converted in unified mode
                    creditCards = creditCards,           // Pre-converted in unified mode
                    totalBalance = totalBalanceInSelectedCurrency,
                    totalAvailableCredit = totalAvailableCreditInSelectedCurrency,
                    availableCurrencies = updatedAvailableCurrencies,
                    isBalanceReady = balanceReady
                )
            }
        }

        launch {
            // Load previous financial month breakdown for comparison
            combine(
                transactionRepository.getTransactionsBetweenDates(prevFinancialStart, prevFinancialEnd),
                userPreferencesRepository.selectedProfileId,
                _cachedAccountBalances.filterNotNull()
            ) { transactions, profileId, balances ->
                computePeriodRollupsByCurrency(filterTransactionsByProfile(transactions, profileId, buildProfileAccountKeys(balances)))
            }.collect { rollupByCurrency ->
                updateBreakdownForSelectedCurrency(rollupByCurrency, isCurrentMonth = false)
            }
        }

        launch {
            // Load cumulative spending sparkline for current + previous financial month comparison
            combine(
                transactionRepository.getTransactionsBetweenDates(
                    startDate = prevFinancialStart,
                    endDate = now
                ),
                userPreferencesRepository.selectedProfileId,
                _cachedAccountBalances.filterNotNull()
            ) { allTransactions, profileId, balances ->
                filterTransactionsByProfile(allTransactions, profileId, buildProfileAccountKeys(balances))
            }.collect { allTransactions ->
                val selectedCurrency = _uiState.value.selectedCurrency
                val isUnified = _uiState.value.isUnifiedMode

                // Split into current and previous financial month
                val currentMonthTxs = allTransactions.filter { it.dateTime.toLocalDate() >= financialStart }
                val lastMonthTxs = allTransactions.filter {
                    val d = it.dateTime.toLocalDate()
                    d >= prevFinancialStart && d < financialStart
                }

                // Filter to spending (EXPENSE + credit card purchases) excluding loans,
                // respecting currency / unified mode. TRANSFER is excluded so credit
                // card bill payments don't double-count alongside the original CREDIT
                // purchase row.
                val isSpending: (TransactionEntity) -> Boolean = { tx ->
                    !tx.isExcludedFromTracking &&
                        (tx.transactionType == TransactionType.EXPENSE ||
                            tx.transactionType == TransactionType.CREDIT) &&
                        tx.loanId == null
                }
                val currentExpenses = if (isUnified) {
                    currentMonthTxs.filter(isSpending)
                } else {
                    currentMonthTxs.filter { isSpending(it) && it.currency == selectedCurrency }
                }

                // Group by day and sum amounts (convert if unified mode)
                val dailySums = mutableMapOf<LocalDate, BigDecimal>()
                for (tx in currentExpenses) {
                    val day = tx.dateTime.toLocalDate()
                    val amount = if (isUnified && tx.currency != selectedCurrency) {
                        currencyConversionService.convertAmount(tx.amount, tx.currency, selectedCurrency)
                    } else {
                        tx.amount
                    }
                    dailySums[day] = (dailySums[day] ?: BigDecimal.ZERO) + amount
                }

                // Build cumulative list: one entry per day from financial month start to today
                val cumulativeList = mutableListOf<BigDecimal>()
                var cumulative = BigDecimal.ZERO
                var day = financialStart
                while (!day.isAfter(now)) {
                    cumulative += (dailySums[day] ?: BigDecimal.ZERO)
                    cumulativeList.add(cumulative)
                    day = day.plusDays(1)
                }

                // Build previous month's cumulative spending (same day count for comparison)
                val lastMonthExpenses = if (isUnified) {
                    lastMonthTxs.filter(isSpending)
                } else {
                    lastMonthTxs.filter { isSpending(it) && it.currency == selectedCurrency }
                }

                val lastMonthDailySums = mutableMapOf<LocalDate, BigDecimal>()
                for (tx in lastMonthExpenses) {
                    val txDay = tx.dateTime.toLocalDate()
                    val amount = if (isUnified && tx.currency != selectedCurrency) {
                        currencyConversionService.convertAmount(tx.amount, tx.currency, selectedCurrency)
                    } else {
                        tx.amount
                    }
                    lastMonthDailySums[txDay] = (lastMonthDailySums[txDay] ?: BigDecimal.ZERO) + amount
                }

                val daysElapsed = ChronoUnit.DAYS.between(financialStart, now).toInt() + 1
                val lastMonthCumulative = mutableListOf<BigDecimal>()
                var lastCum = BigDecimal.ZERO
                var lastDay = prevFinancialStart
                var dayCount = 0
                while (dayCount < daysElapsed && !lastDay.isAfter(prevFinancialEnd)) {
                    lastCum += (lastMonthDailySums[lastDay] ?: BigDecimal.ZERO)
                    lastMonthCumulative.add(lastCum)
                    lastDay = lastDay.plusDays(1)
                    dayCount++
                }

                val useFinancial = _uiState.value.useFinancialMonth
                val periodStart = if (useFinancial) financialStart else now.withDayOfMonth(1)
                val periodEnd = if (useFinancial) {
                    financialEnd
                } else {
                    now.withDayOfMonth(now.lengthOfMonth())
                }
                val periodTxs = allTransactions.filter { tx ->
                    val day = tx.dateTime.toLocalDate()
                    !day.isBefore(periodStart) && !day.isAfter(now)
                }
                val periodSpendingTxs = if (isUnified) {
                    periodTxs.filter(isSpending)
                } else {
                    periodTxs.filter { isSpending(it) && it.currency == selectedCurrency }
                }
                val currentSpendTotal = cumulativeList.lastOrNull() ?: BigDecimal.ZERO
                val lastSpendTotal = lastMonthCumulative.lastOrNull() ?: BigDecimal.ZERO
                val stripLabels = computeHomeStripLabels(
                    periodTransactions = periodTxs,
                    spendingTransactions = periodSpendingTxs,
                    periodStart = periodStart,
                    periodEnd = periodEnd,
                    now = now,
                    selectedCurrency = selectedCurrency,
                    isUnified = isUnified,
                    totalExpenses = currentSpendTotal,
                    spendingIncreased = currentSpendTotal >= lastSpendTotal,
                )
                _uiState.value = _uiState.value.copy(
                    spendingHistory = cumulativeList,
                    balanceHistory = cumulativeList,
                    lastMonthSpendingHistory = lastMonthCumulative,
                    periodDayLabel = formatPeriodDayLabel(financialStart, periodEnd, now),
                    incomeTodayLabel = stripLabels.incomeTodayLabel,
                    topCategoryName = stripLabels.topCategoryName,
                    topCategorySubLabel = stripLabels.topCategorySubLabel,
                    dailyAverageLabel = stripLabels.dailyAverageLabel,
                    paceLabel = stripLabels.paceLabel,
                )
                calculateMonthlyChange()
            }
        }

        launch {
            // Load active loans summary for home carousel
            combine(
                loanRepository.getActiveLoans(),
                userPreferencesRepository.unifiedCurrencyMode,
                userPreferencesRepository.displayCurrency
            ) { loans, isUnified, displayCurrency ->
                if (loans.isEmpty()) null
                else Triple(loans, isUnified, displayCurrency)
            }.collect { summary ->
                if (summary == null) {
                    _uiState.value = _uiState.value.copy(loanSummary = null)
                    return@collect
                }

                val (loans, isUnified, displayCurrency) = summary
                val selectedCurrency = if (isUnified) displayCurrency else _uiState.value.selectedCurrency

                val loanCurrencies = loans.map { it.currency }.distinct()
                if (isUnified && loanCurrencies.size > 1) {
                    currencyConversionService.refreshExchangeRatesForAccount((loanCurrencies + selectedCurrency).distinct())
                }

                val loansForTotals = if (isUnified) {
                    loans
                } else {
                    loans.filter { it.currency.equals(selectedCurrency, ignoreCase = true) }
                }

                var lentTotal = BigDecimal.ZERO
                var borrowedTotal = BigDecimal.ZERO
                for (loan in loansForTotals) {
                    val amount = if (isUnified) {
                        currencyConversionService.convertAmount(
                            amount = loan.remainingAmount,
                            fromCurrency = loan.currency,
                            toCurrency = selectedCurrency
                        )
                    } else {
                        loan.remainingAmount
                    }
                    when (loan.direction) {
                        com.pennywiseai.tracker.data.database.entity.LoanDirection.LENT -> lentTotal += amount
                        com.pennywiseai.tracker.data.database.entity.LoanDirection.BORROWED -> borrowedTotal += amount
                    }
                }

                _uiState.value = _uiState.value.copy(
                    loanSummary = LoanSummary(
                        activeLoans = loans,
                        totalLentRemaining = lentTotal,
                        totalBorrowedRemaining = borrowedTotal
                    )
                )
            }
        }

        launch {
            // Load transaction heatmap (last 26 weeks / 182 days)
            val heatmapStart = LocalDate.now().minusDays(182)
            combine(
                transactionRepository.getTransactionsBetweenDates(
                    startDate = heatmapStart,
                    endDate = LocalDate.now()
                ),
                userPreferencesRepository.selectedProfileId,
                _cachedAccountBalances.filterNotNull()
            ) { transactions, profileId, balances ->
                filterTransactionsByProfile(transactions, profileId, buildProfileAccountKeys(balances))
            }.collect { transactions ->
                val heatmap = transactions
                    .groupBy { it.dateTime.toLocalDate().toEpochDay() }
                    .mapValues { it.value.size }
                _uiState.value = _uiState.value.copy(transactionHeatmap = heatmap)
            }
        }

        launch {
            // Load daily items reactively — re-runs whenever selectedDate changes
            val rawGroupsFlow = transactionGroupRepository.getAllGroups().flatMapLatest { groups ->
                if (groups.isEmpty()) flowOf(emptyList())
                else combine(groups.map { group ->
                    transactionGroupRepository.getTransactionsForGroup(group.id)
                        .map { txns -> group to txns }
                }) { it.toList() }
            }

            _selectedDate.flatMapLatest { date ->
                combine(
                    combine(
                        transactionGroupRepository.getUngroupedTransactionsForDate(date),
                        _cachedAccountBalances,
                    ) { ungrouped, balances ->
                        val profileId = _uiState.value.selectedProfileId
                        val keys = buildProfileAccountKeys(balances ?: emptyList())
                        filterTransactionsByProfile(ungrouped, profileId, keys)
                            .map { HomeRecentItem.SingleTransaction(it) }
                    },
                    combine(
                        rawGroupsFlow,
                        _cachedAccountBalances,
                    ) { groupPairs, balances ->
                        val profileId = _uiState.value.selectedProfileId
                        val keys = buildProfileAccountKeys(balances ?: emptyList())
                        groupPairs.mapNotNull { (group, txns) ->
                            val filtered = filterTransactionsByProfile(txns, profileId, keys)
                                .filter { it.dateTime.toLocalDate() == date }
                            if (filtered.isEmpty()) null
                            else HomeRecentItem.GroupItem(group, filtered)
                        }
                    },
                    userPreferencesRepository.unifiedCurrencyMode,
                    userPreferencesRepository.displayCurrency,
                ) { singles, groups, isUnified, displayCurrency ->
                    val merged = (singles + groups).sortedByDescending { it.sortTime }

                    if (!isUnified) return@combine merged

                    merged.map { item ->
                        when (item) {
                            is HomeRecentItem.SingleTransaction -> {
                                val converted =
                                    if (!item.transaction.currency.equals(displayCurrency, ignoreCase = true)) {
                                        currencyConversionService.convertAmount(
                                            item.transaction.amount,
                                            item.transaction.currency,
                                            displayCurrency,
                                        )
                                    } else {
                                        null
                                    }
                                item.copy(convertedAmount = converted)
                            }
                            is HomeRecentItem.GroupItem -> {
                                val amounts = item.transactions
                                    .filter { !it.currency.equals(displayCurrency, ignoreCase = true) }
                                    .associate { tx ->
                                        tx.id to currencyConversionService.convertAmount(
                                            tx.amount,
                                            tx.currency,
                                            displayCurrency,
                                        )
                                    }
                                item.copy(convertedAmounts = amounts)
                            }
                        }
                    }
                }
            }.collect { items ->
                _uiState.value = _uiState.value.copy(recentItems = items, isLoading = false)
            }
        }

        launch {
            subscriptionRepository.getActiveSubscriptions().collect { list ->
                _uiState.value = _uiState.value.copy(activeSubscriptionCount = list.size)
            }
        }
        } // end dataLoadingJob
    }
    
    private fun calculateMonthlyChange() {
        val currentExpenses = _uiState.value.currentMonthExpenses
        val lastExpenses = _uiState.value.lastMonthExpenses
        val change = currentExpenses - lastExpenses
        val changePercent = if (lastExpenses != BigDecimal.ZERO) {
            change.multiply(BigDecimal(100)).divide(lastExpenses, 0, RoundingMode.HALF_UP).toInt()
        } else {
            0
        }
        _uiState.value = _uiState.value.copy(
            monthlyChange = change,
            monthlyChangePercent = changePercent
        )
    }
    
    /** Navigate the daily-spends section forward (+1) or backward (-1) by [days]. Cannot go past today. */
    fun navigateDateBy(days: Int) {
        val newDate = _selectedDate.value.plusDays(days.toLong())
        if (newDate.isAfter(LocalDate.now())) return
        _selectedDate.value = newDate
        _uiState.value = _uiState.value.copy(selectedDate = newDate, isLoading = true)
    }

    /** Jump directly to a specific date. Cannot go past today. */
    fun navigateToDate(date: LocalDate) {
        if (date.isAfter(LocalDate.now())) return
        _selectedDate.value = date
        _uiState.value = _uiState.value.copy(selectedDate = date, isLoading = true)
    }

    /**
     * Per-day debit + card spend (excluding loan-linked rows), for [startInclusive]…[endInclusive],
     * respecting profile and currency filters.
     */
    private fun dailySpendingTotalsFlow(
        startInclusive: LocalDate,
        endInclusive: LocalDate,
    ): kotlinx.coroutines.flow.Flow<Map<LocalDate, BigDecimal>> =
        combine(
            transactionRepository.getTransactionsBetweenDates(startInclusive, endInclusive),
            userPreferencesRepository.selectedProfileId,
            _cachedAccountBalances.filterNotNull()
        ) { transactions, profileId, balances ->
            val filtered = filterTransactionsByProfile(transactions, profileId, buildProfileAccountKeys(balances))
            val selectedCurrency = _uiState.value.selectedCurrency
            val isUnified = _uiState.value.isUnifiedMode
            val isSpending: (TransactionEntity) -> Boolean = { tx ->
                !tx.isExcludedFromTracking &&
                    (tx.transactionType == TransactionType.EXPENSE ||
                        tx.transactionType == TransactionType.CREDIT) &&
                    tx.loanId == null
            }
            val expenses = if (isUnified) {
                filtered.filter(isSpending)
            } else {
                filtered.filter { isSpending(it) && it.currency == selectedCurrency }
            }
            val dailySums = mutableMapOf<LocalDate, BigDecimal>()
            for (tx in expenses) {
                val day = tx.dateTime.toLocalDate()
                val amount = if (isUnified && tx.currency != selectedCurrency) {
                    currencyConversionService.convertAmount(tx.amount, tx.currency, selectedCurrency)
                } else {
                    tx.amount
                }
                dailySums[day] = (dailySums[day] ?: BigDecimal.ZERO) + amount
            }
            dailySums
        }

    /** Returns a Flow of per-day expense totals for [monthStart]'s month, respecting profile/currency filters. */
    fun getDailyExpensesForMonth(monthStart: LocalDate): kotlinx.coroutines.flow.Flow<Map<LocalDate, BigDecimal>> {
        val monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth())
        return dailySpendingTotalsFlow(monthStart, monthEnd)
    }

    /**
     * Same totals as [getDailyExpensesForMonth] but for an arbitrary inclusive date range
     * (used for the home 7-day strip so it stays correct across month boundaries).
     */
    fun getDailyExpensesBetween(
        startInclusive: LocalDate,
        endInclusive: LocalDate,
    ): kotlinx.coroutines.flow.Flow<Map<LocalDate, BigDecimal>> {
        require(!endInclusive.isBefore(startInclusive)) {
            "endInclusive ($endInclusive) must not be before startInclusive ($startInclusive)"
        }
        return dailySpendingTotalsFlow(startInclusive, endInclusive)
    }

    fun refreshHiddenAccounts() {
        viewModelScope.launch {
            val allBalances = cachedAccountBalances
            if (allBalances.isEmpty()) return@launch

            val hiddenAccounts = sharedPrefs.getStringSet("hidden_accounts", emptySet()) ?: emptySet()

            val visibleBalances = filterVisibleBalances(allBalances, hiddenAccounts)

            val rawRegularAccounts = visibleBalances.filter { !it.isCreditCard && it.balance != BigDecimal.ZERO }
            val rawCreditCards = visibleBalances.filter { it.isCreditCard }

            val selectedCurrency = _uiState.value.selectedCurrency
            val isUnified = _uiState.value.isUnifiedMode

            // Pre-convert individual account entities when unified mode is on
            val regularAccounts = convertAccountEntities(rawRegularAccounts, selectedCurrency, isUnified)
            val creditCards = convertAccountEntities(rawCreditCards, selectedCurrency, isUnified)

            var totalBalance = BigDecimal.ZERO
            for (account in regularAccounts) {
                if (account.currency == selectedCurrency) {
                    totalBalance += account.balance
                } else if (currencyConversionService.hasValidRate(account.currency, selectedCurrency)) {
                    totalBalance += currencyConversionService.convertAmount(
                        amount = account.balance,
                        fromCurrency = account.currency,
                        toCurrency = selectedCurrency
                    )
                }
            }
            var totalAvailableCredit = BigDecimal.ZERO
            for (card in creditCards) {
                val availableInCardCurrency = (card.creditLimit ?: BigDecimal.ZERO) - card.balance
                if (card.currency == selectedCurrency) {
                    totalAvailableCredit += availableInCardCurrency
                } else if (currencyConversionService.hasValidRate(card.currency, selectedCurrency)) {
                    totalAvailableCredit += currencyConversionService.convertAmount(
                        amount = availableInCardCurrency,
                        fromCurrency = card.currency,
                        toCurrency = selectedCurrency
                    )
                }
            }
            // Determine if balance is ready (all conversions successful)
            val accountCurrencies = regularAccounts.map { it.currency }.distinct()
            val creditCardCurrencies = creditCards.map { it.currency }.distinct()
            val allAccountCurrencies = (accountCurrencies + creditCardCurrencies).distinct()
            val needsConversion = allAccountCurrencies.size > 1 &&
                allAccountCurrencies.any { it != selectedCurrency }
            val balanceReady = if (needsConversion) {
                allAccountCurrencies
                    .filter { it != selectedCurrency }
                    .all { currency ->
                        currencyConversionService.hasValidRate(currency, selectedCurrency)
                    }
            } else {
                true
            }

            _uiState.value = _uiState.value.copy(
                accountBalances = regularAccounts,
                creditCards = creditCards,
                totalBalance = totalBalance,
                totalAvailableCredit = totalAvailableCredit,
                isBalanceReady = balanceReady
            )
        }
    }

    /**
     * Background incremental scan on app launch / resume. Throttled and will not cancel an
     * in-flight manual scan (uses [ExistingWorkPolicy.KEEP]).
     */
    fun autoScanIfNeeded() {
        if (!hasSmsReadPermission()) return
        if (smsScanManager.scheduleIncrementalScan(replaceExisting = false)) {
            _uiState.value = _uiState.value.copy(isScanning = true)
            observeWorkProgress()
        }
    }

    /**
     * Scans SMS messages for transactions.
     * @param forceResync If true, performs a full resync from scratch, reprocessing all SMS messages.
     *                    This is useful when bank parsers have been updated and old transactions need to be re-parsed.
     *                    If false (default), performs an incremental scan for new messages only.
     */
    fun scanSmsMessages(forceResync: Boolean = false) {
        smsScanManager.scheduleIncrementalScan(
            forceResync = forceResync,
            replaceExisting = true,
        )
        _uiState.value = _uiState.value.copy(isScanning = true)
        observeWorkProgress()
    }

    private fun hasSmsReadPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED

    private fun observeWorkProgress() {
        val workManager = WorkManager.getInstance(context)

        // Use getWorkInfosById for more direct observation
        workManager.getWorkInfosByTagLiveData(OptimizedSmsReaderWorker.WORK_NAME).observeForever { workInfos ->
            val currentWork = workInfos.firstOrNull { it.tags.contains(OptimizedSmsReaderWorker.WORK_NAME) }
            if (currentWork != null) {
                _smsScanWorkInfo.value = currentWork

                // Update scanning state based on work state
                when (currentWork.state) {
                    WorkInfo.State.SUCCEEDED,
                    WorkInfo.State.FAILED,
                    WorkInfo.State.CANCELLED,
                    WorkInfo.State.BLOCKED -> {
                        _uiState.value = _uiState.value.copy(isScanning = false)
                    }
                    else -> {
                        // Still running or enqueued
                        _uiState.value = _uiState.value.copy(isScanning = true)
                    }
                }
            }
        }
    }

    fun cancelSmsScan() {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(OptimizedSmsReaderWorker.WORK_NAME)
        _uiState.value = _uiState.value.copy(isScanning = false)
    }

    fun refreshAccountBalances() {
        viewModelScope.launch {
            // Use cached balances instead of starting a new .collect — this prevents
            // a race condition where two competing collectors would cause the balance
            // to show with the wrong currency symbol.
            val allBalances = cachedAccountBalances
            if (allBalances.isEmpty()) return@launch

            val hiddenAccounts = sharedPrefs.getStringSet("hidden_accounts", emptySet()) ?: emptySet()

            val balances = filterVisibleBalances(allBalances, hiddenAccounts)
            val rawRegularAccounts = balances.filter { !it.isCreditCard && it.balance != BigDecimal.ZERO }
            val rawCreditCards = balances.filter { it.isCreditCard }

            val accountCurrencies = rawRegularAccounts.map { it.currency }.distinct()
            val creditCardCurrencies = rawCreditCards.map { it.currency }.distinct()
            val allAccountCurrencies = (accountCurrencies + creditCardCurrencies).distinct()

            if (allAccountCurrencies.size > 1 && allAccountCurrencies.isNotEmpty()) {
                currencyConversionService.refreshExchangeRatesForAccount(allAccountCurrencies)
            }

            val selectedCurrency = _uiState.value.selectedCurrency
            val isUnified = _uiState.value.isUnifiedMode

            // Pre-convert individual account entities when unified mode is on
            val regularAccounts = convertAccountEntities(rawRegularAccounts, selectedCurrency, isUnified)
            val creditCards = convertAccountEntities(rawCreditCards, selectedCurrency, isUnified)

            var totalBalanceInSelectedCurrency = BigDecimal.ZERO
            for (account in regularAccounts) {
                if (account.currency == selectedCurrency) {
                    totalBalanceInSelectedCurrency += account.balance
                } else if (currencyConversionService.hasValidRate(account.currency, selectedCurrency)) {
                    totalBalanceInSelectedCurrency += currencyConversionService.convertAmount(
                        amount = account.balance,
                        fromCurrency = account.currency,
                        toCurrency = selectedCurrency
                    )
                }
            }

            var totalAvailableCreditInSelectedCurrency = BigDecimal.ZERO
            for (card in creditCards) {
                val availableInCardCurrency = (card.creditLimit ?: BigDecimal.ZERO) - card.balance
                if (card.currency == selectedCurrency) {
                    totalAvailableCreditInSelectedCurrency += availableInCardCurrency
                } else if (currencyConversionService.hasValidRate(card.currency, selectedCurrency)) {
                    totalAvailableCreditInSelectedCurrency += currencyConversionService.convertAmount(
                        amount = availableInCardCurrency,
                        fromCurrency = card.currency,
                        toCurrency = selectedCurrency
                    )
                }
            }

            // Determine if balance is ready (all conversions successful)
            val needsConversion = allAccountCurrencies.size > 1 &&
                allAccountCurrencies.any { it != selectedCurrency }
            val balanceReady = if (needsConversion) {
                allAccountCurrencies
                    .filter { it != selectedCurrency }
                    .all { currency ->
                        currencyConversionService.hasValidRate(currency, selectedCurrency)
                    }
            } else {
                true
            }

            _uiState.value = _uiState.value.copy(
                accountBalances = regularAccounts,
                creditCards = creditCards,
                totalBalance = totalBalanceInSelectedCurrency,
                totalAvailableCredit = totalAvailableCreditInSelectedCurrency,
                isBalanceReady = balanceReady
            )
        }
    }

    fun updateSystemPrompt() {
        viewModelScope.launch {
            try {
                llmRepository.updateSystemPrompt()
            } catch (e: Exception) {
                // Handle error silently or add error state if needed
            }
        }
    }
    
    fun showBreakdownDialog() {
        _uiState.value = _uiState.value.copy(showBreakdownDialog = true)
    }
    
    fun hideBreakdownDialog() {
        _uiState.value = _uiState.value.copy(showBreakdownDialog = false)
    }
    
    /**
     * Checks for app updates using Google Play In-App Updates.
     * Should be called with the current activity context.
     * @param activity The activity context
     * @param snackbarHostState Optional SnackbarHostState for showing restart prompt
     * @param scope Optional CoroutineScope for launching the snackbar
     */
    fun checkForAppUpdate(
        activity: ComponentActivity,
        snackbarHostState: androidx.compose.material3.SnackbarHostState? = null,
        scope: kotlinx.coroutines.CoroutineScope? = null
    ) {
        inAppUpdateManager.checkForUpdate(activity, snackbarHostState, scope)
    }
    
    fun deleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            _deletedTransaction.value = transaction
            transactionRepository.deleteTransaction(transaction)
        }
    }

    fun toggleExcludedFromTracking(transaction: TransactionEntity) {
        viewModelScope.launch {
            transactionRepository.updateExcludedFromTracking(
                transaction.id,
                !transaction.isExcludedFromTracking,
            )
        }
    }
    
    fun undoDelete() {
        _deletedTransaction.value?.let { transaction ->
            viewModelScope.launch {
                transactionRepository.undoDeleteTransaction(transaction)
                _deletedTransaction.value = null
            }
        }
    }
    
    fun undoDeleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            transactionRepository.undoDeleteTransaction(transaction)
        }
    }
    
    fun clearDeletedTransaction() {
        _deletedTransaction.value = null
    }
    
    /**
     * Checks if eligible for in-app review and shows if appropriate.
     * Should be called with the current activity context.
     */
    fun checkForInAppReview(activity: ComponentActivity) {
        viewModelScope.launch {
            // Get current transaction count as additional eligibility factor
            val transactionCount = transactionRepository.getAllTransactions().first().size
            inAppReviewManager.checkAndShowReviewIfEligible(activity, transactionCount)
        }
    }
    
    fun selectCurrency(currency: String) {
        hasUserSelectedCurrency = true
        _uiState.value = _uiState.value.copy(isBalanceReady = false)
        // Update monthly breakdown values from stored maps
        val availableCurrencies = _uiState.value.availableCurrencies
        updateUIStateForCurrency(currency, availableCurrencies)

        // Refresh account balances to convert them to the new selected currency
        refreshAccountBalances()

        // Also refresh transaction type totals for new currency
        viewModelScope.launch {
            refreshPayPeriodPrefsCache()
            val now = LocalDate.now()
            val useFinancial = userPreferencesRepository.useFinancialMonth.first()
            val (financialStart, _) = budgetPeriodRange(now)
            val spendingStart = if (useFinancial) financialStart else now.withDayOfMonth(1)
            val allTransactions = transactionRepository.getTransactionsBetweenDates(
                startDate = spendingStart,
                endDate = now,
            ).first()
            val transactions = filterTransactions(allTransactions)
            refreshCurrentPeriodRollupFieldsFromTransactions(transactions)
        }
    }

    private fun refreshCurrentPeriodRollupFieldsFromTransactions(transactions: List<TransactionEntity>) {
        val rollupByCurrency = computePeriodRollupsByCurrency(transactions)
        viewModelScope.launch {
            val selectedCurrency = _uiState.value.selectedCurrency
            val isUnified = _uiState.value.isUnifiedMode
            val aggregated = if (isUnified) {
                aggregatePeriodRollups(rollupByCurrency, selectedCurrency)
            } else {
                rollupByCurrency[selectedCurrency] ?: PeriodRollup.ZERO
            }
            _uiState.value = _uiState.value.copy(
                currentMonthInvestment = aggregated.investment,
                currentMonthTransfer = aggregated.transfer,
            )
            calculateMonthlyChange()
        }
    }

    private fun updateBreakdownForSelectedCurrency(
        rollupByCurrency: Map<String, PeriodRollup>,
        isCurrentMonth: Boolean
    ) {
        Log.d(TAG, "updateBreakdownForSelectedCurrency: isCurrentMonth=$isCurrentMonth, currencies=${rollupByCurrency.keys}")
        rollupByCurrency.forEach { (cur, r) ->
            val b = r.toMonthlyBreakdown()
            Log.d(TAG, "  [$cur] income=${b.income} expenses=${b.expenses} net=${b.total}")
        }
        if (isCurrentMonth) {
            currentMonthRollupMap = rollupByCurrency
        } else {
            lastMonthRollupMap = rollupByCurrency
        }

        val transactionCurrencies = (currentMonthRollupMap.keys + lastMonthRollupMap.keys)
        val existingCurrencies = _uiState.value.availableCurrencies
        val availableCurrencies = (existingCurrencies + transactionCurrencies).distinct().sortedWith { a, b ->
            when {
                a == baseCurrency -> -1
                b == baseCurrency -> 1
                else -> a.compareTo(b)
            }
        }

        val currentSelectedCurrency = _uiState.value.selectedCurrency
        if (!availableCurrencies.contains(currentSelectedCurrency) && availableCurrencies.isNotEmpty()) {
            viewModelScope.launch {
                val baseCurrencyPref = userPreferencesRepository.baseCurrency.first()
                val selectedCurrency = when {
                    availableCurrencies.contains(baseCurrencyPref) -> baseCurrencyPref
                    availableCurrencies.contains("INR") -> "INR"
                    else -> availableCurrencies.first()
                }
                updateUIStateForCurrency(selectedCurrency, availableCurrencies)
            }
        } else {
            updateUIStateForCurrency(currentSelectedCurrency, availableCurrencies)
        }
    }

    private fun updateUIStateForCurrency(selectedCurrency: String, availableCurrencies: List<String>) {
        Log.d(TAG, "updateUIStateForCurrency: selectedCurrency=$selectedCurrency, isUnifiedMode=${_uiState.value.isUnifiedMode}")
        if (_uiState.value.isUnifiedMode) {
            viewModelScope.launch {
                val currentAgg = aggregatePeriodRollups(currentMonthRollupMap, selectedCurrency)
                val lastAgg = aggregatePeriodRollups(lastMonthRollupMap, selectedCurrency)
                val currentBreakdown = currentAgg.toMonthlyBreakdown()
                val lastBreakdown = lastAgg.toMonthlyBreakdown()

                Log.d(TAG, "  [unified] current => income=${currentBreakdown.income} expenses=${currentBreakdown.expenses} net=${currentBreakdown.total}")
                Log.d(TAG, "  [unified] last    => income=${lastBreakdown.income} expenses=${lastBreakdown.expenses} net=${lastBreakdown.total}")

                _uiState.value = _uiState.value.copy(
                    currentMonthTotal = currentBreakdown.total,
                    currentMonthIncome = currentBreakdown.income,
                    currentMonthExpenses = currentBreakdown.expenses,
                    currentMonthInvestment = currentAgg.investment,
                    currentMonthTransfer = currentAgg.transfer,
                    lastMonthTotal = lastBreakdown.total,
                    lastMonthIncome = lastBreakdown.income,
                    lastMonthExpenses = lastBreakdown.expenses,
                    selectedCurrency = selectedCurrency,
                    availableCurrencies = availableCurrencies
                )
                calculateMonthlyChange()
            }
        } else {
            val currentAgg = currentMonthRollupMap[selectedCurrency] ?: PeriodRollup.ZERO
            val lastAgg = lastMonthRollupMap[selectedCurrency] ?: PeriodRollup.ZERO
            val currentBreakdown = currentAgg.toMonthlyBreakdown()
            val lastBreakdown = lastAgg.toMonthlyBreakdown()

            Log.d(TAG, "  [single=$selectedCurrency] current => income=${currentBreakdown.income} expenses=${currentBreakdown.expenses} net=${currentBreakdown.total}")
            Log.d(TAG, "  [single=$selectedCurrency] last    => income=${lastBreakdown.income} expenses=${lastBreakdown.expenses} net=${lastBreakdown.total}")
            if (currentMonthRollupMap[selectedCurrency] == null) {
                Log.w(TAG, "  WARNING: no current-month rollup for $selectedCurrency — returning zeros. Available: ${currentMonthRollupMap.keys}")
            }
            if (lastMonthRollupMap[selectedCurrency] == null) {
                Log.w(TAG, "  WARNING: no last-month rollup for $selectedCurrency — returning zeros. Available: ${lastMonthRollupMap.keys}")
            }

            _uiState.value = _uiState.value.copy(
                currentMonthTotal = currentBreakdown.total,
                currentMonthIncome = currentBreakdown.income,
                currentMonthExpenses = currentBreakdown.expenses,
                currentMonthInvestment = currentAgg.investment,
                currentMonthTransfer = currentAgg.transfer,
                lastMonthTotal = lastBreakdown.total,
                lastMonthIncome = lastBreakdown.income,
                lastMonthExpenses = lastBreakdown.expenses,
                selectedCurrency = selectedCurrency,
                availableCurrencies = availableCurrencies
            )
            calculateMonthlyChange()
        }
    }

    private suspend fun aggregatePeriodRollups(
        rollupMap: Map<String, PeriodRollup>,
        targetCurrency: String
    ): PeriodRollup {
        Log.d(TAG, "aggregatePeriodRollups: targetCurrency=$targetCurrency, inputCurrencies=${rollupMap.keys}")
        var income = BigDecimal.ZERO
        var spending = BigDecimal.ZERO
        var transfer = BigDecimal.ZERO
        var investment = BigDecimal.ZERO

        for ((currency, r) in rollupMap) {
            if (currency == targetCurrency) {
                income += r.income
                spending += r.spending
                transfer += r.transfer
                investment += r.investment
            } else {
                val convertedIncome = currencyConversionService.convertAmount(r.income, currency, targetCurrency)
                val convertedSpending = currencyConversionService.convertAmount(r.spending, currency, targetCurrency)
                val convertedTransfer = currencyConversionService.convertAmount(r.transfer, currency, targetCurrency)
                val convertedInvestment = currencyConversionService.convertAmount(r.investment, currency, targetCurrency)
                income += convertedIncome
                spending += convertedSpending
                transfer += convertedTransfer
                investment += convertedInvestment
            }
        }

        return PeriodRollup(
            income = income,
            spending = spending,
            transfer = transfer,
            investment = investment,
        )
    }

    private suspend fun convertAccountEntities(
        entities: List<AccountBalanceEntity>,
        targetCurrency: String,
        isUnifiedMode: Boolean
    ): List<AccountBalanceEntity> {
        if (!isUnifiedMode) return entities
        return entities.map { account ->
            if (account.currency == targetCurrency) {
                account
            } else {
                val convertedBalance = currencyConversionService.convertAmount(
                    amount = account.balance,
                    fromCurrency = account.currency,
                    toCurrency = targetCurrency
                )
                val convertedCreditLimit = if (account.isCreditCard && account.creditLimit != null) {
                    currencyConversionService.convertAmount(
                        amount = account.creditLimit,
                        fromCurrency = account.currency,
                        toCurrency = targetCurrency
                    )
                } else {
                    account.creditLimit
                }
                account.copy(
                    balance = convertedBalance,
                    creditLimit = convertedCreditLimit,
                    currency = targetCurrency
                )
            }
        }
    }

    private fun formatPeriodDayLabel(
        periodStart: LocalDate,
        periodEnd: LocalDate,
        today: LocalDate,
    ): String {
        val totalDays = ChronoUnit.DAYS.between(periodStart, periodEnd).toInt() + 1
        val currentDay = ChronoUnit.DAYS.between(periodStart, today).toInt() + 1
        if (totalDays <= 0) return ""
        return context.getString(
            R.string.home_period_day,
            currentDay.coerceIn(1, totalDays),
            totalDays,
        )
    }

    private data class HomeStripLabels(
        val incomeTodayLabel: String,
        val topCategoryName: String,
        val topCategorySubLabel: String,
        val dailyAverageLabel: String,
        val paceLabel: String,
    )

    private suspend fun computeHomeStripLabels(
        periodTransactions: List<TransactionEntity>,
        spendingTransactions: List<TransactionEntity>,
        periodStart: LocalDate,
        periodEnd: LocalDate,
        now: LocalDate,
        selectedCurrency: String,
        isUnified: Boolean,
        totalExpenses: BigDecimal,
        spendingIncreased: Boolean,
    ): HomeStripLabels {
        val today = now
        val incomeTodayCount = periodTransactions.count { tx ->
            !tx.isExcludedFromTracking &&
                tx.transactionType == TransactionType.INCOME &&
                tx.dateTime.toLocalDate() == today
        }
        val incomeTodayLabel = when (incomeTodayCount) {
            0 -> context.getString(R.string.home_income_today_none)
            1 -> context.getString(R.string.home_income_today_one)
            else -> context.getString(R.string.home_income_today_many, incomeTodayCount)
        }

        val unknownCategory = context.getString(R.string.home_top_category_unknown)
        val categoryTotals = mutableMapOf<String, BigDecimal>()
        for (tx in spendingTransactions) {
            val category = tx.category.trim().ifEmpty { unknownCategory }
            val amount = if (isUnified && !tx.currency.equals(selectedCurrency, ignoreCase = true)) {
                currencyConversionService.convertAmount(tx.amount, tx.currency, selectedCurrency)
            } else {
                tx.amount
            }
            categoryTotals[category] = (categoryTotals[category] ?: BigDecimal.ZERO) + amount
        }
        val topEntry = categoryTotals.maxByOrNull { it.value }
        val (topCategoryName, topCategorySubLabel) = if (topEntry == null || topEntry.value <= BigDecimal.ZERO) {
            context.getString(R.string.home_top_category_unknown) to
                context.getString(R.string.home_top_category_empty)
        } else {
            val share = if (totalExpenses > BigDecimal.ZERO) {
                topEntry.value
                    .multiply(BigDecimal(100))
                    .divide(totalExpenses, 0, RoundingMode.HALF_UP)
                    .toInt()
            } else {
                0
            }
            CurrencyFormatter.formatCurrency(topEntry.value, selectedCurrency) to
                context.getString(R.string.home_top_category_sub, topEntry.key, share)
        }

        val daysElapsed = ChronoUnit.DAYS.between(periodStart, now).toInt().coerceAtLeast(0) + 1
        val dailyAvg = if (daysElapsed > 0) {
            totalExpenses.divide(BigDecimal(daysElapsed), 0, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }
        val dailyAverageLabel = "${CurrencyFormatter.formatCurrency(dailyAvg, selectedCurrency)}/day"
        val paceLabel = if (spendingIncreased) {
            context.getString(R.string.home_pace_above_last)
        } else {
            context.getString(R.string.home_pace_on_track)
        }

        return HomeStripLabels(
            incomeTodayLabel = incomeTodayLabel,
            topCategoryName = topCategoryName,
            topCategorySubLabel = topCategorySubLabel,
            dailyAverageLabel = dailyAverageLabel,
            paceLabel = paceLabel,
        )
    }

    override fun onCleared() {
        super.onCleared()
        inAppUpdateManager.cleanup()
    }
}

data class HomeUiState(
    val userName: String = "User",
    val profileImageUri: String? = null,
    val profileBackgroundColor: Int = 0,
    val selectedDate: LocalDate = LocalDate.now(),
    val currentMonthTotal: BigDecimal = BigDecimal.ZERO,
    val currentMonthIncome: BigDecimal = BigDecimal.ZERO,
    val currentMonthExpenses: BigDecimal = BigDecimal.ZERO,
    val currentMonthTransfer: BigDecimal = BigDecimal.ZERO,
    val currentMonthInvestment: BigDecimal = BigDecimal.ZERO,
    val lastMonthTotal: BigDecimal = BigDecimal.ZERO,
    val lastMonthIncome: BigDecimal = BigDecimal.ZERO,
    val lastMonthExpenses: BigDecimal = BigDecimal.ZERO,
    val monthlyChange: BigDecimal = BigDecimal.ZERO,
    val monthlyChangePercent: Int = 0,
    val recentTransactions: List<TransactionEntity> = emptyList(), // kept for widget compat
    val recentItems: List<HomeRecentItem> = emptyList(),
    val activeSubscriptionCount: Int = 0,
    val spendingPeriodLabel: String = "",
    val useFinancialMonth: Boolean = true,
    val accountBalances: List<AccountBalanceEntity> = emptyList(),
    val creditCards: List<AccountBalanceEntity> = emptyList(),
    val totalBalance: BigDecimal = BigDecimal.ZERO,
    val totalAvailableCredit: BigDecimal = BigDecimal.ZERO,
    val selectedCurrency: String = "INR",
    val availableCurrencies: List<String> = emptyList(),
    val recentTransactionConvertedAmounts: Map<Long, BigDecimal> = emptyMap(),
    val spendingHistory: List<BigDecimal> = emptyList(),
    val balanceHistory: List<BigDecimal> = emptyList(),
    val isLoading: Boolean = true,
    val isScanning: Boolean = false,
    val showBreakdownDialog: Boolean = false,
    val isUnifiedMode: Boolean = false,
    val transactionHeatmap: Map<Long, Int> = emptyMap(),
    val isBalanceReady: Boolean = false,
    val lastMonthSpendingHistory: List<BigDecimal> = emptyList(),
    val payPeriodStartEpochDay: Long = -1L,
    val payPeriodEndEpochDay: Long = -1L,
    val loanSummary: LoanSummary? = null,
    val selectedProfileId: Long? = null,
    val profiles: List<ProfileEntity> = emptyList(),
    val payPeriodSuggestion: SalaryPayPeriodDetector.Suggestion? = null,
    val periodDayLabel: String = "",
    val incomeTodayLabel: String = "",
    val topCategoryName: String = "",
    val topCategorySubLabel: String = "",
    val dailyAverageLabel: String = "",
    val paceLabel: String = "",
)

data class LoanSummary(
    val activeLoans: List<LoanEntity>,
    val totalLentRemaining: BigDecimal,
    val totalBorrowedRemaining: BigDecimal
)