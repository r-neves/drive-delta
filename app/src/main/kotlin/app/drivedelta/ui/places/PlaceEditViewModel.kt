package app.drivedelta.ui.places

import android.content.Context
import android.location.Geocoder
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.drivedelta.domain.model.Place
import app.drivedelta.domain.usecase.place.GetPlacesUseCase
import app.drivedelta.domain.usecase.place.SavePlaceUseCase
import app.drivedelta.ui.navigation.NavArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

/** Default map centre (Lisbon) for a brand-new place until the user positions the marker. */
private const val DEFAULT_LAT = 38.7223
private const val DEFAULT_LNG = -9.1393

data class PlaceEditUiState(
    val editingId: String? = null,
    val name: String = "",
    val address: String = "",
    val lat: Double = DEFAULT_LAT,
    val lng: Double = DEFAULT_LNG,
    val radiusMeters: Float = 100f,
    val iconEmoji: String = "📍",
    val hasMarker: Boolean = false, // false until the user picks/drags a location on a new place
    // Bumped on programmatic moves (autocomplete, "use my location", loading an existing place) so
    // the map recenters the marker + camera. Drags do NOT bump it — the marker is already there.
    val recenterSignal: Int = 0,
    val nameError: Boolean = false,
    val saved: Boolean = false,
) {
    val isEditing: Boolean get() = editingId != null
}

@HiltViewModel
class PlaceEditViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val savePlaceUseCase: SavePlaceUseCase,
    private val getPlacesUseCase: GetPlacesUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val placeId: String? = savedStateHandle[NavArgs.PLACE_ID]
    private var createdAt: Long? = null
    private var geocodeJob: Job? = null

    private val _uiState = MutableStateFlow(PlaceEditUiState())
    val uiState: StateFlow<PlaceEditUiState> = _uiState.asStateFlow()

    init {
        if (placeId != null) loadPlace(placeId)
    }

    private fun loadPlace(id: String) {
        viewModelScope.launch {
            val place = getPlacesUseCase().first().firstOrNull { it.id == id } ?: return@launch
            createdAt = place.createdAt
            _uiState.update {
                it.copy(
                    editingId = place.id,
                    name = place.name,
                    address = place.address,
                    lat = place.lat,
                    lng = place.lng,
                    radiusMeters = place.radiusMeters,
                    iconEmoji = place.iconEmoji,
                    hasMarker = true,
                    recenterSignal = it.recenterSignal + 1,
                )
            }
        }
    }

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value, nameError = false) }
    fun onEmojiChange(emoji: String) = _uiState.update { it.copy(iconEmoji = emoji) }
    fun onRadiusChange(value: Float) = _uiState.update { it.copy(radiusMeters = value) }

    /** Address search selected a prediction: jump the marker + camera there, fill the address label. */
    fun onLocationPicked(lat: Double, lng: Double, address: String) {
        geocodeJob?.cancel()
        _uiState.update {
            it.copy(
                lat = lat, lng = lng, address = address,
                hasMarker = true, recenterSignal = it.recenterSignal + 1,
            )
        }
    }

    /**
     * "Use my location": move the marker + camera to the GPS fix and reverse-geocode the label.
     * Bumps recenter (unlike a drag) because the marker must jump to the new spot.
     */
    fun onUseMyLocation(lat: Double, lng: Double) {
        _uiState.update {
            it.copy(lat = lat, lng = lng, hasMarker = true, recenterSignal = it.recenterSignal + 1)
        }
        scheduleReverseGeocode(lat, lng)
    }

    /** Marker dragged on the map: update position + reverse-geocode. No recenter (marker is there). */
    fun onMarkerMoved(lat: Double, lng: Double) {
        _uiState.update { it.copy(lat = lat, lng = lng, hasMarker = true) }
        scheduleReverseGeocode(lat, lng)
    }

    private fun scheduleReverseGeocode(lat: Double, lng: Double) {
        geocodeJob?.cancel()
        geocodeJob = viewModelScope.launch {
            delay(1_000) // debounce rapid drags
            reverseGeocode(lat, lng)?.let { label ->
                _uiState.update { it.copy(address = label) }
            }
        }
    }

    /** Platform [Geocoder] reverse lookup — needs no Maps/Places key. Null on failure/no result. */
    private suspend fun reverseGeocode(lat: Double, lng: Double): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        runCatching {
            @Suppress("DEPRECATION")
            Geocoder(context, Locale.getDefault())
                .getFromLocation(lat, lng, 1)
                ?.firstOrNull()
                ?.getAddressLine(0)
        }.getOrNull()
    }

    fun save() {
        val state = _uiState.value
        if (state.name.isBlank()) {
            _uiState.update { it.copy(nameError = true) }
            return
        }
        val place = Place(
            id = state.editingId ?: UUID.randomUUID().toString(),
            userId = "", // stamped by the repository from the current session
            name = state.name.trim(),
            address = state.address.trim(),
            lat = state.lat,
            lng = state.lng,
            radiusMeters = state.radiusMeters,
            iconEmoji = state.iconEmoji,
            createdAt = createdAt ?: System.currentTimeMillis(),
        )
        viewModelScope.launch {
            savePlaceUseCase(place)
            _uiState.update { it.copy(saved = true) }
        }
    }
}
