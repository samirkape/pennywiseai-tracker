package com.spendly.tracker.ui.screens.savings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

class SavingsGoalsTest {

    private fun goal(
        id: String = "goal-1",
        name: String = "Emergency",
        kind: SavingsGoalKind = SavingsGoalKind.PERCENT,
        percent: Int = 60,
        amount: Int = 0,
        endMonth: YearMonth = YearMonth.of(2026, 12),
        contributed: Int = 0,
        contributions: List<SavingsContribution> = emptyList(),
    ) = SavingsGoalDefinition(
        id = id,
        name = name,
        kind = kind,
        targetPercent = percent,
        targetAmount = BigDecimal(amount),
        endMonth = endMonth,
        contributedAmount = BigDecimal(contributed),
        contributions = contributions,
    )

    @Test
    fun encodeDecode_roundTripsAllFields() {
        val goals = listOf(
            goal(),
            goal(
                id = "goal-2",
                name = "Laptop",
                kind = SavingsGoalKind.FIXED,
                amount = 120_000,
                endMonth = YearMonth.of(2027, 3),
                contributed = 25_000,
                contributions = listOf(
                    SavingsContribution(1_700_000_000_000L, BigDecimal("25000")),
                ),
            ),
        )

        val decoded = decodeSavingsGoals(encodeSavingsGoals(goals))

        assertEquals(2, decoded.size)
        assertEquals(SavingsGoalKind.FIXED, decoded[1].kind)
        assertEquals(BigDecimal("120000"), decoded[1].targetAmount)
        assertEquals(YearMonth.of(2027, 3), decoded[1].endMonth)
        assertEquals(BigDecimal("25000"), decoded[1].contributedAmount)
        assertEquals(1, decoded[1].contributions.size)
        assertEquals(1_700_000_000_000L, decoded[1].contributions.first().atMillis)
        assertEquals(BigDecimal("25000"), decoded[1].contributions.first().amount)
    }

    @Test
    fun decode_legacyRecordWithoutContribution_defaultsToZero() {
        val legacy = listOf(
            "goal-2",
            "Laptop",
            "FIXED",
            "70",
            "120000",
            "2027-03",
        ).joinToString("\u001F")

        val decoded = decodeSavingsGoals(legacy)

        assertEquals(1, decoded.size)
        assertEquals(BigDecimal.ZERO, decoded[0].contributedAmount)
    }

    @Test
    fun decode_invalidOrEmpty_returnsEmptyList() {
        assertTrue(decodeSavingsGoals(null).isEmpty())
        assertTrue(decodeSavingsGoals("").isEmpty())
        assertTrue(decodeSavingsGoals("not-a-goal").isEmpty())
    }

    @Test
    fun normalize_addsDefaultGoalAndFixesActiveId() {
        val normalized = normalizeSavingsGoals(emptyList(), activeGoalId = "missing")

        assertEquals(1, normalized.goals.size)
        assertNotNull(normalized.activeGoal)
        assertEquals(normalized.goals.first().id, normalized.activeGoalId)
    }

    @Test
    fun upsert_addsAndActivatesGoal() {
        val state = SavingsGoals(goals = listOf(goal()), activeGoalId = "goal-1")
            .upsert(goal(id = "goal-2", name = "Travel"))

        assertEquals(2, state.goals.size)
        assertEquals("goal-2", state.activeGoalId)
        assertTrue(state.hasMultiple)
    }

    @Test
    fun remove_keepsTheLastGoal() {
        val single = SavingsGoals(goals = listOf(goal()), activeGoalId = "goal-1")
        assertEquals(1, single.remove("goal-1").goals.size)

        val multiple = SavingsGoals(
            goals = listOf(goal(), goal(id = "goal-2")),
            activeGoalId = "goal-1",
        ).remove("goal-1")

        assertEquals(1, multiple.goals.size)
        assertEquals("goal-2", multiple.activeGoalId)
    }

    @Test
    fun fixedPlan_usesEarmarkedContributionForProgress() {
        val plan = computeSavingsGoalPlan(
            targetPercent = 70,
            periodStart = LocalDate.of(2026, 1, 1),
            endMonth = YearMonth.of(2026, 12),
            actualIncome = BigDecimal(10_000),
            actualSpent = BigDecimal(7_000),
            salaryMonthlyIncome = BigDecimal(10_000),
            summary = com.spendly.tracker.ui.screens.analytics.SavingsRateSummary(),
            projection = null,
            currency = "INR",
            today = LocalDate.of(2026, 6, 1),
            goalKind = SavingsGoalKind.FIXED,
            targetAmount = BigDecimal(60_000),
            contributedAmount = BigDecimal(3_000),
        )

        assertTrue(plan.isFixedAmount)
        assertEquals(BigDecimal("60000"), plan.requiredTotalSavings)
        assertEquals(BigDecimal("3000"), plan.actualSaved)
        assertEquals(BigDecimal("57000"), plan.remainingToSave)
        assertEquals(5f, plan.goalProgressPercent ?: 0f, 0.01f)
    }

    @Test
    fun fixedPlan_ignoresGlobalSavingsWithoutContribution() {
        val plan = computeSavingsGoalPlan(
            targetPercent = 70,
            periodStart = LocalDate.of(2026, 1, 1),
            endMonth = YearMonth.of(2026, 12),
            actualIncome = BigDecimal(300_000),
            actualSpent = BigDecimal(10_000),
            salaryMonthlyIncome = BigDecimal(10_000),
            summary = com.spendly.tracker.ui.screens.analytics.SavingsRateSummary(),
            projection = null,
            currency = "INR",
            today = LocalDate.of(2026, 6, 1),
            goalKind = SavingsGoalKind.FIXED,
            targetAmount = BigDecimal(60_000),
            contributedAmount = BigDecimal.ZERO,
        )

        assertEquals(BigDecimal.ZERO, plan.actualSaved)
        assertEquals(BigDecimal("60000"), plan.remainingToSave)
        assertEquals(0f, plan.goalProgressPercent ?: 0f, 0.01f)
    }

