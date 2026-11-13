package com.spendly.tracker.utils

import com.spendly.tracker.data.database.entity.TransactionEntity
import com.spendly.tracker.data.database.entity.TransactionType
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeParseException
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Kibana-style (KQL-inspired) query language for transaction search.
 *
 * Query syntax:
 * ```
 * merchant:"UPI"        // merchant name is exactly "UPI" (case-insensitive)
 * merchant:upi          // merchant name CONTAINS "upi" (scoped to merchant only)
 * merchant:upi*         // wildcard match against the whole merchant name
 * name:"UPI"            // "name" is an alias for merchant
 * tag:"work"            // exact tag token match (tags are comma-separated)
 * category:"Food"       // category contains / exact with quotes
 * type:expense          // expense|income|credit|transfer|investment (or a prefix)
 * bank:hdfc, account:1234 (last-4)
 * amount>1000           // >=, <, <= also supported
 * amount:100-200        // inclusive numeric range
 * date:today            // today|yesterday|this week|this month|this year|last N days
 * date:2026-01-15       // ISO date (whole day); date>2026-01-01 etc. for bounds
 * "phone bill"          // free text: broad match across all fields (legacy behavior)
 *                       // when the query has no field/value syntax at all
 * a AND b / a OR b / NOT a / (a OR b) AND c   // implicit AND between clauses
 * ```
 *
 * Quoted values mean "match the whole value", unquoted values mean "contains".
 * Wildcards `*` switch a value to full-value pattern matching.
 *
 * Parse errors never crash the search: [TransactionQuery.error] is set and the
 * query falls back to plain-text matching; the UI shows the error inline.
 */
object TransactionQueryParser {

    class QueryParseException(message: String) : Exception(message)

    fun parse(raw: String): TransactionQuery {
        return try {
            val tokens = tokenize(raw)
            if (tokens.isEmpty()) {
                TransactionQuery(raw, root = null, error = null)
            } else {
                TransactionQuery(raw, root = Parser(tokens).parse(), error = null)
            }
        } catch (e: QueryParseException) {
            TransactionQuery(raw, root = null, error = e.message)
        }
    }
}

/**
 * A parsed query. Evaluate with [matches]; on parse errors this transparently
 * falls back to legacy plain-text matching.
 */
data class TransactionQuery(
    val raw: String,
    internal val root: SearchNode?,
    val error: String?
) {
    fun matches(transaction: TransactionEntity): Boolean {
        if (raw.isBlank()) return true

        // Plain free-text queries (no field syntax) keep the exact legacy
        // behavior: contiguous contains across all fields, incl. amount/type
        // keyword matching ("root == null" also covers parse errors).
        val node = root
        if (node == null || node.isPureFreeText()) {
            return TransactionSearchMatcher.matchesText(transaction, joinedFreeText(node) ?: raw)
        }
        return node.matches(transaction)
    }

    /**
     * True when the query carries an explicit `date:…` clause. Such a clause
     * is the user's chosen time window, so callers may let it supersede the
     * screen's period filter (a broader search range can't narrow the period,
     * which makes the filter appear inert).
     */
    fun hasDateClause(): Boolean = hasFieldClause(root, SearchField.DATE)

    private fun hasFieldClause(node: SearchNode?, field: SearchField): Boolean = when (node) {
        null -> false
        is SearchNode.Field -> node.field == field
        is SearchNode.And -> hasFieldClause(node.left, field) || hasFieldClause(node.right, field)
        is SearchNode.Or -> hasFieldClause(node.left, field) || hasFieldClause(node.right, field)
        is SearchNode.Not -> hasFieldClause(node.inner, field)
        else -> false
    }

    private fun joinedFreeText(node: SearchNode?): String? {
        if (node == null) return null
        val leaves = mutableListOf<String>()
        if (node.collectFreeText(leaves)) return leaves.joinToString(" ")
        return null
    }
}

// ---------------------------------------------------------------------------
// AST
// ---------------------------------------------------------------------------

sealed class SearchNode {

    /** Every leaf is free text -> legacy full-string matching. */
    fun isPureFreeText(): Boolean = collectFreeText(mutableListOf())

    /** Collects free-text leaf values in order; false when any leaf isn't free text. */
    abstract fun collectFreeText(out: MutableList<String>): Boolean

