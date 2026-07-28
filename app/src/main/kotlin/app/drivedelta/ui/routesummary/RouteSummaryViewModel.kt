package app.drivedelta.ui.routesummary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.drivedelta.domain.model.RouteSummary
import app.drivedelta.domain.repository.EnergyPricesRepository
import app.drivedelta.domain.usecase.segment.RouteSummaryUseCase
import app.drivedelta.ui.navigation.NavArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RouteSummaryUiState(
    val loading: Boolean = true,
    val summary: RouteSummary? = null,
    val currencyCode: String = "EUR",
)

/**
 * Backs the Route Summary screen: loads the route-level analytics for the drive named by the
 * [NavArgs.TRIP_ID] nav arg.
 */
@HiltViewModel
class RouteSummaryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    routeSummary: RouteSummaryUseCase,
    private val energyPricesRepository: EnergyPricesRepository,
) : ViewModel() {

    private val tripId: String = checkNotNull(savedStateHandle[NavArgs.TRIP_ID])

    private val _uiState = MutableStateFlow(RouteSummaryUiState())
    val uiState: StateFlow<RouteSummaryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val summary = routeSummary(tripId)
            val currencyCode = energyPricesRepository.getPrices().currencyCode
            _uiState.update { it.copy(loading = false, summary = summary, currencyCode = currencyCode) }
        }
    }
}
