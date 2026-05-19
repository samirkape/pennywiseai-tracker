package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.dao.MerchantAliasDao
import com.pennywiseai.tracker.data.database.entity.MerchantAliasEntity
import com.pennywiseai.tracker.utils.MerchantNameMatcher
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MerchantAliasRepository @Inject constructor(
    private val merchantAliasDao: MerchantAliasDao
) {

    suspend fun getDisplayNameForMerchant(sourceMerchant: String): String? {
        return merchantAliasDao.getDisplayNameForMerchant(sourceMerchant.trim())
    }

    /**
     * Suggests a display name using exact aliases first, then weighted fuzzy matching (>= 90%)
     * against saved aliases and [knownMerchants] from transaction history.
     */
    suspend fun suggestDisplayName(
        sourceMerchant: String,
        knownMerchants: List<String> = emptyList()
    ): String? {
        val source = sourceMerchant.trim()
        if (source.isEmpty()) return null

        getDisplayNameForMerchant(source)?.let { exact ->
            if (!exact.equals(source, ignoreCase = true)) return exact
        }

        val aliases = merchantAliasDao.getAllAliasesList()
        val aliasCandidates = aliases.map { it.sourceMerchant to it.displayName }

        MerchantNameMatcher.findBestDisplayName(source, aliasCandidates)?.let { return it }

        val displayNamesFromAliases = aliases.map { it.displayName }.distinct()
        val merchantLabels = (knownMerchants + displayNamesFromAliases)
            .distinct()
            .filter { !it.equals(source, ignoreCase = true) }

        return MerchantNameMatcher.findBestMerchantLabel(source, merchantLabels)
    }

    suspend fun setAlias(sourceMerchant: String, displayName: String) {
        val source = sourceMerchant.trim()
        val display = displayName.trim()
        if (source.isEmpty() || display.isEmpty() || source.equals(display, ignoreCase = true)) {
            return
        }
        merchantAliasDao.insertOrUpdateAlias(
            MerchantAliasEntity(
                sourceMerchant = source,
                displayName = display,
                updatedAt = LocalDateTime.now()
            )
        )
    }
}
