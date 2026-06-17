package com.pennywiseai.tracker.data.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.GsonBuilder
import androidx.room.withTransaction
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.entity.*
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: PennyWiseDatabase,
    private val userPreferencesRepository: UserPreferencesRepository
) {

    private val gson = GsonBuilder()
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeTypeAdapter())
        .registerTypeAdapter(LocalDate::class.java, LocalDateTypeAdapter())
        .registerTypeAdapter(java.math.BigDecimal::class.java, BigDecimalTypeAdapter())
        .create()

    // ── Public entry points ───────────────────────────────────────────────────

    suspend fun importBackup(
        uri: Uri,
        strategy: ImportStrategy = ImportStrategy.MERGE
    ): ImportResult = withContext(Dispatchers.IO) {
        try {
            val backup = readBackupFile(uri)
            if (!isCompatibleVersion(backup)) {
                return@withContext ImportResult.Error("Incompatible backup version")
            }
            when (strategy) {
                ImportStrategy.REPLACE_ALL -> replaceAllData(backup)
                ImportStrategy.MERGE       -> mergeData(backup)
                ImportStrategy.SELECTIVE   -> mergeData(backup)
            }
        } catch (e: Exception) {
            Log.e("BackupImporter", "Import failed", e)
            ImportResult.Error("Import failed: ${e.message}")
        }
    }

    /**
     * Selective restore — only imports the data categories specified in [options].
     * Uses merge semantics for each selected category.
     */
    suspend fun importBackup(
        uri: Uri,
        options: RestoreOptions
    ): ImportResult = withContext(Dispatchers.IO) {
        try {
            val backup = readBackupFile(uri)
            if (!isCompatibleVersion(backup)) {
                return@withContext ImportResult.Error("Incompatible backup version")
            }
            selectiveRestore(backup, options)
        } catch (e: Exception) {
            Log.e("BackupImporter", "Selective import failed", e)
            ImportResult.Error("Import failed: ${e.message}")
        }
    }

    // ── Parsing ───────────────────────────────────────────────────────────────

    /**
     * Read the backup file, normalise any missing JSON keys (for older backup
     * formats), then deserialise into the model.
     *
     * Gson bypasses Kotlin constructors and therefore ignores default parameter
     * values — any key absent from the JSON becomes null at runtime even when
     * the Kotlin type is non-nullable. Normalising the JsonObject before the
     * final deserialisation call is the safest backward-compat strategy.
     */
    private suspend fun readBackupFile(uri: Uri): PennyWiseBackup {
        return withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val content = BufferedReader(InputStreamReader(inputStream)).readText()
                val rawJson = gson.fromJson(content, com.google.gson.JsonObject::class.java)
                normalizeBackupJson(rawJson)
                gson.fromJson(rawJson, PennyWiseBackup::class.java)
            } ?: throw Exception("Failed to read backup file")
        }
    }

    /**
     * Fills missing JSON keys with safe defaults so older backup formats
     * deserialise without NPEs.
     */
    private fun normalizeBackupJson(root: com.google.gson.JsonObject) {
        // ── database section ─────────────────────────────────────────────────
        val db = root.getAsJsonObject("database") ?: com.google.gson.JsonObject().also {
            root.add("database", it)
        }
        for (key in listOf(
            "transactions", "categories", "cards", "account_balances",
            "subscriptions", "merchant_mappings", "unrecognized_sms", "chat_messages",
            "merchant_aliases", "rules", "rule_applications", "exchange_rates",
            "budgets", "budget_categories", "transaction_splits", "bank_notifications",
            "salary_month_overrides", "transaction_receipts", "loans",
            "transaction_groups", "profiles", "goals", "goal_contributions"
        )) {
            if (!db.has(key) || db.get(key).isJsonNull) {
                db.add(key, com.google.gson.JsonArray())
            }
        }

        // ── preferences section ───────────────────────────────────────────────
        if (!root.has("preferences") || root.get("preferences").isJsonNull) {
            root.add("preferences", com.google.gson.JsonObject())
        }
        val prefs = root.getAsJsonObject("preferences")

        // theme
        if (!prefs.has("theme") || prefs.get("theme").isJsonNull) {
            prefs.add("theme", com.google.gson.JsonObject().apply {
                addProperty("is_dynamic_color_enabled", true)
            })
        }
        val theme = prefs.getAsJsonObject("theme")
        if (!theme.has("is_dynamic_color_enabled")) theme.addProperty("is_dynamic_color_enabled", true)
        if (!theme.has("is_amoled_mode"))           theme.addProperty("is_amoled_mode", false)
        if (!theme.has("blur_effects_enabled"))      theme.addProperty("blur_effects_enabled", true)
        if (!theme.has("compact_analytics_cards"))   theme.addProperty("compact_analytics_cards", true)

        // sms
        if (!prefs.has("sms") || prefs.get("sms").isJsonNull) {
            prefs.add("sms", com.google.gson.JsonObject().apply {
                addProperty("has_skipped_sms_permission", false)
                addProperty("sms_scan_months", 3)
                addProperty("sms_scan_all_time", true)
            })
        }
        val sms = prefs.getAsJsonObject("sms")
        if (!sms.has("has_skipped_sms_permission")) sms.addProperty("has_skipped_sms_permission", false)
        if (!sms.has("sms_scan_months"))            sms.addProperty("sms_scan_months", 3)
        if (!sms.has("sms_scan_all_time"))          sms.addProperty("sms_scan_all_time", true)

        // developer
        if (!prefs.has("developer") || prefs.get("developer").isJsonNull) {
            prefs.add("developer", com.google.gson.JsonObject().apply {
                addProperty("is_developer_mode_enabled", false)
            })
        }
        val dev = prefs.getAsJsonObject("developer")
        if (!dev.has("is_developer_mode_enabled")) dev.addProperty("is_developer_mode_enabled", false)

        // app
        if (!prefs.has("app") || prefs.get("app").isJsonNull) {
            prefs.add("app", com.google.gson.JsonObject().apply {
                addProperty("has_shown_scan_tutorial", false)
                addProperty("has_shown_review_prompt", false)
                addProperty("unified_currency_mode", false)
                addProperty("balance_hidden", false)
                addProperty("has_completed_onboarding", false)
                addProperty("month_start_day", 1)
                addProperty("use_financial_month", true)
                addProperty("use_fixed_budget_period_end", false)
                addProperty("budget_period_end_day", 31)
                addProperty("profile_background_color", 0)
            })
        }
        val app = prefs.getAsJsonObject("app")
        if (!app.has("has_shown_scan_tutorial"))     app.addProperty("has_shown_scan_tutorial", false)
        if (!app.has("has_shown_review_prompt"))      app.addProperty("has_shown_review_prompt", false)
        if (!app.has("unified_currency_mode"))        app.addProperty("unified_currency_mode", false)
        if (!app.has("balance_hidden"))               app.addProperty("balance_hidden", false)
        if (!app.has("has_completed_onboarding"))     app.addProperty("has_completed_onboarding", false)
        if (!app.has("month_start_day"))              app.addProperty("month_start_day", 1)
        if (!app.has("use_financial_month"))          app.addProperty("use_financial_month", true)
        if (!app.has("use_fixed_budget_period_end"))  app.addProperty("use_fixed_budget_period_end", false)
        if (!app.has("budget_period_end_day"))        app.addProperty("budget_period_end_day", 31)
        if (!app.has("profile_background_color"))     app.addProperty("profile_background_color", 0)
        if (!app.has("insights_data_window_months"))  app.addProperty("insights_data_window_months", 3)

        // security (added in a later version)
        if (!prefs.has("security") || prefs.get("security").isJsonNull) {
            prefs.add("security", com.google.gson.JsonObject().apply {
                addProperty("app_lock_enabled", false)
                addProperty("app_lock_timeout_minutes", 1)
            })
        }
        val security = prefs.getAsJsonObject("security")
        if (!security.has("app_lock_enabled"))         security.addProperty("app_lock_enabled", false)
        if (!security.has("app_lock_timeout_minutes")) security.addProperty("app_lock_timeout_minutes", 1)
    }

    /**
     * Check if backup version is compatible.
     * The `_format` field may be absent in very old backups — treat a missing
     * format as a legacy PennyWise v1 backup and try to import anyway.
     */
    private fun isCompatibleVersion(backup: PennyWiseBackup): Boolean {
        val fmt: String? = try { backup.format } catch (_: Exception) { null }
        if (fmt == null) return true
        return fmt.startsWith("PennyWise Backup v1") ||
               fmt.startsWith("Spendly Backup v1")
    }

    private fun latestTransactionTimestampMillis(transactions: List<TransactionEntity>): Long? =
        transactions.maxOfOrNull { it.dateTime }
            ?.atZone(java.time.ZoneId.systemDefault())
            ?.toInstant()
            ?.toEpochMilli()

    // ── REPLACE ALL ───────────────────────────────────────────────────────────

    /**
     * Replace all existing data with backup data.
     *
     * Deleted-transaction semantics: [getAllTransactions] excludes is_deleted=1
     * rows, so the backup only contains transactions active at export time.
     * After a full clear + re-import the DB faithfully reflects the backup
     * snapshot — intentional for REPLACE_ALL.
     */
    private suspend fun replaceAllData(backup: PennyWiseBackup): ImportResult {
        var importedTransactions = 0
        var importedCategories = 0

        // Database operations run inside the transaction; preferences are written
        // outside so DataStore's dispatcher doesn't interfere with the Room
        // transaction context and all writes are guaranteed to complete.
        database.withTransaction {
            clearAllDataForRestore()

            // Loans, groups, and profiles BEFORE transactions so FK
            // references in TransactionEntity resolve correctly.
            backup.database.loans.forEach { loan ->
                database.loanDao().insertLoanForRestore(loan)
            }
            backup.database.transactionGroups.forEach { group ->
                database.transactionGroupDao().insertGroupForRestore(group)
            }
            backup.database.profiles.forEach { profile ->
                database.profileDao().insert(profile)
            }

            backup.database.categories.forEach { category ->
                database.categoryDao().insertCategoryForRestore(category)
                importedCategories++
            }

            importTransactionsForRestore(backup.database.transactions)
            importedTransactions = backup.database.transactions.size

            // Receipts reference transaction IDs — restore after transactions.
            database.transactionReceiptDao().insertReceipts(backup.database.transactionReceipts)

            backup.database.cards.forEach { card ->
                database.cardDao().insertCard(card)
            }
            backup.database.accountBalances.forEach { balance ->
                database.accountBalanceDao().insertBalance(balance)
            }
            backup.database.subscriptions.forEach { subscription ->
                database.subscriptionDao().insertSubscription(subscription)
            }
            backup.database.merchantMappings.forEach { mapping ->
                database.merchantMappingDao().insertMapping(mapping)
            }
            backup.database.merchantAliases.forEach { alias ->
                database.merchantAliasDao().insertAlias(alias)
            }
            backup.database.unrecognizedSms.forEach { sms ->
                database.unrecognizedSmsDao().insert(sms)
            }
            backup.database.chatMessages.forEach { message ->
                database.chatDao().insertMessage(message)
            }
            backup.database.rules.forEach { rule ->
                database.ruleDao().insertRule(rule)
            }
            backup.database.ruleApplications.forEach { application ->
                database.ruleApplicationDao().insertApplication(application)
            }
            backup.database.exchangeRates.forEach { rate ->
                database.exchangeRateDao().insertExchangeRate(rate)
            }
            backup.database.budgets.forEach { budget ->
                database.budgetDao().insertBudget(budget)
            }
            backup.database.budgetCategories.forEach { category ->
                database.budgetDao().insertBudgetCategory(category)
            }
            backup.database.transactionSplits.forEach { split ->
                database.transactionSplitDao().insertSplit(split)
            }
            backup.database.bankNotifications.forEach { notification ->
                database.bankNotificationDao().insertOrReplace(notification)
            }
            backup.database.salaryMonthOverrides.forEach { override ->
                database.salaryMonthOverrideDao().upsert(override)
            }

            // Goals
            backup.database.goals.forEach { goal ->
                database.goalDao().insertGoal(goal)
            }
            backup.database.goalContributions.forEach { contribution ->
                database.goalContributionDao().insertContribution(contribution)
            }
        }

        // Preferences are written after the DB transaction commits successfully.
        // If withTransaction threw, this line is never reached.
        importPreferences(backup.preferences)

        return ImportResult.Success(
            importedTransactions = importedTransactions,
            importedCategories = importedCategories,
            skippedDuplicates = 0,
            latestTransactionTimestamp = latestTransactionTimestampMillis(backup.database.transactions)
        )
    }

    /**
     * Deletes all backup-covered tables in FK-safe order before a full restore.
     */
    private suspend fun clearAllDataForRestore() {
        database.ruleApplicationDao().deleteAllApplications()
        database.transactionReceiptDao().deleteAllReceipts()
        database.transactionSplitDao().deleteAllSplits()
        database.transactionDao().clearAllLinkedTransactionIds()
        database.transactionDao().deleteAllTransactions()

        database.ruleDao().deleteAllRules()
        database.budgetSnapshotDao().deleteAllBudgetCategoryMonthSnapshots()
        database.budgetSnapshotDao().deleteAllBudgetMonthSnapshots()
        database.budgetDao().deleteAllBudgets()

        database.categoryDao().deleteAllCategories()
        database.cardDao().deleteAllCards()
        database.accountBalanceDao().deleteAllBalances()
        database.subscriptionDao().deleteAllSubscriptions()
        database.merchantMappingDao().deleteAllMappings()
        database.merchantAliasDao().deleteAllAliases()
        database.unrecognizedSmsDao().deleteAll()
        database.chatDao().deleteAllMessages()
        database.exchangeRateDao().deleteAllRates()
        database.bankNotificationDao().deleteAllNotifications()
        database.salaryMonthOverrideDao().deleteAllOverrides()
        database.loanDao().deleteAllLoans()
        database.transactionGroupDao().deleteAllGroups()
        database.profileDao().deleteAllProfiles()
        database.goalContributionDao().deleteAllContributions()
        database.goalDao().deleteAllGoals()
    }

    /**
     * Inserts transactions preserving backup IDs, then restores linked-transaction
     * pointers in a second pass (avoids FK-order issues with self-references).
     */
    private suspend fun importTransactionsForRestore(transactions: List<TransactionEntity>) {
        val transactionDao = database.transactionDao()
        val linksToRestore = mutableListOf<Pair<Long, Long>>()

        transactions.forEach { transaction ->
            val linkedId = transaction.linkedTransactionId
            if (linkedId != null) {
                linksToRestore += transaction.id to linkedId
            }
            transactionDao.insertTransactionForRestore(
                transaction.copy(linkedTransactionId = null)
            )
        }

        val transferKindById = transactions.associate { it.id to it.transferKind }
        linksToRestore.forEach { (transactionId, linkedId) ->
            transactionDao.setLinkedTransaction(
                transactionId = transactionId,
                linkedId = linkedId,
                transferKind = transferKindById[transactionId],
            )
        }
    }

    // ── MERGE ─────────────────────────────────────────────────────────────────

    /**
     * Merge backup data with existing data.
     *
     * Key invariants:
     * - **Soft-deleted transactions** (is_deleted=1) are never resurrected.
     *   If a backup contains a transaction whose hash is locally soft-deleted
     *   it is silently skipped — the deletion was intentional.
     * - **Excluded transactions** (is_excluded_from_tracking=1) keep their
     *   local excluded state when the backup has the same hash as non-excluded;
     *   the duplicate-hash skip preserves the local version intact.
     * - New transactions from the backup are inserted with their full entity
     *   state, including isExcludedFromTracking, so exclusions travel correctly
     *   to a fresh device.
     */
    private suspend fun mergeData(backup: PennyWiseBackup): ImportResult {
        var importedTransactions = 0
        var importedCategories = 0
        var skippedDuplicates = 0

        database.withTransaction {
            // Collect existing state ────────────────────────────────────
                val existingTransactions = database.transactionDao()
                    .getAllTransactions().first()  // only is_deleted=0 rows
                val existingTransactionHashes = existingTransactions
                    .map { it.transactionHash }.toSet()
                val existingHashToIdMap = existingTransactions
                    .associateBy({ it.transactionHash }, { it.id })
                val existingHashToTransactionMap = existingTransactions
                    .associateBy { it.transactionHash }

                // Soft-deleted hashes: must never be re-inserted from backup.
                val softDeletedHashes = database.transactionDao()
                    .getSoftDeletedHashes().toSet()

                val existingCategories = database.categoryDao()
                    .getAllCategories().first()
                    .map { it.name }.toSet()

                // Loans, groups, profiles first (FK dependencies) ──────────
                val oldToNewLoanIdMap  = importLoansWithMerge(backup.database.loans)
                val oldToNewGroupIdMap = importGroupsWithMerge(backup.database.transactionGroups)
                importProfilesWithMerge(backup.database.profiles)

                // Categories ───────────────────────────────────────────────
                backup.database.categories.forEach { category ->
                    if (!existingCategories.contains(category.name)) {
                        database.categoryDao().insertCategory(category.copy(id = 0))
                        importedCategories++
                    }
                }

                // Transactions ─────────────────────────────────────────────
                val oldToNewTransactionIdMap = mutableMapOf<Long, Long>()

                backup.database.transactions.forEach { transaction ->
                    val hash = transaction.transactionHash

                    // Never resurrect soft-deleted transactions.
                    if (softDeletedHashes.contains(hash)) {
                        val localId = existingHashToIdMap[hash]
                            ?: database.transactionDao().getTransactionByHash(hash)?.id
                        if (transaction.id != 0L && localId != null) {
                            oldToNewTransactionIdMap[transaction.id] = localId
                        }
                        skippedDuplicates++
                        return@forEach
                    }

                    if (!existingTransactionHashes.contains(hash)) {
                        // New transaction: insert preserving isExcludedFromTracking
                        // so exclusions survive restore to a fresh device.
                        val oldId = transaction.id
                        val remapped = transaction.copy(
                            id = 0,
                            loanId  = transaction.loanId?.let  { oldToNewLoanIdMap[it]  ?: it },
                            groupId = transaction.groupId?.let { oldToNewGroupIdMap[it] ?: it }
                        )
                        val newId = database.transactionDao().insertTransaction(remapped)
                        if (oldId != 0L) oldToNewTransactionIdMap[oldId] = newId
                        importedTransactions++
                    } else {
                        // Duplicate: preserve local version (including local excluded state).
                        val localId = existingHashToIdMap[hash]
                        if (transaction.id != 0L && localId != null) {
                            oldToNewTransactionIdMap[transaction.id] = localId
                        }
                        // Restore description from backup if local row has none.
                        existingHashToTransactionMap[hash]?.let { existing ->
                            val backupDesc = transaction.description?.trim()
                            val existingDesc = existing.description?.trim()
                            if (!backupDesc.isNullOrEmpty() && existingDesc.isNullOrEmpty()) {
                                database.transactionDao().updateTransactionDescriptionIfEmpty(
                                    id = existing.id,
                                    description = backupDesc,
                                    updatedAt = LocalDateTime.now(),
                                )
                            }
                        }
                        skippedDuplicates++
                    }
                }

                // Receipts ─────────────────────────────────────────────────
                importReceiptsWithMerge(backup.database.transactionReceipts, oldToNewTransactionIdMap)

                // Other entities ───────────────────────────────────────────
                importCardsWithMerge(backup.database.cards)
                importAccountBalancesWithMerge(backup.database.accountBalances)
                importSubscriptionsWithMerge(backup.database.subscriptions)
                importMerchantMappingsWithMerge(backup.database.merchantMappings)
                importMerchantAliasesWithMerge(backup.database.merchantAliases)
                importRulesWithMerge(backup.database.rules)

                // Rule applications
                val existingRuleAppIds = database.ruleApplicationDao()
                    .getAllApplications().first().map { it.id }.toSet()
                backup.database.ruleApplications.forEach { application ->
                    if (!existingRuleAppIds.contains(application.id)) {
                        val mappedTxId = application.transactionId.toLongOrNull()?.let { oldId ->
                            oldToNewTransactionIdMap[oldId]?.toString() ?: application.transactionId
                        } ?: application.transactionId
                        database.ruleApplicationDao().insertApplication(
                            application.copy(transactionId = mappedTxId)
                        )
                    }
                }

                // Exchange rates
                val existingRates = database.exchangeRateDao().getAllRatesFlow().first()
                backup.database.exchangeRates.forEach { rate ->
                    val exists = existingRates.any {
                        it.fromCurrency == rate.fromCurrency && it.toCurrency == rate.toCurrency
                    }
                    if (!exists) database.exchangeRateDao().insertExchangeRate(rate)
                }

                importBudgetsWithMerge(backup.database.budgets, backup.database.budgetCategories)

                // Transaction splits
                val existingSplits = database.transactionSplitDao().getAllSplits().first()
                val existingSplitKeys = existingSplits
                    .map { "${it.transactionId}|${it.category}|${it.amount}" }.toSet()
                backup.database.transactionSplits.forEach { split ->
                    val mappedTxId = oldToNewTransactionIdMap[split.transactionId] ?: split.transactionId
                    val key = "${mappedTxId}|${split.category}|${split.amount}"
                    if (!existingSplitKeys.contains(key)) {
                        database.transactionSplitDao().insertSplit(
                            split.copy(id = 0, transactionId = mappedTxId)
                        )
                    }
                }

                backup.database.bankNotifications.forEach { notification ->
                    database.bankNotificationDao().insertOrReplace(notification)
                }
                backup.database.salaryMonthOverrides.forEach { override ->
                    database.salaryMonthOverrideDao().upsert(override)
                }

                // Goals (merge by name)
                importGoalsWithMerge(backup.database.goals, backup.database.goalContributions)

        }

        // Preferences are written after the DB transaction commits successfully.
        // If withTransaction threw, this line is never reached.
        importPreferences(backup.preferences)

        return ImportResult.Success(
            importedTransactions = importedTransactions,
            importedCategories = importedCategories,
            skippedDuplicates = skippedDuplicates,
            latestTransactionTimestamp = latestTransactionTimestampMillis(backup.database.transactions)
        )
    }

    // ── SELECTIVE RESTORE ─────────────────────────────────────────────────────

    /**
     * Selective restore — only imports data categories specified in [options].
     * Uses merge semantics for each selected category.
     */
    private suspend fun selectiveRestore(backup: PennyWiseBackup, options: RestoreOptions): ImportResult {
        val f = options.toFlags() // atomic flags from user-friendly grouped options
        var importedTransactions = 0
        var importedCategories = 0
        var skippedDuplicates = 0

        database.withTransaction {
            // Collect existing state (only if we need it)
            val existingTransactionHashes = if (f.transactions) {
                database.transactionDao().getAllTransactions().first()
                    .map { it.transactionHash }.toSet()
            } else emptySet()
            val softDeletedHashes = if (f.transactions) {
                database.transactionDao().getSoftDeletedHashes().toSet()
            } else emptySet()
            val existingCategories = if (f.categories) {
                database.categoryDao().getAllCategories().first()
                    .map { it.name }.toSet()
            } else emptySet()

            // FK dependency maps
            val oldToNewLoanIdMap = if (f.transactions) {
                importLoansWithMerge(if (f.loans) backup.database.loans else emptyList())
            } else if (f.loans) {
                importLoansWithMerge(backup.database.loans)
            } else emptyMap()

            val oldToNewGroupIdMap = if (f.transactions) {
                importGroupsWithMerge(if (f.transactionGroups) backup.database.transactionGroups else emptyList())
            } else if (f.transactionGroups) {
                importGroupsWithMerge(backup.database.transactionGroups)
            } else emptyMap()

            if (f.profiles) {
                importProfilesWithMerge(backup.database.profiles)
            }

            // Categories
            if (f.categories) {
                backup.database.categories.forEach { category ->
                    if (!existingCategories.contains(category.name)) {
                        database.categoryDao().insertCategory(category.copy(id = 0))
                        importedCategories++
                    }
                }
            }

            // Transactions
            val oldToNewTransactionIdMap = mutableMapOf<Long, Long>()
            if (f.transactions) {
                backup.database.transactions.forEach { transaction ->
                    val hash = transaction.transactionHash
                    if (softDeletedHashes.contains(hash)) {
                        skippedDuplicates++
                        return@forEach
                    }
                    if (!existingTransactionHashes.contains(hash)) {
                        val oldId = transaction.id
                        val remapped = transaction.copy(
                            id = 0,
                            loanId  = transaction.loanId?.let  { oldToNewLoanIdMap[it]  ?: it },
                            groupId = transaction.groupId?.let { oldToNewGroupIdMap[it] ?: it }
                        )
                        val newId = database.transactionDao().insertTransaction(remapped)
                        if (oldId != 0L) oldToNewTransactionIdMap[oldId] = newId
                        importedTransactions++
                    } else {
                        skippedDuplicates++
                    }
                }

                // Receipts
                importReceiptsWithMerge(backup.database.transactionReceipts, oldToNewTransactionIdMap)

                // Transaction splits
                val existingSplits = database.transactionSplitDao().getAllSplits().first()
                val existingSplitKeys = existingSplits
                    .map { "${it.transactionId}|${it.category}|${it.amount}" }.toSet()
                backup.database.transactionSplits.forEach { split ->
                    val mappedTxId = oldToNewTransactionIdMap[split.transactionId] ?: split.transactionId
                    val key = "${mappedTxId}|${split.category}|${split.amount}"
                    if (!existingSplitKeys.contains(key)) {
                        database.transactionSplitDao().insertSplit(
                            split.copy(id = 0, transactionId = mappedTxId)
                        )
                    }
                }
            }

            // Other entities (conditionally imported via merge helpers)
            if (f.cards) {
                importCardsWithMerge(backup.database.cards)
                importAccountBalancesWithMerge(backup.database.accountBalances)
            }
            if (f.subscriptions) {
                importSubscriptionsWithMerge(backup.database.subscriptions)
            }
            if (f.merchantMappings) {
                importMerchantMappingsWithMerge(backup.database.merchantMappings)
                importMerchantAliasesWithMerge(backup.database.merchantAliases)
            }
            if (f.rules) {
                importRulesWithMerge(backup.database.rules)
                // Rule applications
                val existingRuleAppIds = database.ruleApplicationDao()
                    .getAllApplications().first().map { it.id }.toSet()
                backup.database.ruleApplications.forEach { application ->
                    if (!existingRuleAppIds.contains(application.id)) {
                        val mappedTxId = application.transactionId.toLongOrNull()?.let { oldId ->
                            oldToNewTransactionIdMap[oldId]?.toString() ?: application.transactionId
                        } ?: application.transactionId
                        database.ruleApplicationDao().insertApplication(
                            application.copy(transactionId = mappedTxId)
                        )
                    }
                }
            }
            if (f.exchangeRates) {
                val existingRates = database.exchangeRateDao().getAllRatesFlow().first()
                backup.database.exchangeRates.forEach { rate ->
                    val exists = existingRates.any {
                        it.fromCurrency == rate.fromCurrency && it.toCurrency == rate.toCurrency
                    }
                    if (!exists) database.exchangeRateDao().insertExchangeRate(rate)
                }
            }
            if (f.budgets) {
                importBudgetsWithMerge(backup.database.budgets, backup.database.budgetCategories)
            }
            if (f.bankNotifications) {
                backup.database.bankNotifications.forEach { notification ->
                    database.bankNotificationDao().insertOrReplace(notification)
                }
            }
            if (f.salaryOverrides) {
                backup.database.salaryMonthOverrides.forEach { override ->
                    database.salaryMonthOverrideDao().upsert(override)
                }
            }
            if (f.chatMessages) {
                backup.database.chatMessages.forEach { message ->
                    database.chatDao().insertMessage(message)
                }
            }

            // Goals (conditionally imported via merge)
            if (f.goals) {
                importGoalsWithMerge(backup.database.goals, backup.database.goalContributions)
            }
        }

        // Preferences are written after the DB transaction commits successfully.
        if (f.preferences) {
            importPreferences(backup.preferences)
        }

        return ImportResult.Success(
            importedTransactions = importedTransactions,
            importedCategories = importedCategories,
            skippedDuplicates = skippedDuplicates,
            latestTransactionTimestamp = if (importedTransactions > 0)
                latestTransactionTimestampMillis(backup.database.transactions) else null
        )
    }

    /**
     * Import goals with merge semantics.
     * Merges by goal name — if a goal with the same name exists locally it is skipped,
     * otherwise inserted together with its contributions.
     */
    private suspend fun importGoalsWithMerge(
        goals: List<GoalEntity>,
        contributions: List<GoalContributionEntity>
    ) {
        val existingNames = database.goalDao().getAllGoals().first()
            .map { it.name.lowercase() }.toSet()
        goals.forEach { goal ->
            if (!existingNames.contains(goal.name.lowercase())) {
                val newGoalId = database.goalDao().insertGoal(goal.copy(id = 0))
                contributions.filter { it.goalId == goal.id }.forEach { contribution ->
                    database.goalContributionDao().insertContribution(
                        contribution.copy(id = 0, goalId = newGoalId)
                    )
                }
            }
        }
    }

    // ── Merge helpers ─────────────────────────────────────────────────────────

    private suspend fun importCardsWithMerge(cards: List<CardEntity>) {
        val existing = database.cardDao().getAllCards().first()
            .map { "${it.bankName}_${it.cardLast4}" }.toSet()
        cards.forEach { card ->
            if (!existing.contains("${card.bankName}_${card.cardLast4}")) {
                database.cardDao().insertCard(card.copy(id = 0))
            }
        }
    }

    private suspend fun importAccountBalancesWithMerge(balances: List<AccountBalanceEntity>) {
        balances.forEach { balance ->
            database.accountBalanceDao().insertBalance(balance.copy(id = 0))
        }
    }

    private suspend fun importSubscriptionsWithMerge(subscriptions: List<SubscriptionEntity>) {
        val existing = database.subscriptionDao().getAllSubscriptions().first()
            .map { "${it.merchantName}_${it.amount}" }.toSet()
        subscriptions.forEach { subscription ->
            if (!existing.contains("${subscription.merchantName}_${subscription.amount}")) {
                database.subscriptionDao().insertSubscription(subscription.copy(id = 0))
            }
        }
    }

    private suspend fun importMerchantMappingsWithMerge(mappings: List<MerchantMappingEntity>) {
        mappings.forEach { mapping ->
            database.merchantMappingDao().insertMapping(mapping)
        }
    }

    private suspend fun importMerchantAliasesWithMerge(aliases: List<MerchantAliasEntity>) {
        aliases.forEach { alias ->
            database.merchantAliasDao().insertAlias(alias)
        }
    }

    private suspend fun importRulesWithMerge(rules: List<RuleEntity>) {
        val existingIds = database.ruleDao().getAllRules().first().map { it.id }.toSet()
        rules.forEach { rule ->
            if (!existingIds.contains(rule.id)) {
                database.ruleDao().insertRule(rule)
            }
        }
    }

    private suspend fun importBudgetsWithMerge(
        budgets: List<BudgetEntity>,
        budgetCategories: List<BudgetCategoryEntity>
    ) {
        val existingNames = database.budgetDao().getAllBudgets().first().map { it.name }.toSet()
        budgets.forEach { budget ->
            if (!existingNames.contains(budget.name)) {
                val newBudgetId = database.budgetDao().insertBudget(budget.copy(id = 0))
                budgetCategories.filter { it.budgetId == budget.id }.forEach { category ->
                    database.budgetDao().insertBudgetCategory(
                        category.copy(id = 0, budgetId = newBudgetId)
                    )
                }
            }
        }
    }

    /**
     * Import loans with merge semantics.
     * Returns old-loan-ID → new-loan-ID map for transaction FK remapping.
     */
    private suspend fun importLoansWithMerge(loans: List<LoanEntity>): Map<Long, Long> {
        val oldToNew = mutableMapOf<Long, Long>()
        val existing = database.loanDao().getAllLoans().first()
            .associateBy { "${it.personName.lowercase()}_${it.direction}" }
        loans.forEach { loan ->
            val key = "${loan.personName.lowercase()}_${loan.direction}"
            val local = existing[key]
            if (local != null) {
                oldToNew[loan.id] = local.id
            } else {
                val newId = database.loanDao().insertLoan(loan.copy(id = 0))
                oldToNew[loan.id] = newId
            }
        }
        return oldToNew
    }

    /**
     * Import transaction groups with merge semantics.
     * Returns old-group-ID → new-group-ID map for transaction FK remapping.
     */
    private suspend fun importGroupsWithMerge(groups: List<TransactionGroupEntity>): Map<Long, Long> {
        val oldToNew = mutableMapOf<Long, Long>()
        val existing = database.transactionGroupDao().getAllGroups().first()
            .associateBy { it.name.lowercase() }
        groups.forEach { group ->
            val local = existing[group.name.lowercase()]
            if (local != null) {
                oldToNew[group.id] = local.id
            } else {
                val newId = database.transactionGroupDao().insertGroup(group.copy(id = 0))
                oldToNew[group.id] = newId
            }
        }
        return oldToNew
    }

    /**
     * Import profiles with merge semantics.
     * Uses REPLACE conflict strategy so system profiles (Personal/Business)
     * are updated with any name/colour changes from the backup, and custom
     * profiles are re-created with their original IDs so account-balance
     * profile references remain valid.
     */
    private suspend fun importProfilesWithMerge(profiles: List<ProfileEntity>) {
        profiles.forEach { profile ->
            database.profileDao().insert(profile)  // OnConflictStrategy.REPLACE
        }
    }

    /**
     * Import transaction receipts remapping transaction IDs.
     */
    private suspend fun importReceiptsWithMerge(
        receipts: List<TransactionReceiptEntity>,
        oldToNewTransactionIdMap: Map<Long, Long>
    ) {
        receipts.forEach { receipt ->
            val newTxId = oldToNewTransactionIdMap[receipt.transactionId] ?: receipt.transactionId
            database.transactionReceiptDao().insertReceipt(
                receipt.copy(id = 0, transactionId = newTxId)
            )
        }
    }

    // ── Preferences ───────────────────────────────────────────────────────────

    private suspend fun importPreferences(preferences: PreferencesSnapshot) {
        // Theme
        preferences.theme.isDarkThemeEnabled?.let {
            userPreferencesRepository.updateDarkTheme(it)
        }
        userPreferencesRepository.updateDynamicColor(preferences.theme.isDynamicColorEnabled)
        preferences.theme.themeStyle?.let { name ->
            runCatching { com.pennywiseai.tracker.data.preferences.ThemeStyle.valueOf(name) }
                .getOrNull()?.let { userPreferencesRepository.updateThemeStyle(it) }
        }
        preferences.theme.accentColor?.let { name ->
            runCatching { com.pennywiseai.tracker.data.preferences.AccentColor.valueOf(name) }
                .getOrNull()?.let { userPreferencesRepository.updateAccentColor(it) }
        }
        userPreferencesRepository.updateAmoledMode(preferences.theme.isAmoledMode)
        preferences.theme.appFont?.let { name ->
            runCatching { com.pennywiseai.tracker.data.preferences.AppFont.valueOf(name) }
                .getOrNull()?.let { userPreferencesRepository.updateAppFont(it) }
        }
        userPreferencesRepository.updateBlurEffectsEnabled(preferences.theme.blurEffectsEnabled)
        preferences.theme.navBarStyle?.let { name ->
            runCatching { com.pennywiseai.tracker.data.preferences.NavBarStyle.valueOf(name) }
                .getOrNull()?.let { userPreferencesRepository.updateNavBarStyle(it) }
        }
        preferences.theme.analyticsChartType?.let {
            userPreferencesRepository.saveAnalyticsChartType(it)
        }
        userPreferencesRepository.setCompactAnalyticsCardsEnabled(preferences.theme.compactAnalyticsCards)
        preferences.theme.coverStyle?.let { name ->
            runCatching { com.pennywiseai.tracker.data.preferences.CoverStyle.valueOf(name) }
                .getOrNull()?.let { userPreferencesRepository.updateCoverStyle(it) }
        }

        // SMS
        userPreferencesRepository.updateHasSkippedSmsPermission(preferences.sms.hasSkippedSmsPermission)
        userPreferencesRepository.updateSmsScanMonths(preferences.sms.smsScanMonths)
        userPreferencesRepository.updateSmsScanAllTime(preferences.sms.smsScanAllTime)
        preferences.sms.lastScanTimestamp?.let {
            userPreferencesRepository.updateLastScanTimestamp(it)
        }
        preferences.sms.lastScanPeriod?.let {
            userPreferencesRepository.updateLastScanPeriod(it)
        }

        // Developer
        userPreferencesRepository.updateDeveloperMode(preferences.developer.isDeveloperModeEnabled)
        preferences.developer.systemPrompt?.let {
            userPreferencesRepository.updateSystemPrompt(it)
        }

        // App
        userPreferencesRepository.updateHasShownScanTutorial(preferences.app.hasShownScanTutorial)
        preferences.app.firstLaunchTime?.let {
            userPreferencesRepository.updateFirstLaunchTime(it)
        }
        userPreferencesRepository.updateHasShownReviewPrompt(preferences.app.hasShownReviewPrompt)
        preferences.app.lastReviewPromptTime?.let {
            userPreferencesRepository.updateLastReviewPromptTime(it)
        }
        preferences.app.baseCurrency?.let { userPreferencesRepository.updateBaseCurrency(it) }
        userPreferencesRepository.setUnifiedCurrencyMode(preferences.app.unifiedCurrencyMode)
        preferences.app.displayCurrency?.let { userPreferencesRepository.setDisplayCurrency(it) }
        preferences.app.monthlyBudgetLimit?.let { limitStr ->
            runCatching { java.math.BigDecimal(limitStr) }.getOrNull()?.let {
                userPreferencesRepository.updateMonthlyBudgetLimit(it)
            }
        }
        userPreferencesRepository.setBalanceHidden(preferences.app.balanceHidden)
        preferences.app.userName?.let { userPreferencesRepository.updateUserName(it) }
        userPreferencesRepository.updateProfileImageUri(preferences.app.profileImageUri)
        userPreferencesRepository.updateProfileBackgroundColor(preferences.app.profileBackgroundColor)
        userPreferencesRepository.updateHasCompletedOnboarding(preferences.app.hasCompletedOnboarding)
        userPreferencesRepository.updateMainAccountKey(preferences.app.mainAccountKey)
        userPreferencesRepository.updateMonthStartDay(preferences.app.monthStartDay)
        userPreferencesRepository.updateUseFinancialMonth(preferences.app.useFinancialMonth)
        userPreferencesRepository.setUseFixedBudgetPeriodEnd(preferences.app.useFixedBudgetPeriodEnd)
        userPreferencesRepository.updateBudgetPeriodEndDay(preferences.app.budgetPeriodEndDay)
        preferences.app.dismissedSalarySuggestions?.let { raw ->
            val tokens = raw.split('|').filter { it.isNotBlank() }.toSet()
            if (tokens.isNotEmpty()) {
                userPreferencesRepository.setDismissedSalarySuggestions(tokens)
            }
        }
        preferences.app.selectedProfileId?.let {
            userPreferencesRepository.updateSelectedProfileId(it)
        }
        userPreferencesRepository.updateInsightsDataWindowMonths(preferences.app.insightsDataWindowMonths)

        // Security
        userPreferencesRepository.setAppLockEnabled(preferences.security.appLockEnabled)
        userPreferencesRepository.setAppLockTimeoutMinutes(preferences.security.appLockTimeoutMinutes)
    }
}

