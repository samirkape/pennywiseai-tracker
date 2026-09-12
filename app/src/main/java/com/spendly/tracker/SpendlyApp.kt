package com.spendly.tracker

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.spendly.tracker.navigation.AppLock
import com.spendly.tracker.navigation.Home
import com.spendly.tracker.navigation.OnBoarding
import com.spendly.tracker.navigation.SpendlyNavHost
import com.spendly.tracker.ui.theme.SpendlyTheme
import com.spendly.tracker.ui.viewmodel.AppLockViewModel
import com.spendly.tracker.ui.viewmodel.AutoRestoreViewModel
import com.spendly.tracker.ui.viewmodel.ThemeViewModel
import com.spendly.tracker.widget.RecentTransactionsWidgetUpdateWorker
import com.spendly.tracker.worker.InsightsWorker

@Composable
fun SpendlyApp(
    themeViewModel: ThemeViewModel = hiltViewModel(),
    appLockViewModel: AppLockViewModel = hiltViewModel(),
    autoRestoreViewModel: AutoRestoreViewModel = hiltViewModel(),
    editTransactionId: Long? = null,
    openAddTransaction: Boolean = false,
    openTransactions: Boolean = false,
    openSubscriptions: Boolean = false,
    onEditComplete: () -> Unit = {},
    onAddTransactionShortcutHandled: () -> Unit = {},
    onOpenTransactionsHandled: () -> Unit = {},
    onOpenSubscriptionsHandled: () -> Unit = {}
) {
    val themeUiState by themeViewModel.themeUiState.collectAsStateWithLifecycle()
    val appLockUiState by appLockViewModel.uiState.collectAsStateWithLifecycle()
    val autoRestoreState by autoRestoreViewModel.restorePromptState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val darkTheme = themeUiState.isDarkTheme ?: isSystemInDarkTheme()

    val navController = rememberNavController()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Observe lifecycle events and refresh lock state when app resumes from background
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // App came to foreground - check if it should be locked
                appLockViewModel.refreshLockState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Wait for preferences to load before deciding start destination
    if (themeUiState.isLoading) {
        return
    }

    // Migrate existing users: if they already have SMS permission from the old flow,
    // auto-complete onboarding so they aren't forced through it on update
    LaunchedEffect(themeUiState.hasCompletedOnboarding) {
        if (!themeUiState.hasCompletedOnboarding) {
            val hasSmsPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_SMS
            ) == PackageManager.PERMISSION_GRANTED
            if (hasSmsPermission) {
                themeViewModel.markOnboardingCompleted()
            }
        }
    }

    // Set correct start destination based on loaded preferences
    val startDestination: Any = remember(themeUiState.hasCompletedOnboarding) {
        if (themeUiState.hasCompletedOnboarding) Home() else OnBoarding
    }

    // Observe lock state changes and navigate to lock screen if needed
    // But don't navigate when user is actively in Settings configuring app lock
    LaunchedEffect(appLockUiState.isLocked, appLockUiState.isLockEnabled) {
        if (appLockUiState.isLocked && appLockUiState.isLockEnabled) {
            val currentRoute = navController.currentDestination?.route
            // Don't navigate if already on lock screen or in Settings (user is configuring)
            if (currentRoute != AppLock::class.qualifiedName &&
                currentRoute != com.spendly.tracker.navigation.Settings::class.qualifiedName) {
                navController.navigate(AppLock) {
                    // Don't add to back stack, force lock screen
                    popUpTo(navController.graph.startDestinationId) { inclusive = false }
                    launchSingleTop = true
                }
            }
        }
    }
    
    // Navigate to transaction detail when editTransactionId changes
    LaunchedEffect(editTransactionId) {
        editTransactionId?.let { transactionId ->
            navController.navigate(com.spendly.tracker.navigation.TransactionDetail(transactionId)) { launchSingleTop = true }
        }
    }

    // Navigate directly to Add Transaction when requested (e.g., from widget shortcut)
    LaunchedEffect(openAddTransaction) {
        if (openAddTransaction) {
            navController.navigate(com.spendly.tracker.navigation.AddTransaction()) {
                launchSingleTop = true
            }
            onAddTransactionShortcutHandled()
        }
    }

    // Navigate directly to the Transactions list when requested (e.g., from the daily reminder)
    LaunchedEffect(openTransactions) {
        if (openTransactions) {
            navController.navigate(com.spendly.tracker.navigation.Transactions) {
                launchSingleTop = true
            }
            onOpenTransactionsHandled()
        }
    }

    // Navigate to the Subscriptions screen when tapping a prepaid renewal notification
    LaunchedEffect(openSubscriptions) {
        if (openSubscriptions) {
            navController.navigate(com.spendly.tracker.navigation.Subscriptions) {
                launchSingleTop = true
            }
            onOpenSubscriptionsHandled()
        }
    }

    // Check for auto-backup after onboarding is complete and DB might be empty
    LaunchedEffect(themeUiState.hasCompletedOnboarding) {
        if (themeUiState.hasCompletedOnboarding) {
            autoRestoreViewModel.checkForAutoBackup()
        }
    }

    // Confirm to the user when an auto-restore finishes successfully
    LaunchedEffect(autoRestoreState.restoreComplete) {
        if (autoRestoreState.restoreComplete) {
            android.widget.Toast.makeText(
                context,
                "Your data has been restored",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    // Keep widgets and insights current when app launches
    LaunchedEffect(Unit) {
        RecentTransactionsWidgetUpdateWorker.enqueueOneShot(context.applicationContext)
        InsightsWorker.enqueueOneShot(context.applicationContext)
        InsightsWorker.enqueuePeriodic(context.applicationContext)
    }

    SpendlyTheme(
        darkTheme = darkTheme,
        dynamicColor = themeUiState.isDynamicColorEnabled,
        themeStyle = themeUiState.themeStyle,
        accentColor = themeUiState.accentColor,
        isAmoledMode = themeUiState.isAmoledMode,
        appFont = themeUiState.appFont,
        blurEffects = themeUiState.blurEffectsEnabled
    ) {
        SpendlyNavHost(
            navController = navController,
            themeViewModel = themeViewModel,
            startDestination = startDestination,
            onEditComplete = onEditComplete
        )

        if (autoRestoreState.shouldShow) {
            AlertDialog(
                onDismissRequest = { autoRestoreViewModel.dismissRestorePrompt() },
                title = { Text("Restore Previous Data?") },
                text = {
                    Column {
                        Text(
                            if (autoRestoreState.backupTimestamp != null)
                                "A backup from ${autoRestoreState.backupTimestamp} was found. " +
                                "Would you like to restore your data?"
                            else
                                "A previous backup was found. Would you like to restore your data?"
                        )
                        if (autoRestoreState.errorMessage != null) {
                            Spacer(modifier = androidx.compose.ui.Modifier.height(8.dp))
                            Text(
                                text = "Restore failed: ${autoRestoreState.errorMessage}",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                confirmButton = {
                    if (autoRestoreState.isRestoring) {
                        CircularProgressIndicator()
                    } else {
                        TextButton(onClick = { autoRestoreViewModel.restoreFromAutoBackup() }) {
                            Text(if (autoRestoreState.errorMessage != null) "Retry" else "Restore")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { autoRestoreViewModel.dismissRestorePrompt() }) {
                        Text("Start Fresh")
                    }
                }
            )
        }
    }
}
