package ir.exchangerate.app

import android.app.Application
import ir.exchangerate.app.data.DepositsRepository
import ir.exchangerate.app.data.ExchangeRateRepository
import ir.exchangerate.app.data.PreferencesStore

class ExchangeApp : Application() {
    val repository by lazy { ExchangeRateRepository(this) }
    val preferences by lazy { PreferencesStore(this) }
    val deposits by lazy { DepositsRepository(this) }
}
