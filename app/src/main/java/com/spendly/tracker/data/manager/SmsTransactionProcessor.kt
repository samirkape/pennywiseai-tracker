package com.spendly.tracker.data.manager

import android.content.Context
import android.util.Log
import com.spendly.parser.core.ParsedTransaction
import com.spendly.parser.core.bank.BankParserFactory
import com.spendly.tracker.data.database.entity.AccountBalanceEntity
import com.spendly.tracker.data.database.entity.CardType
import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.database.entity.TransactionType
import com.spendly.tracker.data.mapper.toEntity
import com.spendly.tracker.data.mapper.toEntityType
import com.spendly.tracker.data.repository.AccountBalanceRepository
import com.spendly.tracker.data.repository.CardRepository
import com.spendly.tracker.data.repository.MerchantAliasRepository
import com.spendly.tracker.data.repository.MerchantMappingRepository
import com.spendly.tracker.data.repository.SubscriptionRepository
import com.spendly.tracker.data.repository.TransactionRepository
import com.spendly.tracker.domain.repository.RuleRepository
import com.spendly.tracker.domain.service.RuleEngine
import com.spendly.tracker.domain.service.QuickKeywordRuleMatcher
import com.spendly.tracker.domain.service.SelfTransferDetector
import com.spendly.tracker.domain.usecase.CreditCardPaymentLinker
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared processor for SMS transactions. Used by both SmsBroadcastReceiver
 * and OptimizedSmsReaderWorker to ensure consistent transaction processing.
 */
