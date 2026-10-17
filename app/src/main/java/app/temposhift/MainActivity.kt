package app.temposhift

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import app.temposhift.player.PlayerState
import app.temposhift.ui.AppTheme
import app.temposhift.ui.BG
import app.temposhift.ui.screens.TempoShiftApp

class MainActivity : ComponentActivity() {
    private val state: PlayerState by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        state.connect()
        setContent {
            AppTheme(state) {
                Surface(modifier = Modifier.fillMaxSize(), color = BG) {
                    TempoShiftApp(state)
                }
            }
        }
        if (savedInstanceState == null) handleIntent(intent)
        window.decorView.postDelayed({ askBatteryExemption() }, 3000)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** "Open with TempoShift" from a file manager or another app. */
    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { state.openUri(it) }
        }
    }

    /** Asks once to be exempt from battery optimisation so music keeps playing with the screen off. */
    private fun askBatteryExemption() {
        if (state.prefs.batteryAsked) return
        state.prefs.batteryAsked = true
        val pm = getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            try {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            } catch (_: Exception) { }
        }
    }
}
