# Temporary: merchant name normalization backfill

This is a **temporary change** to fix merchant names inside already-stored
transactions (e.g. `THE NEW INDIA Assurance Company` → `The New India
Assurance Company`). The permanent fix lives in these production files:

- `app/src/main/java/com/spendly/tracker/utils/MerchantNameNormalizer.kt`
- `app/src/main/java/com/spendly/tracker/data/mapper/ParsedTransactionMapper.kt` (new SMS ingest)
- `app/src/main/java/com/spendly/tracker/presentation/transactions/TransactionDetailViewModel.kt` (detail-screen save)

…so from install onward, new imports and manual edits are normalized. The
backfill below is only needed to repair historical rows; it must **not** be
committed or shipped. It is idempotent and skips rows where the user manually
renamed the merchant (`merchant_manually_edited = 1`).

## How to use

1. Apply the four snippets below (they're all additive).
2. Build + install, open the app once — the pass runs on startup on a
   background coroutine.
3. Rename this folder to `tmp-merchant-backfill.done/` won't matter — instead:
   undo the four snippets (delete exactly what was added), rebuild, reinstall.
   Running the app again without the snippets does nothing; the DB keeps the
   fixed names. There's no flag, so re-applying later is harmless (0 changes).

## Snippet 1 — `TransactionDao.kt` (add next to `updateMerchantNameById`, ~line 380)

```kotlin
    /**
     * Returns (id, merchant_name) of every non-deleted transaction whose merchant
     * name was not manually renamed by the user. Used by the one-time merchant
     * name normalization backfill pass.
     */
    @Query("SELECT id, merchant_name FROM transactions WHERE is_deleted = 0 AND merchant_manually_edited = 0")
    suspend fun getMerchantNamesForNormalization(): List<MerchantNameNormalizationRow>
```

## Snippet 2 — `TransactionDao.kt` (projection POJO, bottom of file)

```kotlin
/** Minimal projection used by [TransactionDao.getMerchantNamesForNormalization]. */
data class MerchantNameNormalizationRow(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "merchant_name") val merchantName: String,
)
```

## Snippet 3 — `TransactionRepository.kt` (after `undoLastMerchantRename()`, ~line 102)

```kotlin
    /**
     * One-time backfill: re-title-cases merchant_name for every non-deleted
     * transaction that was not manually renamed (merchant_manually_edited = 0).
     * Only rows whose normalization actually changes the name are updated.
     * Returns the number of rows actually changed.
     */
    suspend fun normalizeStoredMerchantNames(): Int {
        val now = LocalDateTime.now()
        val rows = transactionDao.getMerchantNamesForNormalization()
        val changes = rows.filter { MerchantNameNormalizer.normalize(it.merchantName) != it.merchantName }
        for (row in changes) {
            transactionDao.updateMerchantNameById(
                row.id,
                MerchantNameNormalizer.normalize(row.merchantName),
                now,
                manuallyEdited = false,
            )
        }
        return changes.size
    }
```

+ import: `import com.spendly.tracker.utils.MerchantNameNormalizer`

## Snippet 4 — `SpendlyApplication.kt`

```kotlin
    // field:
    @Inject
    lateinit var transactionRepository: TransactionRepository

    // import:
    import com.spendly.tracker.data.repository.TransactionRepository

    // in onCreate(), next to scheduleHistoricalCcLinkerBackfill():
    scheduleMerchantNameNormalization()

    // function, after scheduleHistoricalCcLinkerBackfill():
    /**
     * One-shot historical pass: re-title-cases stored merchant_name values.
     * Idempotent; skips user-renamed rows. Temporary — see
     * tmp-merchant-backfill/README.md.
     */
    private fun scheduleMerchantNameNormalization() {
        applicationScope.launch(Dispatchers.IO) {
            runCatching {
                val changed = transactionRepository.normalizeStoredMerchantNames()
                Log.i("MerchantBackfill", "Normalized $changed merchant names")
            }
        }
    }
```

+ import: `import android.util.Log` (optional, for the log line)