@Singleton
class SmsTransactionProcessor @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val transactionRepository: TransactionRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val cardRepository: CardRepository,
    private val merchantMappingRepository: MerchantMappingRepository,
    private val merchantAliasRepository: MerchantAliasRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val ruleRepository: RuleRepository,
    private val ruleEngine: RuleEngine,
    private val creditCardPaymentLinker: CreditCardPaymentLinker,
    private val selfTransferDetector: SelfTransferDetector
) {
    companion object {
        private const val TAG = "SmsTransactionProcessor"
    }

    /**
     * Result of processing an SMS message
     */
    data class ProcessingResult(
        val success: Boolean,
        val transactionId: Long? = null,
        val reason: String? = null
    )

    /**
     * Parses and saves a transaction from an SMS message.
     *
     * @param sender SMS sender address
     * @param body SMS body text
     * @param timestamp SMS timestamp in milliseconds
     * @return ProcessingResult indicating success/failure and transaction ID
     */
    suspend fun processAndSaveTransaction(
        sender: String,
        body: String,
        timestamp: Long
    ): ProcessingResult {
        try {
            // Get the appropriate parser for this sender
            val parser = BankParserFactory.getParser(sender, body)
            if (parser == null) {
                return ProcessingResult(false, reason = "No parser found for sender: $sender")
            }

            // Parse the SMS
            val parsedTransaction = parser.parse(body, sender, timestamp)
            if (parsedTransaction == null) {
                return ProcessingResult(false, reason = "Could not parse transaction from SMS")
            }

            Log.d(TAG, "Parsed transaction: ${parsedTransaction.amount} from ${parsedTransaction.bankName}")

            // Save the transaction
            return saveParsedTransaction(parsedTransaction, body)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing SMS", e)
            return ProcessingResult(false, reason = e.message)
        }
    }

    /**
     * Saves a parsed transaction to the database with all necessary processing:
     * - Duplicate detection
     * - Merchant mapping
     * - Rule application
     * - Subscription matching
     * - Balance updates
     */
    suspend fun saveParsedTransaction(
        parsedTransaction: ParsedTransaction,
        smsBody: String
    ): ProcessingResult {
        return try {
            // Convert to entity
            val entity = parsedTransaction.toEntity()

            // Check if this transaction was previously deleted by the user
            val existingTransaction = transactionRepository.getTransactionByHash(entity.transactionHash)
            if (existingTransaction != null) {
                if (existingTransaction.isDeleted) {
                    Log.d(TAG, "Skipping previously deleted transaction with hash: ${entity.transactionHash}")
                    return ProcessingResult(false, reason = "Transaction was previously deleted")
                }
                // Transaction already exists and not deleted - normal deduplication
                Log.d(TAG, "Transaction already exists: ${entity.transactionHash}")
                return ProcessingResult(false, reason = "Duplicate transaction")
            }

            // Resolve merchant alias then apply category mapping for future SMS
            val resolvedMerchant = merchantAliasRepository.resolveDisplayNameForIngest(entity.merchantName)
            val entityWithName = if (resolvedMerchant != entity.merchantName) {
                Log.d(TAG, "Resolved merchant alias: ${entity.merchantName} -> $resolvedMerchant")
                entity.copy(merchantName = resolvedMerchant)
            } else {
                entity
            }

            val customCategory = merchantMappingRepository.getCategoryForMerchant(entityWithName.merchantName)
            val entityWithMapping = if (customCategory != null) {
                Log.d(TAG, "Found custom category mapping: ${entityWithName.merchantName} -> $customCategory")
                entityWithName.copy(category = customCategory)
            } else {
                entityWithName
            }

            // Apply rule engine to the transaction
            val activeRules = ruleRepository.getActiveRulesByType(entityWithMapping.transactionType)

            // Check if this transaction should be blocked
            val blockingRule = ruleEngine.shouldBlockTransaction(
                entityWithMapping,
                smsBody,
                activeRules
            )

            if (blockingRule != null) {
                Log.d(TAG, "Transaction blocked by rule: ${blockingRule.name}")
                return ProcessingResult(false, reason = "Blocked by rule: ${blockingRule.name}")
            }

            val (entityWithRules, ruleApplications) = ruleEngine.evaluateRules(
                entityWithMapping,
                smsBody,
                activeRules
            )

            if (ruleApplications.isNotEmpty()) {
                Log.d(TAG, "Applied ${ruleApplications.size} rules to transaction")
                ruleApplications.forEach { application ->
                    Log.d(
                        QuickKeywordRuleMatcher.LOG_TAG,
                        "SMS ingest rule=${application.ruleName} merchant=${entityWithRules.merchantName} " +
                            "category=${entityWithRules.category}",
                    )
                }
            }

            // Check if this transaction matches an active subscription
            val matchedSubscription = subscriptionRepository.matchTransactionToSubscription(
                entityWithRules.merchantName,
                entityWithRules.amount
            )

            val finalEntity = if (matchedSubscription != null) {
                Log.d(TAG, "Transaction matched to active subscription: ${matchedSubscription.merchantName}")
                subscriptionRepository.updateNextPaymentDateAfterCharge(
                    matchedSubscription.id,
                    entityWithRules.dateTime.toLocalDate()
                )
                entityWithRules.copy(isRecurring = true)
            } else {
                entityWithRules
            }

            val rowId = transactionRepository.insertTransaction(finalEntity)
            if (rowId != -1L) {
                Log.d(TAG, "Saved new transaction with ID: $rowId${if (finalEntity.isRecurring) " (Recurring)" else ""}")

                // Save rule applications if any rules were applied
                if (ruleApplications.isNotEmpty()) {
                    ruleRepository.saveRuleApplications(ruleApplications)
                }

                // Process balance updates
                processBalanceUpdate(parsedTransaction, finalEntity, rowId)

                // Classify TRANSFER transactions as self-transfer, others-transfer, or pending review
                if (finalEntity.transactionType == com.spendly.tracker.data.database.entity.TransactionType.TRANSFER) {
                    runCatching {
                        val classification = selfTransferDetector.classify(finalEntity.copy(id = rowId))
                        if (classification != null) {
                            transactionRepository.updateTransferKind(rowId, classification)
                        }
                    }.onFailure { e ->
                        Log.w(TAG, "Self-transfer classification failed: ${e.message}")
                    }
                }

                // Try to link credit card bill payment legs together. This both
                // de-duplicates spending (TRANSFER is excluded from totals) and
                // reduces the credit card outstanding when the link succeeds.
                runCatching {
                    creditCardPaymentLinker.linkIfApplicable(finalEntity.copy(id = rowId))
                }.onFailure { e ->
                    Log.w(TAG, "CC bill payment linker failed: ${e.message}")
                }

                // Trigger widget refresh for recent transactions
                com.spendly.tracker.widget.RecentTransactionsWidgetUpdateWorker.enqueueOneShot(appContext)

                return ProcessingResult(true, transactionId = rowId)
            } else {
                Log.d(TAG, "Transaction already exists (duplicate): ${entity.transactionHash}")
                return ProcessingResult(false, reason = "Duplicate transaction")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving transaction: ${e.message}")
            return ProcessingResult(false, reason = e.message)
        }
    }

    private suspend fun processBalanceUpdate(
        parsedTransaction: ParsedTransaction,
        entity: TransactionEntity,
        rowId: Long
    ) {
        if (parsedTransaction.accountLast4 == null) return

        val isFromCard = parsedTransaction.isFromCard

        val targetAccountLast4: String? = if (isFromCard) {
            var card = parsedTransaction.accountLast4?.let {
                cardRepository.getCard(parsedTransaction.bankName, it)
            }

            if (card == null) {
                val isCredit = (parsedTransaction.type.toEntityType() == TransactionType.CREDIT)
                parsedTransaction.accountLast4?.let { accountLast4 ->
                    cardRepository.findOrCreateCard(
                        cardLast4 = accountLast4,
                        bankName = parsedTransaction.bankName,
                        isCredit = isCredit
                    )
                }
                card = parsedTransaction.accountLast4?.let {
                    cardRepository.getCard(parsedTransaction.bankName, it)
                }
            }

            if (card == null) {
                Log.w(TAG, "Could not create/find card for ${parsedTransaction.bankName}")
                null
            } else {
                // Update card's balance
                cardRepository.updateCardBalance(
                    cardId = card.id,
                    balance = parsedTransaction.balance,
                    source = parsedTransaction.smsBody.take(200),
                    date = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(parsedTransaction.timestamp),
                        ZoneId.systemDefault()
                    )
                )

                when {
                    card.cardType == CardType.CREDIT -> parsedTransaction.accountLast4
                    card.cardType == CardType.DEBIT && card.accountLast4 != null -> card.accountLast4
                    else -> parsedTransaction.accountLast4
                }
            }
        } else {
            parsedTransaction.accountLast4
        }

        if (targetAccountLast4 != null) {
            val isCreditCard = (parsedTransaction.type.toEntityType() == TransactionType.CREDIT) ||
                    parsedTransaction.accountLast4?.let {
                        cardRepository.getCard(parsedTransaction.bankName, it)?.cardType
                    } == CardType.CREDIT

            val existingAccount = accountBalanceRepository.getLatestBalance(
                parsedTransaction.bankName,
                targetAccountLast4
            )

            val newBalance = when {
                parsedTransaction.balance != null -> parsedTransaction.balance!!
                isCreditCard -> {
                    val currentBalance = existingAccount?.balance ?: BigDecimal.ZERO
                    currentBalance + parsedTransaction.amount
                }
                existingAccount?.isCreditCard == true && parsedTransaction.type.toEntityType() == TransactionType.INCOME -> {
                    val currentBalance = existingAccount.balance ?: BigDecimal.ZERO
                    (currentBalance - parsedTransaction.amount).max(BigDecimal.ZERO)
                }
                else -> {
                    // SMS doesn't have explicit balance - calculate based on transaction type
                    val currentBalance = existingAccount?.balance ?: BigDecimal.ZERO
                    when (parsedTransaction.type.toEntityType()) {
                        TransactionType.INCOME -> {
                            // Money coming in - add to balance
                            currentBalance + parsedTransaction.amount
                        }
                        TransactionType.EXPENSE, TransactionType.INVESTMENT -> {
                            // Money going out - subtract from balance
                            (currentBalance - parsedTransaction.amount).max(BigDecimal.ZERO)
                        }
                        TransactionType.CREDIT, TransactionType.TRANSFER -> {
                            // Keep existing balance for transfers (complex logic needed)
                            // Credit should be handled above, this is fallback
                            currentBalance
                        }
                    }
                }
            }

            val balanceEntity = AccountBalanceEntity(
                bankName = parsedTransaction.bankName,
                accountLast4 = targetAccountLast4,
                balance = newBalance,
                timestamp = entity.dateTime,
                transactionId = if (rowId != -1L) rowId else null,
                creditLimit = existingAccount?.creditLimit,
                isCreditCard = isCreditCard || (existingAccount?.isCreditCard ?: false),
                smsSource = parsedTransaction.smsBody.take(500),
                sourceType = "TRANSACTION",
                currency = parsedTransaction.currency
            )

            accountBalanceRepository.insertBalance(balanceEntity)
            Log.d(TAG, "Saved balance update for ${parsedTransaction.bankName} **$targetAccountLast4")
        }
    }
}
