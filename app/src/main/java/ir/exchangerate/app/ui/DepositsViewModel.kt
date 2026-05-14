package ir.exchangerate.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ir.exchangerate.app.ExchangeApp
import ir.exchangerate.app.data.DepositsRepository
import ir.exchangerate.app.data.model.Currency
import ir.exchangerate.app.data.model.Deposit
import ir.exchangerate.app.data.model.Person
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class DepositsViewModel(
    private val repository: DepositsRepository,
) : ViewModel() {

    val deposits: StateFlow<List<Deposit>> = repository.deposits.stateIn(
        viewModelScope, SharingStarted.Eagerly, emptyList(),
    )

    fun add(person: Person, currency: Currency, amount: Double) {
        if (amount <= 0.0) return
        viewModelScope.launch {
            repository.add(
                Deposit(
                    id = UUID.randomUUID().toString(),
                    personKey = person.name,
                    currency = currency,
                    amount = amount,
                    timestampMillis = System.currentTimeMillis(),
                )
            )
        }
    }

    fun remove(id: String) {
        viewModelScope.launch { repository.remove(id) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ExchangeApp
                DepositsViewModel(app.deposits)
            }
        }
    }
}
