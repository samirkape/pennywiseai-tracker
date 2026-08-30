package com.spendly.tracker.ui.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendly.tracker.presentation.home.DayActivity
import com.spendly.tracker.ui.theme.Dimensions
import com.spendly.tracker.ui.theme.Spacing
import com.spendly.tracker.utils.CurrencyFormatter
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeEffectScope
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun HeatmapWidget(
    transactionHeatmap: Map<Long, DayActivity>,
    currency: String,
    modifier: Modifier = Modifier,
    blurEffects: Boolean = false,
    hazeState: HazeState? = null,
    onClick: (() -> Unit)? = null,
    onDayClick: ((LocalDate) -> Unit)? = null,
    onDayLongClick: ((LocalDate) -> Unit)? = null,
) {
    val view = androidx.compose.ui.platform.LocalView.current
    val weeksToShow = 26
    val today = LocalDate.now()
    val startDate = today.minusWeeks((weeksToShow - 1).toLong()).with(DayOfWeek.MONDAY)

    val monthLabels = remember(startDate, today) {
        val allMonthStarts = mutableListOf<Pair<Int, String>>()
        var current = startDate
        var lastMonth = -1
        var weekIndex = 0

        while (current <= today) {
            if (current.monthValue != lastMonth) {
                val formatter = DateTimeFormatter.ofPattern("MMM")
                allMonthStarts.add(weekIndex to current.format(formatter))
                lastMonth = current.monthValue
            }
            current = current.plusWeeks(1)
            weekIndex++
        }

        val filteredLabels = mutableListOf<Pair<Int, String>>()
        for (i in allMonthStarts.indices) {
            val (week, label) = allMonthStarts[i]
            if (i == 0 && allMonthStarts.size > 1) {
                val nextWeek = allMonthStarts[1].first
                if (nextWeek - week < 4) continue
            }
            if (filteredLabels.isEmpty()) {
                filteredLabels.add(week to label)
            } else {
                val lastAddedWeek = filteredLabels.last().first
                if (week - lastAddedWeek >= 4) {
                    filteredLabels.add(week to label)
                }
            }
        }
        filteredLabels
    }

    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        scrollState.scrollTo(scrollState.maxValue)
    }

    // Selected day (tapped cell) — defaults to today if it has activity, else unselected.
    var selectedEpochDay by rememberSaveable { mutableStateOf<Long?>(null) }

    val containerColor = if (blurEffects)
        MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f)
    else MaterialTheme.colorScheme.surfaceContainerLow

    SpendlyCardV2(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (blurEffects && hazeState != null) Modifier
                    .clip(RoundedCornerShape(Dimensions.CornerRadius.large))
                    .hazeEffect(
                        state = hazeState,
                        block = fun HazeEffectScope.() {
                            style = HazeDefaults.style(
                                backgroundColor = Color.Transparent,
                                tint = HazeDefaults.tint(containerColor),
                                blurRadius = 20.dp,
                                noiseFactor = -1f,
                            )
                            blurredEdgeTreatment = BlurredEdgeTreatment.Unbounded
                        }
                    )
                else Modifier
            ),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        )
    ) {
        Column {
            SectionHeaderV2(title = "Activity")

            val cellSize = 14.dp
            val gapSize = 4.dp
            val dayLabelWidth = 20.dp

            Column(
                modifier = Modifier.padding(top = Spacing.sm)
            ) {
                Row {
                    // Day-of-week labels (M, W, F)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(gapSize),
                        modifier = Modifier.width(dayLabelWidth)
                    ) {
                        // Rows: 0=Mon, 1=Tue, 2=Wed, 3=Thu, 4=Fri, 5=Sat, 6=Sun
                        for (d in 0 until 7) {
                            val label = when (d) {
                                0 -> "M"
                                2 -> "W"
                                4 -> "F"
                                else -> null
                            }
                            Box(
                                modifier = Modifier.size(cellSize),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (label != null) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }

                    // Heatmap grid
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(gapSize),
                        modifier = Modifier
                            .horizontalScroll(scrollState)
                            .padding(bottom = 8.dp)
                    ) {
                        for (w in 0 until weeksToShow) {
                            Column(verticalArrangement = Arrangement.spacedBy(gapSize)) {
                                for (d in 0 until 7) {
                                    val date = startDate.plusWeeks(w.toLong()).plusDays(d.toLong())
                                    val epochDay = date.toEpochDay()
                                    val activity = transactionHeatmap[epochDay]
                                    val count = activity?.count ?: 0
                                    val isFuture = date > today
                                    val isSelected = selectedEpochDay == epochDay

                                    val primary = MaterialTheme.colorScheme.primary
                                    val color = when {
                                        isFuture -> MaterialTheme.colorScheme.surfaceContainerHigh
                                        count == 0 -> MaterialTheme.colorScheme.surfaceContainerHigh
                                        count == 1 -> primary.copy(alpha = 0.25f)
                                        count == 2 -> primary.copy(alpha = 0.5f)
                                        count in 3..4 -> primary.copy(alpha = 0.75f)
                                        else -> primary
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(cellSize)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(color)
                                            .then(
                                                if (isSelected) Modifier.border(
                                                    width = 1.5.dp,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    shape = RoundedCornerShape(4.dp)
                                                ) else Modifier
                                            )
                                            .combinedClickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                enabled = !isFuture,
                                                onClick = {
                                                    selectedEpochDay = if (selectedEpochDay == epochDay) null else epochDay
                                                    onDayClick?.invoke(date)
                                                },
                                                onLongClick = {
                                                    view.performHapticFeedback(
                                                        android.view.HapticFeedbackConstants.LONG_PRESS
                                                    )
                                                    selectedEpochDay = epochDay
                                                    onDayLongClick?.invoke(date)
                                                },
                                            )
                                    )
                                }
                            }
                        }
                    }
                }

                // Month labels
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = dayLabelWidth)
                ) {
                    monthLabels.forEach { (weekIndex, label) ->
                        val xOffset = (weekIndex * (cellSize + gapSize).value).dp
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                            modifier = Modifier.offset(x = xOffset)
                        )
                    }
                }

                // Selected-day detail readout
                val selectedDate = selectedEpochDay?.let { LocalDate.ofEpochDay(it) }
                val selectedActivity = selectedEpochDay?.let { transactionHeatmap[it] }
                Box(modifier = Modifier.padding(top = Spacing.sm, start = dayLabelWidth).height(18.dp)) {
                    if (selectedDate != null) {
                        val dateLabel = selectedDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
                        val detailText = if (selectedActivity != null && selectedActivity.count > 0) {
                            val amountText = CurrencyFormatter.formatCurrency(selectedActivity.amount, currency)
                            "$dateLabel \u00B7 ${selectedActivity.count} txn${if (selectedActivity.count == 1) "" else "s"} \u00B7 $amountText"
                        } else {
                            "$dateLabel \u00B7 No spending"
                        }
                        Text(
                            text = detailText,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    } else {
                        Text(
                            text = "Tap a day to see details",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }
}
