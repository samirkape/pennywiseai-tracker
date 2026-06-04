package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.CompiledPatterns
import com.pennywiseai.parser.core.PayrollCreditDetector
import com.pennywiseai.parser.core.MandateInfo
import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.TransferKinds
import kotlinx.datetime.LocalDateTime
import java.math.BigDecimal

/**
 * Base abstract class for Indian bank parsers.
 * Handles common patterns across Indian banks (INR currency, UPI, etc.).
 */
abstract class BaseIndianBankParser : BankParser() {

    override fun getCurrency() = "INR"

    /**
     * Single source of truth for Indian credit card bill payment detection.
     *
     * Banks describe a CC bill payment with phrases like:
     *  - "payment of Rs X received towards your ... credit card"
     *  - "received your payment ... credit card"
     *  - "thank you for the payment ... credit card"
     *  - "payment ... credited to your ... credit card"
     *  - "payment received on your ... credit card"
     *  - BBPS + credit card, or CRED app payments
     *  - "card payment of Rs X" / "Rs X towards credit card"
     *
     * When true, the bill-payment leg should be classified as
     * [TransactionType.TRANSFER] with [TransferKinds.CC_BILL_PAYMENT] so the app
     * never double-counts it as spending alongside the original card purchase.
     */
    open fun isCreditCardBillPayment(message: String): Boolean {
        val m = message.lowercase()

        // Refunds and cashbacks should never be treated as bill payments.
        if (m.contains("refund") || m.contains("reversal") || m.contains("cashback")) {
            return false
        }

        val ccContext = m.contains("credit card") || m.contains(" cc ") ||
            m.contains("cc payment") || m.contains("credit card payment")
        val cardContext = ccContext || m.contains("card") || m.contains("bbps") || m.contains("cred")

        if (!cardContext) return false

        // Bill payment received on the card side (i.e. CC SMS).
        val receivedOnCard = ccContext && (
            m.contains("payment received") ||
            m.contains("received your payment") ||
            m.contains("payment has been received") ||
            (m.contains("payment of") && (m.contains("received") || m.contains("credited"))) ||
            m.contains("credited towards")
        )

        // Bill payment from a bank account towards a credit card.
        val paidTowardsCard = ccContext && (
            m.contains("towards") ||
            m.contains("payment to") ||
            m.contains("thank you for the payment")
        )

        // BBPS / CRED confirmation SMS that explicitly names the credit card.
        val mentionsCredApp = Regex("""\bcred\b""", RegexOption.IGNORE_CASE).containsMatchIn(m)
        val bbpsCred = (m.contains("bbps") || mentionsCredApp) && ccContext

        // Bank debit / UPI to the CRED app (e.g. HDFC "Sent Rs.X ... To CRED Club").
        // These never say "credit card" on the debit leg, but they are bill payments.
        val paidToCredApp =
            Regex("""\bto\s+cred(?:\s+club)?\b""", RegexOption.IGNORE_CASE).containsMatchIn(m) ||
                Regex("""\btowards\s+cred(?:\s+club)?\b""", RegexOption.IGNORE_CASE).containsMatchIn(m) ||
                Regex("""\bupi\s+cred\b""", RegexOption.IGNORE_CASE).containsMatchIn(m)

        // CC bill payment confirmation where the bank says a payment was "credited to your card ending XXXX"
        // (e.g. HDFC: "Online Payment of Rs.X... was credited to your card ending 8711 On DD/MMM/YYYY").
        // These messages use "card ending" but never say "credit card", so ccContext is false above.
        val creditedToCard = m.contains("credited to your card") ||
            (m.contains("payment of") && m.contains("credited") && m.contains("card ending"))

        return receivedOnCard || paidTowardsCard || bbpsCred || paidToCredApp || creditedToCard
    }

    /**
     * Hook applied to every parsed transaction. Tags credit card bill payments
     * uniformly so all Indian bank parsers funnel through the same logic.
     */
    override fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? {
        val parsed = super.parse(smsBody, sender, timestamp) ?: return null
        return applyCreditCardBillPayment(parsed)
    }

