package app.drivedelta.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.drivedelta.ui.auth.AuthScreen
import app.drivedelta.ui.cars.CarEditScreen

/**
 * Top-level navigation. [startDestination] is decided once at launch from the current auth state:
 * signed-in users land on [NavDestinations.MAIN] (the bottom-nav shell), everyone else on Auth.
 * Sign-in and sign-out replace the back stack so Back never crosses the auth boundary. Full-screen
 * editors live here (not inside the tab graph) so they cover the bottom bar.
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
                    navController.navigate(NavDestinations.MAIN) {
                        popUpTo(NavDestinations.AUTH) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(NavDestinations.MAIN) {
            MainScreen(
                onSignedOut = {
                    navController.navigate(NavDestinations.AUTH) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onAddCar = { navController.navigate(NavDestinations.carEdit()) },
                onEditCar = { carId -> navController.navigate(NavDestinations.carEdit(carId)) },
            )
        }
        composable(
            route = NavDestinations.CAR_EDIT_ROUTE,
            arguments = listOf(
                navArgument(NavArgs.CAR_ID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            CarEditScreen(onDone = { navController.popBackStack() })
        }
    }
}
