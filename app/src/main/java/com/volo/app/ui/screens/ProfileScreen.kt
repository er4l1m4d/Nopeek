package com.volo.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalContext
import com.volo.app.data.solana.TokenBalanceApi
import com.volo.app.data.solana.TokenBalances
import com.volo.app.data.solana.formatUiAmount
import com.volo.app.data.solana.openDuelsGate
import com.volo.app.data.solana.openDuelsLabel
import com.volo.app.data.usage.UsagePermissionHelper
import com.volo.app.data.usage.UsagePermissionState
import com.volo.app.data.wallet.WalletState
import com.volo.app.data.wallet.walletManager
import com.volo.app.navigation.LocalActivityResultSender
import com.volo.app.ui.components.InfoCard
import com.volo.app.ui.components.InlineError
import com.volo.app.ui.components.LoadingSkeleton
import com.volo.app.ui.components.MetricRow
import com.volo.app.ui.components.PrimaryButton
import com.volo.app.ui.components.RowCard
import com.volo.app.ui.components.SecondaryButton
import com.volo.app.ui.components.TapIconButton
import com.volo.app.ui.icons.Phosphor
import com.volo.app.ui.theme.VoloColors
import com.volo.app.ui.theme.VoloDimens
import com.volo.app.ui.theme.VoloTheme
import com.volo.app.ui.theme.VoloType
import kotlinx.coroutines.launch

