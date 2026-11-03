package com.puroaudio.player

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import com.puroaudio.player.playback.BitPerfectEngine
import com.puroaudio.player.ui.theme.PuroBackground
import com.puroaudio.player.ui.theme.PuroTheme

class MainActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()

    // Variables de estado para detección de gestos
    private var volumeKeyDownTime = 0L
    private var volumeLastTapTime = 0L
    private var volumeTapCount = 0
    private var longPressHandled = false
    private val LONG_PRESS_THRESHOLD = 600L  // ms para considerar pulsación larga
    private val DOUBLE_TAP_WINDOW = 400L     // ms ventana para doble tap

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            PuroTheme {
                Surface(color = PuroBackground) {
                    PuroApp(playerVm = viewModel)
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val bpState = BitPerfectEngine.state.value
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
             keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) &&
            bpState.enabled && bpState.dacConnected) {

            val now = System.currentTimeMillis()

            if (event?.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                event.startTracking()
                longPressHandled = false
                volumeKeyDownTime = now
                // Detectar doble tap
                if (now - volumeLastTapTime < DOUBLE_TAP_WINDOW) {
                    volumeTapCount++
                } else {
                    volumeTapCount = 1
                }
                volumeLastTapTime = now
            }

            if (event?.isLongPress == true) {
                // Pulsación larga → cambiar pista
                if (!longPressHandled) {
                    longPressHandled = true
                    if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                        viewModel.skipToNext()
                    } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                        viewModel.skipToPrevious()
                    }
                }
                volumeTapCount = 0
                return true
            }

            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean {
        val bpState = BitPerfectEngine.state.value
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) &&
            bpState.enabled && bpState.dacConnected) {
            if (!longPressHandled) {
                longPressHandled = true
                if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                    viewModel.skipToNext()
                } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                    viewModel.skipToPrevious()
                }
            }
            volumeTapCount = 0
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        val bpState = BitPerfectEngine.state.value
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) &&
            bpState.enabled && bpState.dacConnected) {

            val pressDuration = System.currentTimeMillis() - volumeKeyDownTime

            // Si fue tap corto y hubo doble tap → pausar/reanudar
            if (!longPressHandled && pressDuration < LONG_PRESS_THRESHOLD && volumeTapCount >= 2) {
                viewModel.togglePlayPause()
                volumeTapCount = 0
            }
            return true
        }
        return super.onKeyUp(keyCode, event)
    }
}
