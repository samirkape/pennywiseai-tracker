package com.spendly.parser.core.bank

import com.spendly.parser.core.CompiledPatterns
import com.spendly.parser.core.Constants
import com.spendly.parser.core.ParsedTransaction
import com.spendly.parser.core.PayrollCreditDetector
import com.spendly.parser.core.TransactionType
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Base class for bank-specific message parsers.
 * Each bank should extend this class and implement its specific parsing logic.
 */
abstract class BankParser {

    private companion object {
        /**
         * A credit-limit clause and its figure: "Avl Limit: INR 57888.3",
         * "Available Limit: INR 3,30,022.99", "Avl Lmt INR 35182.45". The limit is
         * never the transaction amount, so it's masked out before the amount
         * patterns run (#888).
         */
        val CREDIT_LIMIT_CLAUSE = Regex(
            """\b(?:Avl|Avail|Available)\.?\s+(?:Credit\s+)?(?:Limit|Lmt)\b\s*(?:is)?\s*:?\s*(?:INR|Rs\.?|₹)?\s*[0-9,]*\.?\d+""",
            RegexOption.IGNORE_CASE
        )

        /** A verb saying money actually moved — used to tell a payment from a reminder. */
        val COMPLETED_MONEY_VERB = Regex(
            """\b(debited|deducted|credited|deposited|withdrawn|spent|received|transferred|""" +
                """sent|charged|used|refunded|reversed|paid|purchased)\b"""
        )

        // Order matters: yyyy-first patterns are tried before dd-first ones to avoid
        // misreading "2026-07-18" as day=2026. Patterns that fail range/validity
        // checks (e.g. month > 12) are skipped, so ambiguous dd/MM vs MM/dd inputs
        // safely fall through to the SMS receipt timestamp instead of a wrong date.
        val DATE_TIME_PATTERNS = listOf(
            // 2026-07-18-14:29:23 / 2026-07-18 14:29:23 / 2026/07/18 14:29:23
            // (negative lookahead avoids swallowing a trailing AM/PM marker as if 24-hour)
            DateTimePattern(
                Regex("""\b(\d{4})[-/](\d{1,2})[-/](\d{1,2})[ -](\d{1,2}):(\d{2})(?::(\d{2}))?(?!\s*[AaPp][Mm])\b"""),
                yearFirst = true
            ),
            // 18-07-26 15:07:53 / 18/07/2026 15:07:53 / 18-07-2026 15:07
            DateTimePattern(
                Regex("""\b(\d{1,2})[-/](\d{1,2})[-/](\d{2,4})[ -](\d{1,2}):(\d{2})(?::(\d{2}))?(?!\s*[AaPp][Mm])\b"""),
                yearFirst = false
            ),
            // 01/8/25 03:15 PM / 10/01/25 8:00 AM / 3/18/26, 6:39 PM
            DateTimePattern(
                Regex(
                    """\b(\d{1,2})[/-](\d{1,2})[/-](\d{2,4}),?\s+(\d{1,2}):(\d{2})\s*([AaPp][Mm])\b"""
                ),
                yearFirst = false,
                hasAmPm = true
            )
        )
    }

    /**
     * Returns the name of the bank this parser handles.
     */
    abstract fun getBankName(): String

    /**
     * Checks if this parser can handle messages from the given sender.
     */
    abstract fun canHandle(sender: String): Boolean

    /**
     * Returns the currency used by this bank.
     * Defaults to INR for Indian banks. International banks should override this.
     */
    open fun getCurrency(): String = "INR"

