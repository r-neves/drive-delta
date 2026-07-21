package app.drivedelta.data.remote

import android.content.Context
import android.location.Geocoder
import app.drivedelta.domain.usecase.segment.RoadNameResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

/**
 * [RoadNameResolver] backed by the platform [Geocoder] (no billed API key). Reverse-geocodes a
 * coordinate and returns its thoroughfare (street/road), falling back to the locality. Uses the
 * synchronous getFromLocation on the IO dispatcher; any failure (no backend, no result) returns null
 * so segment building substitutes a placeholder road name.
 */
class GeocoderRoadNameResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) : RoadNameResolver {

    @Suppress("DEPRECATION") // async getFromLocation is API 33+; sync form supports minSdk 26
    override suspend fun roadNameAt(lat: Double, lng: Double): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        runCatching {
            val geocoder = Geocoder(context, Locale.getDefault())
            val address = geocoder.getFromLocation(lat, lng, 1)?.firstOrNull()
            address?.thoroughfare ?: address?.locality
        }.getOrNull()
    }
}
