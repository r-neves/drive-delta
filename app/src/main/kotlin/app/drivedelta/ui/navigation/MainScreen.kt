package app.drivedelta.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.drivedelta.R
import app.drivedelta.ui.cars.CarsScreen
import app.drivedelta.ui.dashboard.DashboardScreen
import app.drivedelta.ui.places.PlacesScreen
import app.drivedelta.ui.theme.DdNavBackground
import app.drivedelta.ui.theme.DdTextTertiary

/** A bottom-nav tab: its route and how it renders in the bar. */
private data class TabItem(
    val route: String,
    @StringRes val label: Int,
    val icon: ImageVector,
)

private val tabs = listOf(
    TabItem(NavDestinations.DASHBOARD, R.string.nav_home, Icons.Outlined.Home),
    TabItem(NavDestinations.CARS, R.string.nav_vehicles, Icons.Outlined.DirectionsCar),
    TabItem(NavDestinations.PLACES, R.string.nav_places, Icons.Outlined.Place),
)

/**
 * The signed-in shell: a bottom navigation bar over its own inner NavHost of tab destinations.
 * Full-screen editors (e.g. car edit) live in the outer graph and are reached via the callbacks so
 * they cover the bar. The inner NavHost is padded by the bar height; each tab owns its top inset.
 */
@Composable
fun MainScreen(
    onSignedOut: () -> Unit,
    onStartTracking: () -> Unit,
    onAddCar: () -> Unit,
    onEditCar: (String) -> Unit,
    onAddPlace: () -> Unit,
    onEditPlace: (String) -> Unit,
    tabNavController: NavHostController = rememberNavController(),
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = { DdBottomBar(tabNavController) },
    ) { padding ->
        NavHost(
            navController = tabNavController,
            startDestination = NavDestinations.DASHBOARD,
            modifier = Modifier.padding(padding),
        ) {
            composable(NavDestinations.DASHBOARD) {
                DashboardScreen(onSignedOut = onSignedOut, onStartTracking = onStartTracking)
            }
            composable(NavDestinations.CARS) {
                CarsScreen(onAddCar = onAddCar, onEditCar = onEditCar)
            }
            composable(NavDestinations.PLACES) {
                PlacesScreen(onAddPlace = onAddPlace, onEditPlace = onEditPlace)
            }
        }
    }
}

@Composable
private fun DdBottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    NavigationBar(containerColor = DdNavBackground) {
        tabs.forEach { tab ->
            val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(tab.route) {
                        // Single instance per tab; preserve/restore each tab's own back stack.
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(stringResource(tab.label)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.surface,
                    unselectedIconColor = DdTextTertiary,
                    unselectedTextColor = DdTextTertiary,
                ),
            )
        }
    }
}
