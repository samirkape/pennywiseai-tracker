package com.spendly.tracker.domain.service

import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.database.entity.TransactionType
import com.spendly.tracker.domain.model.QuickKeywordTextMatchMode
import com.spendly.tracker.domain.model.rule.ConditionOperator
import com.spendly.tracker.domain.model.rule.TransactionField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class QuickKeywordRuleCompilerTest {

    @Test
    fun parseKeywords_splitsAndTrims() {
        val keywords = QuickKeywordRuleCompiler.parseKeywords(" salary , hdfc,gslab , ")
        assertEquals(listOf("salary", "hdfc", "gslab"), keywords)
    }

    @Test
    fun parseKeywords_supportsNewlinesAndPhrases() {
        val keywords = QuickKeywordRuleCompiler.parseKeywords(
            """
            amazon prime
            swiggy;zomato
            UPI/9876543210
            """.trimIndent()
        )
        assertEquals(
            listOf("amazon prime", "swiggy", "zomato", "UPI/9876543210"),
            keywords,
        )
    }

    @Test
    fun parseKeywords_supportsQuotedCommaInPhrase() {
        val keywords = QuickKeywordRuleCompiler.parseKeywords("\"netflix, inc\", spotify")
        assertEquals(listOf("netflix, inc", "spotify"), keywords)
    }

    @Test
    fun compile_preservesKeywordsWithCommasInStorage() {
        val input = QuickKeywordRuleCompiler.QuickKeywordRuleInput(
            name = "Streaming",
            keywords = listOf("netflix, inc", "spotify"),
            merchantLabel = "Netflix",
            categoryLabel = "Subscriptions",
        )
        val rule = QuickKeywordRuleCompiler.compile(input)
        val restored = QuickKeywordRuleCompiler.decompile(rule)
        requireNotNull(restored)
        assertEquals(listOf("netflix, inc", "spotify"), restored.keywords)
    }

    @Test
    fun compileAndDecompile_roundTrip() {
        val input = QuickKeywordRuleCompiler.QuickKeywordRuleInput(
            name = "Salary",
            keywords = listOf("salary", "gslab"),
            merchantLabel = "Salary",
            categoryLabel = "Salary",
            matchType = TransactionType.INCOME,
            syncNameWithLabel = true,
            runOnPastWhenSaved = true,
            applyUncategorizedOnly = false,
        )
        val rule = QuickKeywordRuleCompiler.compile(input)
        assertTrue(QuickKeywordRuleCompiler.isQuickKeywordRule(rule))

        val typeCondition = rule.conditions.first { it.field == TransactionField.TYPE }
        assertEquals(ConditionOperator.IN, typeCondition.operator)
        assertEquals("INCOME,CREDIT", typeCondition.value)

        val restored = QuickKeywordRuleCompiler.decompile(rule)
        requireNotNull(restored)
        assertEquals("Salary", restored.merchantLabel)
        assertEquals("Salary", restored.categoryLabel)
        assertEquals(TransactionType.INCOME, restored.matchType)
        assertTrue(restored.runOnPastWhenSaved)
        assertFalse(restored.applyUncategorizedOnly)
    }

    @Test
    fun ruleEngine_matchesSearchableTextInSms() {
        val input = QuickKeywordRuleCompiler.QuickKeywordRuleInput(
            name = "Salary",
            keywords = listOf("gslab"),
            merchantLabel = "Salary",
            categoryLabel = "Salary",
            matchType = TransactionType.INCOME,
        )
        val rule = QuickKeywordRuleCompiler.compile(input)
        val engine = RuleEngine()

        val transaction = TransactionEntity(
            amount = BigDecimal("50000"),
            merchantName = "NEFT CR",
            category = "Others",
            transactionType = TransactionType.CREDIT,
            dateTime = LocalDateTime.now(),
            smsBody = "Rs 50000 credited from GSLAB PAYROLL",
            transactionHash = "test-hash",
        )

        val (updated, applications) = engine.evaluateRules(
            transaction,
            transaction.smsBody,
            listOf(rule),
        )

        assertEquals(1, applications.size)
        assertEquals("Salary", updated.merchantName)
        assertEquals("Salary", updated.category)
    }

    @Test
    fun matcher_diagnosesCreditAsIncomeLike() {
        val input = QuickKeywordRuleCompiler.QuickKeywordRuleInput(
            name = "Salary",
            keywords = listOf("gslab"),
            merchantLabel = "Salary",
            categoryLabel = "Salary",
            matchType = TransactionType.INCOME,
        )
        val txn = TransactionEntity(
            amount = BigDecimal("1"),
            merchantName = "X",
            category = "Others",
            transactionType = TransactionType.CREDIT,
            dateTime = LocalDateTime.now(),
            smsBody = "GSLAB salary credit",
            transactionHash = "h1",
        )
        val d = QuickKeywordRuleMatcher.diagnose(txn, txn.smsBody, input)
        assertTrue(d.matches)
    }

    @Test
    fun applyOverwrites_fixesInvestmentSalaryType() {
        val input = QuickKeywordRuleCompiler.QuickKeywordRuleInput(
            name = "Salary",
            keywords = listOf("salary"),
            merchantLabel = "Thoughtworks",
            categoryLabel = "Thoughtworks",
            matchType = TransactionType.INCOME,
            overwriteTransactionType = true,
        )
        val sms =
            "deposited for ACH C-SAL-THWORKSTECHINDPV-SalaryDec254"
        val txn = TransactionEntity(
            amount = BigDecimal("197317"),
            merchantName = "THWORKSTECHINDPV",
            category = "Others",
            transactionType = TransactionType.INVESTMENT,
            dateTime = LocalDateTime.now(),
            smsBody = sms,
            transactionHash = "h-ow",
        )
        val d = QuickKeywordRuleMatcher.diagnose(txn, txn.smsBody, input)
        assertTrue(d.matches)
        val patched = QuickKeywordRuleMatcher.applyOverwrites(txn, input)
        assertEquals(TransactionType.INCOME, patched.transactionType)
        assertEquals("Thoughtworks", patched.merchantName)
        assertEquals("Thoughtworks", patched.category)
    }

    @Test
    fun compile_skipsTypeConditionWhenOverwriteTypeEnabled() {
        val input = QuickKeywordRuleCompiler.QuickKeywordRuleInput(
            name = "Salary",
            keywords = listOf("salary"),
            merchantLabel = "Salary",
            categoryLabel = "Salary",
            matchType = TransactionType.INCOME,
            overwriteTransactionType = true,
        )
        val rule = QuickKeywordRuleCompiler.compile(input)
        assertFalse(rule.conditions.any { it.field == TransactionField.TYPE })
        assertTrue(rule.actions.any { it.field == TransactionField.TYPE })
    }

    @Test
    fun matcher_acceptsPayrollMisclassifiedAsInvestment() {
        val input = QuickKeywordRuleCompiler.QuickKeywordRuleInput(
            name = "Salary",
            keywords = listOf("thworkstech"),
            merchantLabel = "Salary",
            categoryLabel = "Salary",
            matchType = TransactionType.INCOME,
        )
        val sms =
            "Update! INR 1,97,317.00 deposited in HDFC Bank A/c XX2518 for ACH C-SAL-THWORKSTECHINDPV-SalaryDec254"
        val txn = TransactionEntity(
            amount = BigDecimal("197317"),
            merchantName = "Unknown",
            category = "Others",
            transactionType = TransactionType.INVESTMENT,
            dateTime = LocalDateTime.now(),
            smsBody = sms,
            transactionHash = "h-payroll",
        )
        val d = QuickKeywordRuleMatcher.diagnose(txn, txn.smsBody, input)
        assertTrue(d.matches)
        assertTrue(QuickKeywordRuleMatcher.passesIncomeTypeFilter(txn, txn.smsBody))
    }

    @Test
    fun applyOverwrites_preservesExplicitHdfcUpiPayeeWhenKeywordIsAccount() {
        val input = QuickKeywordRuleCompiler.QuickKeywordRuleInput(
            name = "Cable",
            keywords = listOf("2518"),
            merchantLabel = "Cable",
            categoryLabel = "Bills & Utilities",
        )
        val sms = """Sent Rs.25.00
From HDFC Bank A/C *2518
To Prajwal Kirana
On 24/05/26
Ref 919580778477
Not You?
Call 18002586161/SMS BLOCK UPI to 7308080808"""
        val txn = TransactionEntity(
            amount = BigDecimal("25"),
            merchantName = "Prajwal Kirana",
            category = "Others",
            transactionType = TransactionType.EXPENSE,
            dateTime = LocalDateTime.now(),
            smsBody = sms,
            transactionHash = "h-cable",
        )
        assertTrue(QuickKeywordRuleMatcher.diagnose(txn, txn.smsBody, input).matches)
        val patched = QuickKeywordRuleMatcher.applyOverwrites(txn, input)
        assertEquals("Prajwal Kirana", patched.merchantName)
    }

    @Test
    fun matcher_rejectsExpenseWhenIncomeOnly() {
        val input = QuickKeywordRuleCompiler.QuickKeywordRuleInput(
            name = "Salary",
            keywords = listOf("hdfc"),
            merchantLabel = "Salary",
            categoryLabel = "Salary",
            matchType = TransactionType.INCOME,
        )
        val txn = TransactionEntity(
            amount = BigDecimal("1"),
            merchantName = "HDFC",
            category = "Others",
            transactionType = TransactionType.EXPENSE,
            dateTime = LocalDateTime.now(),
            smsBody = "debited at HDFC",
            transactionHash = "h2",
        )
        val d = QuickKeywordRuleMatcher.diagnose(txn, txn.smsBody, input)
        assertFalse(d.matches)
        assertTrue(d.reason.contains("Type is"))
    }

    @Test
    fun matchesAnyKeyword_isCaseInsensitiveSubstring() {
        val haystack = "Rs 50000 CREDITED FROM gslab payroll"
        assertTrue(QuickKeywordRuleMatcher.matchesAnyKeyword(haystack, listOf("GSLAB")))
        assertTrue(QuickKeywordRuleMatcher.matchesAnyKeyword(haystack, listOf("gslab")))
        assertFalse(QuickKeywordRuleMatcher.matchesAnyKeyword(haystack, listOf("amazon")))
    }

    @Test
    fun buildSearchableText_includesFullSmsFromScan() {
        val txn = TransactionEntity(
            amount = BigDecimal.ONE,
            merchantName = "NEFT",
            category = "Others",
            transactionType = TransactionType.INCOME,
            dateTime = LocalDateTime.now(),
            smsBody = "Complete SMS body from scan with EMPLOYERCODE",
            transactionHash = "h",
        )
        val text = QuickKeywordRuleMatcher.buildSearchableText(txn, null)
        assertTrue(text.contains("Complete SMS body from scan", ignoreCase = true))
        assertTrue(QuickKeywordRuleMatcher.matchesAnyKeyword(text, listOf("employercode")))
    }

    @Test
    fun compiledRule_usesSubstringKeywordCondition() {
        val rule = QuickKeywordRuleCompiler.compile(
            QuickKeywordRuleCompiler.QuickKeywordRuleInput(
                name = "Test",
                keywords = listOf("foo", "bar"),
                merchantLabel = "Foo Merchant",
                categoryLabel = "Foo Category",
            )
        )
        val keywordCondition = rule.conditions.first {
            it.field == TransactionField.SEARCHABLE_TEXT
        }
        assertEquals(ConditionOperator.CONTAINS_ANY_KEYWORD, keywordCondition.operator)
        assertTrue(keywordCondition.value.contains("foo"))
        assertTrue(keywordCondition.value.contains("bar"))
    }

    @Test
    fun compile_roundTripsTextMatchMode() {
        val input = QuickKeywordRuleCompiler.QuickKeywordRuleInput(
            name = "Prefix",
            keywords = listOf("UPI/"),
            merchantLabel = "UPI",
            categoryLabel = "Transfers",
            textMatchMode = QuickKeywordTextMatchMode.STARTS_WITH_ANY,
        )
        val rule = QuickKeywordRuleCompiler.compile(input)
        val keywordCondition = rule.conditions.first {
            it.field == TransactionField.SEARCHABLE_TEXT
        }
        assertEquals(ConditionOperator.STARTS_WITH_ANY_KEYWORD, keywordCondition.operator)

        val restored = QuickKeywordRuleCompiler.decompile(rule)
        requireNotNull(restored)
        assertEquals(QuickKeywordTextMatchMode.STARTS_WITH_ANY, restored.textMatchMode)
    }

    @Test
    fun matchesKeywords_startsWithAny() {
        val haystack = "UPI/9876543210 paid to merchant"
        assertTrue(
            QuickKeywordRuleMatcher.matchesKeywords(
                haystack,
                listOf("UPI/"),
                QuickKeywordTextMatchMode.STARTS_WITH_ANY,
            ),
        )
        assertFalse(
            QuickKeywordRuleMatcher.matchesKeywords(
                haystack,
                listOf("merchant"),
                QuickKeywordTextMatchMode.STARTS_WITH_ANY,
            ),
        )
    }

    @Test
    fun matchesKeywords_containsAll() {
        val haystack = "amazon prime monthly subscription"
        assertTrue(
            QuickKeywordRuleMatcher.matchesKeywords(
                haystack,
                listOf("amazon", "prime"),
                QuickKeywordTextMatchMode.CONTAINS_ALL,
            ),
        )
        assertFalse(
            QuickKeywordRuleMatcher.matchesKeywords(
                haystack,
                listOf("amazon", "netflix"),
                QuickKeywordTextMatchMode.CONTAINS_ALL,
            ),
        )
    }

    @Test
    fun matchesKeywords_equalsOneOf() {
        val haystack = "swiggy"
        assertTrue(
            QuickKeywordRuleMatcher.matchesKeywords(
                haystack,
                listOf("SwIgGy", "zomato"),
                QuickKeywordTextMatchMode.EQUALS_ONE_OF,
            ),
        )
        assertFalse(
            QuickKeywordRuleMatcher.matchesKeywords(
                "paid to swiggy",
                listOf("swiggy"),
                QuickKeywordTextMatchMode.EQUALS_ONE_OF,
            ),
        )
    }

    @Test
    fun matchesKeywords_notContainsAny() {
        val haystack = "paid to merchant"
        assertTrue(
            QuickKeywordRuleMatcher.matchesKeywords(
                haystack,
                listOf("swiggy", "zomato"),
                QuickKeywordTextMatchMode.NOT_CONTAINS_ANY,
            ),
        )
        assertFalse(
            QuickKeywordRuleMatcher.matchesKeywords(
                "swiggy order",
                listOf("swiggy"),
                QuickKeywordTextMatchMode.NOT_CONTAINS_ANY,
            ),
        )
    }

    @Test
    fun ruleEngine_containsAnyKeywordOperator() {
        val rule = QuickKeywordRuleCompiler.compile(
            QuickKeywordRuleCompiler.QuickKeywordRuleInput(
                name = "Test",
                keywords = listOf("SwIgGy"),
                merchantLabel = "Food",
                categoryLabel = "Food",
            )
        )
        val engine = RuleEngine()
        val txn = TransactionEntity(
            amount = BigDecimal("299"),
            merchantName = "UPI",
            category = "Others",
            transactionType = TransactionType.EXPENSE,
            dateTime = LocalDateTime.now(),
            smsBody = "paid to swiggy for food",
            transactionHash = "x",
        )
        val (_, apps) = engine.evaluateRules(txn, txn.smsBody, listOf(rule))
        assertEquals(1, apps.size)
    }
}
