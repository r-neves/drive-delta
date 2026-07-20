package app.drivedelta.domain.usecase.car

import app.drivedelta.domain.model.Car
import app.drivedelta.domain.repository.CarRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Streams the signed-in user's cars for the list screen. */
class GetCarsUseCase @Inject constructor(
    private val repository: CarRepository,
) {
    operator fun invoke(): Flow<List<Car>> = repository.observeCars()
}
