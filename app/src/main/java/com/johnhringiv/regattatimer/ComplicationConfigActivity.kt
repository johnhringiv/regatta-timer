package com.johnhringiv.regattatimer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService

/**
 * Shown by the system when the complication is added to a watch face:
 * pick the sequence the complication arms (5 or 3 minutes).
 * Must setResult(RESULT_OK) or the complication is not activated.
 */
class ComplicationConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED) // backing out must not add the complication

        val instanceId = intent.getIntExtra(
            ComplicationDataSourceService.EXTRA_CONFIG_COMPLICATION_ID, -1
        )

        setContent {
            MaterialTheme {
                ConfigScreen { mode ->
                    if (instanceId != -1) {
                        ComplicationModeStore(this).setMode(instanceId, mode)
                    }
                    setResult(RESULT_OK)
                    finish()
                }
            }
        }
    }
}

@Composable
private fun ConfigScreen(onPick: (Mode) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "REGATTA", fontSize = 16.sp, color = Color(0xFFF5C518))
        Text(text = "start sequence", fontSize = 12.sp, color = Color(0xFF9E9E9E))
        Spacer(Modifier.height(12.dp))
        ModeButton("5 min") { onPick(Mode.FIVE) }
        Spacer(Modifier.height(8.dp))
        ModeButton("3 min") { onPick(Mode.THREE) }
    }
}

@Composable
private fun ModeButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.width(120.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF1B3A5C),
            contentColor = Color(0xFFF5F5F5),
        ),
    ) {
        Text(label)
    }
}
