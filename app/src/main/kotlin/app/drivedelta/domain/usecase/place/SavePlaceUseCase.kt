package app.drivedelta.domain.usecase.place

import app.drivedelta.domain.model.Place
import app.drivedelta.domain.repository.PlaceRepository
import javax.inject.Inject

/** Creates or updates a place. */
class SavePlaceUseCase @Inject constructor(
    private val repository: PlaceRepository,
) {
    suspend operator fun invoke(place: Place) = repository.savePlace(place)
}
