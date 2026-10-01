package com.volo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.volo.app.navigation.VoloApp
import com.volo.app.ui.theme.VoloTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VoloTheme { VoloApp() } }
    }
}