    /**
     * Re-classifies the given parsed transaction as a CC_BILL_PAYMENT transfer
     * if its SMS body matches [isCreditCardBillPayment]. No-op otherwise.
     */
    protected fun applyCreditCardBillPayment(parsed: ParsedTransaction): ParsedTransaction {
        if (parsed.transferKind == TransferKinds.CC_BILL_PAYMENT) return parsed
        if (!isCreditCardBillPayment(parsed.smsBody)) return parsed
        return parsed.copy(
            type = TransactionType.TRANSFER,
            transferKind = TransferKinds.CC_BILL_PAYMENT
        )
    }

    /**
     * Checks if the message is for an investment transaction.
     * Contains keywords specific to Indian investment platforms and terms.
     */
    override fun isInvestmentTransaction(lowerMessage: String): Boolean {
        if (PayrollCreditDetector.isPayrollCreditMessage(lowerMessage)) {
            return false
        }
        val investmentKeywords = listOf(
            // Clearing corporations
            "iccl",                         // Indian Clearing Corporation Limited
            "indian clearing corporation",
            "nsccl",                        // NSE Clearing Corporation
            "nse clearing",
            "clearing corporation",

            // Auto-pay indicators (excluding mandate/UMRN to avoid subscription false positives)
            "nach",                         // National Automated Clearing House
            "ach",                          // Automated Clearing House
            "ecs",                          // Electronic Clearing Service

            // Investment platforms
            "groww",
            "zerodha",
            "upstox",
            "kite",
            "kuvera",
            "paytm money",
            "etmoney",
            "coin by zerodha",
            "smallcase",
            "angel one",
            "angel broking",
            "5paisa",
            "icici securities",
            "icici direct",
            "hdfc securities",
            "kotak securities",
            "motilal oswal",
            "sharekhan",
            "edelweiss",
            "axis direct",
            "sbi securities",

            // Investment types
            "mutual fund",
            "sip",                          // Systematic Investment Plan
            "elss",                         // Tax saving funds
            "ipo",                          // Initial Public Offering
            "folio",                        // Mutual fund folio
            "demat",
            "stockbroker",
            "digital gold",                 // Digital Gold investments
            "sovereign gold",               // Sovereign Gold Bonds

            // Stock exchanges
            "nse",                          // National Stock Exchange
            "bse",                          // Bombay Stock Exchange
            "cdsl",                         // Central Depository Services
            "nsdl"                          // National Securities Depository
        )

        return investmentKeywords.any { lowerMessage.contains(it) }
    }

    // ==========================================
    // Unified Mandate / Subscription Logic
    // ==========================================

    /**
     * Checks if this is an E-Mandate notification (not a transaction).
     */
    open fun isEMandateNotification(message: String): Boolean {
        val lowerMessage = message.lowercase()
        return lowerMessage.contains("e-mandate") ||
                lowerMessage.contains("upi-mandate") ||
                (lowerMessage.contains("mandate") && lowerMessage.contains("successfully created"))
    }

    /**
     * Checks if this is a future debit notification (subscription alert, not a current transaction).
     */
    open fun isFutureDebitNotification(message: String): Boolean {
        val lowerMessage = message.lowercase()
        return lowerMessage.contains("will be debited") ||
                lowerMessage.contains("mandate set for") ||
                (lowerMessage.contains("upcoming") && lowerMessage.contains("mandate"))
    }

