package app.drivedelta.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.drivedelta.domain.model.EnergyPrices
import app.drivedelta.domain.repository.EnergyPricesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the Energy Prices settings screen (design/mockups/settings-energy-prices.png). Streams the
 * user's [EnergyPrices] and persists each edit immediately (settings-style auto-save).
 */
@HiltViewModel
class EnergyPricesViewModel @Inject constructor(
    private val repository: EnergyPricesRepository,
) : ViewModel() {

    val prices: StateFlow<EnergyPrices> = repository.observePrices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EnergyPrices.default(""))

    /** Applies [transform] to the current settings and persists the result. */
    fun update(transform: (EnergyPrices) -> EnergyPrices) {
        val current = prices.value
        viewModelScope.launch { repository.savePrices(transform(current)) }
    }
}