    abstract fun matches(txn: TransactionEntity): Boolean

    data class And(val left: SearchNode, val right: SearchNode) : SearchNode() {
        override fun collectFreeText(out: MutableList<String>) =
            left.collectFreeText(out) && right.collectFreeText(out)

        override fun matches(txn: TransactionEntity) = left.matches(txn) && right.matches(txn)
    }

    data class Or(val left: SearchNode, val right: SearchNode) : SearchNode() {
        override fun collectFreeText(out: MutableList<String>): Boolean = false

        override fun matches(txn: TransactionEntity) = left.matches(txn) || right.matches(txn)
    }

    data class Not(val inner: SearchNode) : SearchNode() {
        override fun collectFreeText(out: MutableList<String>): Boolean = false

        override fun matches(txn: TransactionEntity) = !inner.matches(txn)
    }

    /** Term/phrase matched broadly across all fields (legacy semantics). */
    data class FreeText(val text: String) : SearchNode() {
        override fun collectFreeText(out: MutableList<String>): Boolean {
            out.add(text)
            return true
        }

        override fun matches(txn: TransactionEntity) = TransactionSearchMatcher.matchesText(txn, text)
    }

    /** Scoped clause: <field> <op> <value>. */
    data class Field(
        val field: SearchField,
        val op: CompareOp,
        val value: String,
        val quoted: Boolean,
    ) : SearchNode() {
        override fun collectFreeText(out: MutableList<String>): Boolean = false

        override fun matches(txn: TransactionEntity) = SearchFieldEvaluator.evaluate(txn, this)
    }
}

/** A `<field>` that carries an operator but whose value sits in the next token. */
data class PendingClause(val field: SearchField, val op: CompareOp)

/** Result of turning a word token into a clause: either a node or a pending `<field><op>` awaiting its value. */
private sealed class ParseClause {
    class Node(val node: SearchNode) : ParseClause()
    class Pending(val pending: PendingClause) : ParseClause()
}

enum class CompareOp { EQ, GT, GTE, LT, LTE }

enum class SearchField(val aliases: List<String>, val label: String) {
    MERCHANT(listOf("merchant", "name", "merchantname"), "Merchant"),
    DESCRIPTION(listOf("description", "desc", "note", "notes"), "Description"),
    SMS(listOf("sms", "body", "message"), "SMS"),
    TAG(listOf("tag", "tags"), "Tag"),
    CATEGORY(listOf("category", "cat"), "Category"),
    TYPE(listOf("type", "kind"), "Type"),
    BANK(listOf("bank", "bankname"), "Bank"),
    ACCOUNT(listOf("account", "acc", "card"), "Account"),
    AMOUNT(listOf("amount", "amt", "rupees"), "Amount"),
    DATE(listOf("date", "on", "when"), "Date"),
    REFERENCE(listOf("ref", "reference", "utr", "refno"), "Reference"),
    SENDER(listOf("sender", "from"), "Sender"),
    CURRENCY(listOf("currency", "cur"), "Currency");

    fun primaryName(): String = aliases.first()

    companion object {
        private val byAlias: Map<String, SearchField> =
            entries.flatMap { f -> f.aliases.map { it to f } }.toMap()

        fun fromAlias(alias: String): SearchField? = byAlias[alias.lowercase(Locale.ROOT)]
    }
}

// ---------------------------------------------------------------------------
// Tokenizer
// ---------------------------------------------------------------------------

private sealed class Token {
    object LParen : Token()
    object RParen : Token()
    data class Word(val text: String) : Token()
    /** Value/phrase enclosed in double quotes. */
    data class Quoted(val text: String) : Token()
}

private fun tokenize(raw: String): List<Token> {
    val tokens = mutableListOf<Token>()
    var i = 0
    while (i < raw.length) {
        when (raw[i]) {
            ' ', '\t', '\n', '\r' -> i++
            '(' -> {
                tokens.add(Token.LParen); i++
            }
            ')' -> {
                tokens.add(Token.RParen); i++
            }
            '"' -> {
                val end = raw.indexOf('"', i + 1)
                if (end == -1) throw TransactionQueryParser.QueryParseException("Missing closing quote")
                tokens.add(Token.Quoted(raw.substring(i + 1, end)))
                i = end + 1
            }
            else -> {
                var j = i
                while (j < raw.length && !raw[j].isWhitespace() &&
                    raw[j] != '(' && raw[j] != ')' && raw[j] != '"'
                ) j++
                tokens.add(Token.Word(raw.substring(i, j)))
                i = j
            }
        }
    }
    return tokens
}

