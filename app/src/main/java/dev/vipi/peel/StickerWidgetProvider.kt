package dev.vipi.peel

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews

/**
 * Idle: a static ImageView showing the poster frame.
 * Playing: a different layout with an auto-starting AdapterViewFlipper over the requested plays
 * followed by a few seconds of the poster frame. AdapterViewFlipper always loops (its constructor
 * forces loopViews = true), so a timer swaps back to the idle layout during that resting tail;
 * both show the poster, so the swap is invisible.
 *
 * Nothing here blocks until playback ends: broadcasts to one app are delivered one at a time, so
 * waiting would make taps on other widgets queue up behind it.
 */
class StickerWidgetProvider : AppWidgetProvider() {
    companion object {
        private const val ACTION_TOGGLE = "dev.vipi.peel.TOGGLE"
        private const val ACTION_STOP = "dev.vipi.peel.STOP"
        private const val EXTRA_TOKEN = "token"

        // The resting tail hides lateness, so stop a little after the end rather than risk early.
        private const val STOP_MARGIN_MS = 1_000L
        // Backup in case the in-process timer never runs (e.g. process killed).
        private const val BACKUP_DELAY_MS = 30_000L
        private val mainHandler = Handler(Looper.getMainLooper())

        fun render(context: Context, widgetId: Int) {
            val info = WidgetStore.info(context, widgetId)
            val views = if (info != null && info.count > 1 && WidgetStore.isPlaying(context, widgetId)) {
                playingViews(context, widgetId, info, WidgetStore.playToken(context, widgetId))
            } else {
                idleViews(context, widgetId, info)
            }
            AppWidgetManager.getInstance(context).updateAppWidget(widgetId, views)
        }

        private fun idleViews(context: Context, widgetId: Int, info: WidgetStore.Info?): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_idle)
            // The launcher may reapply onto the existing view, so set every property both ways.
            views.setViewVisibility(R.id.hint, if (info == null) View.VISIBLE else View.GONE)
            if (info == null) {
                views.setOnClickPendingIntent(R.id.tap, configIntent(context, widgetId))
            } else {
                views.setImageViewUri(R.id.frame, FrameProvider.uri(context, widgetId, info.secret, info.posterIndex))
                views.setOnClickPendingIntent(R.id.tap, broadcast(context, widgetId, ACTION_TOGGLE))
            }
            return views
        }

        private fun playingViews(context: Context, widgetId: Int, info: WidgetStore.Info, token: Int): RemoteViews {
            fun frame(index: Int) = RemoteViews(context.packageName, R.layout.widget_frame).apply {
                setImageViewUri(R.id.frame, FrameProvider.uri(context, widgetId, info.secret, index))
            }
            val items = RemoteViews.RemoteCollectionItems.Builder()
                .setHasStableIds(true)
                .setViewTypeCount(1)
            // Each cycle starts right after the poster and ends on it. "Forever" is one cycle that
            // the flipper repeats; otherwise repeat N cycles, then rest on the poster.
            val played = info.count * if (info.forever) 1 else info.loops
            for (i in 0 until played) items.addItem(i.toLong(), frame((info.posterIndex + 1 + i) % info.count))
            if (!info.forever) {
                for (i in 0 until info.restingFrames) items.addItem((played + i).toLong(), frame(info.posterIndex))
            }
            val views = RemoteViews(context.packageName, PlayingLayouts.layout(info.intervalMs, token))
            views.setRemoteAdapter(R.id.flipper, items.build())
            views.setOnClickPendingIntent(R.id.tap, broadcast(context, widgetId, ACTION_TOGGLE))
            return views
        }

        private fun configIntent(context: Context, widgetId: Int): PendingIntent {
            val intent = Intent(context, ConfigActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return PendingIntent.getActivity(
                context, widgetId, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        private fun broadcast(context: Context, widgetId: Int, action: String, token: Int = 0): PendingIntent {
            val intent = Intent(context, StickerWidgetProvider::class.java)
                .setAction(action)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .putExtra(EXTRA_TOKEN, token)
            return PendingIntent.getBroadcast(
                context, widgetId, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            WidgetStore.stopPlaying(context, id)
            render(context, id)
        }
        WidgetStore.prune(context, allIds(context).toSet())
    }

    private fun allIds(context: Context) =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, StickerWidgetProvider::class.java))

    override fun onDeleted(context: Context, ids: IntArray) {
        ids.forEach { WidgetStore.delete(context, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            // Some launchers keep stale views across an app update; push fresh ones.
            onUpdate(context, AppWidgetManager.getInstance(context), allIds(context))
            return
        }
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        when (intent.action) {
            ACTION_TOGGLE -> toggle(context, widgetId)
            ACTION_STOP -> stopIfCurrent(context, widgetId, intent.getIntExtra(EXTRA_TOKEN, -1))
        }
    }

    private fun toggle(context: Context, widgetId: Int) {
        val info = WidgetStore.info(context, widgetId) ?: return
        if (info.count <= 1) return
        if (WidgetStore.isMidPlayback(context, widgetId, info)) {
            WidgetStore.stopPlaying(context, widgetId)
            render(context, widgetId)
            return
        }
        val token = WidgetStore.startPlaying(context, widgetId)
        render(context, widgetId)

        val duration = info.playDurationMs ?: return // forever: runs until tapped
        val appContext = context.applicationContext
        mainHandler.postDelayed({ stopIfCurrent(appContext, widgetId, token) }, duration + STOP_MARGIN_MS)
        context.getSystemService(AlarmManager::class.java).set(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + duration + BACKUP_DELAY_MS,
            broadcast(context, widgetId, ACTION_STOP, token),
        )
    }

    private fun stopIfCurrent(context: Context, widgetId: Int, token: Int) {
        if (!WidgetStore.isPlaying(context, widgetId) || WidgetStore.playToken(context, widgetId) != token) return
        WidgetStore.stopPlaying(context, widgetId)
        render(context, widgetId)
    }
}
