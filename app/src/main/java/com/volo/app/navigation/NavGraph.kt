package com.volo.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SportsScore
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.volo.app.ui.screens.ActivityScreen
import com.volo.app.ui.screens.ChallengesScreen
import com.volo.app.ui.screens.CreateScreen
import com.volo.app.ui.screens.DuelScreen
import com.volo.app.ui.screens.HomeScreen
import com.volo.app.ui.screens.ProfileScreen
import com.volo.app.ui.theme.Copper
import com.volo.app.ui.theme.Duel
import com.volo.app.ui.theme.Muted
import com.volo.app.ui.theme.Paper

val LocalActivityResultSender = staticCompositionLocalOf<ActivityResultSender?> { null }

private enum class Destination(val label: String, val route: String) {
    Home("Home", NavRoutes.HOME),
    Challenges("Challenges", NavRoutes.CHALLENGES),
    Create("Create", NavRoutes.CREATE_CHALLENGE),
    Activity("Activity", NavRoutes.ACTIVITY),
    Profile("Profile", NavRoutes.PROFILE)
}

@Composable
fun VoloApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val selected = Destination.entries.firstOrNull { it.route == currentRoute }
    Surface(color = if (selected != null) Paper else Duel) {
        Column(Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = NavRoutes.HOME,
                modifier = Modifier.weight(1f)
            ) {
                composable(NavRoutes.HOME) {
                    HomeScreen(onOpenDuel = { navController.navigate(NavRoutes.activeChallenge("demo-challenge")) })
                }
                composable(NavRoutes.CHALLENGES) {
                    ChallengesScreen(onOpenDuel = { navController.navigate(NavRoutes.activeChallenge("demo-challenge")) })
                }
                composable(NavRoutes.CREATE_CHALLENGE) {
                    CreateScreen(onCreated = { navController.navigateToTab(NavRoutes.CHALLENGES) })
                }
                composable(NavRoutes.ACTIVITY) { ActivityScreen() }
                composable(NavRoutes.PROFILE) { ProfileScreen() }
                composable(
                    route = NavRoutes.ACTIVE_CHALLENGE,
                    arguments = listOf(navArgument("pubkey") { type = NavType.StringType })
                ) { backStack ->
                    DuelScreen(pubkey = backStack.arguments?.getString("pubkey").orEmpty(), onBack = { navController.popBackStack() })
                }
            }
            if (selected != null) {
                BottomBar(selected = selected, onSelect = { navController.navigateToTab(it.route) })
            }
        }
    }
}

private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun BottomBar(selected: Destination, onSelect: (Destination) -> Unit) {
    NavigationBar(containerColor = Color.White, modifier = Modifier.padding(bottom = 0.dp)) {
        listOf(Destination.Home, Destination.Challenges).forEach { item ->
            NavigationBarItem(selected == item, { onSelect(item) }, icon = { NavIcon(item) }, label = { Text(item.label, fontSize = 10.sp) }, colors = navColors())
        }
        NavigationBarItem(selected == Destination.Create, { onSelect(Destination.Create) }, icon = { Icon(Icons.Outlined.Add, "Create", tint = Color.White, modifier = Modifier.size(24.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Copper).padding(4.dp)) }, label = { Text("Create", fontSize = 10.sp) }, colors = navColors())
        listOf(Destination.Activity, Destination.Profile).forEach { item ->
            NavigationBarItem(selected == item, { onSelect(item) }, icon = { NavIcon(item) }, label = { Text(item.label, fontSize = 10.sp) }, colors = navColors())
        }
    }
}

@Composable
private fun navColors() = NavigationBarItemDefaults.colors(selectedIconColor = Copper, selectedTextColor = Copper, unselectedIconColor = Muted, unselectedTextColor = Muted, indicatorColor = Color.Transparent)

@Composable
private fun NavIcon(item: Destination) {
    Icon(
        when (item) {
            Destination.Home -> Icons.Outlined.Home
            Destination.Challenges -> Icons.Outlined.SportsScore
            Destination.Activity -> Icons.Outlined.History
            Destination.Profile -> Icons.Outlined.Person
            Destination.Create -> Icons.Outlined.Add
        },
        contentDescription = item.label
    )
}