// ---------------------------------------------------------------------------
// Parser (recursive descent)
//
//   orExpr    := andExpr (OR andExpr)*
//   andExpr   := unary ((AND)? unary)*     // implicit AND between neighbors
//   unary     := NOT unary | '(' orExpr ')' | clause
//   clause    := <field><op>value | term | "phrase"
// ---------------------------------------------------------------------------

private class Parser(private val tokens: List<Token>) {

    private var pos = 0

    fun parse(): SearchNode {
        val node = parseOr()
        if (!atEnd()) {
            val name = when (val token = peek()) {
                is Token.RParen -> ")"
                is Token.LParen -> "("
                is Token.Word -> token.text
                is Token.Quoted -> "\"${token.text}\""
                null -> ""
            }
            throw TransactionQueryParser.QueryParseException("Unexpected \"$name\"")
        }
        return node
    }

    fun parseOr(): SearchNode {
        var left = parseAnd()
        while (peekIsKeyword("or")) {
            next()
            left = SearchNode.Or(left, parseAnd())
        }
        return left
    }

    fun parseAnd(): SearchNode {
        var left = parseUnary()
        while (true) {
            val token = peek() ?: break
            when {
                token is Token.Word && token.text.equals("and", ignoreCase = true) -> {
                    next()
                    left = SearchNode.And(left, parseUnary())
                }
                token is Token.Word && token.text.equals("or", ignoreCase = true) -> break
                token is Token.RParen -> break
                else -> left = SearchNode.And(left, parseUnary())
            }
        }
        return left
    }

    fun parseUnary(): SearchNode {
        val token = peek() ?: throw TransactionQueryParser.QueryParseException("Incomplete query")
        return when {
            token is Token.Word && token.text.equals("not", ignoreCase = true) -> {
                next()
                SearchNode.Not(parseUnary())
            }
            token is Token.LParen -> {
                next()
                val node = parseOr()
                if (peek() !is Token.RParen) {
                    throw TransactionQueryParser.QueryParseException("Missing closing parenthesis")
                }
                next()
                node
            }
            token is Token.Quoted -> {
                next()
                SearchNode.FreeText(token.text)
            }
            token is Token.Word -> {
                next()
                when (val result = tokenToClause(token.text)) {
                    is ParseClause.Pending -> resolvePending(result.pending)
                    is ParseClause.Node -> result.node
                }
            }
            else -> throw TransactionQueryParser.QueryParseException("Unexpected )")
        }
    }

    fun resolvePending(pending: PendingClause): SearchNode {
        val token = peek()
            ?: throw TransactionQueryParser.QueryParseException(
                "Missing value for \"${pending.field.primaryName()}\""
            )
        return when (token) {
            is Token.Quoted -> {
                next()
                makeField(pending.field, pending.op, token.text, quoted = true)
            }
            is Token.Word -> {
                next()
                makeField(pending.field, pending.op, token.text, quoted = false)
            }
            else -> throw TransactionQueryParser.QueryParseException(
                "Missing value for \"${pending.field.primaryName()}\""
            )
        }
    }

    /**
     * Turns a raw word token into a clause. A word like `merchant:upi` or
     * `amount>1000` becomes a field clause only when the part before the
     * operator is a known field alias; otherwise the whole word is free text.
     */
    fun tokenToClause(word: String): ParseClause {
        val triggerIdx = word.indexOfFirst { it == ':' || it == '<' || it == '>' }
        if (triggerIdx <= 0) return ParseClause.Node(SearchNode.FreeText(word))

        val alias = word.substring(0, triggerIdx)
        val field = SearchField.fromAlias(alias)
            ?: return ParseClause.Node(SearchNode.FreeText(word))

        val op: CompareOp
        var rest: String
        if (word[triggerIdx] == ':') {
            rest = word.substring(triggerIdx + 1)
            val (resolvedOp, value) = extractComparisonOperator(rest, fallback = CompareOp.EQ)
            op = resolvedOp
            rest = value
        } else {
            val (resolvedOp, value) = extractComparisonOperator(word.substring(triggerIdx), fallback = null)
            op = resolvedOp
            rest = value
        }
        if (rest.isEmpty()) return ParseClause.Pending(PendingClause(field, op))
        return ParseClause.Node(makeField(field, op, rest, quoted = false))
    }

