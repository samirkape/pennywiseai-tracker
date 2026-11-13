package com.spendly.tracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendly.tracker.data.preferences.UserPreferencesRepository
import com.spendly.tracker.data.repository.TransactionRepository
import com.spendly.tracker.ui.components.DailyReviewPrompt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Prompt shown once per evening when the user opens the app and today's
 * transactions still need a category.
 */
@HiltViewModel
class DailyReviewViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    companion object {
        /** End-of-day window starts at 8 PM local time. */
        const val REVIEW_START_HOUR = 20
    }

    private val _prompt = MutableStateFlow<DailyReviewPrompt?>(null)
    val prompt: StateFlow<DailyReviewPrompt?> = _prompt.asStateFlow()

    private var isChecking = false

    fun checkForPrompt() {
        val now = LocalDateTime.now()
        if (now.hour < REVIEW_START_HOUR) return
        if (isChecking || _prompt.value != null) return

        isChecking = true
        viewModelScope.launch {
            try {
                val today = now.toLocalDate()
                val todayKey = today.toString()
                if (userPreferencesRepository.getLastDailyReviewPromptDate() == todayKey) {
                    return@launch
                }

                val todayCount = transactionRepository.getUncategorizedTransactions(
                    from = today,
                    untilExclusive = today.plusDays(1),
                ).size
                if (todayCount == 0) return@launch

                val totalCount = transactionRepository.getUncategorizedTransactionCount()
                val olderCount = (totalCount - todayCount).coerceAtLeast(0)

                _prompt.value = DailyReviewPrompt(
                    dateKey = todayKey,
                    todayCount = todayCount,
                    olderCount = olderCount,
                )
            } finally {
                isChecking = false
            }
        }
    }

    fun dismiss() {
        val dateKey = _prompt.value?.dateKey
        _prompt.value = null
        if (dateKey != null) {
            viewModelScope.launch {
                userPreferencesRepository.setLastDailyReviewPromptDate(dateKey)
            }
        }
    }
}
