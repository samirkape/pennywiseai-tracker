package com.spendly.tracker.di

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.spendly.tracker.data.database.SpendlyDatabase
import com.spendly.tracker.data.database.dao.AccountBalanceDao
import com.spendly.tracker.data.database.dao.SalaryMonthOverrideDao
import com.spendly.tracker.data.database.dao.ProfileDao
import com.spendly.tracker.data.database.dao.BankNotificationDao
import com.spendly.tracker.data.database.dao.BudgetDao
import com.spendly.tracker.data.database.dao.BudgetSnapshotDao
import com.spendly.tracker.data.database.dao.CardDao
import com.spendly.tracker.data.database.dao.CategoryDao
import com.spendly.tracker.data.database.dao.ChatDao
import com.spendly.tracker.data.database.dao.ExchangeRateDao
import com.spendly.tracker.data.database.dao.LoanDao
import com.spendly.tracker.data.database.dao.TransactionGroupDao
import com.spendly.tracker.data.database.dao.MerchantAliasDao
import com.spendly.tracker.data.database.dao.MerchantMappingDao
import com.spendly.tracker.data.database.dao.RuleApplicationDao
import com.spendly.tracker.data.database.dao.RuleDao
import com.spendly.tracker.data.database.dao.SubscriptionDao
import com.spendly.tracker.data.database.dao.TransactionDao
import com.spendly.tracker.data.database.dao.TransactionReceiptDao
import com.spendly.tracker.data.database.dao.TransactionSplitDao
import com.spendly.tracker.data.database.dao.UnrecognizedSmsDao
import com.spendly.tracker.data.database.dao.GoalContributionDao
import com.spendly.tracker.data.database.dao.GoalDao
import com.spendly.tracker.data.database.dao.InsightsCacheDao
import com.spendly.tracker.data.database.dao.PrepaidAllocationDao
import com.spendly.tracker.data.database.dao.PrepaidExpenseDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Singleton

