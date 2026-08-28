package com.liana.countdown.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope
import com.liana.countdown.CountdownApp
import com.liana.countdown.data.OccasionRepository
import com.liana.countdown.ui.theme.CountdownTheme
import com.liana.countdown.widget.CountdownWidget
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = (application as CountdownApp).repository
        val deepLinked = intent.getLongExtra(EXTRA_OCCASION_ID, 0L).takeIf { it > 0L }

        setContent {
            CountdownTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    CountdownRoot(repository, deepLinked)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The day may have rolled over while the app sat in the background, and the alarm that
        // would have caught it can be deferred by Doze. Cheap insurance against a stale number.
        lifecycleScope.launch { CountdownWidget().updateAll(this@MainActivity) }
    }

    companion object {
        const val EXTRA_OCCASION_ID = "com.liana.countdown.OCCASION_ID"
    }
}

private const val ScreenList = Long.MIN_VALUE
private const val ScreenNew = 0L

@Composable
private fun CountdownRoot(repository: OccasionRepository, deepLinkedId: Long?) {
    var screen by rememberSaveable { mutableLongStateOf(deepLinkedId ?: ScreenList) }

    BackHandler(enabled = screen != ScreenList) { screen = ScreenList }

    when (screen) {
        ScreenList -> OccasionListScreen(
            repository = repository,
            onAdd = { screen = ScreenNew },
            onEdit = { screen = it },
        )

        else -> OccasionEditScreen(
            repository = repository,
            occasionId = screen.takeIf { it != ScreenNew },
            onSaved = { screen = ScreenList },
            onCancel = { screen = ScreenList },
        )
    }
}
