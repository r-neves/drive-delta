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
 * [RoadNameResolver] backed by the platform [Geocoder] (no billed API key).
 *
 * Returns the thoroughfare, or a road designation like `A1` / `IC3` / `N236-1` when the geocoder
 * only fills `featureName`. It deliberately **does not fall back to the locality**: on a motorway
 * the midpoint frequently has no thoroughfare, and returning the town instead produced segments
 * named "Amadora" or "Castanheira de Pêra" in the middle of a continuous road. Those aren't roads,
 * and because segments are grouped by name they shattered otherwise-continuous stretches — on one
 * 173 km drive "Autoestrada do Norte" came back as seven separate runs, and 13% of all distance
 * recorded sat under a locality name. Returning null instead lets the caller carry the surrounding
 * road name across the gap.
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
            address?.thoroughfare
                ?: address?.featureName?.takeIf { ROAD_DESIGNATION.matches(it) }
        }.getOrNull()
    }

    private companion object {
        /**
         * Portuguese/European road designations the geocoder reports as a bare `featureName`:
         * motorways (A1), itinerários (IC8, IP5), nacionais/municipais (N236-1, M501), and the
         * Lisbon ring roads (CREL/CRIL).
         */
        val ROAD_DESIGNATION = Regex("^(A|N|M|EN|EM|IC|IP)\\s?\\d+[A-Za-z0-9-]*$|^(CREL|CRIL)$")
    }
}
