package com.liana.health.ui

import android.content.ActivityNotFoundException
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.liana.health.data.HealthConnectAvailability
import com.liana.widgets.core.design.WidgetTheme

class MainActivity : ComponentActivity() {

    /**
     * Recomputed in [onResume] rather than held for the life of the activity: the user can leave
     * to the Play Store, update the provider and come straight back, and a value read once in
     * onCreate would still be reporting the old answer when they did.
     */
    private var availability by mutableStateOf<HealthConnectAvailability?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WidgetTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AvailabilityScreen(
                        availability = availability,
                        onUpdateProvider = ::openProviderUpdate,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        availability = HealthConnectAvailability.of(this)
    }

    private fun openProviderUpdate() {
        try {
            startActivity(HealthConnectAvailability.providerUpdateIntent())
        } catch (e: ActivityNotFoundException) {
            // A device with Health Connect but no Play Store is unusual but not impossible
            // (sideloaded, or an enterprise image). Failing quietly here would look like a dead
            // button, which is worse than admitting we cannot get there.
            Toast.makeText(this, "No Play Store on this device", Toast.LENGTH_LONG).show()
        }
    }
}