    /**
     * Parses an SMS message and extracts transaction information.
     * Returns null if the message cannot be parsed.
     */
    open fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? {
        // Skip non-transaction messages
        if (!isTransactionMessage(smsBody)) {
            return null
        }

        val amount = extractAmount(smsBody)
        if (amount == null) {
            return null
        }

        val type = extractTransactionType(smsBody)
        if (type == null) {
            return null
        }

        // Extract available limit for credit card transactions
        val availableLimit = if (type == TransactionType.CREDIT) {
            val limit = extractAvailableLimit(smsBody)
            limit
        } else {
            null
        }

        val rawAccountLast4 = extractAccountLast4(smsBody)
        val safeAccountLast4 = rawAccountLast4?.let { extractLast4Digits(it) } ?: rawAccountLast4

        return ParsedTransaction(
            amount = amount,
            type = type,
            merchant = extractMerchant(smsBody, sender),
            reference = extractReference(smsBody),
            accountLast4 = safeAccountLast4,
            balance = extractBalance(smsBody),
            creditLimit = availableLimit,  // TODO: This is actually available limit, will be fixed in SmsReaderWorker
            smsBody = smsBody,
            sender = sender,
            // Prefer the transaction date/time reported inside the SMS body (as sent by the
            // bank) over the SMS inbox receipt timestamp, since delivery delays can cause the
            // two to diverge. Falls back to the SMS timestamp when no date/time can be parsed.
            timestamp = extractMessageDateTime(smsBody) ?: timestamp,
            bankName = getBankName(),
            isFromCard = detectIsCard(smsBody),
            currency = transactionCurrency(smsBody),
            isMobileWallet = isMobileWallet(),
            accountCurrency = accountCurrency(smsBody)
        )
    }

    /**
     * Attempts to extract the transaction date/time embedded in the SMS body text
     * (e.g. "18-07-26 15:07:53", "2026-07-18-14:29:23", or "01/8/25 03:15 PM"),
     * returning it as epoch milliseconds in the device's default time zone.
     * Returns null when no recognizable date/time is found -- including messages
     * that omit the time entirely, or use a format not covered below -- in which
     * case callers should fall back to the SMS receipt timestamp.
     *
     * Public so historical transactions can be re-derived from their stored SMS
     * body text (see `TransactionRepository.reconcileTransactionDatesFromSms`).
     */
    open fun extractMessageDateTime(message: String): Long? {
        for (pattern in DATE_TIME_PATTERNS) {
            val match = pattern.regex.find(message) ?: continue
            val (year, month, day) = pattern.dateOf(match) ?: continue
            val (hour, minute, second) = pattern.timeOf(match) ?: continue

            if (month !in 1..12 || day !in 1..31 || hour !in 0..23 || minute !in 0..59 || second !in 0..59) {
                continue
            }

            val localDateTime = try {
                LocalDateTime.of(year, month, day, hour, minute, second)
            } catch (e: Exception) {
                continue
            }

            return localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }

        return null
    }

    /** Describes one supported "date [separator] time" layout found in bank SMS bodies. */
    private class DateTimePattern(
        val regex: Regex,
        private val yearFirst: Boolean,
        /** True when group 6 is an AM/PM marker instead of an optional seconds value. */
        private val hasAmPm: Boolean = false
    ) {
        fun dateOf(match: MatchResult): Triple<Int, Int, Int>? {
            val g1 = match.groupValues[1].toIntOrNull() ?: return null
            val g2 = match.groupValues[2].toIntOrNull() ?: return null
            val g3 = match.groupValues[3].toIntOrNull() ?: return null

            return if (yearFirst) {
                Triple(g1, g2, g3) // yyyy, MM, dd
            } else {
                val year = if (g3 < 100) 2000 + g3 else g3
                Triple(year, g2, g1) // dd, MM, yy(yy) -> yyyy, MM, dd
            }
        }

        fun timeOf(match: MatchResult): Triple<Int, Int, Int>? {
            var hour = match.groupValues[4].toIntOrNull() ?: return null
            val minute = match.groupValues[5].toIntOrNull() ?: return null

            if (hasAmPm) {
                // Group 6 holds "AM"/"PM" here; there are no seconds in this layout.
                when (match.groupValues[6].uppercase()) {
                    "PM" -> if (hour < 12) hour += 12
                    "AM" -> if (hour == 12) hour = 0
                    else -> return null
                }
                return Triple(hour, minute, 0)
            }

            val second = match.groupValues[6].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
            return Triple(hour, minute, second)
        }
    }

