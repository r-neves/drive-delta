package app.drivedelta.ui.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Wires the tracking permission suite as a sequential chain and returns an action to launch it. The
 * order matters: Android rejects background location unless fine location is already granted, and
 * each request must be its own dialog. The chain is fine → background (API 29+) → notifications
 * (API 33+) → battery-optimisation exemption → [onAllGranted].
 *
 * For Checkpoint 5 a denied step simply doesn't advance (the trip won't start); the permanently-
 * denied rationale + settings deep-links are Checkpoint 10 hardening. [onAllGranted] fires once the
 * chain reaches the end regardless of the optional grants (background/notifications/battery), since
 * fine location alone is enough to record while the app is foreground.
 */
@Composable
fun rememberStartTrackingPermissionFlow(onAllGranted: () -> Unit): () -> Unit {
    val context = LocalContext.current

    // Steps are declared in reverse so each earlier launcher can reference the next one's callback.
    val batteryLauncher = rememberLauncherForActivityResult(StartActivityForResult()) {
        onAllGranted()
    }
    val notificationLauncher = rememberLauncherForActivityResult(RequestPermission()) {
        requestBatteryExemptionThen(context, batteryLauncher, onAllGranted)
    }

    // Shared continuation after location is settled: notifications → battery → done.
    val afterLocation: () -> Unit = {
        requestNotificationsThen(context, notificationLauncher) {
            requestBatteryExemptionThen(context, batteryLauncher, onAllGranted)
        }
    }

    val backgroundLauncher = rememberLauncherForActivityResult(RequestPermission()) { afterLocation() }
    val fineLauncher = rememberLauncherForActivityResult(RequestMultiplePermissions()) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) requestBackgroundThen(context, backgroundLauncher, afterLocation)
    }

    return {
        if (hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)) {
            requestBackgroundThen(context, backgroundLauncher, afterLocation)
        } else {
            fineLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }
}

private fun requestBackgroundThen(
    context: Context,
    launcher: ActivityResultLauncher<String>,
    next: () -> Unit,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
        !hasPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    ) {
        launcher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    } else {
        next()
    }
}

private fun requestNotificationsThen(
    context: Context,
    launcher: ActivityResultLauncher<String>,
    next: () -> Unit,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        !hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
    ) {
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        next()
    }
}

private fun requestBatteryExemptionThen(
    context: Context,
    launcher: ActivityResultLauncher<Intent>,
    next: () -> Unit,
) {
    val powerManager = context.getSystemService(PowerManager::class.java)
    if (powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true) {
        next()
        return
    }
    val intent = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${context.packageName}"),
    )
    // Some OEMs/emulators don't resolve this action; fall through so tracking still starts.
    runCatching { launcher.launch(intent) }.onFailure { next() }
}

private fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
