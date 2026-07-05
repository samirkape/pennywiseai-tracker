package com.spendly.tracker.data.repository

import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.domain.model.KeywordBatchUndoSession
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeywordRuleBatchUndoRepository @Inject constructor() {

    private var snapshots: List<TransactionEntity> = emptyList()
    private var session: KeywordBatchUndoSession? = null

    fun saveUndoSession(
        ruleName: String,
        beforeSnapshots: List<TransactionEntity>,
        undoWindowMs: Long = DEFAULT_UNDO_WINDOW_MS,
    ) {
        snapshots = beforeSnapshots
        val now = System.currentTimeMillis()
        session = KeywordBatchUndoSession(
            ruleName = ruleName,
            appliedAtMillis = now,
            expiresAtMillis = now + undoWindowMs,
            transactionCount = beforeSnapshots.size,
        )
    }

    fun getActiveSession(): KeywordBatchUndoSession? {
        val current = session ?: return null
        if (System.currentTimeMillis() >= current.expiresAtMillis) {
            clear()
            return null
        }
        return current
    }

    fun consumeSnapshotsForUndo(): List<TransactionEntity>? {
        val current = getActiveSession() ?: return null
        val data = snapshots
        if (data.isEmpty()) {
            clear()
            return null
        }
        clear()
        return data
    }

    fun clear() {
        snapshots = emptyList()
        session = null
    }

    companion object {
        const val DEFAULT_UNDO_WINDOW_MS = 30 * 60 * 1000L
    }
}
