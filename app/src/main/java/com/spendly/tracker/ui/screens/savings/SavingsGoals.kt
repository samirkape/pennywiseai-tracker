package com.spendly.tracker.ui.screens.savings

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/** Whether a goal tracks a share of income or a fixed amount of money. */
enum class SavingsGoalKind {
    PERCENT,
    FIXED,
}

/** A single amount set aside for a goal, kept so the user can review or remove it. */
data class SavingsContribution(
    val atMillis: Long,
    val amount: BigDecimal,
)

/** A single savings goal the user can switch between. */
data class SavingsGoalDefinition(
    val id: String,
    val name: String,
    val kind: SavingsGoalKind = SavingsGoalKind.PERCENT,
    val targetPercent: Int = DEFAULT_SAVINGS_GOAL_PERCENT,
    val targetAmount: BigDecimal = BigDecimal.ZERO,
    val endMonth: YearMonth = defaultSavingsGoalEndMonth(),
    /** Money explicitly set aside for this goal; used by fixed-amount goals. */
    val contributedAmount: BigDecimal = BigDecimal.ZERO,
    /** Newest last; older goals may have a total without per-entry history. */
    val contributions: List<SavingsContribution> = emptyList(),
) {
    val normalizedPercent: Int get() = targetPercent.coerceIn(0, 100)

    val normalizedAmount: BigDecimal
        get() = targetAmount.coerceAtLeast(BigDecimal.ZERO)

    val normalizedContribution: BigDecimal
        get() = contributedAmount.coerceAtLeast(BigDecimal.ZERO)

    /** True when the goal has enough information to be planned. */
    val isConfigured: Boolean
        get() = kind == SavingsGoalKind.PERCENT || normalizedAmount.signum() > 0
}

/** All goals plus which one is currently driving the page, widget and reminders. */
data class SavingsGoals(
    val goals: List<SavingsGoalDefinition> = emptyList(),
    val activeGoalId: String? = null,
) {
    val activeGoal: SavingsGoalDefinition?
        get() = goals.firstOrNull { it.id == activeGoalId } ?: goals.firstOrNull()

    val hasMultiple: Boolean get() = goals.size > 1

    /** Replaces or appends [goal], making it active. */
    fun upsert(goal: SavingsGoalDefinition): SavingsGoals {
        val normalized = goal.normalized()
        val updated = goals.filterNot { it.id == normalized.id } + normalized
        return copy(goals = updated, activeGoalId = normalized.id)
    }

    fun remove(goalId: String): SavingsGoals {
        if (goals.size <= 1) return this
        val updated = goals.filterNot { it.id == goalId }
        val nextActive = activeGoalId.takeIf { it != goalId && updated.any { goal -> goal.id == it } }
            ?: updated.firstOrNull()?.id
        return copy(goals = updated, activeGoalId = nextActive)
    }

    fun select(goalId: String): SavingsGoals =
        if (goals.any { it.id == goalId }) copy(activeGoalId = goalId) else this

    /** Adds an earmarked contribution to [goalId] without changing the active goal. */
    fun withContribution(
        goalId: String,
        amount: BigDecimal,
        nowMillis: Long = System.currentTimeMillis(),
    ): SavingsGoals {
        if (amount.signum() <= 0) return this
        if (goals.none { it.id == goalId }) return this
        val updated = goals.map { goal ->
            if (goal.id == goalId) {
                goal.copy(
                    contributedAmount = goal.normalizedContribution + amount,
                    contributions = goal.contributions + SavingsContribution(nowMillis, amount),
                )
            } else {
                goal
            }
        }
        return copy(goals = updated)
    }

    /** Removes up to [amount] previously earmarked for [goalId]; never goes below zero. */
    fun withoutContribution(goalId: String, amount: BigDecimal): SavingsGoals {
        if (amount.signum() <= 0) return this
        if (goals.none { it.id == goalId }) return this
        val updated = goals.map { goal ->
            if (goal.id == goalId) {
                // Undo targets the newest matching entry; legacy totals have no entries.
                val index = goal.contributions.indexOfLast {
                    it.amount.compareTo(amount) == 0
                }
                val remaining = if (index >= 0) {
                    goal.contributions.filterIndexed { i, _ -> i != index }
                } else {
                    goal.contributions
                }
                goal.copy(
                    contributedAmount = (goal.normalizedContribution - amount)
                        .coerceAtLeast(BigDecimal.ZERO),
                    contributions = remaining,
                )
            } else {
                goal
            }
        }
        return copy(goals = updated)
    }

    /** Removes one logged contribution by its timestamp, adjusting the saved total. */
    fun removeContribution(goalId: String, atMillis: Long): SavingsGoals {
        if (goals.none { it.id == goalId }) return this
        val updated = goals.map { goal ->
            if (goal.id == goalId) {
                val entry = goal.contributions.firstOrNull { it.atMillis == atMillis }
                    ?: return@map goal
                goal.copy(
                    contributedAmount = (goal.normalizedContribution - entry.amount)
                        .coerceAtLeast(BigDecimal.ZERO),
                    contributions = goal.contributions.filterNot { it.atMillis == atMillis },
                )
            } else {
                goal
            }
        }
        return copy(goals = updated)
    }
}

