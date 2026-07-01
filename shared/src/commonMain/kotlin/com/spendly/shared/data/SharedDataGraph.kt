package com.spendly.shared.data

import com.spendly.shared.data.bootstrap.SharedDataInitializer
import com.spendly.shared.data.local.SharedDatabase
import com.spendly.shared.data.local.SharedDatabaseFactory
import com.spendly.shared.data.repository.RoomSharedAccountRepository
import com.spendly.shared.data.repository.RoomSharedBudgetRepository
import com.spendly.shared.data.repository.RoomSharedCategoryRepository
import com.spendly.shared.data.repository.RoomSharedExchangeRateRepository
import com.spendly.shared.data.repository.RoomSharedMerchantMappingRepository
import com.spendly.shared.data.repository.RoomSharedRuleRepository
import com.spendly.shared.data.repository.RoomSharedSplitRepository
import com.spendly.shared.data.repository.RoomSharedSubscriptionRepository
import com.spendly.shared.data.repository.RoomSharedTransactionRepository
import com.spendly.shared.data.repository.RoomSharedUnrecognizedSmsRepository
import com.spendly.shared.data.repository.SharedAccountRepository
import com.spendly.shared.data.repository.SharedBudgetRepository
import com.spendly.shared.data.repository.SharedCategoryRepository
import com.spendly.shared.data.repository.SharedExchangeRateRepository
import com.spendly.shared.data.repository.SharedMerchantMappingRepository
import com.spendly.shared.data.repository.SharedRuleRepository
import com.spendly.shared.data.repository.SharedSplitRepository
import com.spendly.shared.data.repository.SharedSubscriptionRepository
import com.spendly.shared.data.repository.SharedTransactionRepository
import com.spendly.shared.data.repository.SharedUnrecognizedSmsRepository

class SharedDataGraph private constructor(
    val database: SharedDatabase,
    val transactionRepository: SharedTransactionRepository,
    val categoryRepository: SharedCategoryRepository,
    val subscriptionRepository: SharedSubscriptionRepository,
    val accountRepository: SharedAccountRepository,
    val splitRepository: SharedSplitRepository,
    val merchantMappingRepository: SharedMerchantMappingRepository,
    val ruleRepository: SharedRuleRepository,
    val exchangeRateRepository: SharedExchangeRateRepository,
    val budgetRepository: SharedBudgetRepository,
    val unrecognizedSmsRepository: SharedUnrecognizedSmsRepository
) {
    private val initializer = SharedDataInitializer(categoryRepository)

    suspend fun initialize() {
        initializer.seedDefaultCategoriesIfNeeded()
    }

    companion object {
        private val _instance: SharedDataGraph by lazy { create() }

        fun getInstance(): SharedDataGraph = _instance

        fun create(factory: SharedDatabaseFactory = SharedDatabaseFactory()): SharedDataGraph {
            val database = factory.createDatabase()
            return SharedDataGraph(
                database = database,
                transactionRepository = RoomSharedTransactionRepository(database.transactionDao()),
                categoryRepository = RoomSharedCategoryRepository(database.categoryDao()),
                subscriptionRepository = RoomSharedSubscriptionRepository(database.subscriptionDao()),
                accountRepository = RoomSharedAccountRepository(database.accountBalanceDao(), database.cardDao(), database.transactionDao()),
                splitRepository = RoomSharedSplitRepository(database.transactionSplitDao()),
                merchantMappingRepository = RoomSharedMerchantMappingRepository(database.merchantMappingDao()),
                ruleRepository = RoomSharedRuleRepository(database.ruleDao(), database.ruleApplicationDao()),
                exchangeRateRepository = RoomSharedExchangeRateRepository(database.exchangeRateDao()),
                budgetRepository = RoomSharedBudgetRepository(database.budgetDao(), database.categoryBudgetLimitDao()),
                unrecognizedSmsRepository = RoomSharedUnrecognizedSmsRepository(database.unrecognizedSmsDao())
            )
        }
    }
}
