package app.drivedelta.data.remote.roads

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Google Roads API response DTOs. `snapToRoads` returns points snapped to the road network, each
 * carrying the road's Google `placeId`; interpolated points (added when `interpolate=true`) have a
 * null [originalIndex]. The API does NOT return road names — those are resolved separately
 * (reverse-geocode of the segment midpoint) in segment building.
 */
@Serializable
data class RoadsSnapResponse(
    val snappedPoints: List<SnappedPointDto> = emptyList(),
)

@Serializable
data class SnappedPointDto(
    val location: RoadsLocationDto,
    val originalIndex: Int? = null,
    @SerialName("placeId") val placeId: String = "",
)

@Serializable
data class RoadsLocationDto(
    val latitude: Double,
    val longitude: Double,
)
