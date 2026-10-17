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
        setContent {
            CompositionLocalProvider(LocalActivityResultSender provides mwaSender) {
                NopeekTheme {
                    NopeekApp()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        runEagerRecovery()
    }

    /**
     * The eager half of the two-chances-to-report design (plan Task 13): when the app
     * is foregrounded, any window that closed while the app was dead or backgrounded
     * gets its report now. The scheduled worker is the first chance; MIUI force-stops
     * and battery savers are why one chance is not enough.
     *
     * Runs from onResume, NOT onCreate (review-found): onCreate only fires on a new
     * process. A user returning via the recents switcher — the exact "force-stop, set
     * the phone down, come back" path Review Focus 5 describes — would skip recovery
     * entirely, because the process was still alive and only onResume ran.
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
            }.onFailure {
                // "Failure is logged, never thrown into the UI" (plan Task 13 Step 3).
                // A bare runCatching swallows Room/DAO regressions silently — a stranded
                // recovery would then look identical to "nothing was due".
                android.util.Log.e("NOPEEK_RECOVERY", "eager recovery failed", it)
            }
        }
    }
}
