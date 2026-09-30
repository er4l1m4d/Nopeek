package com.volo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SportsScore
import androidx.compose.material.icons.outlined.Wallet
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import com.volo.app.ui.theme.VoloTheme

private val Copper = Color(0xFFB7613F)
private val Ink = Color(0xFF172126)
private val Paper = Color(0xFFF4F5F1)
private val Muted = Color(0xFF607076)
private val Celadon = Color(0xFFC9DCCF)
private val Duel = Color(0xFF10191D)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VoloTheme { VoloApp() } }
    }
}

private enum class Destination(val label: String) {
    Home("Home"), Challenges("Challenges"), Create("Create"), Activity("Activity"), Profile("Profile")
}

@Composable
private fun VoloApp() {
    var destination by remember { mutableStateOf(Destination.Home) }
    var duelOpen by remember { mutableStateOf(false) }
    Surface(color = if (duelOpen) Duel else Paper) {
        if (duelOpen) {
            DuelScreen(onBack = { duelOpen = false })
        } else {
            Column(Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = destination,
                    modifier = Modifier.weight(1f),
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "destination"
                ) { screen ->
                    when (screen) {
                        Destination.Home -> HomeScreen(onOpenDuel = { duelOpen = true })
                        Destination.Challenges -> ChallengesScreen(onOpenDuel = { duelOpen = true })
                        Destination.Create -> CreateScreen(onCreated = { destination = Destination.Challenges })
                        Destination.Activity -> ActivityScreen()
                        Destination.Profile -> ProfileScreen()
                    }
                }
                BottomBar(destination) { destination = it }
            }
        }
    }
}

@Composable
private fun HomeScreen(onOpenDuel: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp, 28.dp, 24.dp, 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Tuesday, September 29", color = Muted, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                Text("Stay in the frame.", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            }
        }
        item { DuelCard(onOpenDuel) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard("18m", "of 30m used", Modifier.weight(1f))
                MetricCard("5 days", "current streak", Modifier.weight(1f))
            }
        }
        item { VerificationCard() }
    }
}

@Composable
private fun DuelCard(onOpen: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Ink).clickable(onClick = onOpen).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            StatusChip("PASSING", Celadon, Ink)
            Text("ACTIVE DUEL", color = Color(0xFFA4B0AF), fontSize = 11.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold)
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("No doomscrolling after 9pm", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Instagram · with Maya", color = Color(0xFFA4B0AF), fontSize = 14.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("18m / 30m", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 20.sp)
                Text("$5.00 USDC locked", color = Color(0xFFA4B0AF), fontSize = 12.sp)
            }
            Icon(Icons.Outlined.ArrowForward, contentDescription = "View duel", tint = Color(0xFFD17A54))
        }
    }
}

@Composable
private fun MetricCard(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(Color.White).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(value, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Text(label, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun VerificationCard() {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Celadon.copy(alpha = .55f)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Shield, contentDescription = null, tint = Ink, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Usage access is on", color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("Your next report is due in 42 minutes.", color = Muted, fontSize = 12.sp)
        }
        Icon(Icons.Outlined.ArrowForward, contentDescription = "Open usage settings", tint = Ink, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ChallengesScreen(onOpenDuel: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 28.dp)) {
        Text("Challenges", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip("ACTIVE 1", Copper, Color.White)
            Text("Pending 2", color = Muted, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), fontSize = 13.sp)
            Text("Completed", color = Muted, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), fontSize = 13.sp)
        }
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ChallengeRow("No doomscrolling after 9pm", "Instagram · Maya", "PASSING", "18m / 30m", true, onOpenDuel)
            ChallengeRow("Read before scrolling", "YouTube · Jules", "PENDING", "Starts tomorrow", false, {})
            ChallengeRow("Morning phone-free hour", "TikTok · Ari", "COMPLETED", "Passed · Sep 21", false, {})
        }
    }
}

@Composable
private fun ChallengeRow(title: String, subtitle: String, status: String, detail: String, active: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Muted, fontSize = 12.sp)
            Text(detail, color = if (active) Copper else Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
        StatusChip(status, if (active) Copper else Color(0xFFE9ECE7), if (active) Color.White else Muted)
    }
}

@Composable
private fun CreateScreen(onCreated: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Create a duel", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("Make the promise specific. Make it matter.", color = Muted, fontSize = 15.sp)
        FormRow("Target app", "Instagram")
        FormRow("Daily limit", "30 minutes")
        FormRow("Duration", "24 hours")
        FormRow("Stake", "5.00 USDC")
        FormRow("Opponent", "Enter wallet address")
        Spacer(Modifier.weight(1f))
        PrimaryButton("Review duel", onCreated)
    }
}