    /**
     * Parses combined Mandate / E-Mandate / UPI-Mandate subscription information.
     * Returns a general MandateInfo implementation.
     */
    open fun parseMandateSubscription(message: String): MandateInfo? {
        if (!isEMandateNotification(message) && !isFutureDebitNotification(message)) {
            return null
        }

        // 1. Extract amount
        // Patterns: "Rs.1050.00", "INR 59.00", "Rs 123.45"
        val amount = CompiledPatterns.Amount.INR_PATTERN.find(message)?.let { match ->
            val amountStr = match.groupValues[1].replace(",", "")
            try {
                BigDecimal(amountStr)
            } catch (e: NumberFormatException) {
                null
            }
        } ?: CompiledPatterns.Amount.RS_PATTERN.find(message)?.let { match ->
            val amountStr = match.groupValues[1].replace(",", "")
            try {
                BigDecimal(amountStr)
            } catch (e: NumberFormatException) {
                null
            }
        } ?: return null

        // 2. Extract merchant
        // Patterns: "towards Google Play", "for Netflix", "Info: Spotify"
        var merchant = "Unknown Subscription"
        val merchantPatterns = listOf(
            Regex("""towards\s+([^.\n]+?)(?:\s+from|\s+A/c|\s+UMRN|\s+ID:|\s+Alert:|\s*\.|$)""", RegexOption.IGNORE_CASE),
            Regex("""for\s+([^.\n]+?)(?:\s+mandate|\s+will\s+be|\s+ID:|\s+Act:|\s*\.|$)""", RegexOption.IGNORE_CASE),
            Regex("""Info:\s*([^.\n]+?)(?:\s*$)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in merchantPatterns) {
            pattern.find(message)?.let { match ->
                val m = cleanMerchantName(match.groupValues[1].trim())
                if (isValidMerchantName(m)) merchant = m
            }
        }

        // 3. Extract date (for future debits)
        // Patterns: "on 29-May-25", "set for 29-May-25"
        // Matches DD-MMM-YY, dd/MM/yyyy formats common in Indian banks
        val datePattern = Regex("""(?:on|for)\s+(${CompiledPatterns.Date.DD_MMM_YY.pattern}|${CompiledPatterns.Date.DD_MM_YYYY.pattern})""", RegexOption.IGNORE_CASE)
        val dateStr = datePattern.find(message)?.groupValues?.get(1)?.let { rawDate ->
            // Normalize slashes to dashes if needed or keep as is, consumer will parse
            rawDate
        }

        // 4. Extract UMN (Unique Mandate Number) if present
        val umnPattern = Regex("""UMN[:\s]+([^.\s]+)""", RegexOption.IGNORE_CASE)
        val umn = umnPattern.find(message)?.groupValues?.get(1)

        return object : MandateInfo {
            override val amount = amount
            override val nextDeductionDate = dateStr
            override val merchant = merchant
            override val umn = umn
            override val dateFormat = "dd-MMM-yy" // Default fallback
        }
    }

    // ==========================================
    // Unified Balance Update Logic
    // ==========================================

    /**
     * Checks if this is a balance update notification (not a transaction).
     */
    open fun isBalanceUpdateNotification(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Check for balance update patterns
        // Must contain "Available Balance" or similar keywords
        // And typically "as on" or "is Rs." without transaction words like "debited", "spent"
        val hasBalanceKeyword = lowerMessage.contains("available bal") ||
                lowerMessage.contains("avl bal") ||
                lowerMessage.contains("account balance") ||
                lowerMessage.contains("a/c balance") ||
                lowerMessage.contains("updated balance")

        val hasTxnKeyword = lowerMessage.contains("debited") ||
                lowerMessage.contains("credited") ||
                lowerMessage.contains("withdrawn") ||
                lowerMessage.contains("deposited") ||
                lowerMessage.contains("spent") ||
                lowerMessage.contains("transferred") ||
                lowerMessage.contains("payment of")

        return hasBalanceKeyword && !hasTxnKeyword
    }

    data class BaseBalanceUpdateInfo(
        val bankName: String,
        val accountLast4: String?,
        val balance: BigDecimal,
        val asOfDate: LocalDateTime? = null
    )

    /**
     * Parses generic balance update notification.
     */
    open fun parseBalanceUpdate(message: String): BaseBalanceUpdateInfo? {
        if (!isBalanceUpdateNotification(message)) {
            return null
        }

        // Extract account last 4 digits
        val accountLast4 = extractAccountLast4(message)

        // Extract balance amount
        // Patterns: "is Rs. 12,345", "Avl Bal Rs 12345"
        val balance = extractBalance(message) ?: return null

        return BaseBalanceUpdateInfo(
            bankName = getBankName(),
            accountLast4 = accountLast4,
            balance = balance
        )
    }

    // ==========================================
    // Common Helper Methods
    // ==========================================

    /**
     * Helper function to convert month abbreviation to number.
     */
    protected fun getMonthNumber(monthAbbr: String): Int {
        return when (monthAbbr.uppercase()) {
            "JAN" -> 1
            "FEB" -> 2
            "MAR" -> 3
            "APR" -> 4
            "MAY" -> 5
            "JUN" -> 6
            "JUL" -> 7
            "AUG" -> 8
            "SEP" -> 9
            "OCT" -> 10
            "NOV" -> 11
            "DEC" -> 12
            else -> 1
        }
    }
}
