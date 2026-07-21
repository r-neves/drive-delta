package app.drivedelta.domain.usecase.segment

/**
 * Resolves a human-readable road name for a coordinate. The Roads API returns only a `placeId`, so
 * segment building reverse-geocodes the segment midpoint through this abstraction (implemented with
 * the platform Geocoder — no extra API key). Returns null when no name is available.
 */
interface RoadNameResolver {
    suspend fun roadNameAt(lat: Double, lng: Double): String?
}
