package com.pennywiseai.tracker.utils

/**
 * Weighted similarity for SMS-style merchant names (e.g. fss4firstcry vs Firstcry).
 */
object MerchantNameMatcher {

    const val MATCH_THRESHOLD = 0.90

    private const val WEIGHT_JARO_WINKLER = 0.45
    private const val WEIGHT_CONTAINMENT = 0.35
    private const val WEIGHT_TOKEN_OVERLAP = 0.20

    fun weightedSimilarity(a: String, b: String): Double {
        val left = a.trim()
        val right = b.trim()
        if (left.isEmpty() || right.isEmpty()) return 0.0
        if (left.equals(right, ignoreCase = true)) return 1.0

        val leftNorm = normalizeAlphanumeric(left)
        val rightNorm = normalizeAlphanumeric(right)
        if (leftNorm == rightNorm) return 0.98

        val leftAlpha = lettersOnly(left)
        val rightAlpha = lettersOnly(right)

        val jaro = jaroWinkler(leftNorm, rightNorm)
        val containment = containmentScore(leftAlpha, rightAlpha)
        val tokens = tokenOverlapScore(left, right)

        val blended = WEIGHT_JARO_WINKLER * jaro +
            WEIGHT_CONTAINMENT * containment +
            WEIGHT_TOKEN_OVERLAP * tokens

        // Strong signal when a clean name is embedded in an SMS code (e.g. fss4firstcry → firstcry)
        return if (containment >= 0.85) {
            maxOf(blended, containment)
        } else {
            blended
        }.coerceIn(0.0, 1.0)
    }

    /**
     * Returns the display name from [candidates] with the highest weighted score >= [MATCH_THRESHOLD],
     * or null if none qualify.
     */
    fun findBestDisplayName(
        query: String,
        candidates: List<Pair<String, String>>
    ): String? {
        val source = query.trim()
        if (source.isEmpty() || candidates.isEmpty()) return null

        var bestScore = 0.0
        var bestDisplay: String? = null

        for ((candidateSource, displayName) in candidates) {
            val score = weightedSimilarity(source, candidateSource)
            if (score >= MATCH_THRESHOLD &&
                score > bestScore &&
                !displayName.equals(source, ignoreCase = true)
            ) {
                bestScore = score
                bestDisplay = displayName
            }
        }
        return bestDisplay
    }

    /**
     * Among known merchant labels, returns the best match above threshold (used when no alias exists
     * but the user already has a cleaner name in their transaction history).
     */
    fun findBestMerchantLabel(query: String, knownMerchants: List<String>): String? {
        val source = query.trim()
        if (source.isEmpty()) return null

        var bestScore = 0.0
        var bestLabel: String? = null

        for (merchant in knownMerchants) {
            if (merchant.equals(source, ignoreCase = true)) continue
            val score = weightedSimilarity(source, merchant)
            if (score >= MATCH_THRESHOLD && score > bestScore) {
                bestScore = score
                bestLabel = merchant
            }
        }
        return bestLabel
    }

    private fun normalizeAlphanumeric(value: String): String =
        value.lowercase().filter { it.isLetterOrDigit() }

    private fun lettersOnly(value: String): String =
        value.lowercase().filter { it.isLetter() }

    private fun tokenOverlapScore(a: String, b: String): Double {
        val tokensA = tokenize(a)
        val tokensB = tokenize(b)
        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0.0

        val intersection = tokensA.intersect(tokensB).size
        val union = tokensA.union(tokensB).size
        return if (union == 0) 0.0 else intersection.toDouble() / union
    }

    private fun tokenize(value: String): Set<String> {
        return value
            .lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 2 }
            .toSet()
    }

    private fun containmentScore(leftAlpha: String, rightAlpha: String): Double {
        if (leftAlpha.isEmpty() || rightAlpha.isEmpty()) return 0.0
        val (shorter, longer) = if (leftAlpha.length <= rightAlpha.length) {
            leftAlpha to rightAlpha
        } else {
            rightAlpha to leftAlpha
        }
        if (longer.contains(shorter)) {
            val ratio = shorter.length.toDouble() / longer.length
            return 0.85 + (0.15 * ratio)
        }
        return levenshteinRatio(shorter, longer)
    }

    private fun levenshteinRatio(a: String, b: String): Double {
        if (a == b) return 1.0
        val distance = levenshteinDistance(a, b)
        val maxLen = maxOf(a.length, b.length)
        return if (maxLen == 0) 1.0 else 1.0 - (distance.toDouble() / maxLen)
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        val costs = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var previous = costs[0]
            costs[0] = i
            for (j in 1..b.length) {
                val temp = costs[j]
                costs[j] = minOf(
                    costs[j] + 1,
                    costs[j - 1] + 1,
                    previous + if (a[i - 1] == b[j - 1]) 0 else 1
                )
                previous = temp
            }
        }
        return costs[b.length]
    }

    private fun jaroWinkler(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0

        val matchDistance = maxOf(s1.length, s2.length) / 2 - 1
        val s1Matches = BooleanArray(s1.length)
        val s2Matches = BooleanArray(s2.length)

        var matches = 0
        for (i in s1.indices) {
            val start = maxOf(0, i - matchDistance)
            val end = minOf(i + matchDistance + 1, s2.length)
            for (j in start until end) {
                if (s2Matches[j] || s1[i] != s2[j]) continue
                s1Matches[i] = true
                s2Matches[j] = true
                matches++
                break
            }
        }

        if (matches == 0) return 0.0

        var transpositions = 0
        var k = 0
        for (i in s1.indices) {
            if (!s1Matches[i]) continue
            while (!s2Matches[k]) k++
            if (s1[i] != s2[k]) transpositions++
            k++
        }

        val jaro = (
            matches.toDouble() / s1.length +
                matches.toDouble() / s2.length +
                (matches - transpositions / 2.0) / matches
            ) / 3.0

        var prefix = 0
        val prefixLimit = minOf(4, minOf(s1.length, s2.length))
        while (prefix < prefixLimit && s1[prefix] == s2[prefix]) prefix++

        return jaro + prefix * 0.1 * (1.0 - jaro)
    }
}