/**
 * Hilt module that provides database-related dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private const val LEGACY_DATABASE_NAME = "pennywise_database"

    /**
     * Provides the singleton instance of SpendlyDatabase.
     * 
     * @param context Application context
     * @return Configured Room database instance
     */
    @Provides
    @Singleton
    fun provideSpendlyDatabase(
        @ApplicationContext context: Context
    ): SpendlyDatabase {
        recoverLegacyDatabaseIfNeeded(context)

        val database = Room.databaseBuilder(
            context,
            SpendlyDatabase::class.java,
            SpendlyDatabase.DATABASE_NAME
        )
            // Add manual migrations here when needed
            .addMigrations(
                SpendlyDatabase.MIGRATION_12_14,
                SpendlyDatabase.MIGRATION_13_14,
                SpendlyDatabase.MIGRATION_14_15,
                SpendlyDatabase.MIGRATION_20_21,
                SpendlyDatabase.MIGRATION_21_22,
                SpendlyDatabase.MIGRATION_22_23,
                SpendlyDatabase.MIGRATION_38_39,
                SpendlyDatabase.MIGRATION_44_45,
                SpendlyDatabase.MIGRATION_45_46,
                SpendlyDatabase.MIGRATION_46_47,
                SpendlyDatabase.MIGRATION_47_48,
                SpendlyDatabase.MIGRATION_48_49,
                SpendlyDatabase.MIGRATION_49_50,
                SpendlyDatabase.MIGRATION_50_51,
                SpendlyDatabase.MIGRATION_51_52,
                SpendlyDatabase.MIGRATION_52_53,
                SpendlyDatabase.MIGRATION_53_54,
                SpendlyDatabase.MIGRATION_54_55,
                SpendlyDatabase.MIGRATION_55_56,
                SpendlyDatabase.MIGRATION_56_57,
                SpendlyDatabase.MIGRATION_58_59,
                SpendlyDatabase.MIGRATION_59_60,
                SpendlyDatabase.MIGRATION_60_61
            )
            .fallbackToDestructiveMigrationOnDowngrade()

            // Enable auto-migrations
            // Room will automatically detect schema changes between versions

            // Add callback to seed default data on first creation
            .addCallback(DatabaseCallback())

            .build()

        // Set the singleton instance so BroadcastReceivers can access it
        SpendlyDatabase.setInstance(database)

        return database
    }

    /**
     * Restores legacy data if a user upgraded from a build that used the old DB filename.
     * We only replace the current file when it is missing or appears empty.
     */
    private fun recoverLegacyDatabaseIfNeeded(context: Context) {
        val currentDb = context.getDatabasePath(SpendlyDatabase.DATABASE_NAME)
        val legacyDb = context.getDatabasePath(LEGACY_DATABASE_NAME)

        if (!legacyDb.exists()) return

        val legacyRows = readTransactionCount(legacyDb)
        if (legacyRows <= 0L) return

        val currentRows = if (currentDb.exists()) readTransactionCount(currentDb) else -1L
        val shouldRecover = !currentDb.exists() || currentRows == 0L
        if (!shouldRecover) return

        if (currentDb.exists()) {
            val backup = File(currentDb.parentFile, "${SpendlyDatabase.DATABASE_NAME}.pre_recovery.bak")
            currentDb.copyTo(backup, overwrite = true)
        }

        copyDatabaseFamily(legacyDb, currentDb)
    }

    private fun copyDatabaseFamily(fromMain: File, toMain: File) {
        copyOne(fromMain, toMain)
        copyOne(File(fromMain.absolutePath + "-wal"), File(toMain.absolutePath + "-wal"))
        copyOne(File(fromMain.absolutePath + "-shm"), File(toMain.absolutePath + "-shm"))
    }

    private fun copyOne(from: File, to: File) {
        if (!from.exists()) return
        to.parentFile?.mkdirs()
        from.copyTo(to, overwrite = true)
    }

    private fun readTransactionCount(dbFile: File): Long {
        if (!dbFile.exists()) return -1L
        return runCatching {
            SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("SELECT COUNT(*) FROM transactions", null).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getLong(0) else -1L
                }
            }
        }.getOrDefault(-1L)
    }

    /**
     * Provides the TransactionDao from the database.
     * 
     * @param database The SpendlyDatabase instance
     * @return TransactionDao for accessing transaction data
     */
    @Provides
    @Singleton
    fun provideTransactionDao(database: SpendlyDatabase): TransactionDao {
        return database.transactionDao()
    }
    
    /**
     * Provides the SubscriptionDao from the database.
     * 
     * @param database The SpendlyDatabase instance
     * @return SubscriptionDao for accessing subscription data
     */
    @Provides
    @Singleton
    fun provideSubscriptionDao(database: SpendlyDatabase): SubscriptionDao {
        return database.subscriptionDao()
    }
    
    /**
     * Provides the ChatDao from the database.
     * 
     * @param database The SpendlyDatabase instance
     * @return ChatDao for accessing chat message data
     */
    @Provides
    @Singleton
    fun provideChatDao(database: SpendlyDatabase): ChatDao {
        return database.chatDao()
    }
    
    /**
     * Provides the MerchantMappingDao from the database.
     * 
     * @param database The SpendlyDatabase instance
     * @return MerchantMappingDao for accessing merchant mapping data
     */
    @Provides
    @Singleton
    fun provideMerchantMappingDao(database: SpendlyDatabase): MerchantMappingDao {
        return database.merchantMappingDao()
    }

    @Provides
    @Singleton
    fun provideMerchantAliasDao(database: SpendlyDatabase): MerchantAliasDao {
        return database.merchantAliasDao()
    }
    
    /**
     * Provides the CategoryDao from the database.
     * 
     * @param database The SpendlyDatabase instance
     * @return CategoryDao for accessing category data
     */
    @Provides
    @Singleton
    fun provideCategoryDao(database: SpendlyDatabase): CategoryDao {
        return database.categoryDao()
    }
    
    /**
     * Provides the AccountBalanceDao from the database.
     * 
     * @param database The SpendlyDatabase instance
     * @return AccountBalanceDao for accessing account balance data
     */
    @Provides
    @Singleton
    fun provideAccountBalanceDao(database: SpendlyDatabase): AccountBalanceDao {
        return database.accountBalanceDao()
    }
    
    /**
     * Provides the UnrecognizedSmsDao from the database.
     * 
     * @param database The SpendlyDatabase instance
     * @return UnrecognizedSmsDao for accessing unrecognized SMS data
     */
    @Provides
    @Singleton
    fun provideUnrecognizedSmsDao(database: SpendlyDatabase): UnrecognizedSmsDao {
        return database.unrecognizedSmsDao()
    }
    
    /**
     * Provides the CardDao from the database.
     *
     * @param database The SpendlyDatabase instance
     * @return CardDao for accessing card data
     */
    @Provides
    @Singleton
    fun provideCardDao(database: SpendlyDatabase): CardDao {
        return database.cardDao()
    }

    /**
     * Provides the RuleDao from the database.
     *
     * @param database The SpendlyDatabase instance
     * @return RuleDao for accessing rule data
     */
    @Provides
    @Singleton
    fun provideRuleDao(database: SpendlyDatabase): RuleDao {
        return database.ruleDao()
    }

    /**
     * Provides the RuleApplicationDao from the database.
     *
     * @param database The SpendlyDatabase instance
     * @return RuleApplicationDao for accessing rule application data
     */
    @Provides
    @Singleton
    fun provideRuleApplicationDao(database: SpendlyDatabase): RuleApplicationDao {
        return database.ruleApplicationDao()
    }

    /**
     * Provides the ExchangeRateDao from the database.
     *
     * @param database The SpendlyDatabase instance
     * @return ExchangeRateDao for accessing exchange rate data
     */
    @Provides
    @Singleton
    fun provideExchangeRateDao(database: SpendlyDatabase): ExchangeRateDao {
        return database.exchangeRateDao()
    }

    /**
     * Provides the BudgetDao from the database.
     *
     * @param database The SpendlyDatabase instance
     * @return BudgetDao for accessing budget data
     */
    @Provides
    @Singleton
    fun provideBudgetDao(database: SpendlyDatabase): BudgetDao {
        return database.budgetDao()
    }

    /**
     * Provides the TransactionSplitDao from the database.
     *
     * @param database The SpendlyDatabase instance
     * @return TransactionSplitDao for accessing transaction split data
     */
    @Provides
    @Singleton
    fun provideTransactionSplitDao(database: SpendlyDatabase): TransactionSplitDao {
        return database.transactionSplitDao()
    }

    @Provides
    @Singleton
    fun provideBankNotificationDao(database: SpendlyDatabase): BankNotificationDao {
        return database.bankNotificationDao()
    }

    @Provides
    @Singleton
    fun provideLoanDao(database: SpendlyDatabase): LoanDao {
        return database.loanDao()
    }

    @Provides
    @Singleton
    fun provideTransactionGroupDao(database: SpendlyDatabase): TransactionGroupDao {
        return database.transactionGroupDao()
    }

    @Provides
    @Singleton
    fun provideBudgetSnapshotDao(database: SpendlyDatabase): BudgetSnapshotDao {
        return database.budgetSnapshotDao()
    }

    @Provides
    @Singleton
    fun provideProfileDao(database: SpendlyDatabase): ProfileDao {
        return database.profileDao()
    }

    @Provides
    @Singleton
    fun provideSalaryMonthOverrideDao(database: SpendlyDatabase): SalaryMonthOverrideDao {
        return database.salaryMonthOverrideDao()
    }

    @Provides
    @Singleton
    fun provideTransactionReceiptDao(database: SpendlyDatabase): TransactionReceiptDao {
        return database.transactionReceiptDao()
    }

    @Provides
    @Singleton
    fun provideInsightsCacheDao(database: SpendlyDatabase): InsightsCacheDao {
        return database.insightsCacheDao()
    }

    @Provides
    @Singleton
    fun provideGoalDao(database: SpendlyDatabase): GoalDao {
        return database.goalDao()
    }

    @Provides
    @Singleton
    fun provideGoalContributionDao(database: SpendlyDatabase): GoalContributionDao {
        return database.goalContributionDao()
    }

    @Provides
    @Singleton
    fun providePrepaidExpenseDao(database: SpendlyDatabase): PrepaidExpenseDao {
        return database.prepaidExpenseDao()
    }

    @Provides
    @Singleton
    fun providePrepaidAllocationDao(database: SpendlyDatabase): PrepaidAllocationDao {
        return database.prepaidAllocationDao()
    }
}

