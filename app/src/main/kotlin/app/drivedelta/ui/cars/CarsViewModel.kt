package app.drivedelta.ui.cars

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.drivedelta.domain.model.Car
import app.drivedelta.domain.usecase.car.DeleteCarUseCase
import app.drivedelta.domain.usecase.car.GetCarsUseCase
import app.drivedelta.domain.usecase.car.SaveCarUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CarsViewModel @Inject constructor(
    getCarsUseCase: GetCarsUseCase,
    private val saveCarUseCase: SaveCarUseCase,
    private val deleteCarUseCase: DeleteCarUseCase,
) : ViewModel() {

    val cars: StateFlow<List<Car>> = getCarsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Holds the last swiped-away car so [undoDelete] can restore it. Single-slot: newest wins. */
    private var recentlyDeleted: Car? = null

    /** Soft-deletes [car]; it leaves the list immediately (the flow filters deleted rows). */
    fun deleteCar(car: Car) {
        recentlyDeleted = car
        viewModelScope.launch { deleteCarUseCase(car.id) }
    }

    /** Re-inserts the last deleted car (un-deletes it), driven by the snackbar's Undo action. */
    fun undoDelete() {
        val car = recentlyDeleted ?: return
        recentlyDeleted = null
        viewModelScope.launch { saveCarUseCase(car) }
    }
}
