package com.nopeek.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.nopeek.app.data.demo.DemoSeeder
import com.nopeek.app.data.repositories.ChallengeRepository
import com.nopeek.app.navigation.LocalActivityResultSender
import com.nopeek.app.navigation.NopeekApp
import com.nopeek.app.ui.theme.NopeekTheme
import androidx.compose.runtime.CompositionLocalProvider

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mwaSender = ActivityResultSender(this)
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
}
