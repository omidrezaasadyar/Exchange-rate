package ir.exchangerate.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ir.exchangerate.app.ui.screens.RatesScreen
import ir.exchangerate.app.ui.screens.SettingsScreen
import ir.exchangerate.app.ui.theme.ExchangeRateTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ExchangeRateTheme {
                var screen by remember { mutableStateOf(Screen.Rates) }
                when (screen) {
                    Screen.Rates -> RatesScreen(onOpenSettings = { screen = Screen.Settings })
                    Screen.Settings -> SettingsScreen(onBack = { screen = Screen.Rates })
                }
            }
        }
    }

    private enum class Screen { Rates, Settings }
}
