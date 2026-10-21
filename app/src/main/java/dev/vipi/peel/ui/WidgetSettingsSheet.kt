package dev.vipi.peel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.vipi.peel.R
import dev.vipi.peel.WidgetStore

/**
 * Shown over the home screen when the launcher's "Widget settings" / reconfigure is used on a
 * widget that already has a GIF: the same controls as the app's widget card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetSettingsSheet(
    widgetId: Int,
    info: WidgetStore.Info,
    onChangeGif: () -> Unit,
    onLoopsChange: (Int) -> Unit,
    onDone: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDone,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.widget_settings_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            FramePreview(
                widgetId,
                info,
                stringResource(R.string.preview_description_single),
                Modifier.previewSize().clip(MaterialTheme.shapes.large),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    widgetStats(info),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(onClick = onChangeGif, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                    Icon(painterResource(R.drawable.ic_photo_library), contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.change_gif_long))
                }
            }
            if (info.count > 1) LoopCountRow(info = info, onLoopsChange = onLoopsChange)
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.done)) }
        }
    }
}
