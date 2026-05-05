package ir.exchangerate.app

import android.app.Application
import ir.exchangerate.app.data.ExchangeRateRepository
import ir.exchangerate.app.data.PreferencesStore

class ExchangeApp : Application() {
    val repository by lazy { ExchangeRateRepository() }
    val preferences by lazy { PreferencesStore(this) }
}