    /**
     * Currency of this message's amount. Defaults to the bank's home currency;
     * parsers whose cards post foreign-currency spends override it so the amount
     * isn't tagged with the wrong currency.
     */
    protected open fun transactionCurrency(message: String): String = getCurrency()

    /**
     * Currency the account's balance/limit is kept in, when it differs from
     * [transactionCurrency] — see [ParsedTransaction.accountCurrency]. Null by
     * default ("same as the transaction").
     */
    protected open fun accountCurrency(message: String): String? = null

    /**
     * Whether this parser represents a mobile-money wallet whose SMS carries a
     * running balance but no per-account number (e.g. eMola, M-Pesa Mozambique).
     * When true, the app derives a single service-level account row from the
     * balance instead of requiring an accountLast4. Defaults to false.
     */
    open fun isMobileWallet(): Boolean = false

    /**
     * Checks if the message is a transaction message (not OTP, promotional, etc.)
     */
    protected open fun isTransactionMessage(message: String): Boolean {
        if (isNonTransactionMessage(message)) return false

        // Ignore card-side confirmation messages that are not actual spend/income entries.
        // A card-side bill-payment leg ("payment of X … credited to your … Credit Card")
        // also matches that shape but must survive: BaseIndianBankParser re-classifies it
        // as a TRANSFER + CC_BILL_PAYMENT so it links against the bank-side debit leg.
        if (isCreditCardConfirmationMessage(message) && !isCardBillPaymentConfirmation(message)) return false

        // Must contain transaction keywords
        val transactionKeywords = listOf(
            "debited", "credited", "withdrawn", "deposited",
            "spent", "received", "transferred", "paid"
        )

        return message.lowercase().let { lower -> transactionKeywords.any { lower.contains(it) } }
    }

    /**
     * Messages that carry an amount but are never a transaction: OTPs, promos,
     * payment requests and due/reminder notices.
     *
     * Subclasses that need their own keyword list should call this instead of
     * re-listing these checks — copies drifted out of sync and let reminders
     * through as real spends.
     */
    protected fun isNonTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Skip OTP messages
        if (lowerMessage.contains("otp") ||
            // Hyphenated "One-Time Password" (Amex SafeKey) used to slip through
            // and got booked as a card spend.
            lowerMessage.contains(Regex("""one[-\s]?time password""")) ||
            lowerMessage.contains("verification code")
        ) {
            return true
        }

        // Skip promotional messages
        if (lowerMessage.contains("offer") ||
            lowerMessage.contains("discount") ||
            lowerMessage.contains("cashback offer") ||
            lowerMessage.contains("win ")
        ) {
            return true
        }

        // Skip payment request messages (common across banks)
        if (lowerMessage.contains("has requested") ||
            lowerMessage.contains("payment request") ||
            lowerMessage.contains("collect request") ||
            lowerMessage.contains("requesting payment") ||
            lowerMessage.contains("requests rs") ||
            lowerMessage.contains("ignore if already paid")
        ) {
            return true
        }

        // Skip merchant payment acknowledgments
        if (lowerMessage.contains("have received payment")) {
            return true
        }

        // Skip IPO/ASBA fund blocking — the money is earmarked, not debited.
        // ("Your ASBA application ... value of Rs X is blocked in your ... account")
        if (lowerMessage.contains("asba") ||
            lowerMessage.contains("is blocked in your")
        ) {
            return true
        }

        // Skip reward/gift voucher DELIVERY notices — no money moves in the
        // account. Deliberately narrow: buying a voucher is a real expense, so
        // anything carrying a debit verb still parses.
        if ((lowerMessage.contains("e-voucher") || lowerMessage.contains("evoucher")) &&
            (lowerMessage.contains("received") ||
                lowerMessage.contains("reward") ||
                lowerMessage.contains("redemption")) &&
            !lowerMessage.contains("spent") &&
            !lowerMessage.contains("debited") &&
            !lowerMessage.contains("charged")
        ) {
            return true
        }

