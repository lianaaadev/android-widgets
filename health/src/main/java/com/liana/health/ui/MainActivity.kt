package com.liana.health.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.health.connect.client.PermissionController
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope
import com.liana.health.HealthApp
import com.liana.health.data.HealthConnectAvailability
import com.liana.health.data.HealthConnectSettings
import com.liana.health.data.HealthPermissionState
import com.liana.health.widget.WeightWidget
import com.liana.widgets.core.design.WidgetTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var state by mutableStateOf(MainState())

    private val repository by lazy { (application as HealthApp).repository }

    /**
     * Health Connect permissions do not go through the normal runtime-permission APIs — they are
     * granted by the provider through its own contract. Registered unconditionally at
     * construction, as Activity Result requires.
     */
    private val requestPermissions = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract() as
            ActivityResultContract<Set<String>, Set<String>>
    ) { granted ->
        if (repository.metric.permission in granted) {
            HealthConnectSettings.clearPrompts(this)
        }
        refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WidgetTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    MainScreen(
                        state = state,
                        onGrant = ::requestPermissions,
                        onOpenSettings = ::openHealthConnectSettings,
                        onUpdateProvider = ::openProviderUpdate,
                        onRefresh = ::refresh,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Permission can be revoked, and the provider updated, entirely outside this app. Both
        // are cheap to re-check and expensive to get wrong, so they are re-read on every resume
        // rather than cached for the life of the activity.
        refresh()
    }

    private fun refresh() {
        state = state.copy(
            availability = HealthConnectAvailability.of(this),
            promptsExhausted = HealthConnectSettings.promptsExhausted(this),
            loading = true,
            error = null,
        )

        if (state.availability != HealthConnectAvailability.Available) {
            state = state.copy(loading = false)
            // Still update: an uninstalled or downgraded provider changes what the widget must
            // say, and that is exactly the case where it would otherwise sit showing a number
            // it can no longer source.
            lifecycleScope.launch { WeightWidget().updateAll(this@MainActivity) }
            return
        }

        lifecycleScope.launch {
            val permission = repository.permissionState().getOrElse { error ->
                state = state.copy(loading = false, error = error.toString())
                return@launch
            }

            if (permission !is HealthPermissionState.Granted) {
                state = state.copy(permission = permission, loading = false, records = emptyList())
                return@launch
            }

            val records = repository.readWindow()
            // refresh() rather than read(): it writes through to the cache the widget renders
            // from, so opening the app is itself a refresh. Until Phase 3 brings the worker,
            // this and a reboot are the only things that update a placed widget.
            val snapshot = repository.refresh()

            state = state.copy(
                permission = permission,
                records = records.getOrDefault(emptyList()),
                snapshot = snapshot.getOrNull(),
                error = (records.exceptionOrNull() ?: snapshot.exceptionOrNull())?.toString(),
                loading = false,
            )

            WeightWidget().updateAll(this@MainActivity)
        }
    }

    private fun requestPermissions() {
        HealthConnectSettings.recordPrompt(this)
        requestPermissions.launch(repository.requiredPermissions)
    }

    private fun openHealthConnectSettings() {
        // Two actions, because the settings screen moved into the system module in Android 14
        // and the older one only exists inside the provider APK. First that resolves wins.
        val opened = HealthConnectSettings.settingsIntents().any { tryStart(it) }
        if (!opened) {
            Toast.makeText(this, "Could not open Health Connect", Toast.LENGTH_LONG).show()
        }
    }

    private fun openProviderUpdate() {
        if (!tryStart(HealthConnectAvailability.providerUpdateIntent())) {
            Toast.makeText(this, "No Play Store on this device", Toast.LENGTH_LONG).show()
        }
    }

    private fun tryStart(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
