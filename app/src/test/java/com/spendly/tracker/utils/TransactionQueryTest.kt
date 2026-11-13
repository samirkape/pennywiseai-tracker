package com.spendly.tracker.utils

import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.database.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class TransactionQueryTest {

    private fun transaction(
        merchantName: String = "Store",
        description: String? = null,
        smsBody: String? = null,
        tags: String = "",
        category: String = "Shopping",
        type: TransactionType = TransactionType.EXPENSE,
        amount: BigDecimal = BigDecimal("100"),
        dateTime: LocalDateTime = LocalDateTime.of(2026, 1, 15, 12, 30),
        bankName: String? = null,
        accountNumber: String? = null,
        reference: String? = null,
        smsSender: String? = null,
    ) = TransactionEntity(
        amount = amount,
        merchantName = merchantName,
        category = category,
        transactionType = type,
        dateTime = dateTime,
        transactionHash = "hash",
        description = description,
        smsBody = smsBody,
        tags = tags,
        bankName = bankName,
        accountNumber = accountNumber,
        reference = reference,
        smsSender = smsSender,
    )

    // ------------------------------------------------------------------
    // Scoped matching (the "UPI" problem)
    // ------------------------------------------------------------------

    @Test
    fun unscopedTerm_matchesSmsBody() {
        val tx = transaction(merchantName = "Netflix", smsBody = "Spent Rs 199 via UPI")
        assertTrue(TransactionQueryParser.parse("upi").matches(tx))
    }

    @Test
    fun merchantScoped_containsOnlyLooksAtMerchant() {
        val tx = transaction(merchantName = "Netflix", smsBody = "Spent Rs 199 via UPI")
        assertFalse(TransactionQueryParser.parse("merchant:upi").matches(tx))
        assertTrue(TransactionQueryParser.parse("merchant:netf*").matches(tx))
    }

    @Test
    fun merchantQuoted_isExactMatch() {
        val exact = transaction(merchantName = "UPI")
        val partial = transaction(merchantName = "UPI PAY")
        assertTrue(TransactionQueryParser.parse("merchant:\"UPI\"").matches(exact))
        assertFalse(TransactionQueryParser.parse("merchant:\"UPI\"").matches(partial))
        assertTrue(TransactionQueryParser.parse("merchant:\"upi*\"").matches(partial))
    }

    @Test
    fun nameIsAnAliasForMerchant() {
        val tx = transaction(merchantName = "UPI")
        assertTrue(TransactionQueryParser.parse("name:\"UPI\"").matches(tx))
    }

    @Test
    fun quotedPhrase_isBroadFreeText() {
        val tx = transaction(description = "team lunch reimbursement")
        assertTrue(TransactionQueryParser.parse("\"team lunch\"").matches(tx))
        assertFalse(TransactionQueryParser.parse("\"lunch team\"").matches(tx))
    }

    // ------------------------------------------------------------------
    // Field matching
    // ------------------------------------------------------------------

    @Test
    fun tags_matchWholeTokens() {
        val tx = transaction(tags = "work, reimbursable")
        assertTrue(TransactionQueryParser.parse("tag:\"work\"").matches(tx))
        assertTrue(TransactionQueryParser.parse("tag:reimb").matches(tx))
        assertFalse(TransactionQueryParser.parse("tag:\"reimburs\"").matches(tx))
    }

    @Test
    fun descriptionField() {
        val tx = transaction(description = "Coffee with team")
        assertTrue(TransactionQueryParser.parse("desc:coffee").matches(tx))
        assertTrue(TransactionQueryParser.parse("note:\"Coffee with team\"").matches(tx))
        assertFalse(TransactionQueryParser.parse("desc:coffee").matches(transaction()))
    }

    @Test
    fun smsField() {
        val tx = transaction(smsBody = "Rs 500 debited a/c XX1234")
        assertTrue(TransactionQueryParser.parse("sms:debited").matches(tx))
        assertFalse(TransactionQueryParser.parse("sms:debited").matches(transaction()))
    }

    @Test
    fun categoryField() {
        val tx = transaction(category = "Food & Drink")
        assertTrue(TransactionQueryParser.parse("category:\"food & drink\"").matches(tx))
        assertTrue(TransactionQueryParser.parse("cat:food").matches(tx))
    }

    @Test
    fun bankField() {
        val tx = transaction(bankName = "HDFC BANK")
        assertTrue(TransactionQueryParser.parse("bank:hdfc").matches(tx))
        assertFalse(TransactionQueryParser.parse("bank:hdfc").matches(transaction()))
    }

    @Test
    fun accountField_matchesLast4() {
        val tx = transaction(accountNumber = "5123456789012345")
        assertTrue(TransactionQueryParser.parse("account:2345").matches(tx))
        assertFalse(TransactionQueryParser.parse("account:2345").matches(transaction()))
    }

    @Test
    fun referenceField() {
        val tx = transaction(reference = "UPI/123456789")
        assertTrue(TransactionQueryParser.parse("ref:123456789").matches(tx))
        assertTrue(TransactionQueryParser.parse("ref:\"UPI/123456789\"").matches(tx))
        assertFalse(TransactionQueryParser.parse("ref:987").matches(tx))
    }

    @Test
    fun currencyField() {
        val tx = transaction()
        assertTrue(TransactionQueryParser.parse("currency:inr").matches(tx))
        assertFalse(TransactionQueryParser.parse("currency:usd").matches(tx))
    }

    // ------------------------------------------------------------------
    // Type
    // ------------------------------------------------------------------

    @Test
    fun type_matchesAliasesAndPrefixes() {
        assertTrue(TransactionQueryParser.parse("type:expense").matches(transaction()))
        assertTrue(TransactionQueryParser.parse("type:exp").matches(transaction()))
        assertTrue(TransactionQueryParser.parse("type:income").matches(transaction(type = TransactionType.INCOME)))
        assertTrue(TransactionQueryParser.parse("type:credit").matches(transaction(type = TransactionType.CREDIT)))
        assertTrue(TransactionQueryParser.parse("type:transfer").matches(transaction(type = TransactionType.TRANSFER)))
        assertTrue(TransactionQueryParser.parse("type:invest").matches(transaction(type = TransactionType.INVESTMENT)))
        assertFalse(TransactionQueryParser.parse("type:expense").matches(transaction(type = TransactionType.TRANSFER)))
    }

    // ------------------------------------------------------------------
    // Amount
    // ------------------------------------------------------------------

    @Test
    fun amount_equality() {
        assertTrue(TransactionQueryParser.parse("amount:100").matches(transaction()))
        assertTrue(TransactionQueryParser.parse("amount:100.0").matches(transaction()))
        assertFalse(TransactionQueryParser.parse("amount:101").matches(transaction()))
    }

    @Test
    fun amount_comparisons() {
        assertTrue(TransactionQueryParser.parse("amount>99.99").matches(transaction()))
        assertTrue(TransactionQueryParser.parse("amount>=100").matches(transaction()))
        assertTrue(TransactionQueryParser.parse("amount<101").matches(transaction()))
        assertTrue(TransactionQueryParser.parse("amount<=100").matches(transaction()))
        assertFalse(TransactionQueryParser.parse("amount>100").matches(transaction()))
    }

    @Test
    fun amount_range_isInclusive() {
        assertTrue(TransactionQueryParser.parse("amount:100-200").matches(transaction()))
        assertTrue(TransactionQueryParser.parse("amount:50-100").matches(transaction()))
        assertFalse(TransactionQueryParser.parse("amount:101-200").matches(transaction()))
    }

    // ------------------------------------------------------------------
    // Date
    // ------------------------------------------------------------------

    @Test
    fun date_isoDay_isWholeDay() {
        val tx = transaction(dateTime = LocalDateTime.of(2026, 1, 15, 12, 30))
        assertTrue(TransactionQueryParser.parse("date:2026-01-15").matches(tx))
        assertFalse(TransactionQueryParser.parse("date:2026-01-16").matches(tx))
    }

    @Test
    fun date_comparisons() {
        val tx = transaction(dateTime = LocalDateTime.of(2026, 1, 15, 12, 30))
        assertTrue(TransactionQueryParser.parse("date>2026-01-01").matches(tx))
        assertTrue(TransactionQueryParser.parse("date>=2026-01-15").matches(tx))
        assertTrue(TransactionQueryParser.parse("date<2026-02-01").matches(tx))
        assertFalse(TransactionQueryParser.parse("date>2026-01-15").matches(tx))
    }

    @Test
    fun date_isoMonth() {
        val tx = transaction(dateTime = LocalDateTime.of(2026, 1, 15, 12, 30))
        assertTrue(TransactionQueryParser.parse("date:\"2026-01\"").matches(tx))
        assertFalse(TransactionQueryParser.parse("date:\"2026-02\"").matches(tx))
    }

    @Test
    fun date_customRange_inclusive() {
        val tx = transaction(dateTime = LocalDateTime.of(2026, 1, 15, 12, 30))
        assertTrue(TransactionQueryParser.parse("date:2026-01-01-2026-01-31").matches(tx))
        assertFalse(TransactionQueryParser.parse("date:2026-02-01-2026-03-01").matches(tx))
        // End-of-range day itself matches (inclusive).
        assertTrue(
            TransactionQueryParser.parse("date:2026-01-15-2026-01-15")
                .matches(transaction(dateTime = LocalDateTime.of(2026, 1, 15, 23, 59)))
        )
    }

    @Test
    fun date_customRange_reversedBounds_areSwapped() {
        val tx = transaction(dateTime = LocalDateTime.of(2026, 1, 15, 12, 30))
        assertTrue(TransactionQueryParser.parse("date:2026-01-31-2026-01-01").matches(tx))
    }

    @Test
    fun date_namedRanges() {
        val now = LocalDateTime.now()
        assertTrue(TransactionQueryParser.parse("date:today").matches(transaction(dateTime = now)))
        assertFalse(TransactionQueryParser.parse("date:yesterday").matches(transaction(dateTime = now)))
        assertTrue(TransactionQueryParser.parse("date:\"this month\"").matches(transaction(dateTime = now)))
        assertTrue(TransactionQueryParser.parse("date:\"this year\"").matches(transaction(dateTime = now)))
        assertTrue(TransactionQueryParser.parse("date:\"last 30 days\"").matches(transaction(dateTime = now)))
        assertFalse(TransactionQueryParser.parse("date:\"last 30 days\"").matches(transaction(dateTime = LocalDateTime.of(2020, 1, 1, 0, 0))))
    }

    @Test
    fun date_unknownValue_isParseError() {
        val query = TransactionQueryParser.parse("date:notadate")
        assertNotNull(query.error)
    }

    @Test
    fun senderField_matchesSmsSender() {
        val tx = transaction(smsSender = "HDFCBK")
        assertTrue(TransactionQueryParser.parse("sender:hdfc").matches(tx))
        assertTrue(TransactionQueryParser.parse("from:hdfcbk").matches(tx))
        assertFalse(TransactionQueryParser.parse("sender:ICICI").matches(tx))
    }

    @Test
    fun hasDateClause_isFalseForDateLookalikeValues() {
        // A date-ish *value* in another field must not hijack the period filter.
        assertFalse(TransactionQueryParser.parse("merchant:\"date:2026\"").hasDateClause())
        assertFalse(TransactionQueryParser.parse("desc:\"last 90 days\"").hasDateClause())
        assertFalse(TransactionQueryParser.parse("note:\"yesterday\"").hasDateClause())
        assertFalse(TransactionQueryParser.parse("sender:today").hasDateClause())
    }

    @Test
    fun hasDateClause_throughBooleanOperators() {
        assertTrue(TransactionQueryParser.parse("NOT date:today").hasDateClause())
        assertTrue(TransactionQueryParser.parse("(amount>100 OR date:2026-01-01)").hasDateClause())
        assertTrue(TransactionQueryParser.parse("date:\"this week\" OR merchant:x").hasDateClause())
        assertFalse(TransactionQueryParser.parse("(merchant:x OR category:Food) AND amount>10").hasDateClause())
    }

    @Test
    fun date_presetClause_combinesWithOtherFields() {
        val inside = transaction(
            merchantName = "Swiggy",
            category = "Food & Dining",
            dateTime = LocalDateTime.now().minusDays(3),
        )
        val outsideWindow = transaction(
            merchantName = "Swiggy",
            category = "Food & Dining",
            dateTime = LocalDateTime.now().minusDays(400),
        )
        val wrongCategory = transaction(
            merchantName = "Swiggy",
            category = "Travel",
            dateTime = LocalDateTime.now().minusDays(3),
        )
        val q = TransactionQueryParser.parse(
            "date:\"last 30 days\" AND category:\"Food & Dining\" AND merchant:swiggy"
        )
        assertNull(q.error)
        assertTrue(q.matches(inside))
        assertFalse(q.matches(outsideWindow))
        assertFalse(q.matches(wrongCategory))
    }

    @Test
    fun everyField_combinesWithDateClause() {
        val recent = LocalDateTime.now().minusDays(2)
        val tx = transaction(
            merchantName = "Swiggy",
            description = "order 123",
            smsBody = "debited rs 250 at swiggy",
            tags = "delivery, food",
            category = "Food & Dining",
            type = TransactionType.EXPENSE,
            amount = BigDecimal("250"),
            dateTime = recent,
            bankName = "HDFC Bank",
            accountNumber = "5123456789012345",
            reference = "UTR998877",
            smsSender = "HDFCBK",
        )
        val clauses = listOf(
            "merchant:swiggy",
            "name:swiggy",
            "desc:order",
            "note:\"order 123\"",
            "sms:swiggy",
            "tag:delivery",
            "category:\"Food & Dining\"",
            "type:expense",
            "amount>100",
            "amount:250",
            "bank:hdfc",
            "account:2345",
            "ref:utr998877",
            "utr:998877",
            "sender:hdfcbk",
            "from:hdfc",
        )
        clauses.forEach { clause ->
            val alone = TransactionQueryParser.parse(clause)
            assertNull("$clause should parse", alone.error)
            assertTrue("$clause alone should match", alone.matches(tx))

            val withDate = TransactionQueryParser.parse("$clause AND date:\"last 30 days\"")
            assertNull("$clause + date should parse", withDate.error)
            assertTrue("$clause + date should match", withDate.matches(tx))
            assertTrue("$clause + date overrides period", withDate.hasDateClause())

            // Same clause against a transaction outside the date window → excluded.
            val old = transaction(
                merchantName = "Swiggy",
                description = "order 123",
                smsBody = "debited rs 250 at swiggy",
                tags = "delivery, food",
                category = "Food & Dining",
                type = TransactionType.EXPENSE,
                amount = BigDecimal("250"),
                dateTime = LocalDateTime.now().minusDays(400),
                bankName = "HDFC Bank",
                accountNumber = "5123456789012345",
                reference = "UTR998877",
                smsSender = "HDFCBK",
            )
            assertFalse("$clause + date must exclude old rows", withDate.matches(old))
        }
    }

    // ------------------------------------------------------------------
    // Boolean operators
    // ------------------------------------------------------------------

    @Test
    fun implicitAnd_betweenClauses() {
        val tx = transaction(merchantName = "Swiggy", amount = BigDecimal("250"))
        assertTrue(TransactionQueryParser.parse("merchant:swiggy amount>200").matches(tx))
        assertFalse(TransactionQueryParser.parse("merchant:swiggy amount>300").matches(tx))
    }

    @Test
    fun and_or_explicit() {
        val tx = transaction(merchantName = "Swiggy", category = "Food")
        assertTrue(TransactionQueryParser.parse("merchant:swiggy AND category:food").matches(tx))
        assertFalse(TransactionQueryParser.parse("merchant:swiggy AND category:travel").matches(tx))
        assertTrue(TransactionQueryParser.parse("merchant:swiggy OR merchant:zomato").matches(tx))
        assertTrue(TransactionQueryParser.parse("merchant:zomato OR merchant:swiggy").matches(tx))
        assertFalse(TransactionQueryParser.parse("merchant:zomato OR merchant:amazon").matches(tx))
    }

    @Test
    fun not_negates() {
        val tx = transaction(merchantName = "Swiggy")
        assertTrue(TransactionQueryParser.parse("NOT merchant:zomato").matches(tx))
        assertFalse(TransactionQueryParser.parse("NOT merchant:swiggy").matches(tx))
        assertTrue(TransactionQueryParser.parse("merchant:swiggy NOT category:travel").matches(tx))
    }

    @Test
    fun parentheses_grouping() {
        val a = transaction(merchantName = "Swiggy", category = "Food")
        val b = transaction(merchantName = "Swiggy", category = "Travel")
        val c = transaction(merchantName = "Zomato", category = "Travel")

        val query = TransactionQueryParser.parse("(merchant:swiggy AND category:food) OR merchant:zomato")
        assertTrue(query.matches(a))
        assertFalse(query.matches(b))
        assertTrue(query.matches(c))

        // Without parentheses, AND binds tighter than OR
        val noParens = TransactionQueryParser.parse("merchant:swiggy AND category:food OR merchant:zomato")
        assertTrue(noParens.matches(a))
        assertFalse(noParens.matches(b))
        assertTrue(noParens.matches(c))
    }

    @Test
    fun or_disablesLegacyWholeStringMatching() {
        // Typing "a or b" used to be a contiguous string search; now it's an OR.
        val tx = transaction(merchantName = "Swiggy")
        assertTrue(TransactionQueryParser.parse("swiggy or netflix").matches(tx))
        assertTrue(TransactionQueryParser.parse("swiggy OR netflix").matches(transaction(merchantName = "Netflix")))
    }

    // ------------------------------------------------------------------
    // Wizard editing helpers (flatten / remove / serialize / append)
    // ------------------------------------------------------------------

    @Test
    fun flatAndChain_flattensFieldClauses() {
        val chain = parseFlatAndChain("merchant:swiggy tag:\"work\" NOT type:transfer")
        assertNotNull(chain)
        // merchant field, tag field, and a NOT-wrapped type field
        assertEquals(3, chain!!.size)
        assertEquals(2, chain.filterIsInstance<SearchNode.Field>().size)
    }

    @Test
    fun hasDateClause_detectsDateFields() {
        assertTrue(TransactionQueryParser.parse("date:\"last 90 days\"").hasDateClause())
        assertTrue(TransactionQueryParser.parse("date:2026-10-28").hasDateClause())
        assertTrue(TransactionQueryParser.parse("date:2026-01-01-2026-01-31").hasDateClause())
        assertTrue(TransactionQueryParser.parse("date>2026-01-01").hasDateClause())
        assertTrue(
            TransactionQueryParser.parse("merchant:swiggy AND date:\"this month\"")
                .hasDateClause()
        )
        assertFalse(TransactionQueryParser.parse("team lunch").hasDateClause())
        assertFalse(TransactionQueryParser.parse("merchant:swiggy amount>100").hasDateClause())
        assertFalse(TransactionQueryParser.parse("").hasDateClause())
    }

    @Test
    fun flatAndChain_nullForPureFreeText() {
        assertNull(parseFlatAndChain("team lunch"))
        assertNull(parseFlatAndChain(""))
    }

    @Test
    fun flatAndChain_keepsOrGroupAsSingleChip() {
        val chain = parseFlatAndChain("merchant:swiggy AND (category:\"Food\" OR category:\"Travel\")")
        assertNotNull(chain)
        assertEquals(2, chain!!.size)
        assertTrue(chain.last() is SearchNode.Or)
    }

    @Test
    fun removeClause_removesByIndex_andReqesualizesRest() {
        val query = "merchant:swiggy and amount>100 and tag:\"work\""
        val withoutAmount = removeFlatClause(query, 1)
        assertFalse(withoutAmount.contains("amount"))
        assertTrue(withoutAmount.contains("merchant:swiggy") && withoutAmount.contains("tag:\"work\""))
    }

    @Test
    fun removeClause_lastRemaining_returnsEmpty() {
        assertTrue(removeFlatClause("merchant:swiggy", 0).isBlank())
    }

    @Test
    fun flatAndChain_invalidQuery_fallsBackToNoEditing() {
        assertNull(parseFlatAndChain("merchant:\"unterminated"))
    }

    @Test
    fun appendClause_joinsWithAnd() {
        assertEquals("merchant:swiggy and amount>100", appendClauseText("merchant:swiggy ", "amount>100"))
        assertEquals("amount>100", appendClauseText("", "amount>100"))
    }

    @Test
    fun toQueryString_roundTrips() {
        val query = "merchant:swiggy and amount>100.5 and tag:\"work\""
        val chain = parseFlatAndChain(query)!!
        val roundTripped = chain.joinToString(" and ") { it.toQueryString() }
        assertEquals(query, roundTripped)
    }

    // ------------------------------------------------------------------
    // Errors & fallback
    // ------------------------------------------------------------------

    @Test
    fun unterminatedQuote_isError_andFallsBackToPlainText() {
        val tx = transaction(smsBody = "Spent via \"UPI\" link")
        val query = TransactionQueryParser.parse("upi link")
        assertNull(query.error)

        val broken = TransactionQueryParser.parse("\"missing quote")
        assertNotNull(broken.error)
        assertFalse(broken.error.isNullOrBlank())
    }

    @Test
    fun unbalancedParen_isError() {
        assertNotNull(TransactionQueryParser.parse("(merchant:upi").error)
        assertNotNull(TransactionQueryParser.parse("merchant:upi)").error)
        assertNull(TransactionQueryParser.parse("(merchant:upi)").error)
    }

    @Test
    fun missingFieldValue_isError() {
        assertNotNull(TransactionQueryParser.parse("merchant:").error)
        assertNull(TransactionQueryParser.parse("merchant:\"UPI\"").error)
        assertNull(TransactionQueryParser.parse("amount> 100").error)
    }

    @Test
    fun invalidAmount_isError() {
        assertNotNull(TransactionQueryParser.parse("amount:abc").error)
    }

    // ------------------------------------------------------------------
    // Type-ahead (in-progress clause completion)
    // ------------------------------------------------------------------

    @Test
    fun inProgressClause_parsesTrailingToken() {
        val s = parseInProgressClause("merchant:ne")
        assertNotNull(s)
        assertEquals(SearchField.MERCHANT, s!!.field)
        assertEquals("ne", s.partialValue)

        val typed = parseInProgressClause("merchant:swiggy and amount")
        assertTrue(typed != null && typed.field == SearchField.AMOUNT && typed.partialValue.isEmpty())

        // A bare field name (before ":" is typed) suggests immediately
        val bare = parseInProgressClause("merchant:swiggy and tag")
        assertTrue(bare != null && bare.field == SearchField.TAG)
    }

    @Test
    fun inProgressClause_handlesComparisonPrefix() {
        val s = parseInProgressClause("amount:")
        assertEquals(SearchField.AMOUNT, s!!.field)
        // Trailing comparison operators are not suggestable values
        val gt = parseInProgressClause("merchant:swiggy and amount>")
        assertTrue(gt == null || gt.partialValue.isEmpty())
    }

    @Test
    fun inProgressClause_nullForPlainTextAndCommitted() {
        assertNull(parseInProgressClause("team lunch"))
        assertNull(parseInProgressClause("merchant:swiggy ")) // committed (trailing space)
        assertNull(parseInProgressClause("upi")) // no field syntax
        assertNull(parseInProgressClause("swiggy:x")) // "swiggy" is not a field alias
    }

    @Test
    fun inProgressClause_quotedPartialSuggests() {
        val s = parseInProgressClause("merchant:swiggy and tag:\"wor")
        assertEquals(SearchField.TAG, s!!.field)
        assertEquals("wor", s.partialValue)
    }

    @Test
    fun replaceInProgressClause_swapsOnlyTrailingToken() {
        assertEquals(
            "merchant:\"Netflix\"",
            replaceInProgressClause("merchant:ne", "merchant:\"Netflix\"")
        )
        assertEquals(
            "merchant:swiggy and tag:\"work\"",
            replaceInProgressClause("merchant:swiggy and tag:wor", "tag:\"work\"")
        )
        assertEquals("merchant:\"Netflix\"", replaceInProgressClause("", "merchant:\"Netflix\""))
    }

    // ------------------------------------------------------------------
    // Legacy compatibility (plain text queries)
    // ------------------------------------------------------------------

    @Test
    fun plainText_behavesLikeLegacyMatcher() {
        listOf("amazon", "lunch", "reimbursable", "   ", "flight").forEach { query ->
            val tx = transaction(
                merchantName = "Amazon Pay",
                description = "Team lunch reimbursement",
                tags = "work, reimbursable",
            )
            val expected = TransactionSearchMatcher.matches(tx, query)
            val actual = TransactionQueryParser.parse(query).matches(tx)
            assertTrue("query=$query", expected == actual)
        }
    }

    @Test
    fun plainText_amountSubstringStillWorks() {
        assertTrue(TransactionQueryParser.parse("199").matches(transaction(amount = BigDecimal("1990"))))
    }

    @Test
    fun blankQuery_matchesEverything() {
        assertTrue(TransactionQueryParser.parse("").matches(transaction()))
        assertTrue(TransactionQueryParser.parse("   ").matches(transaction()))
    }
}
