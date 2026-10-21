package dev.vipi.peel.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.vipi.peel.FrameDecoder
import dev.vipi.peel.R
import dev.vipi.peel.WidgetStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Shows a widget's poster frame on a transparency checkerboard; tapping plays one loop,
 * exactly as the home-screen widget does. Frames are decoded one at a time from disk.
 */
@Composable
fun FramePreview(widgetId: Int, info: WidgetStore.Info, description: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dir = remember(widgetId) { WidgetStore.framesDir(context, widgetId) }
    var playing by remember(info.version, info.loops) { mutableStateOf(false) }
    var frame by remember(info.version) { mutableStateOf<ImageBitmap?>(null) }
    val animated = info.count > 1

    suspend fun load(index: Int) = withContext(Dispatchers.IO) {
        BitmapFactory.decodeFile(FrameDecoder.frameFile(dir, index).path)?.asImageBitmap()
    }

    LaunchedEffect(info.version, info.loops, playing) {
        if (!playing) {
            frame = load(info.posterIndex)
            return@LaunchedEffect
        }
        // Same order and repeat count as the widget: start after the poster, end on it.
        var i = 1
        while (info.forever || i <= info.count * info.loops) {
            val start = System.nanoTime()
            load((info.posterIndex + i) % info.count)?.let { frame = it }
            i++
            val spentMs = (System.nanoTime() - start) / 1_000_000
            delay((info.intervalMs - spentMs).coerceAtLeast(0))
        }
        playing = false
    }

    val label = stringResource(if (playing) R.string.preview_stop else R.string.preview_play)
    Box(
        modifier
            .semantics { contentDescription = description }
            .checkerboard()
            .then(
                if (animated) Modifier.clickable(onClickLabel = label, role = Role.Button) { playing = !playing }
                else Modifier,
            ),
    ) {
        frame?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(12.dp),
            )
        }
        AnimatedVisibility(
            visible = animated && !playing,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shadowElevation = 2.dp,
            ) {
                Icon(
                    painterResource(R.drawable.ic_play),
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp).size(20.dp),
                )
            }
        }
    }
}

/** The classic "this area is transparent" pattern, tinted from the theme. */
@Composable
fun Modifier.checkerboard(cell: Float = 12f): Modifier {
    val light = MaterialTheme.colorScheme.surfaceContainerHigh
    val dark = MaterialTheme.colorScheme.surfaceContainerHighest
    return drawBehind { drawChecker(light, dark, cell * density) }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawChecker(a: Color, b: Color, cell: Float) {
    drawRect(a)
    val cols = (size.width / cell).toInt() + 1
    val rows = (size.height / cell).toInt() + 1
    for (row in 0 until rows) for (col in 0 until cols) {
        if ((row + col) % 2 == 1) drawRect(b, Offset(col * cell, row * cell), Size(cell, cell))
    }
}
