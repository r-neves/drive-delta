package app.drivedelta

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.drivedelta.core.auth.AuthRepository
import app.drivedelta.ui.navigation.AppNavGraph
import app.drivedelta.ui.navigation.NavDestinations
import app.drivedelta.ui.theme.DriveDeltaTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val startDestination = if (authRepository.isSignedIn) {
            NavDestinations.DASHBOARD
        } else {
            NavDestinations.AUTH
        }

        setContent {
            DriveDeltaTheme {
                AppNavGraph(startDestination = startDestination)
            }
        }
    }
}
