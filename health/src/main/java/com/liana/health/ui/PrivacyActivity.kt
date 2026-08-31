package com.liana.health.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.liana.widgets.core.design.TextPrimary
import com.liana.widgets.core.design.TextSecondary
import com.liana.widgets.core.design.WidgetTheme

/**
 * The destination for both permission-rationale routes: the `ACTION_SHOW_PERMISSIONS_RATIONALE`
 * filter on [MainActivity] for Android 13 and below, and the `VIEW_PERMISSION_USAGE`
 * activity-alias for 14+. Health Connect refuses to grant permissions to an app that declares
 * neither, so this screen is a precondition for the read path rather than a nicety.
 */
class PrivacyActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WidgetTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    PrivacyScreen()
                }
            }
        }
    }
}

@Composable
private fun PrivacyScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 62.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = "What this app does with your data",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
        )
        Text(
            text = "It reads your weight from Health Connect and shows it on your home screen. " +
                "That is the whole of it.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        Text(
            text = "The most recent reading is kept on this device so the widget has something " +
                "to draw when a background read fails or you revoke permission. Nothing is sent " +
                "anywhere: the app holds no INTERNET permission, so it cannot open a network " +
                "connection at all — not to us, not to anyone.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        Text(
            text = "It asks to read weight, and to do so in the background. It does not ask for " +
                "your weight history beyond the last 30 days, and it does not ask for anything " +
                "else Health Connect holds — not steps, sleep, heart rate or cycle tracking.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        Text(
            text = "You can withdraw either permission at any time in Health Connect. The " +
                "widget will keep showing the last number it saw, greyed out and dated, rather " +
                "than going blank.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
    }
}
