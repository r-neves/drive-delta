package app.drivedelta.ui.places

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.drivedelta.domain.model.Place
import app.drivedelta.domain.usecase.place.DeletePlaceUseCase
import app.drivedelta.domain.usecase.place.GetPlacesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlacesViewModel @Inject constructor(
    getPlacesUseCase: GetPlacesUseCase,
    private val deletePlaceUseCase: DeletePlaceUseCase,
) : ViewModel() {

    val places: StateFlow<List<Place>> = getPlacesUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Hard-deletes [place] after the user confirms in the list dialog (F3: no undo, unlike cars). */
    fun deletePlace(place: Place) {
        viewModelScope.launch { deletePlaceUseCase(place) }
    }
}
