package com.volo.app.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavArgumentBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavOptions
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navOptions

@Composable
fun VoloNavHost(modifier: Modifier = Modifier) {
    val navController: NavHostController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = NavRoutes.HOME,
        modifier = modifier
    ) {
        val slideIn = { slideInHorizontally { it } }
        val slideOut = { slideOutHorizontally { -it } }
        composable(NavRoutes.HOME) { HomeRoute(navController) }
        composable(NavRoutes.CREATE_CHALLENGE) { CreateChallengeRoute(navController) }
        composable(
            route = NavRoutes.ACTIVE_CHALLENGE,
            arguments = listOf(navArgument("pubkey") { type = androidx.navigation.navArgumentType<String>() })
        ) { backStackEntry ->
            val pubkey = backStackEntry.arguments?.getString("pubkey") ?: ""
            ActiveChallengeRoute(navController, pubkey)
        }
        composable(NavRoutes.PROFILE) { ProfileRoute(navController) }
    }
}

fun NavControllerExt.activeChallenge(nav: NavHostController, pubkey: String) {
    nav.navigate(NavRoutes.activeChallenge(pubkey))
}

fun NavigateAction(nav: NavHostController, route: String) {
    nav.navigate(route) { launchSingleTop = true }
}

private fun HomeRoute(nav: NavHostController) { VoloHomeSurface(nav) }
private fun CreateChallengeRoute(nav: NavHostController) { VoloCreateSurface(nav) }
private fun ActiveChallengeRoute(nav: NavHostController, pubkey: String) { VoloActiveDuelSurface(nav, pubkey) }
private fun ProfileRoute(nav: NavHostController) { VoloProfileSurface(nav) }

@Composable
fun VoloHomeSurface(nav: NavHostController) {}
@Composable
fun VoloCreateSurface(nav: NavHostController) {}
@Composable
fun VoloActiveDuelSurface(nav: NavHostController, pubkey: String) {}
@Composable
fun VoloProfileSurface(nav: NavHostController) {}
