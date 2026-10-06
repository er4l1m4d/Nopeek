package com.nopeek.app.ui.screens

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
import com.nopeek.app.R
import com.nopeek.app.ui.components.OutlineButton
import com.nopeek.app.ui.components.PrimaryButton
import com.nopeek.app.ui.theme.Nopeek
import com.nopeek.app.ui.theme.NopeekSpace
import com.nopeek.app.ui.theme.NopeekText
import com.nopeek.app.ui.theme.NopeekTheme
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    val colors = Nopeek.colors
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
                style = NopeekText.DisplayHero,
                color = colors.onSurface,
            )
            Text(
                text = "Don't peek.",
                style = NopeekText.BodyMd,
                color = colors.onSurfaceMuted,
                modifier = Modifier.padding(top = NopeekSpace.S12),
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
    val colors = Nopeek.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .padding(horizontal = NopeekSpace.S16),
    ) {
        Text(
            text = "Welcome to Nopeek.",
            style = NopeekText.DisplayTitle,
            color = colors.onSurface,
            modifier = Modifier.padding(top = NopeekSpace.S16),
        )
        Text(
            text = "Have you dueled before?",
            style = NopeekText.BodyLg,
            color = colors.onSurfaceMuted,
            modifier = Modifier.padding(top = NopeekSpace.S8),
        )
        PrimaryButton(
            label = "I'm new to Nopeek",
            onClick = onNew,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = NopeekSpace.S32),
            hero = true,
        )
        OutlineButton(
            label = "I've used Nopeek",
            onClick = onExisting,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = NopeekSpace.S12, bottom = NopeekSpace.S24),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10261B)
@Composable
private fun SplashScreenPreview() {
    NopeekTheme {
        SplashScreen(onTimeout = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10261B)
@Composable
private fun ChoiceScreenPreview() {
    NopeekTheme {
        ChoiceScreen(onNew = {}, onExisting = {})
    }
}