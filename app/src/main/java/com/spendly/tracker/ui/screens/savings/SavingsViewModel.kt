package com.spendly.tracker.ui.screens.savings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendly.tracker.core.Constants
import com.spendly.tracker.data.currency.CurrencyConversionService
import com.spendly.tracker.data.preferences.SavingsSnapshotStore
import com.spendly.tracker.data.preferences.UserPreferencesRepository
import com.spendly.tracker.data.repository.SalaryMonthOverrideRepository
import com.spendly.tracker.data.repository.TransactionRepository
import com.spendly.tracker.domain.service.SalaryPayPeriodDetector
import com.spendly.tracker.presentation.common.getDateRangeForYearMonthNavigation
import com.spendly.tracker.ui.screens.analytics.SAVINGS_PROJECTION_DEFAULT_HORIZON
import com.spendly.tracker.ui.screens.analytics.SAVINGS_RATE_DEFAULT_WINDOW
import com.spendly.tracker.ui.screens.analytics.SavingsCustomRange
import com.spendly.tracker.ui.screens.analytics.SavingsProjection
import com.spendly.tracker.ui.screens.analytics.SavingsRateSummary
import com.spendly.tracker.ui.screens.analytics.computeMonthlySavingsRates
import com.spendly.tracker.ui.screens.analytics.computeSavingsProjection
import com.spendly.tracker.utils.DateRangeUtils
import com.spendly.tracker.widget.SavingsWidgetUpdateWorker
import com.spendly.tracker.worker.SavingsGoalReminderWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SavingsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val currencyConversionService: CurrencyConversionService,
    private val salaryMonthOverrideRepository: SalaryMonthOverrideRepository,
    private val savingsSnapshotStore: SavingsSnapshotStore,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _currency = MutableStateFlow("INR")
    val currency: StateFlow<String> = _currency.asStateFlow()

    private val _isUnifiedMode = MutableStateFlow(false)
    val isUnifiedMode: StateFlow<Boolean> = _isUnifiedMode.asStateFlow()

    val useFinancialMonth: StateFlow<Boolean> = userPreferencesRepository.useFinancialMonth
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val navMonthStartDay: StateFlow<Int> = userPreferencesRepository.monthStartDay
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1)

    private val navUseFixedBudgetPeriodEnd: StateFlow<Boolean> = userPreferencesRepository.useFixedBudgetPeriodEnd
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val navBudgetPeriodEndDay: StateFlow<Int> = userPreferencesRepository.budgetPeriodEndDay
        .stateIn(viewModelScope, SharingStarted.Eagerly, 31)

    private val navOverridesMap: StateFlow<Map<String, Int>> = salaryMonthOverrideRepository.overridesMap
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val navSalaryDayAdjustment: StateFlow<DateRangeUtils.SalaryDayAdjustment> =
        userPreferencesRepository.salaryDayAdjustment
            .stateIn(viewModelScope, SharingStarted.Eagerly, DateRangeUtils.SalaryDayAdjustment.EXACT)

    private val _savingsWindowMonths = MutableStateFlow(SAVINGS_RATE_DEFAULT_WINDOW)
    val savingsWindowMonths: StateFlow<Int> = _savingsWindowMonths.asStateFlow()

    /** Last month count chosen with the Custom slider, kept even while a preset is active. */
    val savingsCustomMonths: StateFlow<Int> = userPreferencesRepository.getSavingsRateCustomMonths()
        .map { it ?: SAVINGS_RATE_DEFAULT_WINDOW }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SAVINGS_RATE_DEFAULT_WINDOW)

    /** How many months ahead the projection looks. Independent of the history window. */
    val savingsProjectionHorizon: StateFlow<Int> = userPreferencesRepository.getSavingsProjectionHorizon()
        .map { it ?: SAVINGS_PROJECTION_DEFAULT_HORIZON }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SAVINGS_PROJECTION_DEFAULT_HORIZON)

    private val _savingsCustomRange = MutableStateFlow<SavingsCustomRange?>(null)
    val savingsCustomRange: StateFlow<SavingsCustomRange?> = _savingsCustomRange.asStateFlow()

    /** Target share of income to keep, as a whole percent. */
    private val _goalPercent = MutableStateFlow(DEFAULT_SAVINGS_GOAL_PERCENT)
    val goalPercent: StateFlow<Int> = _goalPercent.asStateFlow()

    /** Last month of the goal period; defaults to December of the current year. */
    private val _goalEndMonth = MutableStateFlow(defaultSavingsGoalEndMonth())
    val goalEndMonth: StateFlow<YearMonth> = _goalEndMonth.asStateFlow()

    /** Whether the active goal tracks a share of income or a fixed amount. */
    private val _goalKind = MutableStateFlow(SavingsGoalKind.PERCENT)

    /** Fixed amount for [SavingsGoalKind.FIXED] goals. */
    private val _goalTargetAmount = MutableStateFlow(BigDecimal.ZERO)

    /** All goals plus the active one, mirrored from preferences. */
    private val _savingsGoals = MutableStateFlow(
        normalizeSavingsGoals(emptyList(), activeGoalId = null),
    )
    val savingsGoals: StateFlow<SavingsGoals> = _savingsGoals.asStateFlow()

    /** True once the persisted goals have been read, so deep links can target the real active goal. */
    private val _savingsGoalsLoaded = MutableStateFlow(false)
    val savingsGoalsLoaded: StateFlow<Boolean> = _savingsGoalsLoaded.asStateFlow()

    /** Milestone to celebrate in-app; null when there is nothing to show. */
    private val _celebrationMilestone = MutableStateFlow<Int?>(null)
    val celebrationMilestone: StateFlow<Int?> = _celebrationMilestone.asStateFlow()

    private var lastCelebratedMilestone = 0

    /** Whether the savings-goal reminder notification is enabled. */
    val savingsGoalReminderEnabled: StateFlow<Boolean> =
        userPreferencesRepository.getSavingsGoalReminderEnabled()
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Selected ISO days-of-week for the reminder (Monday = 1); never empty. */
    val savingsReminderDays: StateFlow<Set<Int>> =
        userPreferencesRepository.getSavingsGoalReminderDays()
            .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_SAVINGS_REMINDER_DAYS)

    /** Hour of day for the reminder (0–23). */
    val savingsReminderHour: StateFlow<Int> =
        userPreferencesRepository.getSavingsGoalReminderHour()
            .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_SAVINGS_REMINDER_HOUR)

    /** Persisted month-by-month history, newest first. */
    val savingsHistory: StateFlow<List<SavingsMonthSnapshot>> =
        savingsSnapshotStore.snapshots()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            userPreferencesRepository.baseCurrency.collect { _currency.value = it }
        }
        viewModelScope.launch {
            userPreferencesRepository.unifiedCurrencyMode.collect { _isUnifiedMode.value = it }
        }
        viewModelScope.launch {
            userPreferencesRepository.getSavingsRateWindowMonths().first()?.let { _savingsWindowMonths.value = it }
            userPreferencesRepository.getSavingsRateRange().first()?.let { (start, end) ->
                runCatching {
                    val range = SavingsCustomRange(YearMonth.parse(start), end?.let { YearMonth.parse(it) })
                    _savingsCustomRange.value = range
                    _savingsWindowMonths.value = range.windowMonths()
                }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.getSavingsGoals().collect { goals ->
                _savingsGoals.value = goals
                _savingsGoalsLoaded.value = true
                goals.activeGoal?.let { goal ->
                    _goalPercent.value = goal.normalizedPercent
                    _goalEndMonth.value = goal.endMonth
                    _goalKind.value = goal.kind
                    _goalTargetAmount.value = goal.normalizedAmount
                }
            }
        }
    }

    fun setGoalPercent(percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        _goalPercent.value = clamped
        val active = _savingsGoals.value.activeGoal ?: defaultSavingsGoal()
        saveSavingsGoal(active.copy(targetPercent = clamped))
    }

    fun setGoalEndMonth(month: YearMonth) {
        _goalEndMonth.value = month
        val active = _savingsGoals.value.activeGoal ?: defaultSavingsGoal()
        saveSavingsGoal(active.copy(endMonth = month))
    }

    /** Changes the fixed amount of the active goal. */
    fun setGoalTargetAmount(amount: BigDecimal) {
        val normalized = amount.coerceAtLeast(BigDecimal.ZERO)
        _goalTargetAmount.value = normalized
        val active = _savingsGoals.value.activeGoal ?: defaultSavingsGoal()
        saveSavingsGoal(active.copy(targetAmount = normalized))
    }

    /** Switches the goal driving the page, widget and reminders. */
    fun selectSavingsGoal(goalId: String) {
        applySavingsGoals(_savingsGoals.value.select(goalId))
    }

    /** Adds a goal or updates the matching one, then makes it active. */
    fun saveSavingsGoal(goal: SavingsGoalDefinition) {
        applySavingsGoals(_savingsGoals.value.upsert(goal))
    }

    /** Removes a goal; the last remaining goal cannot be deleted. */
    fun deleteSavingsGoal(goalId: String) {
        applySavingsGoals(_savingsGoals.value.remove(goalId))
    }

    /** Adds money set aside for [goalId] without changing the active goal. */
    fun addSavingsContribution(goalId: String, amount: BigDecimal) {
        val updated = _savingsGoals.value.withContribution(goalId, amount)
        if (updated == _savingsGoals.value) return
        applySavingsGoals(updated)
    }

    /** Earmarks [amount] toward the currently selected goal. */
    fun addContributionToActiveGoal(amount: BigDecimal) {
        _savingsGoals.value.activeGoal?.let { goal ->
            addSavingsContribution(goal.id, amount)
        }
    }

    /** Reverses a contribution, e.g. from the snackbar's Undo action. */
    fun removeSavingsContribution(goalId: String, amount: BigDecimal) {
        val updated = _savingsGoals.value.withoutContribution(goalId, amount)
        if (updated == _savingsGoals.value) return
        applySavingsGoals(updated)
    }

    /** Removes one logged contribution from the goal's history. */
    fun removeSavingsContributionEntry(goalId: String, atMillis: Long) {
        val updated = _savingsGoals.value.removeContribution(goalId, atMillis)
        if (updated == _savingsGoals.value) return
        applySavingsGoals(updated)
    }

    fun dismissCelebration() {
        _celebrationMilestone.value = null
    }

    /** Clears the persisted monthly history shown on the page. */
    fun clearSavingsHistory() {
        viewModelScope.launch { savingsSnapshotStore.clear() }
    }

    private fun applySavingsGoals(goals: SavingsGoals) {
        _savingsGoals.value = goals
        goals.activeGoal?.let { goal ->
            _goalPercent.value = goal.normalizedPercent
            _goalEndMonth.value = goal.endMonth
            _goalKind.value = goal.kind
            _goalTargetAmount.value = goal.normalizedAmount
        }
        viewModelScope.launch {
            userPreferencesRepository.saveSavingsGoals(goals)
            SavingsWidgetUpdateWorker.enqueueOneShot(appContext)
        }
    }

    /** Turns the reminder on or off and schedules/cancels the workers. */
    fun setSavingsGoalReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.saveSavingsGoalReminderEnabled(enabled)
            if (enabled) {
                // Re-enabling reminders is an explicit request to hear from us again.
                userPreferencesRepository.saveSavingsNotificationSnoozeUntil(0L)
                val schedule = SavingsReminderSchedule.from(
                    isoDays = userPreferencesRepository.getSavingsGoalReminderDays().first(),
                    hour = userPreferencesRepository.getSavingsGoalReminderHour().first(),
                )
                SavingsGoalReminderWorker.enqueuePeriodic(appContext, schedule)
            } else {
                SavingsGoalReminderWorker.cancelPeriodic(appContext)
            }
        }
    }

    /** Adds or removes one day; the last remaining day cannot be deselected. */
    fun toggleSavingsGoalReminderDay(dayOfWeek: Int) {
        val day = dayOfWeek.coerceIn(1, 7)
        viewModelScope.launch {
            val current = userPreferencesRepository.getSavingsGoalReminderDays().first()
            val updated = if (day in current) {
                if (current.size <= 1) return@launch else current - day
            } else {
                current + day
            }
            userPreferencesRepository.saveSavingsGoalReminderDays(updated)
            rescheduleReminderIfEnabled()
        }
    }

    fun setSavingsGoalReminderHour(hour: Int) {
        val normalized = hour.coerceIn(0, 23)
        viewModelScope.launch {
            userPreferencesRepository.saveSavingsGoalReminderHour(normalized)
            rescheduleReminderIfEnabled()
        }
    }

    private suspend fun rescheduleReminderIfEnabled() {
        if (!userPreferencesRepository.getSavingsGoalReminderEnabled().first()) return
        // Changing the schedule also lifts an earlier snooze so the new time takes effect.
        userPreferencesRepository.saveSavingsNotificationSnoozeUntil(0L)
        val schedule = SavingsReminderSchedule.from(
            isoDays = userPreferencesRepository.getSavingsGoalReminderDays().first(),
            hour = userPreferencesRepository.getSavingsGoalReminderHour().first(),
        )
        SavingsGoalReminderWorker.enqueuePeriodic(appContext, schedule)
    }

    fun setExtraMonthlySavings(amount: BigDecimal) {
        _extraMonthlySavings.value = amount.coerceAtLeast(BigDecimal.ZERO)
    }

    /** Moves a passed goal deadline forward by whole years until it is in the future. */
    fun rolloverGoalToNextYear() {
        setGoalEndMonth(rolloverSavingsGoalMonth(_goalEndMonth.value))
    }

    fun saveSavingsCustomMonths(months: Int) {
        viewModelScope.launch { userPreferencesRepository.saveSavingsRateCustomMonths(months) }
    }

    fun saveSavingsProjectionHorizon(months: Int) {
        viewModelScope.launch { userPreferencesRepository.saveSavingsProjectionHorizon(months) }
    }

    /** Sets a date-picked savings window (end null = Now). */
    fun applySavingsRange(startMonth: YearMonth, endMonth: YearMonth?) {
        val range = SavingsCustomRange(startMonth, endMonth)
        _savingsCustomRange.value = range
        viewModelScope.launch {
            userPreferencesRepository.saveSavingsRateRange(startMonth.toString(), endMonth?.toString())
            SavingsWidgetUpdateWorker.enqueueOneShot(appContext)
        }
        applySavingsWindowInternal(range.windowMonths())
    }

    /** Sets the savings window to the last [months] months. */
    fun applySavingsWindow(months: Int) {
        _savingsCustomRange.value = null
        viewModelScope.launch { userPreferencesRepository.saveSavingsRateRange(null, null) }
        applySavingsWindowInternal(months)
    }

    private fun applySavingsWindowInternal(months: Int) {
        _savingsWindowMonths.value = months
        viewModelScope.launch {
            userPreferencesRepository.saveSavingsRateWindowMonths(months)
            SavingsWidgetUpdateWorker.enqueueOneShot(appContext)
        }
    }

    private fun savingsMonthRanges(
        window: Int,
        usesCalendar: Boolean,
        nav: SavingsNav,
        endMonth: YearMonth = YearMonth.now(),
    ): List<Pair<YearMonth, Pair<LocalDate, LocalDate>>> =
        (window - 1 downTo 0).map { back ->
            val ym = endMonth.minusMonths(back.toLong())
            ym to getDateRangeForYearMonthNavigation(
                yearMonth = ym,
                useCalendarMonth = usesCalendar,
                monthStartDay = nav.monthStartDay,
                monthStartOverrides = nav.overrides,
                useFixedBudgetPeriodEnd = nav.fixedPeriodEnd,
                budgetPeriodEndDay = nav.periodEndDay,
                salaryDayAdjustment = nav.salaryDayAdjustment,
            )
        }

    /** Income vs. spending for the last N months, independent of the Analytics period. */
    val savingsRate: StateFlow<SavingsRateSummary> = combine(
        combine(_savingsWindowMonths, _savingsCustomRange) { window, range -> window to range },
        _currency,
        _isUnifiedMode,
        combine(
            navMonthStartDay,
            navOverridesMap,
            navUseFixedBudgetPeriodEnd,
            navBudgetPeriodEndDay,
            navSalaryDayAdjustment,
        ) { a, b, c, d, e -> SavingsNav(a, b, c, d, e) },
        useFinancialMonth,
    ) { windowAndRange, currency, unified, nav, financial ->
        SavingsInputs(windowAndRange.first, currency, unified, nav, financial, windowAndRange.second)
    }.flatMapLatest { input ->
        val ranges = savingsMonthRanges(
            input.window,
            !input.useFinancialMonth,
            input.nav,
            input.customRange?.resolvedEnd() ?: YearMonth.now(),
        )
        transactionRepository.getNetTransactionsBetweenDates(
            startDate = ranges.minOf { it.second.first },
            endDate = ranges.maxOf { it.second.second },
        ).mapLatest { all ->
            val txs = if (input.unified) all else all.filter { it.currency == input.currency }
            val months = computeMonthlySavingsRates(
                transactions = txs,
                ranges = ranges,
                currentMonth = YearMonth.now(),
                amountOf = { tx ->
                    if (input.unified) {
                        currencyConversionService.convertAmount(tx.amount, tx.currency, input.currency)
                    } else {
                        tx.amount
                    }
                },
            )
            SavingsRateSummary(
                months = months,
                windowMonths = input.window,
                customRange = input.customRange,
                currency = input.currency,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SavingsRateSummary(),
    )

    /** Forward-looking estimate derived from [savingsRate], refreshed on every transaction. */
    val savingsProjection: StateFlow<SavingsProjection?> = combine(
        savingsRate,
        savingsProjectionHorizon,
    ) { summary, horizon ->
        computeSavingsProjection(summary, horizon)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null,
    )

    /**
     * Income and spending from the savings-rate tile's window start up to today, in the
     * page currency, plus the average salary credit found in that window.
     */
    private val goalActuals: StateFlow<GoalActuals> = combine(
        _goalEndMonth,
        savingsRate,
        _currency,
        _isUnifiedMode,
    ) { endMonth, summary, currency, unified ->
        val periodStart = summary.months.firstOrNull()?.start ?: YearMonth.now().atDay(1)
        GoalActualsQuery(endMonth, periodStart, currency, unified)
    }
        .flatMapLatest { query ->
            val today = LocalDate.now()
            if (query.periodStart.isAfter(today)) {
                flowOf(GoalActuals(periodStart = query.periodStart))
            } else {
                val periodEnd = minOf(today, query.endMonth.atEndOfMonth())
                if (query.periodStart.isAfter(periodEnd)) {
                    flowOf(GoalActuals(periodStart = query.periodStart))
                } else {
                    transactionRepository.getNetTransactionsBetweenDates(query.periodStart, periodEnd)
                        .map { all ->
                            val txs = if (query.unified) {
                                all
                            } else {
                                all.filter { it.currency == query.currency }
                            }
                            val bucket = computeMonthlySavingsRates(
                                transactions = txs,
                                ranges = listOf(
                                    YearMonth.from(periodEnd) to (query.periodStart to periodEnd),
                                ),
                                currentMonth = YearMonth.now(),
                                amountOf = { tx ->
                                    if (query.unified) {
                                        currencyConversionService.convertAmount(
                                            tx.amount,
                                            tx.currency,
                                            query.currency,
                                        )
                                    } else {
                                        tx.amount
                                    }
                                },
                            ).first()

                            var salarySum = BigDecimal.ZERO
                            var salaryCount = 0
                            for (tx in txs) {
                                if (SalaryPayPeriodDetector.scoreCandidate(tx) <
                                    SalaryPayPeriodDetector.MIN_SCORE
                                ) {
                                    continue
                                }
                                salarySum += if (query.unified) {
                                    currencyConversionService.convertAmount(
                                        tx.amount,
                                        tx.currency,
                                        query.currency,
                                    )
                                } else {
                                    tx.amount
                                }
                                salaryCount++
                            }
                            val salaryAverage = if (salaryCount > 0) {
                                salarySum.divide(
                                    BigDecimal(salaryCount),
                                    2,
                                    java.math.RoundingMode.HALF_UP,
                                )
                            } else {
                                null
                            }

                            GoalActuals(
                                income = bucket.income,
                                spent = bucket.spent,
                                salaryMonthlyIncome = salaryAverage,
                                periodStart = query.periodStart,
                            )
                        }
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = GoalActuals(periodStart = YearMonth.now().atDay(1)),
        )

    /** Monthly save/spend plan for the active goal, refreshed with every transaction. */
    val goalPlan: StateFlow<SavingsGoalPlan> = combine(
        combine(
            _goalPercent,
            _goalEndMonth,
            _goalKind,
            _goalTargetAmount,
            _savingsGoals,
        ) { percent, endMonth, kind, amount, goals ->
            GoalSettings(percent, endMonth, kind, amount, goals)
        },
        goalActuals,
        savingsRate,
        savingsProjection,
        _currency,
    ) { settings, actuals, summary, projection, currency ->
        fun planFor(
            kind: SavingsGoalKind,
            percent: Int,
            amount: BigDecimal,
            contribution: BigDecimal,
            endMonth: YearMonth,
            otherMonthlySavings: BigDecimal,
        ): SavingsGoalPlan = computeSavingsGoalPlan(
            targetPercent = percent,
            periodStart = actuals.periodStart,
            endMonth = endMonth,
            actualIncome = actuals.income,
            actualSpent = actuals.spent,
            salaryMonthlyIncome = actuals.salaryMonthlyIncome,
            summary = summary,
            projection = projection,
            currency = currency,
            goalKind = kind,
            targetAmount = amount,
            contributedAmount = contribution,
            otherMonthlySavings = otherMonthlySavings,
        )

        val active = settings.goals.activeGoal
        // Extra goals are funded from what is left after the main goal. The main goal
        // itself stays untouched, so its card never subtracts the extras.
        val isMainGoal = active?.id == DEFAULT_SAVINGS_GOAL_ID
        val otherMonthlySavings = if (isMainGoal) {
            BigDecimal.ZERO
        } else {
            settings.goals.goals
                .filter { it.id != active?.id }
                .fold(BigDecimal.ZERO) { total, goal ->
                    total + planFor(
                        kind = goal.kind,
                        percent = goal.normalizedPercent,
                        amount = goal.normalizedAmount,
                        contribution = goal.normalizedContribution,
                        endMonth = goal.endMonth,
                        otherMonthlySavings = BigDecimal.ZERO,
                    ).monthlySavingsNeeded
                }
        }

        planFor(
            kind = settings.kind,
            percent = settings.percent,
            amount = settings.targetAmount,
            contribution = active?.normalizedContribution ?: BigDecimal.ZERO,
            endMonth = settings.endMonth,
            otherMonthlySavings = otherMonthlySavings,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = computeSavingsGoalPlan(
            targetPercent = DEFAULT_SAVINGS_GOAL_PERCENT,
            periodStart = YearMonth.now().atDay(1),
            endMonth = defaultSavingsGoalEndMonth(),
            actualIncome = BigDecimal.ZERO,
            actualSpent = BigDecimal.ZERO,
            salaryMonthlyIncome = null,
            summary = SavingsRateSummary(),
            projection = null,
            currency = "INR",
        ),
    )

    /** Extra monthly amount the user is exploring in the what-if simulator. */
    private val _extraMonthlySavings = MutableStateFlow(BigDecimal.ZERO)
    val extraMonthlySavings: StateFlow<BigDecimal> = _extraMonthlySavings.asStateFlow()

    /** Actual-then-projected savings-rate line through the goal deadline. */
    val goalForecast: StateFlow<SavingsGoalForecast> = combine(goalPlan, savingsRate) { plan, summary ->
        computeSavingsGoalForecast(plan, summary)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SavingsGoalForecast(),
    )

    /** Result of adding [extraMonthlySavings] on top of the current plan. */
    val goalSimulation: StateFlow<SavingsGoalSimulation> = combine(goalPlan, _extraMonthlySavings) { plan, extra ->
        simulateExtraSavings(plan, extra)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SavingsGoalSimulation(),
    )

    init {
        viewModelScope.launch {
            // Mirror the monthly buckets into durable snapshots so history survives after
            // those months leave the savings-rate window.
            combine(savingsRate, goalPlan) { summary, plan -> summary to plan }
                .collect { (summary, plan) ->
                    if (!Constants.Features.SAVINGS_GOAL_ENHANCEMENTS_ENABLED) return@collect
                    savingsSnapshotStore.record(
                        buildSavingsHistory(
                            months = summary.months,
                            targetPercent = plan.targetPercent,
                            capturedAtMillis = System.currentTimeMillis(),
                        )
                    )
                }
        }
        viewModelScope.launch {
            if (!Constants.Features.SAVINGS_GOAL_ENHANCEMENTS_ENABLED) return@launch
            lastCelebratedMilestone = userPreferencesRepository
                .getSavingsGoalLastCelebrated()
                .first()
            goalPlan.collect { plan ->
                val progress = plan.goalProgressPercent ?: return@collect
                if (progress <= 0f) return@collect
                val milestone = MILESTONES.firstOrNull {
                    progress >= it && it > lastCelebratedMilestone
                } ?: return@collect
                lastCelebratedMilestone = milestone
                _celebrationMilestone.value = milestone
                userPreferencesRepository.saveSavingsGoalLastCelebrated(milestone)
            }
        }
    }
}

private val MILESTONES = listOf(100, 75, 50, 25)

private data class GoalSettings(
    val percent: Int,
    val endMonth: YearMonth,
    val kind: SavingsGoalKind,
    val targetAmount: BigDecimal,
    val goals: SavingsGoals,
)

private data class GoalActuals(
    val income: BigDecimal = BigDecimal.ZERO,
    val spent: BigDecimal = BigDecimal.ZERO,
    val salaryMonthlyIncome: BigDecimal? = null,
    val periodStart: LocalDate,
)

private data class GoalActualsQuery(
    val endMonth: YearMonth,
    val periodStart: LocalDate,
    val currency: String,
    val unified: Boolean,
)

private data class SavingsNav(
    val monthStartDay: Int,
    val overrides: Map<String, Int>,
    val fixedPeriodEnd: Boolean,
    val periodEndDay: Int,
    val salaryDayAdjustment: DateRangeUtils.SalaryDayAdjustment =
        DateRangeUtils.SalaryDayAdjustment.EXACT,
)

private data class SavingsInputs(
    val window: Int,
    val currency: String,
    val unified: Boolean,
    val nav: SavingsNav,
    val useFinancialMonth: Boolean,
    val customRange: SavingsCustomRange? = null,
)