@Composable
private fun FormRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label.uppercase(), color = Muted, fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
        Text(value, color = if (value.startsWith("Enter")) Muted else Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ActivityScreen() {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Activity", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        val events = listOf("Duel created" to "Today, 8:42 PM", "Usage report verified" to "Today, 7:18 PM", "Stake locked" to "Today, 6:59 PM", "Previous stake returned" to "Sep 21")
        events.forEach { (title, time) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(if (title.contains("verified") || title.contains("returned")) Celadon else Color(0xFFE9ECE7)), contentAlignment = Alignment.Center) {
                    Icon(if (title.contains("Stake")) Icons.Outlined.Wallet else Icons.Outlined.Shield, contentDescription = null, tint = Ink, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text(title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp); Text(time, color = Muted, fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun ProfileScreen() {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Profile", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Ink).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Wallet, contentDescription = null, tint = Color(0xFFD17A54), modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text("Wallet connected", color = Color.White, fontWeight = FontWeight.Bold); Text("6k9T...cRWmmj", color = Color(0xFFA4B0AF), fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
            Text("2.48 USDC", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
        ProfileRow("Seeker status", "Verification unavailable on this device")
        ProfileRow("SKR balance", "Not connected")
        ProfileRow("Usage access", "Enabled")
        ProfileRow("Privacy", "Review what Volo measures")
    }
}

@Composable
private fun ProfileRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(label, color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp); Text(value, color = Muted, fontSize = 12.sp) }
        Icon(Icons.Outlined.ArrowForward, contentDescription = "Open $label", tint = Muted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun DuelScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Duel).padding(24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = Color.White) }
        Text("ACTIVE DUEL · 24H WINDOW", color = Color(0xFFA4B0AF), fontSize = 11.sp, letterSpacing = 1.1.sp, fontWeight = FontWeight.Bold)
        Text("No doomscrolling\nafter 9pm", color = Color.White, fontSize = 34.sp, lineHeight = 37.sp, fontWeight = FontWeight.Bold)
        Text("Instagram · with Maya", color = Color(0xFFA4B0AF), fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().aspectRatio(1f).padding(horizontal = 20.dp), contentAlignment = Alignment.Center) { UsageRing() }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text("YOUR USAGE", color = Color(0xFFA4B0AF), fontSize = 10.sp, letterSpacing = 1.sp); Text("18m / 30m", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 17.sp) }
            Column(horizontalAlignment = Alignment.End) { Text("MAYA'S REPORT", color = Color(0xFFA4B0AF), fontSize = 10.sp, letterSpacing = 1.sp); Text("22m / 30m", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 17.sp) }
        }
        OpponentBar()
        Spacer(Modifier.weight(1f))
        PrimaryButton("Check usage", {})
        Text("Last opponent report: 8 minutes ago", color = Color(0xFFA4B0AF), fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun UsageRing() {
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawArc(Color(0xFF334247), -90f, 360f, false, style = Stroke(14.dp.toPx(), cap = StrokeCap.Round))
            drawArc(Color(0xFFD17A54), -90f, 216f, false, style = Stroke(14.dp.toPx(), cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("18m", color = Color.White, fontSize = 42.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold); Text("remaining 12m", color = Color(0xFFA4B0AF), fontSize = 12.sp) }
    }
}

@Composable
private fun OpponentBar() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Color(0xFF334247))) { Box(Modifier.fillMaxWidth(.73f).height(8.dp).clip(CircleShape).background(Color(0xFFB9D5C5))) }
    }
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(12.dp)).background(Copper).clickable(onClick = onClick), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun StatusChip(label: String, background: Color, content: Color) {
    Text(label, color = content, fontSize = 10.sp, letterSpacing = .8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(CircleShape).background(background).padding(horizontal = 10.dp, vertical = 6.dp))
}

@Composable
private fun BottomBar(selected: Destination, onSelect: (Destination) -> Unit) {
    NavigationBar(containerColor = Color.White, modifier = Modifier.navigationBarsPadding()) {
        listOf(Destination.Home, Destination.Challenges).forEach { item -> NavigationBarItem(selected == item, { onSelect(item) }, icon = { NavIcon(item) }, label = { Text(item.label, fontSize = 10.sp) }, colors = navColors()) }
        NavigationBarItem(selected == Destination.Create, { onSelect(Destination.Create) }, icon = { Icon(Icons.Outlined.Add, "Create", tint = Color.White, modifier = Modifier.size(24.dp).clip(CircleShape).background(Copper).padding(4.dp)) }, label = { Text("Create", fontSize = 10.sp) }, colors = navColors())
        listOf(Destination.Activity, Destination.Profile).forEach { item -> NavigationBarItem(selected == item, { onSelect(item) }, icon = { NavIcon(item) }, label = { Text(item.label, fontSize = 10.sp) }, colors = navColors()) }
    }
}

@Composable
private fun navColors() = NavigationBarItemDefaults.colors(selectedIconColor = Copper, selectedTextColor = Copper, unselectedIconColor = Muted, unselectedTextColor = Muted, indicatorColor = Color.Transparent)

@Composable
private fun NavIcon(item: Destination) { Icon(when (item) { Destination.Home -> Icons.Outlined.Home; Destination.Challenges -> Icons.Outlined.SportsScore; Destination.Activity -> Icons.Outlined.History; Destination.Profile -> Icons.Outlined.Person; Destination.Create -> Icons.Outlined.Add }, contentDescription = item.label) }
