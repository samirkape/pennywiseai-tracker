package com.spendly.tracker.domain.usecase

import com.spendly.tracker.data.database.dao.AccountBalanceDao
import com.spendly.tracker.data.database.dao.LoanDao
import com.spendly.tracker.data.database.entity.AccountBalanceEntity
import com.spendly.tracker.data.database.entity.NetWorthSourceEntity
import com.spendly.tracker.data.repository.NetWorthSourceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.math.BigDecimal
import javax.inject.Inject

/** One line in the net worth breakdown. [amount] is always positive; [isLiability] gives its sign. */
data class NetWorthLine(
    val label: String,
    val amount: BigDecimal,
    val isLiability: Boolean,
    /** Set for user-editable rows (manual/CAS sources); null for derived rows. */
    val sourceId: Long? = null,
    val group: NetWorthGroup
)

enum class NetWorthGroup(val title: String) {
    BANK("Bank accounts"),
    INVESTMENTS("Investments"),
    OTHER_ASSETS("Other assets"),
    CREDIT_CARDS("Credit cards"),
    LOANS("Loans"),
    OTHER_LIABILITIES("Other liabilities")
}

data class NetWorthSummary(
    val assets: BigDecimal = BigDecimal.ZERO,
    val liabilities: BigDecimal = BigDecimal.ZERO,
    val lines: List<NetWorthLine> = emptyList()
) {
    val netWorth: BigDecimal get() = assets - liabilities
}

/**
 * Net worth = bank balances (latest SMS-derived) + manual/CAS sources + money lent
 * minus credit card outstanding, money borrowed and manual liabilities.
 *
 * Only entries in [currency] are counted; other currencies are skipped rather than
 * summed at face value.
 */
class ComputeNetWorthUseCase @Inject constructor(
    private val accountBalanceDao: AccountBalanceDao,
    private val loanDao: LoanDao,
    private val sourceRepository: NetWorthSourceRepository
) {

    operator fun invoke(currency: String): Flow<NetWorthSummary> = combine(
        accountBalanceDao.getAllLatestBalances(),
        sourceRepository.getAll(),
        loanDao.getTotalLentRemaining(),
        loanDao.getTotalBorrowedRemaining()
    ) { balances, sources, lent, borrowed ->
        compute(currency, balances, sources, lent, borrowed)
    }

    companion object {
        internal fun compute(
            currency: String,
            balances: List<AccountBalanceEntity>,
            sources: List<NetWorthSourceEntity>,
            lent: BigDecimal,
            borrowed: BigDecimal
        ): NetWorthSummary {
            val lines = mutableListOf<NetWorthLine>()

            balances.filter { it.currency == currency }.forEach { b ->
                val label = "${b.bankName} ••${b.accountLast4}"
                if (b.isCreditCard) {
                    lines += NetWorthLine(label, b.balance.abs(), true, group = NetWorthGroup.CREDIT_CARDS)
                } else {
                    // An overdrawn account is a liability, not a negative asset.
                    val isNegative = b.balance.signum() < 0
                    lines += NetWorthLine(label, b.balance.abs(), isNegative, group = NetWorthGroup.BANK)
                }
            }

            sources.filter { it.currency == currency }.forEach { s ->
                val group = when {
                    s.type.isLiability -> NetWorthGroup.OTHER_LIABILITIES
                    s.type == com.spendly.tracker.data.database.entity.NetWorthSourceType.MUTUAL_FUND ||
                        s.type == com.spendly.tracker.data.database.entity.NetWorthSourceType.STOCKS ->
                        NetWorthGroup.INVESTMENTS
                    else -> NetWorthGroup.OTHER_ASSETS
                }
                lines += NetWorthLine(s.name, s.value, s.type.isLiability, s.id, group)
            }

            if (lent.signum() > 0) {
                lines += NetWorthLine("Money lent", lent, false, group = NetWorthGroup.LOANS)
            }
            if (borrowed.signum() > 0) {
                lines += NetWorthLine("Money borrowed", borrowed, true, group = NetWorthGroup.LOANS)
            }

            val assets = lines.filter { !it.isLiability }.fold(BigDecimal.ZERO) { acc, l -> acc + l.amount }
            val liabilities = lines.filter { it.isLiability }.fold(BigDecimal.ZERO) { acc, l -> acc + l.amount }
            return NetWorthSummary(assets, liabilities, lines)
        }
    }
}
