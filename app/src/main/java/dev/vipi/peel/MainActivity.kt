package dev.vipi.peel

import android.app.ActivityOptions
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.vipi.peel.ui.PeelTheme
import dev.vipi.peel.ui.HomeScreen
import dev.vipi.peel.ui.WidgetItem

class MainActivity : ComponentActivity() {
    private val widgetManager by lazy { getSystemService(AppWidgetManager::class.java) }
    private val provider by lazy { ComponentName(this, StickerWidgetProvider::class.java) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PeelTheme {
                var widgets by remember { mutableStateOf(loadWidgets()) }
                // Widgets can be added, removed or changed from the launcher while we're away.
                LifecycleResumeEffect(Unit) {
                    widgets = loadWidgets()
                    onPauseOrDispose { }
                }
                HomeScreen(
                    widgets = widgets,
                    canPin = widgetManager.isRequestPinAppWidgetSupported,
                    onAddWidget = ::requestPin,
                    onChooseGif = ::openPicker,
                    onAbout = { startActivity(Intent(this, AboutActivity::class.java)) },
                    onLoopsChange = { id, loops ->
                        WidgetStore.setLoops(this, id, loops)
                        StickerWidgetProvider.render(this, id)
                        widgets = loadWidgets()
                    },
                )
            }
        }
    }

    private fun loadWidgets(): List<WidgetItem> {
        val ids = widgetManager.getAppWidgetIds(provider).sorted()
        WidgetStore.prune(this, ids.toSet())
        return ids.mapIndexed { i, id -> WidgetItem(id = id, number = i + 1, info = WidgetStore.info(this, id)) }
    }

    private fun requestPin() {
        // Once the launcher accepts it, jump straight into choosing a GIF (system fills in the id).
        // The launcher sends this PendingIntent, so on Android 14+ we must explicitly allow it to
        // start our activity; otherwise it's blocked as a background activity launch.
        val options = when {
            Build.VERSION.SDK_INT >= 36 -> ActivityOptions.makeBasic()
                .setPendingIntentCreatorBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE,
                ).toBundle()
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> ActivityOptions.makeBasic()
                .setPendingIntentCreatorBackgroundActivityStartMode(
                    @Suppress("DEPRECATION") ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                ).toBundle()
            else -> null
        }
        val onPinned = PendingIntent.getActivity(
            this,
            0,
            Intent(this, ConfigActivity::class.java)
                .putExtra(ConfigActivity.EXTRA_GO_HOME, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            // CANCEL_CURRENT: options only apply to a newly created PendingIntent, not an updated one.
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_CANCEL_CURRENT,
            options,
        )
        widgetManager.requestPinAppWidget(provider, null, onPinned)
    }

    private fun openPicker(widgetId: Int) {
        startActivity(
            Intent(this, ConfigActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
        )
    }
}
