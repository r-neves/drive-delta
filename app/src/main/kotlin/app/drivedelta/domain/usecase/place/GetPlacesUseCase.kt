package app.drivedelta.domain.usecase.place

import app.drivedelta.domain.model.Place
import app.drivedelta.domain.repository.PlaceRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Streams the signed-in user's places for the list screen. */
class GetPlacesUseCase @Inject constructor(
    private val repository: PlaceRepository,
) {
    operator fun invoke(): Flow<List<Place>> = repository.observePlaces()
}