    /** Leading `>=`, `<=`, `>`, `<` and the remaining value; null fallback only for non-colon positions. */
    private fun extractComparisonOperator(
        rest: String,
        fallback: CompareOp?,
    ): Pair<CompareOp, String> = when {
        rest.startsWith(">=") -> CompareOp.GTE to rest.substring(2)
        rest.startsWith("<=") -> CompareOp.LTE to rest.substring(2)
        rest.startsWith(">") -> CompareOp.GT to rest.substring(1)
        rest.startsWith("<") -> CompareOp.LT to rest.substring(1)
        fallback != null -> fallback to rest
        else -> throw TransactionQueryParser.QueryParseException("Missing value after field")
    }

    fun makeField(field: SearchField, op: CompareOp, value: String, quoted: Boolean): SearchNode.Field {
        // Validate numeric/date values eagerly so users get a parse error
        // message instead of silently matching nothing.
        when (field) {
            SearchField.AMOUNT -> SearchFieldEvaluator.parseAmount(value)
            SearchField.DATE -> SearchFieldEvaluator.tryParseDateRange(value)
                ?: throw TransactionQueryParser.QueryParseException("Unknown date \"$value\"")
            else -> Unit
        }
        return SearchNode.Field(field, op, value, quoted)
    }

    fun peek(): Token? = tokens.getOrNull(pos)

    fun next(): Token = tokens[pos++]

    fun atEnd(): Boolean = pos >= tokens.size

    fun peekIsKeyword(kw: String): Boolean =
        (peek() as? Token.Word)?.text?.equals(kw, ignoreCase = true) == true
}

// ---------------------------------------------------------------------------
// Evaluation
// ---------------------------------------------------------------------------

private object SearchFieldEvaluator {

    fun evaluate(txn: TransactionEntity, clause: SearchNode.Field): Boolean = when (clause.field) {
        SearchField.MERCHANT -> textMatches(txn.merchantName, clause)
        SearchField.DESCRIPTION -> textMatches(txn.description, clause)
        SearchField.SMS -> textMatches(txn.smsBody, clause)
        SearchField.CATEGORY -> textMatches(txn.category, clause)
        SearchField.BANK -> textMatches(txn.bankName, clause)
        SearchField.REFERENCE -> textMatches(txn.reference, clause)
        SearchField.SENDER -> textMatches(txn.smsSender, clause)
        SearchField.CURRENCY -> textMatches(txn.currency, clause)
        SearchField.TAG -> tagMatches(txn.tags, clause)
        SearchField.TYPE -> typeMatches(txn.transactionType, clause)
        SearchField.ACCOUNT -> accountMatches(txn.accountNumber, clause)
        SearchField.AMOUNT -> amountMatches(txn.amount, clause)
        SearchField.DATE -> dateMatches(txn.dateTime, clause)
    }

    /**
     * Quoted (or wildcard) values match the whole value; bare values (without
     * wildcard) do a contains match. Comparisons (`>`/`<`) are meaningless for
     * text fields and never match.
     */
    private fun textMatches(target: String?, clause: SearchNode.Field): Boolean {
        if (target.isNullOrEmpty()) return false
        if (clause.op != CompareOp.EQ) return false
        return if (clause.quoted || hasWildcard(clause.value)) {
            fullPatternMatches(clause.value, target)
        } else {
            target.contains(clause.value, ignoreCase = true)
        }
    }

    /** Tags are a single comma-separated string; match per-token. */
    private fun tagMatches(rawTags: String?, clause: SearchNode.Field): Boolean {
        val tokens = rawTags?.split(',')?.map { it.trim() } ?: emptyList()
        if (tokens.isEmpty()) return false
        return tokens.any { token ->
            if (token.isEmpty()) {
                false
            } else if (clause.quoted || hasWildcard(clause.value)) {
                fullPatternMatches(clause.value, token)
            } else {
                token.contains(clause.value, ignoreCase = true)
            }
        }
    }

