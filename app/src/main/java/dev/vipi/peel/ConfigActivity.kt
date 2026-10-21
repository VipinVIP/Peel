package dev.vipi.peel

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.vipi.peel.ui.PeelTheme
import dev.vipi.peel.ui.WidgetSettingsSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Picks a GIF/WebP for one widget. Launched by the launcher when a widget is added or
 * reconfigured, by the pin callback, by tapping an empty widget, and from the app.
 * Transparent until there's something to show (progress or an error).
 */
class ConfigActivity : ComponentActivity() {
    companion object {
        /** Set for freshly pinned widgets: show the home screen afterwards so the new widget is seen. */
        const val EXTRA_GO_HOME = "go_home"
    }

    private val viewModel: ConfigViewModel by viewModels()
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private val pickImage = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        when {
            uri != null -> viewModel.decode(widgetId, uri)
            viewModel.settingsMode -> Unit // back to the settings sheet
            else -> finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setResult(RESULT_CANCELED, resultIntent())
        if (savedInstanceState == null) {
            // A widget that already has a GIF is being reconfigured: show its settings.
            // A brand-new one has nothing to configure yet, so go straight to choosing a GIF.
            if (WidgetStore.info(this, widgetId) != null) viewModel.openSettings(widgetId) else launchPicker()
        }

        setContent {
            PeelTheme {
                val state by viewModel.state.collectAsState()
                val info by viewModel.info.collectAsState()
                when (state) {
                    ConfigViewModel.State.Idle -> Unit
                    ConfigViewModel.State.Settings -> info?.let {
                        WidgetSettingsSheet(
                            widgetId = widgetId,
                            info = it,
                            onChangeGif = ::launchPicker,
                            onLoopsChange = { loops -> viewModel.setLoops(widgetId, loops) },
                            onDone = ::finishOk,
                        )
                    }
                    ConfigViewModel.State.Working -> PreparingDialog()
                    ConfigViewModel.State.Failed, ConfigViewModel.State.TooLarge -> FailedDialog(
                        tooLarge = state == ConfigViewModel.State.TooLarge,
                        onRetry = {
                            viewModel.reset()
                            launchPicker()
                        },
                        onCancel = { if (viewModel.settingsMode) viewModel.backToSettings(widgetId) else finish() },
                    )
                    ConfigViewModel.State.Done -> LaunchedEffect(Unit) {
                        setResult(RESULT_OK, resultIntent())
                        if (intent.getBooleanExtra(EXTRA_GO_HOME, false)) {
                            startActivity(
                                Intent(Intent.ACTION_MAIN)
                                    .addCategory(Intent.CATEGORY_HOME)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                        finish()
                    }
                }
            }
        }
    }

    private fun launchPicker() = pickImage.launch(arrayOf("image/gif", "image/webp"))

    private fun finishOk() {
        setResult(RESULT_OK, resultIntent())
        finish()
    }

    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
}

class ConfigViewModel(app: Application) : AndroidViewModel(app) {
    enum class State { Idle, Settings, Working, Failed, TooLarge, Done }

    private val _state = MutableStateFlow(State.Idle)
    val state = _state.asStateFlow()

    private val _info = MutableStateFlow<WidgetStore.Info?>(null)
    val info = _info.asStateFlow()

    /** Opened from "Widget settings" on an existing widget, rather than to set up a new one. */
    var settingsMode = false
        private set

    fun reset() {
        _state.value = if (settingsMode) State.Settings else State.Idle
    }

    fun openSettings(widgetId: Int) {
        settingsMode = true
        backToSettings(widgetId)
    }

    fun backToSettings(widgetId: Int) {
        _info.value = WidgetStore.info(getApplication(), widgetId)
        _state.value = State.Settings
    }

    fun setLoops(widgetId: Int, loops: Int) {
        val context = getApplication<Application>()
        WidgetStore.setLoops(context, widgetId, loops)
        StickerWidgetProvider.render(context, widgetId)
        _info.value = WidgetStore.info(context, widgetId)
    }

    fun decode(widgetId: Int, uri: Uri) {
        if (_state.value == State.Working) return
        _state.value = State.Working
        viewModelScope.launch {
            val context = getApplication<Application>()
            val outcome = withContext(Dispatchers.Default) {
                runCatching {
                    val result = FrameDecoder.decode(context, uri, WidgetStore.framesDir(context, widgetId))
                    WidgetStore.save(context, widgetId, result)
                    StickerWidgetProvider.render(context, widgetId)
                    Log.i(TAG, "widget $widgetId: ${result.count} frames @ ${result.intervalMs}ms, poster ${result.posterIndex}")
                }.onFailure { Log.e(TAG, "decode failed", it) }
            }
            _state.value = when (outcome.exceptionOrNull()) {
                null -> if (settingsMode) {
                    _info.value = WidgetStore.info(context, widgetId)
                    State.Settings
                } else {
                    State.Done
                }
                is FrameDecoder.TooLargeException, is OutOfMemoryError -> State.TooLarge
                else -> State.Failed
            }
        }
    }

    private companion object {
        const val TAG = "Peel"
    }
}

@Composable
private fun PreparingDialog() {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(min = 280.dp, max = 560.dp),
        ) {
            Row(
                Modifier.padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                CircularProgressIndicator(Modifier.size(40.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.preparing_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.preparing_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun FailedDialog(tooLarge: Boolean, onRetry: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        icon = { Icon(painterResource(R.drawable.ic_error), contentDescription = null) },
        title = { Text(stringResource(R.string.decode_failed_title)) },
        text = {
            Text(
                if (tooLarge) {
                    stringResource(R.string.decode_too_large_body, FrameDecoder.MAX_FILE_MB)
                } else {
                    stringResource(R.string.decode_failed_body)
                },
            )
        },
        confirmButton = { TextButton(onClick = onRetry) { Text(stringResource(R.string.try_again)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}
