package app.drivedelta.domain.usecase.car

import app.drivedelta.domain.model.Car
import app.drivedelta.domain.repository.CarRepository
import javax.inject.Inject

/** Creates or updates a car. Also used to restore a swipe-deleted car (undo). */
class SaveCarUseCase @Inject constructor(
    private val repository: CarRepository,
) {
    suspend operator fun invoke(car: Car) = repository.saveCar(car)
}