    private fun typeMatches(type: TransactionType, clause: SearchNode.Field): Boolean {
        val aliases = typeAliases.getValue(type)
        val value = clause.value.trim().lowercase(Locale.ROOT)
        if (value.isEmpty()) return false
        return aliases.any { it == value || it.startsWith(value) }
    }

    private val typeAliases: Map<TransactionType, List<String>> = mapOf(
        TransactionType.EXPENSE to listOf("expense", "debit", "spend"),
        TransactionType.INCOME to listOf("income", "received"),
        TransactionType.CREDIT to listOf("credit", "cc", "creditcard", "card"),
        TransactionType.TRANSFER to listOf("transfer", "upitransfer"),
        TransactionType.INVESTMENT to listOf("investment", "invest", "invested", "mutualfund"),
    )

    /** Bare digits (or a `*digits` pattern) match the account/card last-4; otherwise text rules. */
    private fun accountMatches(accountNumber: String?, clause: SearchNode.Field): Boolean {
        if (accountNumber.isNullOrEmpty()) return false
        val value = clause.value.trim()
        if (value.isNotEmpty() && value.all { it.isDigit() } &&
            clause.op == CompareOp.EQ && !clause.quoted
        ) {
            return accountNumber.endsWith(value)
        }
        return textMatches(accountNumber, clause)
    }

    private fun amountMatches(amount: BigDecimal, clause: SearchNode.Field): Boolean {
        return when (val value = parseAmount(clause.value)) {
            is AmountValue.Range -> when (clause.op) {
                CompareOp.EQ -> amount >= value.start && amount <= value.end
                CompareOp.GT -> amount > value.end
                CompareOp.LT -> amount < value.start
                CompareOp.GTE -> amount >= value.start
                CompareOp.LTE -> amount <= value.end
            }
            is AmountValue.Single -> when (clause.op) {
                CompareOp.EQ -> amount.compareTo(value.value) == 0
                CompareOp.GT -> amount > value.value
                CompareOp.GTE -> amount >= value.value
                CompareOp.LT -> amount < value.value
                CompareOp.LTE -> amount <= value.value
            }
        }
    }

    /** "100,000.50" / "₹100" / "100-200" (inclusive range). Also used for parse-time validation. */
    internal fun parseAmount(raw: String): AmountValue {
        val cleaned = raw.filter { it.isDigit() || it == '.' || it == '-' }
        if (cleaned.isEmpty()) {
            throw TransactionQueryParser.QueryParseException("Invalid amount \"$raw\"")
        }
        val rangeMatch = Regex("^(\\d+(?:\\.\\d+)?)-(\\d+(?:\\.\\d+)?)$").find(cleaned)
        return if (rangeMatch != null) {
            try {
                AmountValue.Range(
                    BigDecimal(rangeMatch.groupValues[1]),
                    BigDecimal(rangeMatch.groupValues[2]),
                )
            } catch (e: NumberFormatException) {
                throw TransactionQueryParser.QueryParseException("Invalid amount \"$raw\"")
            }
        } else {
            try {
                AmountValue.Single(BigDecimal(cleaned))
            } catch (e: NumberFormatException) {
                throw TransactionQueryParser.QueryParseException("Invalid amount \"$raw\"")
            }
        }
    }

    private fun dateMatches(dateTime: LocalDateTime, clause: SearchNode.Field): Boolean {
        val range = tryParseDateRange(clause.value)
            ?: throw TransactionQueryParser.QueryParseException("Unknown date \"${clause.value}\"")
        return when (clause.op) {
            CompareOp.EQ -> dateTime >= range.start && dateTime <= range.end
            CompareOp.GT -> dateTime > range.end
            CompareOp.GTE -> dateTime >= range.start
            CompareOp.LT -> dateTime < range.start
            CompareOp.LTE -> dateTime <= range.end
        }
    }