/**
 * Database callback to seed initial data when database is first created
 */
class DatabaseCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        
        // Seed default categories for new installations
        CoroutineScope(Dispatchers.IO).launch {
            seedCategories(db)
            seedProfiles(db)
        }
    }
    
    private fun seedCategories(db: SupportSQLiteDatabase) {
        val categories = listOf(
            Triple("Food & Dining", "#FC8019", false),
            Triple("Groceries", "#5AC85A", false),
            Triple("Transportation", "#000000", false),
            Triple("Shopping", "#FF9900", false),
            Triple("Bills & Utilities", "#4CAF50", false),
            Triple("Entertainment", "#E50914", false),
            Triple("Healthcare", "#10847E", false),
            Triple("Investments", "#00D09C", false),
            Triple("Banking", "#004C8F", false),
            Triple("Personal Care", "#6A4C93", false),
            Triple("Education", "#673AB7", false),
            Triple("Mobile", "#2A3890", false),
            Triple("Fitness", "#FF3278", false),
            Triple("Insurance", "#0066CC", false),
            Triple("Travel", "#00BCD4", false),
            Triple("Salary", "#4CAF50", true),
            Triple("Income", "#4CAF50", true),
            Triple("Others", "#757575", false)
        )
        
        categories.forEachIndexed { index, (name, color, isIncome) ->
            db.execSQL("""
                INSERT OR IGNORE INTO categories (name, color, is_system, is_income, display_order, created_at, updated_at)
                VALUES (?, ?, 1, ?, ?, datetime('now'), datetime('now'))
            """.trimIndent(), arrayOf<Any>(name, color, if (isIncome) 1 else 0, index + 1))
        }
    }

    private fun seedProfiles(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT OR IGNORE INTO profiles (id, name, color_hex, sort_order) VALUES (1, 'Personal', '#4CAF50', 0)")
        db.execSQL("INSERT OR IGNORE INTO profiles (id, name, color_hex, sort_order) VALUES (2, 'Business', '#2196F3', 1)")
    }
}
