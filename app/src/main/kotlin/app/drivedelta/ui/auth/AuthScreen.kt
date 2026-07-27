package app.drivedelta.ui.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.drivedelta.R
import app.drivedelta.ui.theme.DdSecondary
import app.drivedelta.ui.theme.DdSurfaceElevated
import app.drivedelta.ui.theme.DdTextDim
import app.drivedelta.ui.theme.LocalDdTokens
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes

private const val SIGN_IN_CANCELLED = 12501

@Composable
fun AuthScreen(
    onSignedIn: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val genericError = stringResource(R.string.auth_error_generic)

    val googleSignInClient = remember {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                .getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken != null) {
                viewModel.onGoogleIdToken(idToken)
            } else {
                viewModel.onSignInError(genericError)
            }
        } catch (e: ApiException) {
            // User cancelled → reset loading silently; otherwise surface a generic error.
            if (e.statusCode == CommonStatusCodes.CANCELED || e.statusCode == SIGN_IN_CANCELLED) {
                viewModel.onSignInError(null)
            } else {
                viewModel.onSignInError(genericError)
            }
        }
    }

    LaunchedEffect(uiState.signedIn) {
        if (uiState.signedIn) onSignedIn()
    }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeError()
        }
    }

    val tokens = LocalDdTokens.current

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = tokens.screenPadding)
                .padding(top = 40.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Brand block sits in the upper-middle; the button + notice are pinned to the bottom.
            Spacer(Modifier.weight(1f))

            Surface(
                modifier = Modifier.size(88.dp),
                shape = RoundedCornerShape(tokens.radiusCard),
                color = DdSurfaceElevated,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(R.drawable.ic_apex_logo),
                        contentDescription = stringResource(R.string.auth_logo_desc),
                        modifier = Modifier.size(44.dp),
                    )
                }
            }

            Spacer(Modifier.height(tokens.spaceXl))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 38.sp,
            )

            Spacer(Modifier.height(tokens.spaceMd))

            Text(
                text = stringResource(R.string.auth_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                fontSize = 18.sp,
            )

            Spacer(Modifier.weight(1.4f))

            Button(
                onClick = {
                    viewModel.onSignInLaunched()
                    launcher.launch(googleSignInClient.signInIntent)
                },
                enabled = !uiState.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(tokens.radiusMd)),
                shape = RoundedCornerShape(tokens.radiusMd),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DdSurfaceElevated,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.ic_google_g),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(tokens.spaceMd))
                        Text(
                            text = stringResource(R.string.auth_sign_in_google),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(tokens.spaceLg))

            Text(
                text = buildAnnotatedString {
                    append(stringResource(R.string.auth_terms_prefix))
                    append(" ")
                    withStyle(SpanStyle(color = DdSecondary)) {
                        append(stringResource(R.string.auth_terms))
                    }
                    append(" ")
                    append(stringResource(R.string.auth_terms_conj))
                    append(" ")
                    withStyle(SpanStyle(color = DdSecondary)) {
                        append(stringResource(R.string.auth_privacy))
                    }
                    append(".")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = DdTextDim,
                textAlign = TextAlign.Center,
            )
        }
    }
}