/** Stable id used by the goal that predates the multi-goal picker. */
const val DEFAULT_SAVINGS_GOAL_ID = "default"

/** Default goal used when nothing has been saved yet. */
fun defaultSavingsGoal(
    today: LocalDate = LocalDate.now(),
    percent: Int = DEFAULT_SAVINGS_GOAL_PERCENT,
    endMonth: YearMonth = defaultSavingsGoalEndMonth(today),
): SavingsGoalDefinition = SavingsGoalDefinition(
    id = DEFAULT_SAVINGS_GOAL_ID,
    name = "",
    kind = SavingsGoalKind.PERCENT,
    targetPercent = percent.coerceIn(0, 100),
    targetAmount = BigDecimal.ZERO,
    endMonth = endMonth,
)

/** Normalizes persisted goals so the UI always has at least one usable goal. */
fun normalizeSavingsGoals(
    goals: List<SavingsGoalDefinition>,
    activeGoalId: String?,
    fallback: SavingsGoalDefinition = defaultSavingsGoal(),
): SavingsGoals {
    val normalized = goals
        .distinctBy { it.id }
        .filter { it.id.isNotBlank() }
        .map { it.normalized() }
        .ifEmpty { listOf(fallback.normalized()) }
    val active = activeGoalId?.takeIf { id -> normalized.any { it.id == id } }
        ?: normalized.first().id
    return SavingsGoals(goals = normalized, activeGoalId = active)
}

fun SavingsGoalDefinition.normalized(): SavingsGoalDefinition = copy(
    id = id.ifBlank { UUID.randomUUID().toString() },
    name = name.take(MAX_GOAL_NAME_LENGTH),
    targetPercent = targetPercent.coerceIn(0, 100),
    targetAmount = targetAmount.coerceAtLeast(BigDecimal.ZERO),
    contributedAmount = contributedAmount.coerceAtLeast(BigDecimal.ZERO),
)

/** New goal id; UUID keeps goals distinct across imports and installs. */
fun newSavingsGoalId(): String = UUID.randomUUID().toString()

private const val MAX_GOAL_NAME_LENGTH = 40
private const val RECORD_SEPARATOR = '\u001E'
private const val FIELD_SEPARATOR = '\u001F'

/**
 * Compact persistence format for the goal list. Control characters never appear in a
 * goal name, so splitting stays unambiguous without pulling in a JSON parser in tests.
 */
fun encodeSavingsGoals(goals: List<SavingsGoalDefinition>): String =
    goals.joinToString(RECORD_SEPARATOR.toString()) { goal ->
        listOf(
            goal.id,
            goal.name.replace(RECORD_SEPARATOR, ' ').replace(FIELD_SEPARATOR, ' '),
            goal.kind.name,
            goal.targetPercent.toString(),
            goal.targetAmount.toPlainString(),
            goal.endMonth.toString(),
            goal.normalizedContribution.toPlainString(),
            goal.contributions.joinToString(";") {
                "${it.atMillis}:${it.amount.toPlainString()}"
            },
        ).joinToString(FIELD_SEPARATOR.toString())
    }

fun decodeSavingsGoals(raw: String?): List<SavingsGoalDefinition> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw.split(RECORD_SEPARATOR)
        .mapNotNull { record ->
            val parts = record.split(FIELD_SEPARATOR)
            // Records gain fields over time; accept every older shape.
            if (parts.size !in LEGACY_FIELD_COUNT..FIELD_COUNT) return@mapNotNull null
            runCatching {
                SavingsGoalDefinition(
                    id = parts[0],
                    name = parts[1],
                    kind = runCatching { SavingsGoalKind.valueOf(parts[2]) }
                        .getOrDefault(SavingsGoalKind.PERCENT),
                    targetPercent = parts[3].toIntOrNull() ?: DEFAULT_SAVINGS_GOAL_PERCENT,
                    targetAmount = parts[4].toBigDecimalOrNull() ?: BigDecimal.ZERO,
                    endMonth = YearMonth.parse(parts[5]),
                    contributedAmount = parts.getOrNull(6)?.toBigDecimalOrNull()
                        ?: BigDecimal.ZERO,
                    contributions = decodeContributions(parts.getOrNull(7)),
                ).normalized()
            }.getOrNull()
        }
        .distinctBy { it.id }
}

private const val FIELD_COUNT = 8
private const val LEGACY_FIELD_COUNT = 6

/** Parses `timestamp:amount;timestamp:amount`, skipping malformed entries. */
private fun decodeContributions(raw: String?): List<SavingsContribution> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw.split(';').mapNotNull { entry ->
        val pieces = entry.split(':', limit = 2)
        val atMillis = pieces.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
        val amount = pieces.getOrNull(1)?.toBigDecimalOrNull() ?: return@mapNotNull null
        if (amount.signum() <= 0) null else SavingsContribution(atMillis, amount)
    }
}
