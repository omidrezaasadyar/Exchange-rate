package ir.exchangerate.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import ir.exchangerate.app.data.ThemeMode
import ir.exchangerate.app.ui.screens.DepositsScreen
import ir.exchangerate.app.ui.screens.RatesScreen
import ir.exchangerate.app.ui.screens.SettingsScreen
import ir.exchangerate.app.ui.theme.ExchangeRateTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val app = LocalContext.current.applicationContext as ExchangeApp
            val themeMode by app.preferences.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val systemDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            ExchangeRateTheme(darkTheme = isDark) {
                var screen by remember { mutableStateOf(Screen.Rates) }
                when (screen) {
                    Screen.Rates -> RatesScreen(
                        onOpenSettings = { screen = Screen.Settings },
                        onOpenDeposits = { screen = Screen.Deposits },
                    )
                    Screen.Settings -> SettingsScreen(onBack = { screen = Screen.Rates })
                    Screen.Deposits -> DepositsScreen(onBack = { screen = Screen.Rates })
                }
            }
        }
    }

    private enum class Screen { Rates, Settings, Deposits }
}