    /**
     * Supports ISO dates (`2026-01-15`), ISO months (`2026-01`), and named
     * ranges (`today`, `yesterday`, `this week`, `this month`, `this year`,
     * `last N days`). Quoted values may contain spaces or underscores. Also
     * used for parse-time validation.
     */
    internal fun tryParseDateRange(raw: String): DateRange? {
        val normalized = raw.trim().lowercase(Locale.ROOT).replace(" ", "").replace("_", "")
        val today = LocalDate.now()

        return when {
            normalized == "today" -> dayRange(today)
            normalized == "yesterday" -> dayRange(today.minusDays(1))
            normalized == "thisweek" -> DateRange(
                today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(),
                today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).atTime(LocalTime.MAX)
            )
            normalized == "thismonth" -> {
                val month = YearMonth.from(today)
                DateRange(month.atDay(1).atStartOfDay(), month.atEndOfMonth().atTime(LocalTime.MAX))
            }
            normalized == "thisyear" -> DateRange(
                today.withDayOfYear(1).atStartOfDay(),
                today.with(TemporalAdjusters.lastDayOfYear()).atTime(LocalTime.MAX)
            )
            Regex("^last(\\d+)days$").matches(normalized) -> {
                val days = Regex("^last(\\d+)days$").find(normalized)!!.groupValues[1].toLong()
                DateRange(
                    today.minusDays(days - 1).atStartOfDay(),
                    today.atTime(LocalTime.MAX),
                )
            }
            Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(normalized) -> try {
                dayRange(LocalDate.parse(normalized))
            } catch (e: DateTimeParseException) {
                null
            }
            // Custom inclusive range: 2026-01-01-2026-01-31 (reversed bounds are swapped)
            Regex("^(\\d{4}-\\d{2}-\\d{2})-(\\d{4}-\\d{2}-\\d{2})$").matches(normalized) -> {
                val match = Regex("^(\\d{4}-\\d{2}-\\d{2})-(\\d{4}-\\d{2}-\\d{2})$").find(normalized)!!
                try {
                    val start = LocalDate.parse(match.groupValues[1])
                    val end = LocalDate.parse(match.groupValues[2])
                    if (start.isAfter(end)) dayRangeSpan(end, start) else dayRangeSpan(start, end)
                } catch (e: DateTimeParseException) {
                    null
                }
            }
            Regex("^\\d{4}-\\d{2}$").matches(normalized) -> try {
                val month = YearMonth.parse(normalized)
                DateRange(month.atDay(1).atStartOfDay(), month.atEndOfMonth().atTime(LocalTime.MAX))
            } catch (e: DateTimeParseException) {
                null
            }
            else -> null
        }
    }

    private fun dayRange(date: LocalDate) = DateRange(date.atStartOfDay(), date.atTime(LocalTime.MAX))

    /** Inclusive span of two days. */
    private fun dayRangeSpan(start: LocalDate, end: LocalDate) =
        DateRange(start.atStartOfDay(), end.atTime(LocalTime.MAX))

    private fun fullPatternMatches(patternValue: String, target: String): Boolean {
        if (!hasWildcard(patternValue)) {
            return target.equals(patternValue, ignoreCase = true)
        }
        return Regex(wildcardToRegex(patternValue), RegexOption.IGNORE_CASE)
            .matches(target)
    }

    private fun hasWildcard(value: String) = value.contains('*')

    private fun wildcardToRegex(pattern: String): String = buildString {
        for (c in pattern) {
            when (c) {
                '*' -> append(".*")
                '?' -> append(".")
                else -> append(Regex.escape(c.toString()))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Parse-time validated values (resolved by [SearchFieldEvaluator])
// ---------------------------------------------------------------------------

/** Numeric amount literal or inclusive range. */
internal sealed class AmountValue {
    data class Single(val value: BigDecimal) : AmountValue()
    data class Range(val start: BigDecimal, val end: BigDecimal) : AmountValue()
}

/** Resolved date value: a whole day/month/year period or a named range. */
internal class DateRange(val start: LocalDateTime, val end: LocalDateTime)

// ---------------------------------------------------------------------------
// Query editing support (used by the search wizard UI)
//
// The query string is the single source of truth. The wizard appends clauses
// as text and renders the current query as removable chips derived by parsing
// the string back into its AST.
// ---------------------------------------------------------------------------

/**
 * Flattens the query into the ordered list of clauses of a pure AND chain
 * (field clauses, free-text terms, `NOT` clauses, and whole OR-groups as
 * single chips). Returns null when the query is plain free text (natural
 * typing — no structured chips) or could not be parsed.
 */
fun parseFlatAndChain(raw: String): List<SearchNode>? {
    if (raw.isBlank()) return null
    val query = TransactionQueryParser.parse(raw)
    val root = query.root ?: return null
    if (root.isPureFreeText()) return null
    return flattenAndChain(root)
}

private fun flattenAndChain(node: SearchNode): List<SearchNode>? = when (node) {
    is SearchNode.And -> {
        val left = flattenAndChain(node.left)
        val right = flattenAndChain(node.right)
        if (left != null && right != null) left + right else null
    }
    is SearchNode.Or -> listOf(node)
    is SearchNode.Not -> listOf(node)
    is SearchNode.FreeText -> listOf(node)
    is SearchNode.Field -> listOf(node)
}

/** Removes the clause at [index] (as produced by [parseFlatAndChain]) from the query. */
fun removeFlatClause(raw: String, index: Int): String {
    val chain = parseFlatAndChain(raw) ?: return raw
    val remaining = chain.filterIndexed { i, _ -> i != index }
    return remaining.joinToString(" and ") { it.toQueryString() }
}

/** Appends a clause to an existing query, joining with an explicit AND. */
fun appendClauseText(existing: String, clause: String): String =
    if (existing.isBlank()) clause else "${existing.trimEnd()} and $clause"

/**
 * Serializes a node back to query text (round-trips through the parser).
 * Free text is re-quoted when it contains spaces; OR groups get parentheses.
 */
fun SearchNode.toQueryString(): String = when (this) {
    is SearchNode.And -> "${left.toQueryString()} and ${right.toQueryString()}"
    is SearchNode.Or -> "(${left.toQueryString()} or ${right.toQueryString()})"
    is SearchNode.Not -> "not ${inner.toQueryString()}"
    is SearchNode.FreeText -> if (text.contains(' ')) "\"$text\"" else text
    is SearchNode.Field -> serializeFieldClause(this)
}

private fun serializeFieldClause(clause: SearchNode.Field): String {
    val op = when (clause.op) {
        CompareOp.EQ -> ":"
        CompareOp.GT -> ">"
        CompareOp.GTE -> ">="
        CompareOp.LT -> "<"
        CompareOp.LTE -> "<="
    }
    val value = if (clause.quoted) "\"${clause.value}\"" else clause.value
    return "${clause.field.primaryName()}$op$value"
}

/** Escapes a user-provided value for embedding in a query clause. */
fun sanitizeQueryValue(value: String): String = value.replace("\"", " ").trim()

/**
 * The in-progress field clause at the end of the query being typed, e.g.
 * `merchant:ne`, `tag:` — used to offer tap-to-complete value suggestions.
 * Null when the query doesn't end on a partially typed field clause.
 */
data class PendingSuggestion(val field: SearchField, val partialValue: String)

fun parseInProgressClause(raw: String): PendingSuggestion? {
    // A committed clause ends with whitespace (or the query is empty).
    if (raw.isBlank() || raw.trimEnd() != raw) return null
    val word = raw.substringAfterLast(' ')
    val triggerIdx = word.indexOfFirst { it == ':' || it == '<' || it == '>' }
    if (triggerIdx <= 0) {
        // Bare field name being typed (e.g. "... and merchant") suggests directly;
        // unknown words are plain free text.
        return SearchField.fromAlias(word)?.let { PendingSuggestion(it, "") }
    }
    val field = SearchField.fromAlias(word.substring(0, triggerIdx)) ?: return null
    var rest = word.substring(triggerIdx + 1)
    listOf(">=", "<=", ">", "<").forEach { op -> rest = rest.removePrefix(op) }
    return PendingSuggestion(field, rest.trimStart('"'))
}

/** Replaces the trailing in-progress token of [raw] with a complete [clause]. */
fun replaceInProgressClause(raw: String, clause: String): String {
    if (raw.isBlank()) return clause
    val word = raw.substringAfterLast(' ')
    return raw.dropLast(word.length).plus(clause)
}
