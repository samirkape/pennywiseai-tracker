package com.pennywiseai.tracker.domain.usecase

import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionState
import com.pennywiseai.tracker.data.database.entity.BudgetImpactType
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.SubscriptionRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.domain.repository.RuleRepository
import com.pennywiseai.tracker.domain.service.RuleEngine
import com.pennywiseai.tracker.utils.UnrecognizedSmsPrefillParser
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.LocalDateTime
import javax.inject.Inject

class AddTransactionUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val creditCardPaymentLinker: CreditCardPaymentLinker,
    private val ruleRepository: RuleRepository,
    private val ruleEngine: RuleEngine,
) {
    suspend fun execute(
        amount: BigDecimal,
        merchant: String,
        category: String,
        type: TransactionType,
        date: LocalDateTime,
        notes: String? = null,
        isRecurring: Boolean = false,
        bankName: String? = null,
        accountLast4: String? = null,
        currency: String = "INR",
        receiptPaths: List<String> = emptyList(),
        budgetCategory: String? = null,
        budgetImpactType: BudgetImpactType? = null,
        smsBody: String? = null,
        smsSender: String? = null,
        transferKind: String? = null,
        tags: String = "",
    ): Long {
        val transactionHash = if (!smsBody.isNullOrBlank() && !smsSender.isNullOrBlank()) {
            generateSmsBackedTransactionHash(smsSender, smsBody)
        } else {
            generateManualTransactionHash(
                amount = amount,
                merchant = merchant,
                date = date,
            )
        }

        val existing = transactionRepository.getTransactionByHash(transactionHash)
        if (existing != null && !existing.isDeleted) {
            return existing.id
        }

        // Create the transaction entity
        val transaction = TransactionEntity(
            amount = amount,
            merchantName = merchant,
            category = category,
            transactionType = type,
            dateTime = date,
            description = notes,
            smsBody = smsBody,
            bankName = bankName ?: smsSender?.let { UnrecognizedSmsPrefillParser.inferBankFromSender(it) }
                ?: "Manual Entry",
            smsSender = smsSender,
            accountNumber = accountLast4,
            balanceAfter = null,
            transactionHash = transactionHash,
            isRecurring = isRecurring,
            currency = currency,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
            receiptPath = null,
            budgetCategory = budgetCategory,
            budgetImpactType = budgetImpactType,
            transferKind = transferKind,
            tags = tags,
        )

        // Insert the transaction
        var transactionId = transactionRepository.insertTransaction(transaction)

        if (transactionId != -1L && !smsBody.isNullOrBlank()) {
            val saved = transaction.copy(id = transactionId)
            val activeRules = ruleRepository.getActiveRulesByType(saved.transactionType)
            val (withRules, _) = ruleEngine.evaluateRules(saved, smsBody, activeRules)
            if (withRules != saved) {
                transactionRepository.updateTransaction(withRules)
            }
        }

        // Insert receipt images into the new receipts table
        if (transactionId != -1L && receiptPaths.isNotEmpty()) {
            transactionRepository.insertReceipts(transactionId, receiptPaths)
        }

        // Update account balance if account was selected
        if (transactionId != -1L && bankName != null && accountLast4 != null) {
            updateAccountBalance(
                bankName = bankName,
                accountLast4 = accountLast4,
                amount = amount,
                type = type,
                date = date,
                transactionId = transactionId
            )
        }

        // Try linking this transaction to a credit card bill payment counterpart
        // (no-op for everything that isn't a CC bill payment leg).
        if (transactionId != -1L) {
            runCatching {
                creditCardPaymentLinker.linkIfApplicable(transaction.copy(id = transactionId))
            }
        }
        
        // If marked as recurring, create a subscription
        if (isRecurring && transactionId != -1L) {
            val nextPaymentDate = date.toLocalDate().plusMonths(1) // Default to monthly

            val subscription = SubscriptionEntity(
                merchantName = merchant,
                amount = amount,
                nextPaymentDate = nextPaymentDate,
                state = SubscriptionState.ACTIVE,
                bankName = "Manual Entry",
                category = category,
                currency = currency,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )

            subscriptionRepository.insertSubscription(subscription)
        }

        return transactionId
    }
    
    private suspend fun updateAccountBalance(
        bankName: String,
        accountLast4: String,
        amount: BigDecimal,
        type: TransactionType,
        date: LocalDateTime,
        transactionId: Long
    ) {
        // Get current account balance
        val currentAccount = accountBalanceRepository.getLatestBalance(bankName, accountLast4)

        if (currentAccount != null) {
            // Calculate new balance based on transaction type
            val newBalance = when (type) {
                TransactionType.INCOME -> currentAccount.balance + amount
                TransactionType.EXPENSE, TransactionType.CREDIT -> currentAccount.balance - amount
                TransactionType.TRANSFER -> currentAccount.balance - amount  // Simplified - from account
                TransactionType.INVESTMENT -> currentAccount.balance - amount
            }

            // Insert new balance record
            accountBalanceRepository.insertBalance(
                currentAccount.copy(
                    id = 0,  // Auto-generate new ID
                    balance = newBalance,
                    timestamp = date,
                    transactionId = transactionId,
                    sourceType = "TRANSACTION",
                    smsSource = null
                )
            )
        }
    }

    private fun generateSmsBackedTransactionHash(sender: String, smsBody: String): String {
        val data = "SMS_${sender}_${smsBody}"
        return MessageDigest.getInstance("MD5")
            .digest(data.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun generateManualTransactionHash(
        amount: BigDecimal,
        merchant: String,
        date: LocalDateTime
    ): String {
        // Create a unique hash for manual transactions
        // Format: MANUAL_<amount>_<merchant>_<datetime>
        val data = "MANUAL_${amount}_${merchant}_${date}"

        return MessageDigest.getInstance("MD5")
            .digest(data.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}