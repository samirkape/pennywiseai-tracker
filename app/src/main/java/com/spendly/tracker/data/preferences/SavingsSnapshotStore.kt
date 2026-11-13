package com.spendly.tracker.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.spendly.tracker.ui.screens.savings.SavingsMonthSnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

private val Context.savingsSnapshotDataStore by preferencesDataStore(
    name = "savings_monthly_snapshots",
)

/**
 * Persists one small snapshot per month so the Savings page can show history beyond the
 * months currently visible in the savings-rate window. Snapshots are written from the same
 * buckets the page already computes, which keeps them consistent with the chart.
 */
@Singleton
class SavingsSnapshotStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val KEY_PREFIX = "snapshot_"
        private const val MAX_SNAPSHOTS = 36
        private const val FIELD_SEPARATOR = "|"
    }

    /** Newest first, capped so the store cannot grow forever. */
    fun snapshots(): Flow<List<SavingsMonthSnapshot>> =
        context.savingsSnapshotDataStore.data.map { preferences ->
            preferences.asMap()
                .mapNotNull { (key, value) ->
                    val month = key.name
                        .takeIf { it.startsWith(KEY_PREFIX) }
                        ?.removePrefix(KEY_PREFIX)
                        ?: return@mapNotNull null
                    (value as? String)?.let { decode(month, it) }
                }
                .sortedByDescending { it.month }
                .take(MAX_SNAPSHOTS)
        }

    /** Writes only snapshots whose stored values changed, then prunes the oldest months. */
    suspend fun record(snapshots: List<SavingsMonthSnapshot>) {
        if (snapshots.isEmpty()) return
        context.savingsSnapshotDataStore.edit { preferences ->
            val now = System.currentTimeMillis()
            snapshots.forEach { snapshot ->
                val key = stringPreferencesKey(KEY_PREFIX + snapshot.month)
                // Keep the first capture time so "tracking since" stays stable when the
                // same month is refreshed as new transactions arrive.
                val previous = preferences[key]?.let { decode(snapshot.month.toString(), it) }
                val capturedAt = previous?.capturedAtMillis?.takeIf { it > 0L }
                    ?: snapshot.capturedAtMillis.takeIf { it > 0L }
                    ?: now
                val encoded = encode(snapshot.copy(capturedAtMillis = capturedAt))
                if (preferences[key] != encoded) preferences[key] = encoded
            }
            val stale = preferences.asMap()
                .keys
                .map { it.name }
                .filter { it.startsWith(KEY_PREFIX) }
                .sorted()
                .dropLast(MAX_SNAPSHOTS)
            stale.forEach { preferences.remove(stringPreferencesKey(it)) }
        }
    }

    /** Removes every stored month, e.g. after the user clears the history. */
    suspend fun clear() {
        context.savingsSnapshotDataStore.edit { preferences -> preferences.clear() }
    }

    /** Removes one month; the page re-records it if that month is still in the window. */
    suspend fun delete(month: YearMonth) {
        context.savingsSnapshotDataStore.edit { preferences ->
            preferences.remove(stringPreferencesKey(KEY_PREFIX + month))
        }
    }

    private fun encode(snapshot: SavingsMonthSnapshot): String = listOf(
        snapshot.income.toPlainString(),
        snapshot.spent.toPlainString(),
        snapshot.targetPercent.toString(),
        snapshot.capturedAtMillis.toString(),
    ).joinToString(FIELD_SEPARATOR)

    private fun decode(month: String, raw: String): SavingsMonthSnapshot? = runCatching {
        val parts = raw.split(FIELD_SEPARATOR)
        if (parts.size != 4) return@runCatching null
        SavingsMonthSnapshot(
            month = YearMonth.parse(month),
            income = BigDecimal(parts[0]),
            spent = BigDecimal(parts[1]),
            targetPercent = parts[2].toInt().coerceIn(0, 100),
            capturedAtMillis = parts[3].toLong(),
        )
    }.getOrNull()
}
