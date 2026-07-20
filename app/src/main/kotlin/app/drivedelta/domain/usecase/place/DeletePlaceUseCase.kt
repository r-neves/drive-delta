package app.drivedelta.domain.usecase.place

import app.drivedelta.domain.model.Place
import app.drivedelta.domain.repository.PlaceRepository
import javax.inject.Inject

/** Deletes a place after the user confirms in the list's AlertDialog. */
class DeletePlaceUseCase @Inject constructor(
    private val repository: PlaceRepository,
) {
    suspend operator fun invoke(place: Place) = repository.deletePlace(place)
}
