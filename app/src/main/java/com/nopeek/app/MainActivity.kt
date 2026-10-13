package com.nopeek.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.nopeek.app.data.demo.DemoSeeder
import com.nopeek.app.data.local.AppDatabase
import com.nopeek.app.data.local.NopeekPrefs
import com.nopeek.app.data.repositories.ChallengeRepository
import com.nopeek.app.data.usage.ReportRecovery
import com.nopeek.app.data.usage.ReportScheduler
import com.nopeek.app.data.wallet.walletManager
import com.nopeek.app.navigation.LocalActivityResultSender
import com.nopeek.app.navigation.NopeekApp
import com.nopeek.app.ui.theme.NopeekTheme
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mwaSender = ActivityResultSender(this)
        walletManager.attach(this)
        ChallengeRepository.getInstance(this).startPolling()
        DemoSeeder.seedIfNeeded(this)
        runEagerRecovery()
        setContent {
            CompositionLocalProvider(LocalActivityResultSender provides mwaSender) {
                NopeekTheme {
                    NopeekApp()
                }
            }
        }
    }

    /**
     * The eager half of the two-chances-to-report design (plan Task 13): when the app
     * opens, any window that closed while the app was dead gets its report now. The
     * scheduled worker is the first chance; MIUI force-stops and battery savers are why
     * one chance is not enough.
     *
     * The local wallet comes from prefs, not from the in-memory WalletManager: recovery
     * runs precisely when that state is gone (force-stop → fresh process). A duel row
     * names two wallets, and reporting for the wrong one would spend the OPPONENT's
     * single-use nonce — which is why ReportRecovery refuses to guess and this hook
     * stayed unwired until the wallet was persisted at connect time.
     */
    private fun runEagerRecovery() {
        val localWallet = NopeekPrefs(this).localWalletPubkey ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                val dao = AppDatabase.getInstance(this@MainActivity).challengeDao()
                val candidates = ReportRecovery.windowsToRecover(
                    entities = dao.getRecoverableChallenges(),
                    localWalletPubkey = localWallet,
                    nowMs = System.currentTimeMillis(),
                )
                for (params in candidates) {
                    ReportScheduler.reportNowIfDue(this@MainActivity, params)
                }
            }
        }
    }
}