    @Test
    fun fixedPlan_spendableComesAfterOtherGoalsMonthlySavings() {
        val plan = computeSavingsGoalPlan(
            targetPercent = 70,
            periodStart = LocalDate.of(2026, 1, 1),
            endMonth = YearMonth.of(2026, 12),
            actualIncome = BigDecimal.ZERO,
            actualSpent = BigDecimal.ZERO,
            salaryMonthlyIncome = BigDecimal(10_000),
            summary = com.spendly.tracker.ui.screens.analytics.SavingsRateSummary(),
            projection = null,
            currency = "INR",
            today = LocalDate.of(2026, 7, 1),
            goalKind = SavingsGoalKind.FIXED,
            targetAmount = BigDecimal(12_000),
            otherMonthlySavings = BigDecimal(3_000),
        )

        // 12,000 over 6 months = 2,000/month for this goal, plus 3,000 for the rest.
        assertEquals(BigDecimal("2000.00"), plan.monthlySavingsNeeded)
        assertEquals(0, BigDecimal("5000").compareTo(plan.spendablePerMonth))
        assertTrue(plan.onTrack)
    }

    @Test
    fun withContribution_addsAmountWithoutChangingActiveGoal() {
        val state = SavingsGoals(
            goals = listOf(goal(), goal(id = "goal-2", name = "Laptop", kind = SavingsGoalKind.FIXED)),
            activeGoalId = "goal-1",
        )

        val updated = state.withContribution("goal-2", BigDecimal(2_500), nowMillis = 1_234L)

        assertEquals("goal-1", updated.activeGoalId)
        assertEquals(
            BigDecimal("2500"),
            updated.goals.first { it.id == "goal-2" }.contributedAmount,
        )
        val logged = updated.goals.first { it.id == "goal-2" }.contributions.single()
        assertEquals(1_234L, logged.atMillis)
        assertEquals(BigDecimal("2500"), logged.amount)
        assertEquals(0, state.goals.first { it.id == "goal-2" }.contributedAmount.signum())
    }

    @Test
    fun removeContribution_dropsEntryAndReducesTotal() {
        val state = SavingsGoals(
            goals = listOf(
                goal(
                    id = "goal-2",
                    name = "Laptop",
                    kind = SavingsGoalKind.FIXED,
                    contributed = 3_000,
                    contributions = listOf(
                        SavingsContribution(100L, BigDecimal(1_000)),
                        SavingsContribution(200L, BigDecimal(2_000)),
                    ),
                ),
            ),
            activeGoalId = "goal-2",
        )

        val updated = state.removeContribution("goal-2", 100L)

        val goal = updated.goals.single()
        assertEquals(BigDecimal("2000"), goal.contributedAmount)
        assertEquals(1, goal.contributions.size)
        assertEquals(200L, goal.contributions.single().atMillis)
    }

    @Test
    fun withoutContribution_reversesAmountAndNeverGoesNegative() {
        val state = SavingsGoals(
            goals = listOf(
                goal(),
                goal(id = "goal-2", name = "Laptop", kind = SavingsGoalKind.FIXED, contributed = 2_500),
            ),
            activeGoalId = "goal-2",
        )

        val reversed = state.withoutContribution("goal-2", BigDecimal(1_000))
        assertEquals(
            BigDecimal("1500"),
            reversed.goals.first { it.id == "goal-2" }.contributedAmount,
        )

        val floored = reversed.withoutContribution("goal-2", BigDecimal(9_999))
        assertEquals(
            0,
            floored.goals.first { it.id == "goal-2" }.contributedAmount.signum(),
        )
        // Undo must not switch the selected goal.
        assertEquals("goal-2", floored.activeGoalId)
    }

    @Test
    fun fixedPace_usesTimeShareInsteadOfIncomeShare() {
        val plan = computeSavingsGoalPlan(
            targetPercent = 70,
            periodStart = LocalDate.of(2026, 1, 1),
            endMonth = YearMonth.of(2026, 12),
            actualIncome = BigDecimal.ZERO,
            actualSpent = BigDecimal(1_000),
            salaryMonthlyIncome = null,
            summary = com.spendly.tracker.ui.screens.analytics.SavingsRateSummary(),
            projection = null,
            currency = "INR",
            today = LocalDate.of(2026, 7, 1),
            goalKind = SavingsGoalKind.FIXED,
            targetAmount = BigDecimal(12_000),
        )

        val pace = evaluateSavingsPace(plan, today = LocalDate.of(2026, 7, 1))

        // Half the year elapsed, so roughly half of the fixed target was expected.
        assertEquals(6_000.0, pace.expectedSaved.toDouble(), 150.0)
        assertFalse(pace.status == SavingsPaceStatus.NO_INCOME)
    }

    @Test
    fun lastGoalIsNotDeletable() {
        val state = SavingsGoals(goals = listOf(goal()), activeGoalId = "goal-1")
        assertFalse(state.hasMultiple)
    }

    @Test
    fun defaultGoalUsesPassedValues() {
        val default = defaultSavingsGoal(
            percent = 55,
            endMonth = YearMonth.of(2027, 6),
        )

        assertEquals(55, default.targetPercent)
        assertEquals(YearMonth.of(2027, 6), default.endMonth)
        assertEquals(0, default.targetAmount.signum())
    }
}