        // Skip convert-your-bill-to-EMI offers: "You can convert your Credit Card
        // … outstanding bill of Rs.X into SmartEMI … SMS SMARTEMI YES" (#885).
        // Only the invitation — a completed EMI payment ("Rs.3282 debited …
        // towards SmartEMI") still parses. Debit verbs only: the offer itself says
        // "Ignore if any amount is already paid".
        if (lowerMessage.contains("convert") &&
            (lowerMessage.contains("smartemi") || lowerMessage.contains("into emi")) &&
            !Regex("""\b(debited|deducted|spent|charged|withdrawn)\b""").containsMatchIn(lowerMessage)
        ) {
            return true
        }

        // Skip auto-debit intimations ("INR X for <merchant> will be auto-debited
        // ... by <date>"). These announce a future debit; the real debit arrives
        // as its own SMS, so booking them doubled the spend.
        if (lowerMessage.contains("will be auto-debited") ||
            lowerMessage.contains("will be auto debited")
        ) {
            return true
        }

        // Bill-due reminders: "… bill of INR X due on 28-09-26. Pay on <link>".
        // A real payment can mention a due date too ("Sent Rs.X … for EMI due on
        // …"), so only skip when no completed-money verb is present. The list
        // covers every completed verb the parsers recognise; word boundaries so
        // the "paid" inside "Postpaid" doesn't count.
        if (lowerMessage.contains("due on") && !COMPLETED_MONEY_VERB.containsMatchIn(lowerMessage)) {
            return true
        }

        // Skip payment reminder/due messages
        if (lowerMessage.contains("is due") ||
            lowerMessage.contains("min amount due") ||
            lowerMessage.contains("minimum amount due") ||
            lowerMessage.contains("in arrears") ||
            lowerMessage.contains("is overdue") ||
            lowerMessage.contains("ignore if paid") ||
            (lowerMessage.contains("pls pay") && lowerMessage.contains("min of"))
        ) {
            return true
        }

