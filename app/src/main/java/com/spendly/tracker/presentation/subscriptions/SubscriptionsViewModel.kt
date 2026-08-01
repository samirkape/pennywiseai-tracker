package com.spendly.tracker.presentation.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendly.tracker.data.currency.CurrencyConversionService
import com.spendly.tracker.data.database.entity.PrepaidExpenseEntity
import com.spendly.tracker.data.database.entity.SubscriptionEntity
import com.spendly.tracker.data.database.entity.SubscriptionState
import com.spendly.tracker.data.preferences.UserPreferencesRepository
import com.spendly.tracker.data.repository.PrepaidExpenseRepository
import com.spendly.tracker.data.repository.SubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    private val prepaidExpenseRepository: PrepaidExpenseRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val currencyConversionService: CurrencyConversionService
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubscriptionsUiState())
    val uiState: StateFlow<SubscriptionsUiState> = _uiState.asStateFlow()

    init {
        loadSubscriptions()
    }

    private fun loadSubscriptions() {
        viewModelScope.launch {
            combine(
                subscriptionRepository.getActiveSubscriptions(),
                prepaidExpenseRepository.getActivePlans(),
                userPreferencesRepository.unifiedCurrencyMode,
                userPreferencesRepository.displayCurrency,
                userPreferencesRepository.baseCurrency
            ) { subscriptions, prepaidPlans, isUnified, displayCurrency, baseCurrency ->
                arrayOf(subscriptions, prepaidPlans, isUnified, displayCurrency, baseCurrency)
            }.collect { values ->
                @Suppress("UNCHECKED_CAST")
                val subscriptions = (values[0] as List<SubscriptionEntity>).deduplicated()
                @Suppress("UNCHECKED_CAST")
                val prepaidPlans = values[1] as List<PrepaidExpenseEntity>
                val isUnified = values[2] as Boolean
                val displayCurrency = values[3] as String
                val baseCurrency = values[4] as String
                val totalMonthlyAmount = if (isUnified) {
                    var total = BigDecimal.ZERO
                    for (sub in subscriptions) {
                        total += currencyConversionService.convertAmount(
                            sub.amount, sub.currency, displayCurrency
                        )
                    }
                    total
                } else {
                    subscriptions.sumOf { it.amount }
                }

                val convertedAmounts = if (isUnified) {
                    val map = mutableMapOf<Long, BigDecimal>()
                    for (sub in subscriptions) {
                        if (!sub.currency.equals(displayCurrency, ignoreCase = true)) {
                            map[sub.id] = currencyConversionService.convertAmount(
                                sub.amount, sub.currency, displayCurrency
                            )
                        }
                    }
                    map
                } else {
                    emptyMap()
                }

                // Monthly-equivalent cost of active prepaid plans — for planning visibility
                // only, not tied to actual expense reporting (the source payment already
                // counts in full on its real day everywhere else).
                var prepaidMonthlyAmount = BigDecimal.ZERO
                for (plan in prepaidPlans) {
                    val monthlyEquivalent = plan.totalAmount.divide(BigDecimal(plan.totalMonths), 2, RoundingMode.HALF_UP)
                    prepaidMonthlyAmount += if (isUnified) {
                        currencyConversionService.convertAmount(monthlyEquivalent, plan.currency, displayCurrency)
                    } else {
                        monthlyEquivalent
                    }
                }

                _uiState.value = _uiState.value.copy(
                    activeSubscriptions = subscriptions,
                    activePrepaidPlans = prepaidPlans,
                    totalMonthlyAmount = totalMonthlyAmount,
                    prepaidMonthlyAmount = prepaidMonthlyAmount,
                    activePrepaidPlanCount = prepaidPlans.size,
                    convertedAmounts = convertedAmounts,
                    displayCurrency = if (isUnified) displayCurrency else baseCurrency,
                    isUnifiedMode = isUnified,
                    isLoading = false
                )
            }
        }
    }
    
    fun hideSubscription(subscriptionId: Long) {
        viewModelScope.launch {
            subscriptionRepository.hideSubscription(subscriptionId)
            _uiState.value = _uiState.value.copy(
                lastHiddenSubscription = _uiState.value.activeSubscriptions.find { it.id == subscriptionId }
            )
        }
    }
    
    fun undoHide() {
        _uiState.value.lastHiddenSubscription?.let { subscription ->
            viewModelScope.launch {
                subscriptionRepository.unhideSubscription(subscription.id)
                _uiState.value = _uiState.value.copy(lastHiddenSubscription = null)
            }
        }
    }

    fun deleteSubscriptionPermanently(subscriptionId: Long) {
        viewModelScope.launch {
            subscriptionRepository.deleteSubscription(subscriptionId)
        }
    }

    fun saveSubscriptionEdits(subscription: SubscriptionEntity) {
        viewModelScope.launch {
            subscriptionRepository.updateSubscription(
                subscription.copy(updatedAt = java.time.LocalDateTime.now())
            )
        }
    }

    private fun List<SubscriptionEntity>.deduplicated(): List<SubscriptionEntity> {
        val result = mutableListOf<SubscriptionEntity>()
        for (sub in this) {
            val existingIndex = result.indexOfFirst { existing ->
                existing.amount == sub.amount &&
                existing.currency.equals(sub.currency, ignoreCase = true) &&
                merchantNamesOverlap(existing.merchantName, sub.merchantName)
            }
            if (existingIndex == -1) {
                result.add(sub)
            } else if (isBetterCandidate(sub, result[existingIndex])) {
                result[existingIndex] = sub
            }
        }
        return result
    }

    private fun merchantNamesOverlap(a: String, b: String): Boolean {
        val normA = a.trim().lowercase()
        val normB = b.trim().lowercase()
        return normA == normB || normA.contains(normB) || normB.contains(normA)
    }

    private fun isBetterCandidate(candidate: SubscriptionEntity, current: SubscriptionEntity): Boolean {
        if (candidate.umn != null && current.umn == null) return true
        if (candidate.bankName != "Manual Entry" && current.bankName == "Manual Entry") return true
        return false
    }
}

data class SubscriptionsUiState(
    val activeSubscriptions: List<SubscriptionEntity> = emptyList(),
    val activePrepaidPlans: List<PrepaidExpenseEntity> = emptyList(),
    val totalMonthlyAmount: BigDecimal = BigDecimal.ZERO,
    val prepaidMonthlyAmount: BigDecimal = BigDecimal.ZERO,
    val activePrepaidPlanCount: Int = 0,
    val convertedAmounts: Map<Long, BigDecimal> = emptyMap(),
    val displayCurrency: String? = null,
    val isUnifiedMode: Boolean = false,
    val isLoading: Boolean = true,
    val lastHiddenSubscription: SubscriptionEntity? = null
) {
    val combinedMonthlyAmount: BigDecimal get() = totalMonthlyAmount + prepaidMonthlyAmount
    val combinedYearlyAmount: BigDecimal get() = combinedMonthlyAmount.multiply(BigDecimal(12))
}