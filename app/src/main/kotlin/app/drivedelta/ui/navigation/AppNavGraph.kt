package app.drivedelta.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.drivedelta.ui.auth.AuthScreen
import app.drivedelta.ui.dashboard.DashboardScreen

/**
 * Top-level navigation. [startDestination] is decided once at launch from the current auth state:
 * signed-in users land on Dashboard, everyone else on Auth. Sign-in and sign-out replace the back
 * stack so Back never crosses the auth boundary.
 */
@Composable
fun AppNavGraph(
    startDestination: String,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable(NavDestinations.AUTH) {
            AuthScreen(
                onSignedIn = {
                    navController.navigate(NavDestinations.DASHBOARD) {
                        popUpTo(NavDestinations.AUTH) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(NavDestinations.DASHBOARD) {
            DashboardScreen(
                onSignedOut = {
                    navController.navigate(NavDestinations.AUTH) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
    }
}
