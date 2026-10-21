package dev.vipi.peel

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.FileNotFoundException

/**
 * Read-only access to decoded frames for the launcher process.
 * URI shape: content://<authority>/<widgetId>/<secret>/<frameIndex>
 *
 * The provider has to be exported (the widget host is another app), so the URIs carry a random
 * per-widget secret: other apps can't guess or enumerate them and can't read the user's images.
 * The secret is replaced whenever the GIF changes, which also busts the launcher's image cache.
 */
class FrameProvider : ContentProvider() {
    companion object {
        fun uri(context: Context, widgetId: Int, secret: String, index: Int): Uri =
            Uri.Builder()
                .scheme("content")
                .authority("${context.packageName}.frames")
                .appendPath(widgetId.toString())
                .appendPath(secret)
                .appendPath(index.toString())
                .build()
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val segments = uri.pathSegments
        val widgetId = segments.getOrNull(0)?.toIntOrNull()
        val secret = segments.getOrNull(1)
        val index = segments.getOrNull(2)?.toIntOrNull()
        val context = context!!
        if (segments.size != 3 || widgetId == null || secret == null || index == null || index < 0 || mode != "r" ||
            !WidgetStore.secretMatches(context, widgetId, secret)
        ) {
            throw FileNotFoundException() // same answer for every rejection; reveals nothing
        }
        val file = FrameDecoder.frameFile(WidgetStore.framesDir(context, widgetId), index)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri) = "image/png"
    override fun onCreate() = true
    override fun query(uri: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<String>?) = 0
}
