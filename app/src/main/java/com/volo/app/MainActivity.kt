package com.volo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.volo.app.data.demo.DemoSeeder
import com.volo.app.data.repositories.ChallengeRepository
import com.volo.app.navigation.LocalActivityResultSender
import com.volo.app.navigation.VoloApp
import com.volo.app.ui.theme.VoloTheme
import androidx.compose.runtime.CompositionLocalProvider

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mwaSender = ActivityResultSender(this)
        ChallengeRepository.getInstance(this).startPolling()
        DemoSeeder.seedIfNeeded(this)
        setContent {
            CompositionLocalProvider(LocalActivityResultSender provides mwaSender) {
                VoloTheme {
                    VoloApp()
                }
            }
        }
    }
}
