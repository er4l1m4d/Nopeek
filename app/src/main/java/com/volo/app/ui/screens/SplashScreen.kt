package com.volo.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.volo.app.R
import com.volo.app.ui.components.OutlineButton
import com.volo.app.ui.components.PrimaryButton
import com.volo.app.ui.theme.Volo
import com.volo.app.ui.theme.VoloText
import com.volo.app.ui.theme.VoloTheme
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    val colors = Volo.colors
    var fired by remember { mutableStateOf(false) }
    fun fire() {
        if (!fired) {
            fired = true
            onTimeout()
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .clickable(onClick = ::fire, role = Role.Button),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = "Nopeek mark",
                modifier = Modifier.size(72.dp),
            )
            Text(
                text = buildAnnotatedString {
                    append("NOPEEK")
                    withStyle(SpanStyle(color = colors.primary)) {
                        append(".")
                    }
                },
                style = VoloText.DisplayHero,
                color = colors.onSurface,
            )
            Text(
                text = "Don't peek.",
                style = VoloText.BodyMd,
                color = colors.onSurfaceMuted,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
    LaunchedEffect(Unit) {
        delay(1200)
        fire()
    }
}

@Composable
fun ChoiceScreen(onNew: () -> Unit, onExisting: () -> Unit) {
    val colors = Volo.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = "Welcome to Nopeek.",
            style = VoloText.DisplayTitle,
            color = colors.onSurface,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = "Have you dueled before?",
            style = VoloText.BodyLg,
            color = colors.onSurfaceMuted,
            modifier = Modifier.padding(top = 8.dp),
        )
        PrimaryButton(
            label = "I'm new to Nopeek",
            onClick = onNew,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp),
            hero = true,
        )
        OutlineButton(
            label = "I've used Nopeek",
            onClick = onExisting,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 24.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10261B)
@Composable
private fun SplashScreenPreview() {
    VoloTheme {
        SplashScreen(onTimeout = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10261B)
@Composable
private fun ChoiceScreenPreview() {
    VoloTheme {
        ChoiceScreen(onNew = {}, onExisting = {})
    }
}