/**
 * Profile on the dark design system (demo wiring preserved — no wallet
 * logic changes, only visual treatment of the 4 wallet states).
 * Anatomy: wallet card (mono shortened address + Copy affordance;
 * disconnected/connecting/connected/failed branches), mono balance rows,
 * usage-access status [InfoCard], Seeker badge or honest unavailable
 * [InlineError], settings [RowCard]s.
 */
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
        TokenBalanceApi(
            com.volo.app.data.solana.ProgramClient(com.volo.app.data.solana.IdlParser.parse(json))
        )
    }
    val connectedAddress = (walletState as? WalletState.Connected)?.address
    var balances by remember { mutableStateOf<TokenBalances?>(null) }
    LaunchedEffect(connectedAddress) {
        if (connectedAddress == null) {
            balances = null
        } else {
            balances = tokenApi.getAll(connectedAddress)
        }
    }
    val skrOpen = balances?.let {
        openDuelsGate(it.skrRaw, it.skrDecimals)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VoloColors.bg)
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = VoloDimens.spaceXl,
                vertical = VoloDimens.spaceXxl,
            ),
        verticalArrangement = Arrangement.spacedBy(VoloDimens.spaceLg),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(VoloDimens.spaceXs)) {
            Text(
                text = "Profile",
                style = VoloType.headline,
                color = VoloColors.text,
            )
            Text(
                text = "Wallet, access, and settings.",
                style = VoloType.bodySmall,
                color = VoloColors.textMuted,
            )
        }
        WalletCard(
            state = walletState,
            onConnect = { sender?.let { scope.launch { walletManager.connect(it) } } },
            onDisconnect = { sender?.let { scope.launch { walletManager.disconnect(it) } } },
            modifier = Modifier.fillMaxWidth(),
        )
        if (connectedAddress != null) {
            BalancesCard(
                balances = balances,
                modifier = Modifier.fillMaxWidth(),
            )
            OpenDuelsGateRow(
                open = skrOpen == true,
                known = skrOpen != null,
                onClick = { if (skrOpen == true) onOpenDuels() },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        InfoCard(
            title = "Usage access",
            subtitle = usageState.label(),
            modifier = Modifier.fillMaxWidth(),
            onClick = { runCatching { context.startActivity(UsagePermissionHelper.settingsIntent()) } },
            leading = {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(VoloColors.surfaceHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (usageState == UsagePermissionState.GRANTED) {
                            Phosphor.CheckCircle
                        } else {
                            Phosphor.LockKey
                        },
                        contentDescription = null,
                        tint = VoloColors.copper,
                        modifier = Modifier.size(22.dp),
                    )
                }
            },
        )
        if (seekerVerified()) {
            RowCard(
                title = "Verified Seeker",
                subtitle = "Genesis token verified on this device.",
                modifier = Modifier.fillMaxWidth(),
                trailing = {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(VoloColors.surfaceHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Phosphor.Trophy,
                            contentDescription = null,
                            tint = VoloColors.copper,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                },
            )
        } else {
            InlineError(
                message = seekerStatusLabel(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        RowCard(
            title = "Privacy",
            subtitle = "Review what Volo measures.",
            modifier = Modifier.fillMaxWidth(),
            trailing = {
                Icon(
                    imageVector = Phosphor.ArrowRight,
                    contentDescription = null,
                    tint = VoloColors.textMuted,
                    modifier = Modifier.size(22.dp),
                )
            },
        )
    }
}

/** Plan 10.1 Step 4: "Seeker Status: Not Verified (requires Seeker hardware)". */
internal fun seekerStatusLabel(): String =
    when (walletManager.identityProvider.verifyGenesisToken(walletManager.walletAddress.orEmpty())) {
        is com.volo.app.data.identity.IdentityVerificationResult.VerifiedSeeker -> "Verified Seeker"
        is com.volo.app.data.identity.IdentityVerificationResult.Error -> "Verification error"
        com.volo.app.data.identity.IdentityVerificationResult.Unverified ->
            "Not Verified (requires Seeker hardware)"
    }

internal fun seekerVerified(): Boolean =
    (walletManager.identityProvider.verifyGenesisToken(walletManager.walletAddress.orEmpty()) is com.volo.app.data.identity.IdentityVerificationResult.VerifiedSeeker)

@Composable
private fun WalletCard(
    state: WalletState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(VoloDimens.radiusCard),
        color = VoloColors.surfaceRaised,
        contentColor = VoloColors.text,
        border = BorderStroke(1.dp, VoloColors.line),
    ) {
        Column(
            modifier = Modifier.padding(VoloDimens.spaceLg),
            verticalArrangement = Arrangement.spacedBy(VoloDimens.spaceMd),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(VoloDimens.spaceMd),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(VoloColors.surfaceHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Phosphor.Wallet,
                        contentDescription = null,
                        tint = VoloColors.copper,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(VoloDimens.spaceXs),
                ) {
                    when (state) {
                        is WalletState.Disconnected -> {
                            Text(
                                text = "Wallet not connected",
                                style = VoloType.titleSmall,
                                color = VoloColors.text,
                            )
                            Text(
                                text = "Connect to hold stakes on devnet.",
                                style = VoloType.bodySmall,
                                color = VoloColors.textMuted,
                            )
                        }
                        is WalletState.Connecting -> {
                            Text(
                                text = "Connecting…",
                                style = VoloType.titleSmall,
                                color = VoloColors.text,
                            )
                            Text(
                                text = "Approve the request in your wallet app.",
                                style = VoloType.bodySmall,
                                color = VoloColors.textMuted,
                            )
                        }
                        is WalletState.Connected -> {
                            val clipboard = LocalClipboardManager.current
                            Text(
                                text = if (state.label != null) {
                                    "Wallet connected · ${state.label}"
                                } else {
                                    "Wallet connected"
                                },
                                style = VoloType.titleSmall,
                                color = VoloColors.text,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(VoloDimens.spaceXs),
                            ) {
                                Text(
                                    text = shorten(state.address),
                                    style = VoloType.monoSmall,
                                    color = VoloColors.textMuted,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                TapIconButton(
                                    contentDescription = "Copy wallet address",
                                    onClick = {
                                        clipboard.setText(AnnotatedString(state.address))
                                    },
                                ) {
                                    Icon(
                                        imageVector = Phosphor.Copy,
                                        contentDescription = null,
                                        tint = VoloColors.textMuted,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                        is WalletState.Error -> {
                            Text(
                                text = "Connection failed",
                                style = VoloType.titleSmall,
                                color = VoloColors.text,
                            )
                            Text(
                                text = state.message,
                                style = VoloType.bodySmall,
                                color = VoloColors.fail,
                            )
                        }
                    }
                }
            }
            when (state) {
                is WalletState.Disconnected -> {
                    PrimaryButton(
                        label = "Connect",
                        onClick = onConnect,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                is WalletState.Error -> {
                    SecondaryButton(
                        label = "Try again",
                        onClick = onConnect,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                is WalletState.Connected -> {
                    SecondaryButton(
                        label = "Disconnect",
                        onClick = onDisconnect,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                is WalletState.Connecting -> Unit
            }
        }
    }
}

@Composable
private fun BalancesCard(
    balances: TokenBalances?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(VoloDimens.radiusCard),
        color = VoloColors.surface,
        contentColor = VoloColors.text,
        border = BorderStroke(1.dp, VoloColors.line),
    ) {
        Column(
            modifier = Modifier.padding(VoloDimens.spaceLg),
            verticalArrangement = Arrangement.spacedBy(VoloDimens.spaceMd),
        ) {
            MetricRow(
                title = "SOL balance",
                value = if (balances == null) {
                    "Loading…"
                } else {
                    "${formatUiAmount(balances.solLamports, 9)} SOL"
                },
                modifier = Modifier.fillMaxWidth(),
            )
            MetricRow(
                title = "USDC balance",
                value = if (balances == null) {
                    "Loading…"
                } else {
                    "${formatUiAmount(balances.usdcRaw, balances.usdcDecimals)} USDC"
                },
                modifier = Modifier.fillMaxWidth(),
            )
            MetricRow(
                title = "SKR balance",
                value = if (balances == null) {
                    "Loading…"
                } else {
                    "${formatUiAmount(balances.skrRaw, balances.skrDecimals)} SKR"
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Plan 9.1 Step 3: SKR >= 10 -> "Open Duels: Available" badge, else locked. */
@Composable
private fun OpenDuelsGateRow(
    open: Boolean,
    known: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!known) {
        LoadingSkeleton(
            height = 56.dp,
            modifier = modifier,
        )
        return
    }
    if (open) {
        RowCard(
            title = openDuelsLabel(open = true),
            subtitle = "SKR balance meets the gate.",
            modifier = modifier,
            onClick = onClick,
            trailing = {
                Icon(
                    imageVector = Phosphor.ArrowRight,
                    contentDescription = null,
                    tint = VoloColors.copper,
                    modifier = Modifier.size(22.dp),
                )
            },
        )
    } else {
        RowCard(
            title = openDuelsLabel(open = false),
            subtitle = "Hold at least 10 SKR to unlock.",
            modifier = modifier,
            trailing = {
                Icon(
                    imageVector = Phosphor.LockKey,
                    contentDescription = null,
                    tint = VoloColors.textMuted,
                    modifier = Modifier.size(22.dp),
                )
            },
        )
    }
}

private fun shorten(address: String): String =
    if (address.length <= 12) address else "${address.take(4)}…${address.takeLast(6)}"

@Preview(showBackground = true, backgroundColor = 0xFF10191D)
@Composable
private fun ProfileScreenPreview() {
    VoloTheme {
        ProfileScreen()
    }
}
