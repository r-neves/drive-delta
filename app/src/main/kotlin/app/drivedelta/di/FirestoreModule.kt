package app.drivedelta.di

import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirestoreModule {

    // This project's Firestore instance is a NAMED database, not the SDK's "(default)". The KTX
    // Firebase.firestore accessor targets "(default)", which doesn't exist here — so bind the
    // named instance explicitly. See PROGRESS.md.
    private const val DATABASE_ID = "drivedelta-firestore"

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance(DATABASE_ID)
}
