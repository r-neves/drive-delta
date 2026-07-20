package app.drivedelta.domain.usecase.car

import app.drivedelta.domain.repository.CarRepository
import javax.inject.Inject

/** Soft-deletes a car so the deletion syncs to Firestore before the row is eventually removed. */
class DeleteCarUseCase @Inject constructor(
    private val repository: CarRepository,
) {
    suspend operator fun invoke(carId: String) = repository.deleteCar(carId)
}
