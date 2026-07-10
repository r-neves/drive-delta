package app.drivedelta.core.auth

import kotlinx.coroutines.flow.Flow

/**
 * Authentication surface for the app. Backed by Firebase Auth (Google SSO). All per-user data
 * queries elsewhere filter by [currentUserId].
 */
interface AuthRepository {

    /** Firebase UID of the signed-in user, or null when signed out. */
    val currentUserId: String?

    /** True when a user is currently signed in. */
    val isSignedIn: Boolean

    /** Emits true/false as the auth state changes (drives navigation). */
    val authState: Flow<Boolean>

    /**
     * Exchanges a Google ID token for a Firebase session.
     * @return success on sign-in, or failure carrying the underlying exception.
     */
    suspend fun signInWithGoogle(idToken: String): Result<Unit>

    /** Signs the current user out of Firebase. */
    fun signOut()
}
