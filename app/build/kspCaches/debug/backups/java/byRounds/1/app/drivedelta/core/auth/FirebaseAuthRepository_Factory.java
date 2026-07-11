package app.drivedelta.core.auth;

import com.google.firebase.auth.FirebaseAuth;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation"
})
public final class FirebaseAuthRepository_Factory implements Factory<FirebaseAuthRepository> {
  private final Provider<FirebaseAuth> firebaseAuthProvider;

  public FirebaseAuthRepository_Factory(Provider<FirebaseAuth> firebaseAuthProvider) {
    this.firebaseAuthProvider = firebaseAuthProvider;
  }

  @Override
  public FirebaseAuthRepository get() {
    return newInstance(firebaseAuthProvider.get());
  }

  public static FirebaseAuthRepository_Factory create(Provider<FirebaseAuth> firebaseAuthProvider) {
    return new FirebaseAuthRepository_Factory(firebaseAuthProvider);
  }

  public static FirebaseAuthRepository newInstance(FirebaseAuth firebaseAuth) {
    return new FirebaseAuthRepository(firebaseAuth);
  }
}
