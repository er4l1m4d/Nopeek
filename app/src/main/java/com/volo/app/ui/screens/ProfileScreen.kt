package com.volo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Wallet
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.volo.app.data.usage.UsagePermissionHelper
import com.volo.app.data.wallet.WalletState
import com.volo.app.data.wallet.walletManager
import com.volo.app.navigation.LocalActivityResultSender
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.volo.app.ui.theme.Celadon
import com.volo.app.ui.theme.Clay
import com.volo.app.ui.theme.Copper
import com.volo.app.ui.theme.DuelMuted
import com.volo.app.ui.theme.Ink
import com.volo.app.ui.theme.Muted
import com.volo.app.ui.theme.SurfaceMuted
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(onOpenDuels: () -> Unit = {}) {
    val sender = LocalActivityResultSender.current
    val scope = rememberCoroutineScope()
    val walletState = walletManager.state

    val context = LocalContext.current
    var usageState by remember { mutableStateOf(UsagePermissionHelper.state(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) usageState = UsagePermissionHelper.state(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Plan 9.1: SOL / USDC / SKR balances for the connected wallet.
    val tokenApi = remember {
        val json = context.assets.open("idl.json").bufferedReader().use { it.readText() }
        com.volo.app.data.solana.TokenBalanceApi(
            com.volo.app.data.solana.ProgramClient(com.volo.app.data.solana.IdlParser.parse(json))
        )
    }
    val connectedAddress = (walletState as? WalletState.Connected)?.address
    var balances by remember { mutableStateOf<com.volo.app.data.solana.TokenBalances?>(null) }
    LaunchedEffect(connectedAddress) {
        if (connectedAddress == null) {
            balances = null
        } else {
            balances = tokenApi.getAll(connectedAddress)
        }
    }
    val skrOpen = balances?.let {
        com.volo.app.data.solana.openDuelsGate(it.skrRaw, it.skrDecimals)
    }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Profile", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        WalletRow(
            state = walletState,
            onConnect = { sender?.let { scope.launch { walletManager.connect(it) } } },
            onDisconnect = { sender?.let { scope.launch { walletManager.disconnect(it) } } }
        )
        if (connectedAddress != null) {
            val b = balances
            ProfileRow(
                "Wallet address",
                "${connectedAddress.take(4)}…${connectedAddress.takeLast(6)}",
            )
            ProfileRow(
                "SOL balance",
                if (b == null) "Loading…" else "${com.volo.app.data.solana.formatUiAmount(b.solLamports, 9)} SOL",
            )
            ProfileRow(
                "USDC balance",
                if (b == null) "Loading…" else com.volo.app.data.solana.formatUiAmount(b.usdcRaw, b.usdcDecimals),
            )
            ProfileRow(
                "SKR balance",
                if (b == null) "Loading…" else com.volo.app.data.solana.formatUiAmount(b.skrRaw, b.skrDecimals),
            )
            OpenDuelsGateRow(
                open = skrOpen == true,
                known = skrOpen != null,
                onClick = { if (skrOpen == true) onOpenDuels() },
            )
        }
        ProfileRow("Seeker status", "Verification unavailable on this device")
        ProfileRow(
            label = "Usage access",
            value = usageState.label(),
            onClick = { runCatching { context.startActivity(UsagePermissionHelper.settingsIntent()) } }
        )
        ProfileRow("Privacy", "Review what Volo measures")
    }
}

/** Plan 9.1 Step 3: SKR >= 10 -> "Open Duels: Available" badge, else locked. */
@Composable
private fun OpenDuelsGateRow(open: Boolean, known: Boolean, onClick: () -> Unit) {
    val background = when {
        !known -> Color.White
        open -> Celadon
        else -> SurfaceMuted
    }
    val textColor = when {
        !known -> Muted
        open -> Ink
        else -> Muted
    }
    val label = when {
        !known -> "Open Duels"
        open -> com.volo.app.data.solana.openDuelsLabel(open = true)
        else -> com.volo.app.data.solana.openDuelsLabel(open = false)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .clickable(enabled = open, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = textColor,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        if (open) {
            Icon(Icons.Outlined.ArrowForward, contentDescription = "Open Duels", tint = Ink, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun WalletRow(state: WalletState, onConnect: () -> Unit, onDisconnect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Ink).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Wallet, contentDescription = null, tint = Color(0xFFD17A54), modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            when (state) {
                is WalletState.Disconnected -> {
                    Text("Wallet not connected", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Connect to hold stakes on devnet.", color = DuelMuted, fontSize = 12.sp)
                }
                is WalletState.Connecting -> {
                    Text("Connecting…", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Approve the request in your wallet app.", color = DuelMuted, fontSize = 12.sp)
                }
                is WalletState.Connected -> {
                    Text(if (state.label != null) "Wallet connected · ${state.label}" else "Wallet connected", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(shorten(state.address), color = DuelMuted, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
                is WalletState.Error -> {
                    Text("Connection failed", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(state.message, color = Color(0xFFE08373), fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        val action = when (state) {
            is WalletState.Disconnected -> "Connect" to onConnect
            is WalletState.Error -> "Try again" to onConnect
            is WalletState.Connected -> "Disconnect" to onDisconnect
            is WalletState.Connecting -> null
        }
        if (action != null) {
            Text(
                action.first,
                color = Copper,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = true, onClick = action.second)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

private fun shorten(address: String): String =
    if (address.length <= 12) address else "${address.take(4)}…${address.takeLast(6)}"

@Composable
private fun ProfileRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).let { row ->
            if (onClick != null) row.clickable(onClick = onClick) else row
        }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) { Text(label, color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp); Text(value, color = Muted, fontSize = 12.sp) }
        Icon(Icons.Outlined.ArrowForward, contentDescription = "Open $label", tint = Muted, modifier = Modifier.size(18.dp))
    }
}
