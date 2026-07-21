package app.drivedelta.data.remote.roads

import app.drivedelta.BuildConfig
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit binding for the Google Roads API. Called at most once per completed trip (post-ride),
 * never during live tracking, per the cost-control rule. Base URL: https://roads.googleapis.com/.
 */
interface RoadsApiService {

    /**
     * Snaps a GPS [path] ("lat,lng|lat,lng|…", max 100 points) to the road network. With
     * [interpolate] true the response also contains interpolated points that fill the road geometry
     * between snapped inputs.
     */
    @GET("v1/snapToRoads")
    suspend fun snapToRoads(
        @Query("path") path: String,
        @Query("interpolate") interpolate: Boolean = true,
        @Query("key") key: String = BuildConfig.ROADS_API_KEY,
    ): RoadsSnapResponse
}
