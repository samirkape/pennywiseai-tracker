package com.spendly.tracker.ui.screens.payperiod

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.spendly.tracker.ui.theme.Dimensions
import com.spendly.tracker.ui.theme.Spacing
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun PayPeriodExplorerScreen(
	periodStartEpochDay: Long,
	periodEndEpochDay: Long,
	onNavigateBack: () -> Unit,
	viewModel: PayPeriodExplorerViewModel = hiltViewModel(),
) {
	LaunchedEffect(periodStartEpochDay, periodEndEpochDay) {
		viewModel.start(periodStartEpochDay, periodEndEpochDay)
	}
	val uiState by viewModel.uiState.collectAsState()

	Column(
		modifier = Modifier
			.fillMaxWidth()
			.padding(Dimensions.Padding.content),
		verticalArrangement = Arrangement.spacedBy(Spacing.md),
	) {
		Text(
			text = uiState.periodRangeLabel.ifBlank { "Period timeline" },
			style = MaterialTheme.typography.titleMedium,
			color = MaterialTheme.colorScheme.onSurface,
		)
		PayPeriodExplorerContent(
			periodStartEpochDay = periodStartEpochDay,
			periodEndEpochDay = periodEndEpochDay,
			modifier = Modifier.fillMaxWidth(),
			showViewTransactionsButton = true,
			onViewTransactions = onNavigateBack,
		)
	}
}

@Composable
fun PayPeriodExplorerContent(
	periodStartEpochDay: Long,
	periodEndEpochDay: Long,
	modifier: Modifier = Modifier,
	showViewTransactionsButton: Boolean = true,
	onViewTransactions: (() -> Unit)? = null,
	viewModel: PayPeriodExplorerViewModel = hiltViewModel(),
) {
	val uiState by viewModel.uiState.collectAsState()
	val periodStart = LocalDate.ofEpochDay(periodStartEpochDay)
	val periodEnd = LocalDate.ofEpochDay(periodEndEpochDay)

	LaunchedEffect(periodStartEpochDay, periodEndEpochDay) {
		viewModel.start(periodStartEpochDay, periodEndEpochDay)
	}

	if (uiState.dayLabels.isEmpty()) {
		Text(
			text = "No spend data for this period.",
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = modifier.padding(Dimensions.Padding.content),
		)
		return
	}

	val selectedIndex = uiState.dayLabels.indexOf(uiState.selectedDate).coerceAtLeast(0)

	Column(
		modifier = modifier,
		verticalArrangement = Arrangement.spacedBy(Spacing.md),
	) {
		Card(
			colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
			shape = MaterialTheme.shapes.large,
		) {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(Dimensions.Padding.content),
				verticalArrangement = Arrangement.spacedBy(Spacing.sm),
			) {
				Text(
					text = "Spent through ${uiState.selectedDate.format(DateTimeFormatter.ofPattern("MMM d"))}",
					style = MaterialTheme.typography.labelMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
				Text(
					text = uiState.spentThroughSelected.setScale(2, RoundingMode.HALF_UP).toPlainString(),
					style = MaterialTheme.typography.headlineMedium,
					color = MaterialTheme.colorScheme.onSurface,
				)
				TimelineSparkline(
					values = uiState.cumulativeSeries,
					selectedIndex = selectedIndex,
					dayLabels = uiState.dayLabels,
					onPointSelected = { date -> viewModel.selectDate(date) },
					modifier = Modifier
						.fillMaxWidth()
						.height(110.dp),
				)
			}
		}

		LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
			itemsIndexed(uiState.dayLabels) { index, date ->
				val isSelected = index == selectedIndex
				Surface(
					onClick = { viewModel.selectDate(date) },
					shape = CircleShape,
					color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
				) {
					Text(
						text = date.dayOfMonth.toString(),
						style = MaterialTheme.typography.labelMedium,
						color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
						modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
					)
				}
			}
		}

		if (showViewTransactionsButton && onViewTransactions != null) {
			Button(onClick = onViewTransactions, modifier = Modifier.fillMaxWidth()) {
				Text("View transactions")
				Icon(imageVector = Icons.Default.ArrowForward, contentDescription = null)
			}
		}
	}
}

@Composable
private fun TimelineSparkline(
	values: List<BigDecimal>,
	selectedIndex: Int,
	dayLabels: List<LocalDate>,
	onPointSelected: (LocalDate) -> Unit,
	modifier: Modifier = Modifier,
) {
	val lineColor = MaterialTheme.colorScheme.primary
	val surfaceColor = MaterialTheme.colorScheme.surface
	Canvas(
		modifier = modifier.pointerInput(Unit) {
			detectTapGestures { offset ->
				if (values.isEmpty()) return@detectTapGestures
				val index = ((offset.x / size.width) * values.size).toInt().coerceIn(0, values.lastIndex)
				if (index < dayLabels.size) {
					onPointSelected(dayLabels[index])
				}
			}
		}
	) {
		if (values.isEmpty()) return@Canvas

		val maxValue = maxOf(values.maxOf { it.toFloat() }, 1f)
		val points = values.mapIndexed { index, value ->
			val x = if (values.size == 1) size.width / 2f else index * size.width / (values.size - 1).coerceAtLeast(1)
			val y = size.height - (value.toFloat() / maxValue) * (size.height * 0.8f) - 8f
			Offset(x, y)
		}
		val activeIndex = selectedIndex.coerceIn(points.indices)

		val activePath = Path().apply {
			moveTo(points.first().x, points.first().y)
			for (i in 1..activeIndex) lineTo(points[i].x, points[i].y)
		}
		drawPath(activePath, color = lineColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))

		if (activeIndex < points.lastIndex) {
			val inactivePath = Path().apply {
				moveTo(points[activeIndex].x, points[activeIndex].y)
				for (i in activeIndex + 1..points.lastIndex) lineTo(points[i].x, points[i].y)
			}
			drawPath(
				inactivePath,
				color = lineColor.copy(alpha = 0.28f),
				style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),
			)
		}

		val selected = points[activeIndex]
		drawLine(
			color = lineColor.copy(alpha = 0.5f),
			start = selected,
			end = Offset(selected.x, size.height),
			strokeWidth = 1.dp.toPx(),
			pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f),
		)
		drawCircle(color = surfaceColor, radius = 5.dp.toPx(), center = selected)
		drawCircle(color = lineColor, radius = 3.dp.toPx(), center = selected)
	}
}




