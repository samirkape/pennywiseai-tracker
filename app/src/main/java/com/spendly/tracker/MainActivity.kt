package com.spendly.tracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import com.spendly.tracker.receiver.SmsBroadcastReceiver
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    companion object {
        const val EXTRA_OPEN_ADD_TRANSACTION = "com.spendly.tracker.OPEN_ADD_TRANSACTION"
        const val EXTRA_OPEN_TRANSACTIONS = "com.spendly.tracker.OPEN_TRANSACTIONS"
        const val EXTRA_OPEN_SUBSCRIPTIONS = "com.spendly.tracker.OPEN_SUBSCRIPTIONS"
    }

    // Transaction ID to edit when launched from notification
    var editTransactionId by mutableStateOf<Long?>(null)
        private set

    // Flag to navigate directly to Add Transaction when launched from a shortcut/widget
    var openAddTransaction by mutableStateOf(false)
        private set

    // Flag to navigate directly to the Transactions list when launched from the daily reminder
    var openTransactions by mutableStateOf(false)
        private set

    // Flag to navigate directly to the Subscriptions screen when launched from a renewal reminder
    var openSubscriptions by mutableStateOf(false)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen before super.onCreate()
        installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle intent if activity is launched from notification
        handleEditIntent(intent)

        setContent {
            SpendlyApp(
                editTransactionId = editTransactionId,
                openAddTransaction = openAddTransaction,
                openTransactions = openTransactions,
                openSubscriptions = openSubscriptions,
                onEditComplete = { editTransactionId = null },
                onAddTransactionShortcutHandled = { openAddTransaction = false },
                onOpenTransactionsHandled = { openTransactions = false },
                onOpenSubscriptionsHandled = { openSubscriptions = false }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle intent when activity is already running
        handleEditIntent(intent)
    }

    private fun handleEditIntent(intent: Intent?) {
        if (intent?.action == SmsBroadcastReceiver.ACTION_EDIT_TRANSACTION) {
            val transactionId = intent.getLongExtra(SmsBroadcastReceiver.EXTRA_TRANSACTION_ID, -1)
            if (transactionId != -1L) {
                editTransactionId = transactionId
            }
        }
        if (intent?.getBooleanExtra(EXTRA_OPEN_ADD_TRANSACTION, false) == true) {
            openAddTransaction = true
        }
        if (intent?.getBooleanExtra(EXTRA_OPEN_TRANSACTIONS, false) == true) {
            openTransactions = true
        }
        if (intent?.getBooleanExtra(EXTRA_OPEN_SUBSCRIPTIONS, false) == true) {
            openSubscriptions = true
        }
    }
}