        return false
    }

    /**
     * Extracts the transaction currency from the message.
     * Can be overridden by specific bank parsers for custom logic.
     */
    protected open fun extractCurrency(message: String): String? {
        // Default implementation - try to find currency pattern
        val currencyPattern = Regex("""([A-Z]{3})\s*[0-9,]+(?:\.\d{2})?""", RegexOption.IGNORE_CASE)
        currencyPattern.find(message)?.let { match ->
            return match.groupValues[1].uppercase()
        }
        return null
    }

    /**
     * Extracts the transaction amount from the message.
     */
    protected open fun extractAmount(message: String): BigDecimal? {
        val withoutLimit = CREDIT_LIMIT_CLAUSE.replace(message, " ")
        for (pattern in CompiledPatterns.Amount.ALL_PATTERNS) {
            pattern.find(withoutLimit)?.let { match ->
                val amountStr = match.groupValues[1].replace(",", "")
                return try {
                    BigDecimal(amountStr)
                } catch (e: NumberFormatException) {
                    null
                }
            }
        }

        return null
    }

    /**
     * Extracts the transaction type (INCOME/EXPENSE/INVESTMENT).
     */
    protected open fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()

        // Check for investment transactions first (highest priority)
        if (isInvestmentTransaction(lowerMessage)) {
            return TransactionType.INVESTMENT
        }

        return when {
            lowerMessage.contains("debited") -> TransactionType.EXPENSE
            lowerMessage.contains("withdrawn") -> TransactionType.EXPENSE
            lowerMessage.contains("spent") -> TransactionType.EXPENSE
            lowerMessage.contains("charged") -> TransactionType.EXPENSE
            lowerMessage.contains("paid") -> TransactionType.EXPENSE
            lowerMessage.contains("purchase") -> TransactionType.EXPENSE
            lowerMessage.contains("deducted") -> TransactionType.EXPENSE

            lowerMessage.contains("credited") -> TransactionType.INCOME
            lowerMessage.contains("deposited") -> TransactionType.INCOME
            lowerMessage.contains("received") -> TransactionType.INCOME
            lowerMessage.contains("refund") -> TransactionType.INCOME
            lowerMessage.contains("cashback") && !lowerMessage.contains("earn cashback") -> TransactionType.INCOME

            else -> null
        }
    }

    /**
     * Checks if the message is for an investment transaction.
     * Can be overridden by specific bank parsers for custom logic.
     */
    protected open fun isInvestmentTransaction(lowerMessage: String): Boolean {
        if (PayrollCreditDetector.isPayrollCreditMessage(lowerMessage)) {
            return false
        }
        // Long/specific phrases — substring match is safe
        val substringKeywords = listOf(
            // Clearing corporations
            "iccl",
            "indian clearing corporation",
            "nsccl",
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
            "elss",
            "folio",
            "demat",
            "stockbroker",
            "digital gold",
            "sovereign gold",

            // Stock exchanges
            "cdsl",
            "nsdl"
        )

        // Short keywords — require word boundary to avoid matching inside other words
        val wordKeywords = listOf(
            "kite",   // Zerodha's app; avoid matching "kitesurf" etc.
            "sip",    // Systematic Investment Plan; avoid matching "sipla" etc.
            "ipo",    // Initial Public Offering
            "nse",    // National Stock Exchange; avoid matching "license"
            "bse"     // Bombay Stock Exchange; avoid matching "observe"
        )

        if (substringKeywords.any { lowerMessage.contains(it) }) return true
        return wordKeywords.any { Regex("""\b${Regex.escape(it)}\b""").containsMatchIn(lowerMessage) }
    }

    /**
     * Extracts merchant/payee information.
     */
    protected open fun extractMerchant(message: String, sender: String): String? {
        for (pattern in CompiledPatterns.Merchant.ALL_PATTERNS) {
            pattern.find(message)?.let { match ->
                val merchant = cleanMerchantName(match.groupValues[1].trim())
                if (isValidMerchantName(merchant)) {
                    return merchant
                }
            }
        }

        return null
    }

    /**
     * Extracts transaction reference number.
     */
    protected open fun extractReference(message: String): String? {
        for (pattern in CompiledPatterns.Reference.ALL_PATTERNS) {
            pattern.find(message)?.let { match ->
                return match.groupValues[1].trim()
            }
        }

        return null
    }

    /**
     * Extracts last 4 digits from a raw captured string.
     * Filters to digits only, takes last 4. Returns null if fewer than 3 digits.
     */
    protected fun extractLast4Digits(raw: String): String? {
        val digits = raw.filter { it.isDigit() }
        val last4 = digits.takeLast(4)
        return if (last4.length >= 3) last4 else null
    }

    /**
     * Extracts last 4 digits of account number.
     */
    protected open fun extractAccountLast4(message: String): String? {
        for (pattern in CompiledPatterns.Account.ALL_PATTERNS) {
            pattern.find(message)?.let { match ->
                val rawCapture = match.groupValues[1]
                val last4 = extractLast4Digits(rawCapture)

                if (last4 != null && isValidAccountLast4(last4, match.value, message)) {
                    return last4
                }
            }
        }

        return null
    }

    /**
     * Validates that the extracted 4 digits are actually part of an account number,
     * not a date, RRN, or other numeric field.
     */
    private fun isValidAccountLast4(last4: String, matchedText: String, fullMessage: String): Boolean {
        // Escape the last4 for safe regex usage
        val escapedLast4 = Regex.escape(last4)

        // Check if it's part of a date pattern (dd/mm/yyyy, dd-mm-yyyy, etc.)
        val datePatterns = listOf(
            Regex("""\d{1,2}[/-]\d{1,2}[/-]$escapedLast4"""),  // 04/11/2025, 05-02-2025
            Regex("""$escapedLast4[/-]\d{1,2}[/-]\d{1,2}"""),  // 2025/11/04, 2025-02-05
            Regex("""\bon\s+\d{1,2}[/-]\d{1,2}[/-]$escapedLast4""", RegexOption.IGNORE_CASE),  // "on 04/11/2025"
            Regex("""\bdated\s+\d{1,2}[/-]\d{1,2}[/-]$escapedLast4""", RegexOption.IGNORE_CASE)  // "dated 05-02-2025"
        )

        for (datePattern in datePatterns) {
            if (datePattern.find(fullMessage) != null) {
                return false
            }
        }

        // Check if it's a standalone year (2024, 2025, etc.)
        if (last4.toIntOrNull() in 2000..2099) {
            // Only reject if it appears to be a year in date context
            val yearContextPatterns = listOf(
                Regex("""\bon\s+\d{1,2}[/-]\d{1,2}[/-]$escapedLast4""", RegexOption.IGNORE_CASE),
                Regex("""\bdated\s+.*?$escapedLast4""", RegexOption.IGNORE_CASE),
                Regex("""$escapedLast4(?:\s|$)""")  // Year at end of phrase
            )

            for (yearPattern in yearContextPatterns) {
                if (yearPattern.find(fullMessage) != null) {
                    // Only reject if NOT preceded by "Account" or "A/c" within 25 chars
                    val accountBeforeYear = Regex("""(?:A/c|Account|Acct).{0,25}$escapedLast4""", RegexOption.IGNORE_CASE)
                    if (accountBeforeYear.find(fullMessage) == null) {
                        return false
                    }
                }
            }
        }

        return true
    }

    /**
     * Extracts balance after transaction.
     */
    protected open fun extractBalance(message: String): BigDecimal? {
        for (pattern in CompiledPatterns.Balance.ALL_PATTERNS) {
            pattern.find(message)?.let { match ->
                val balanceStr = match.groupValues[1].replace(",", "")
                return try {
                    BigDecimal(balanceStr)
                } catch (e: NumberFormatException) {
                    null
                }
            }
        }

        return null
    }

    /**
     * Extracts credit card available limit from the message.
     * This is the remaining credit available to spend, NOT the total credit limit.
     */
    protected open fun extractAvailableLimit(message: String): BigDecimal? {

        // Currency token: most banks print "Rs"/"₹", but some (e.g. IndusInd) use
        // "Avl Lmt: INR ..." — accept all three. Only ever evaluated for CREDIT
        // messages (extractAvailableLimit is gated on type == CREDIT in parse()).
        val cur = """(?:Rs\.?|INR|₹)"""
        val creditLimitPatterns = listOf(
            // "Available limit Rs.111,111.89" / "Available limit INR 111,111.89"
            Regex("""Available\s+limit\s+$cur\s*([0-9,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
            // "Available limit: Rs 111,111.89"
            Regex("""Available\s+limit:?\s*$cur\s*([0-9,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
            // "Avl Lmt: Rs 111,111.89" / "Avl Lmt: INR 111,111.89" (ICICI, IndusInd, others)
            Regex("""Avl\s+Lmt:?\s*$cur\s*([0-9,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
            // "Avail Limit Rs.111,111.89"
            Regex("""Avail\s+Limit:?\s*$cur\s*([0-9,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
            // "Available Credit Limit: Rs.111,111.89"
            Regex("""Available\s+Credit\s+Limit:?\s*$cur\s*([0-9,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
            // "Limit: Rs.111,111.89" (generic, but only for credit card messages)
            Regex("""(?:^|\s)Limit:?\s*$cur\s*([0-9,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
        )

        for ((index, pattern) in creditLimitPatterns.withIndex()) {
            pattern.find(message)?.let { match ->
                val limitStr = match.groupValues[1].replace(",", "")
                return try {
                    val limit = BigDecimal(limitStr)
                    limit
                } catch (e: NumberFormatException) {
                    null
                }
            }
        }

        return null
    }

    /**
     * Detects if the transaction is from a card (credit/debit) based on message patterns.
     * First excludes account-related patterns, then checks for actual card patterns.
     */
    protected open fun detectIsCard(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // FIRST: Explicitly exclude account-related patterns - these are NOT cards
        val accountPatterns = listOf(
            "a/c",           // Account abbreviation (e.g., "from HDFC Bank A/c 120092")
            "account",       // Full word account (e.g., "from HDFC Bank Account XX0093")
            "ac ",           // Account abbreviation with space
            "acc ",          // Account abbreviation
            "saving account",
            "current account",
            "savings a/c",
            "current a/c"
        )

        // If message contains account patterns, it's NOT a card transaction
        for (pattern in accountPatterns) {
            if (lowerMessage.contains(pattern)) {
                return false
            }
        }

        // SECOND: Check for actual card-specific patterns
        val cardPatterns = listOf(
            "card ending",
            "card xx",
            "debit card",
            "credit card",
            "card no.",
            "card number",
            "card *",
            "card x"
        )

        // Check for card patterns
        for (pattern in cardPatterns) {
            if (lowerMessage.contains(pattern)) {
                return true
            }
        }

        // Check for masked card number patterns (e.g., "XXXX1234", "*1234", "ending 1234")
        // BUT only if we haven't already excluded it as an account transaction
        val maskedCardRegex = Regex("""(?:xx|XX|\*{2,})?\d{4}""")
        if (lowerMessage.contains("ending") && maskedCardRegex.containsMatchIn(message)) {
            return true
        }

        return false
    }

    /**
     * Cleans merchant name by removing common suffixes and noise.
     */
    protected open fun cleanMerchantName(merchant: String): String {
        return merchant
            .replace(CompiledPatterns.Cleaning.TRAILING_PARENTHESES, "")
            .replace(CompiledPatterns.Cleaning.REF_NUMBER_SUFFIX, "")
            .replace(CompiledPatterns.Cleaning.DATE_SUFFIX, "")
            .replace(CompiledPatterns.Cleaning.UPI_SUFFIX, "")
            .replace(CompiledPatterns.Cleaning.TIME_SUFFIX, "")
            .replace(CompiledPatterns.Cleaning.TRAILING_DASH, "")
            .replace(CompiledPatterns.Cleaning.PVT_LTD, "")
            .replace(CompiledPatterns.Cleaning.LTD, "")
            .trim()
    }

    /**
     * Validates if the extracted merchant name is valid.
     */
    protected open fun isValidMerchantName(name: String): Boolean {
        val commonWords =
            setOf("USING", "VIA", "THROUGH", "BY", "WITH", "FOR", "TO", "FROM", "AT", "THE")

        return name.length >= Constants.Parsing.MIN_MERCHANT_NAME_LENGTH &&
                name.any { it.isLetter() } &&
                name.uppercase() !in commonWords &&
                !name.all { it.isDigit() } &&
                !name.contains("@") // Not a UPI ID
    }

    /**
     * True when the message is a credit-card bill-payment leg that must survive the
     * [isCreditCardConfirmationMessage] skip, because it gets re-classified as a
     * TRANSFER + CC_BILL_PAYMENT (see BaseIndianBankParser.applyCreditCardBillPayment).
     */
    protected open fun isCardBillPaymentConfirmation(message: String): Boolean = false

    /**
     * Ignore credit-card confirmation messages that are not actual spend/income entries.
     * Examples: "HDFC Bank Cardmember, Online Payment of Rs... was credited to your card ending 9908 ..."
     * These messages intentionally confirm a bill-payment credit on the card side and should not be
     * added as standalone transactions, while still keeping the dedicated CC_BILL_PAYMENT detection
     * logic available for the bank-side payment leg.
     */
    protected open fun isCreditCardConfirmationMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        return (lowerMessage.contains("credited to your card ending") ||
            (lowerMessage.contains("card ending") && lowerMessage.contains("credited"))) &&
            (lowerMessage.contains("online payment of") || lowerMessage.contains("payment of") ||
                lowerMessage.contains("cardmember"))
    }
}
