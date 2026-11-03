package com.puroaudio.player

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import com.puroaudio.player.playback.BitPerfectEngine
import com.puroaudio.player.ui.theme.PuroBackground
import com.puroaudio.player.ui.theme.PuroTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            PuroTheme {
                Surface(color = PuroBackground) {
                    PuroApp()
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
            keyCode == KeyEvent.KEYCODE_VOLUME_MUTE
        ) {
            val bpState = BitPerfectEngine.state.value
            if (bpState.enabled && bpState.dacConnected && bpState.bitPerfectActive) {
                // Consumir el evento para mantener el volumen del stream intacto a 0 dB hacia el DAC
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
