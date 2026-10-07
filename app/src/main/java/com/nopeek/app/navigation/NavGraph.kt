package com.nopeek.app.navigation

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.nopeek.app.data.local.ChallengeEntity
import com.nopeek.app.data.local.NopeekPrefs
import com.nopeek.app.data.repositories.ChallengeRepository
import com.nopeek.app.ui.components.NopeekScaffold
import com.nopeek.app.ui.screens.ChoiceScreen
import com.nopeek.app.ui.screens.CreateScreen
import com.nopeek.app.ui.screens.DuelScreen
import com.nopeek.app.ui.screens.DuelsScreen
import com.nopeek.app.ui.screens.HomeScreen
import com.nopeek.app.ui.screens.OnboardingScreen
import com.nopeek.app.ui.screens.OpenDuelsScreen
import com.nopeek.app.ui.screens.ProfileScreen
import com.nopeek.app.ui.screens.RecoveryLockScreen
import com.nopeek.app.ui.screens.SplashScreen
import com.nopeek.app.ui.screens.StatsScreen
import com.nopeek.app.ui.theme.Nopeek
import kotlinx.coroutines.launch

val LocalActivityResultSender = staticCompositionLocalOf<ActivityResultSender?> { null }

@Composable
fun NopeekApp() {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val selected = Destination.entries.firstOrNull { it.route == entry?.destination?.route }
    if (selected == null) {
        Surface(color = Nopeek.colors.background) {
            NopeekNavHost(navController)
        }
    } else {
        NopeekScaffold(
            current = selected,
            onSelect = { navController.navigateToTab(it.route) },
        ) { padding ->
            NopeekNavHost(navController, Modifier.padding(padding))
        }
    }
}

fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun NopeekNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = "splash",
        modifier = modifier,
        enterTransition = { fadeIn(tween(300)) + slideInHorizontally(tween(300)) { it / 4 } },
        exitTransition = { fadeOut(tween(250)) },
        popEnterTransition = { fadeIn(tween(250)) },
        popExitTransition = { fadeOut(tween(250)) },
    ) {
        composable("splash") {
            val context = LocalContext.current
            SplashScreen(onTimeout = {
                val next = if (NopeekPrefs(context).routedBefore) "home" else "choice"
                navController.navigate(next) {
                    popUpTo("splash") { inclusive = true }
                }
            })
        }
        composable("choice") {
            val context = LocalContext.current
            ChoiceScreen(
                onNew = { navController.navigate("onboarding") },
                onExisting = {
                    NopeekPrefs(context).knownUser = true
                    navController.navigate("home") {
                        // Pop the choice screen itself: it is gone from the graph once routed,
                        // and leaving it under Home makes system back re-ask the question.
                        popUpTo("choice") { inclusive = true }
                    }
                },
            )
        }
        composable("onboarding") {
            val context = LocalContext.current
            OnboardingScreen(onDone = {
                NopeekPrefs(context).seenOnboarding = true
                navController.navigate("home") {
                    // Same trap: without this the finished onboarding sits under Home and
                    // reappears on the first back press.
                    popUpTo("choice") { inclusive = true }
                }
            })
        }
        composable("home") {
            HomeScreen(
                onOpenDuel = { navController.navigate(NavRoutes.activeChallenge("demo-challenge")) },
                onGoToDuels = { navController.navigateToTab("duels") },
                hasActiveDuel = false,
            )
        }
        composable("duels") {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val mwaSender = LocalActivityResultSender.current
            DuelsScreen(
                onOpenDuel = { navController.navigate(NavRoutes.activeChallenge("demo-challenge")) },
                onOpenRecovery = { pk -> navController.navigate(NavRoutes.recoveryLock(pk)) },
                onSettle = { entity -> settle(scope, context, mwaSender, entity) },
                onNewDuel = { navController.navigate("create_challenge") },
            )
        }
        composable("create_challenge") {
            CreateScreen(onCreated = { navController.navigateToTab("duels") })
        }
        composable("stats") {
            StatsScreen()
        }
        composable("profile") {
            ProfileScreen(onOpenDuels = { navController.navigate("open_duels") })
        }
        composable("open_duels") {
            OpenDuelsScreen(onBack = { navController.popBackStack() }, hasOpenDuels = false)
        }
        composable(
            "active_challenge/{pubkey}",
            arguments = listOf(navArgument("pubkey") { type = NavType.StringType }),
        ) { backStackEntry ->
            val pk = backStackEntry.arguments?.getString("pubkey") ?: ""
            DuelScreen(
                pubkey = pk,
                onBack = { navController.popBackStack() },
                onForfeit = { navController.popBackStack() },
            )
        }
        composable(
            "recovery_lock/{pubkey}",
            arguments = listOf(navArgument("pubkey") { type = NavType.StringType }),
        ) { backStackEntry ->
            val pk = backStackEntry.arguments?.getString("pubkey") ?: ""
            RecoveryLockScreen(pubkey = pk, onBack = { navController.popBackStack() })
        }
    }
}

private fun settle(
    scope: kotlinx.coroutines.CoroutineScope,
    context: Context,
    mwaSender: ActivityResultSender?,
    entity: ChallengeEntity,
) {
    // No navController here on purpose: settlement is fire-and-report. The row flips
    // to SETTLED through the repository's own observation, so navigating away from
    // the list on success would only hide the receipt the user just earned.
    if (mwaSender == null) return
    scope.launch {
        val result = ChallengeRepository.getInstance(context).settleWithUser(entity, mwaSender)
        result.exceptionOrNull()?.let {
            Toast.makeText(context, it.message ?: "Settlement failed.", Toast.LENGTH_LONG).show()
        }
        result.getOrNull()?.let { sig ->
            Toast.makeText(context, "Settled on-chain: ${sig.take(8)}…", Toast.LENGTH_SHORT).show()
        }
    }
}

@Deprecated("Use NopeekApp instead")
@Composable
fun AppNavGraph(navController: NavHostController = rememberNavController()) {
    NopeekNavHost(navController)
}
