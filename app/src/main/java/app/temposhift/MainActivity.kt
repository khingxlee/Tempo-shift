package app.temposhift

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    private lateinit var state: PlayerState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        state = PlayerState(applicationContext)
        state.connect()
        setContent {
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = BG) {
                    TempoShiftApp(state)
                }
            }
        }
    }

    override fun onDestroy() {
        state.disconnect()
        super.onDestroy()
    }
}
