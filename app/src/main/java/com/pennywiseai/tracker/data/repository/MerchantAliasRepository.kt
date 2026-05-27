package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.dao.MerchantAliasDao
import com.pennywiseai.tracker.data.database.entity.MerchantAliasEntity
import com.pennywiseai.tracker.utils.MerchantAliasAuditor
import com.pennywiseai.tracker.utils.MerchantAliasResolver
import com.pennywiseai.tracker.utils.MerchantNameMatcher
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

enum class MerchantAliasSaveResult {
    SUCCESS,
    EMPTY_FIELDS,
    SAME_SOURCE_AND_DISPLAY,
    DUPLICATE_SOURCE,
    NOT_FOUND,
}

@Singleton
class MerchantAliasRepository @Inject constructor(
    private val merchantAliasDao: MerchantAliasDao
) {

    suspend fun getDisplayNameForMerchant(sourceMerchant: String): String? {
        return merchantAliasDao.getDisplayNameForMerchant(sourceMerchant.trim())
    }

    suspend fun getAllDisplayNames(): List<String> {
        return merchantAliasDao.getAllAliasesList()
            .map { it.displayName }
            .distinct()
    }

    /**
     * Suggests display names using exact aliases first, then weighted fuzzy matching (>= 60%)
     * against saved aliases and [knownMerchants] from transaction history (up to 3, best first).
     */
    suspend fun suggestDisplayNames(
        sourceMerchant: String,
        knownMerchants: List<String> = emptyList(),
    ): List<String> {
        val source = sourceMerchant.trim()
        if (source.isEmpty()) return emptyList()

        getDisplayNameForMerchant(source)?.let { exact ->
            if (!exact.equals(source, ignoreCase = true)) return listOf(exact)
        }

        val aliases = merchantAliasDao.getAllAliasesList()
        val aliasCandidates = aliases.map { it.sourceMerchant to it.displayName }
        val displayNamesFromAliases = aliases.map { it.displayName }.distinct()
        val merchantLabels = (knownMerchants + displayNamesFromAliases)
            .distinct()
            .filter { !it.equals(source, ignoreCase = true) }

        return MerchantNameMatcher.findSuggestions(
            query = source,
            aliasCandidates = aliasCandidates,
            merchantLabels = merchantLabels,
        )
    }

    suspend fun getAllAliasesList(): List<MerchantAliasEntity> {
        return merchantAliasDao.getAllAliasesList()
    }

    /** Exact match only — safe for automatic SMS ingest. */
    suspend fun resolveDisplayNameForIngest(sourceMerchant: String): String {
        return MerchantAliasResolver.resolveExact(sourceMerchant, getAllAliasesList())
    }

    suspend fun resolveDisplayName(sourceMerchant: String): String {
        return MerchantAliasResolver.resolve(sourceMerchant, getAllAliasesList())
    }

    suspend fun auditAllAliases(): List<MerchantAliasAuditor.AuditResult> =
        MerchantAliasAuditor.auditAll(getAllAliasesList())

    suspend fun findSuspiciousAliases(): List<MerchantAliasAuditor.AuditResult> =
        MerchantAliasAuditor.suspiciousAliases(getAllAliasesList())

    suspend fun deleteAlias(sourceMerchant: String) {
        merchantAliasDao.deleteAlias(sourceMerchant.trim())
    }

    suspend fun setAlias(sourceMerchant: String, displayName: String) {
        val source = sourceMerchant.trim()
        val display = displayName.trim()
        if (source.isEmpty() || display.isEmpty() || source.equals(display, ignoreCase = true)) {
            return
        }
        val existing = merchantAliasDao.getAlias(source)
        merchantAliasDao.insertOrUpdateAlias(
            MerchantAliasEntity(
                sourceMerchant = source,
                displayName = display,
                createdAt = existing?.createdAt ?: LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            )
        )
    }

    suspend fun addAlias(sourceMerchant: String, displayName: String): MerchantAliasSaveResult {
        val source = sourceMerchant.trim()
        val display = displayName.trim()
        if (source.isEmpty() || display.isEmpty()) return MerchantAliasSaveResult.EMPTY_FIELDS
        if (source.equals(display, ignoreCase = true)) {
            return MerchantAliasSaveResult.SAME_SOURCE_AND_DISPLAY
        }
        if (merchantAliasDao.getAlias(source) != null) return MerchantAliasSaveResult.DUPLICATE_SOURCE
        setAlias(source, display)
        return MerchantAliasSaveResult.SUCCESS
    }

    suspend fun updateAlias(
        originalSource: String,
        newSource: String,
        newDisplay: String,
    ): MerchantAliasSaveResult {
        val original = originalSource.trim()
        val source = newSource.trim()
        val display = newDisplay.trim()
        if (source.isEmpty() || display.isEmpty()) return MerchantAliasSaveResult.EMPTY_FIELDS
        if (source.equals(display, ignoreCase = true)) {
            return MerchantAliasSaveResult.SAME_SOURCE_AND_DISPLAY
        }
        val existing = merchantAliasDao.getAlias(original) ?: return MerchantAliasSaveResult.NOT_FOUND
        if (!source.equals(original, ignoreCase = true) && merchantAliasDao.getAlias(source) != null) {
            return MerchantAliasSaveResult.DUPLICATE_SOURCE
        }
        if (!source.equals(original, ignoreCase = true)) {
            merchantAliasDao.deleteAlias(original)
        }
        merchantAliasDao.insertOrUpdateAlias(
            MerchantAliasEntity(
                sourceMerchant = source,
                displayName = display,
                createdAt = existing.createdAt,
                updatedAt = LocalDateTime.now(),
            )
        )
        return MerchantAliasSaveResult.SUCCESS
    }
}
