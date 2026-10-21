package dev.vipi.peel.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.vipi.peel.FrameDecoder
import dev.vipi.peel.R
import dev.vipi.peel.WidgetStore
import kotlinx.coroutines.launch
import java.util.Locale

class WidgetItem(val id: Int, val number: Int, val info: WidgetStore.Info?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    widgets: List<WidgetItem>,
    canPin: Boolean,
    onAddWidget: () -> Unit,
    onChooseGif: (widgetId: Int) -> Unit,
    onLoopsChange: (widgetId: Int, loops: Int) -> Unit,
    onAbout: () -> Unit,
) {
    // Landscape phones have little height to spare: use the small app bar there.
    val compactHeight = LocalConfiguration.current.screenHeightDp < 480
    val scrollBehavior = if (compactHeight) {
        TopAppBarDefaults.pinnedScrollBehavior()
    } else {
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val pinUnsupported = stringResource(R.string.pin_unsupported)
    val addWidget = {
        if (canPin) onAddWidget() else scope.launch { snackbar.showSnackbar(pinUnsupported) }
        Unit
    }

    // Help lives inline while there's nothing else to show; after that it moves behind an
    // info button so the main screen is just the widgets.
    var showHelp by rememberSaveable { mutableStateOf(false) }
    if (showHelp) HowItWorksSheet(onDismiss = { showHelp = false })

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            val title = @Composable { Text(stringResource(R.string.home_title)) }
            val actions: @Composable RowScope.() -> Unit = {
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.more_options))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.about)) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_info), contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onAbout()
                            },
                        )
                    }
                }
            }
            if (compactHeight) {
                TopAppBar(title = title, actions = actions, scrollBehavior = scrollBehavior)
            } else {
                MediumTopAppBar(title = title, actions = actions, scrollBehavior = scrollBehavior)
            }
        },
        floatingActionButton = {
            if (widgets.isNotEmpty()) {
                val addLabel = stringResource(R.string.add_widget)
                ExtendedFloatingActionButton(
                    onClick = addWidget,
                    // Keeps a spoken name when it collapses to just the icon.
                    modifier = Modifier.semantics { contentDescription = addLabel },
                    icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                    text = { Text(stringResource(R.string.add_widget)) },
                    expanded = compactHeight || scrollBehavior.state.collapsedFraction < 0.5f,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                // Content scrolls under the transparent navigation bar; padding keeps it reachable.
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                // Readable width on tablets/foldables/landscape.
                modifier = Modifier.widthIn(max = 640.dp).fillMaxSize(),
            ) {
                if (widgets.isEmpty()) {
                    item { EmptyState(onAddWidget = addWidget) }
                    item { SectionHeader(R.string.start_title, Modifier.padding(top = 8.dp)) }
                    item { HowItWorks() }
                } else {
                    item {
                        // Help sits on the heading line, next to what it explains.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SectionHeader(R.string.your_widgets, Modifier.weight(1f))
                            TextButton(
                                onClick = { showHelp = true },
                                contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_info),
                                    contentDescription = null,
                                    modifier = Modifier.size(ButtonDefaults.IconSize),
                                )
                                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                Text(stringResource(R.string.start_title))
                            }
                        }
                    }
                    items(widgets, key = { it.id }) { widget ->
                        WidgetCard(
                            widget,
                            onChooseGif = { onChooseGif(widget.id) },
                            onLoopsChange = { onLoopsChange(widget.id, it) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(@StringRes text: Int, modifier: Modifier = Modifier) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 4.dp).semantics { heading() },
    )
}

@Composable
private fun EmptyState(onAddWidget: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(96.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_widgets), contentDescription = null, Modifier.size(44.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.empty_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onAddWidget, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.add_widget))
            }
        }
    }
}

@Composable
private fun WidgetCard(widget: WidgetItem, onChooseGif: () -> Unit, onLoopsChange: (Int) -> Unit) {
    val info = widget.info
    ElevatedCard(shape = MaterialTheme.shapes.extraLarge) {
        val previewModifier = Modifier
            .padding(8.dp)
            .previewSize()
            .clip(MaterialTheme.shapes.large)
        if (info != null) {
            FramePreview(
                widget.id,
                info,
                stringResource(R.string.preview_description, widget.number),
                previewModifier,
            )
        } else {
            Box(previewModifier.checkerboard(), contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(R.drawable.ic_photo_library),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.widget_label, widget.number),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    widgetStats(info),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (info == null) {
                Button(onClick = onChooseGif) { Text(stringResource(R.string.choose_gif)) }
            } else {
                FilledTonalButton(onClick = onChooseGif, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                    Icon(painterResource(R.drawable.ic_edit), contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.change_gif))
                }
            }
        }
        if (info != null && info.count > 1) {
            LoopCountRow(
                info = info,
                onLoopsChange = onLoopsChange,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            )
        }
    }
}

@Composable
internal fun widgetStats(info: WidgetStore.Info?): String = when {
    info == null -> stringResource(R.string.widget_empty)
    info.count <= 1 -> stringResource(R.string.widget_still)
    else -> stringResource(
        R.string.widget_stats,
        info.count,
        String.format(Locale.getDefault(), "%.1f", info.count * info.intervalMs / 1000f),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LoopCountRow(info: WidgetStore.Info, onLoopsChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val forever = info.forever
    val lastCount = if (forever) info.finiteLoops else info.loops
    val maxLoops = info.maxLoops
    val setCount = onLoopsChange
    val countDescription = if (forever) {
        stringResource(R.string.loops_forever_description)
    } else {
        pluralStringResource(R.plurals.loops_description, lastCount, lastCount)
    }
    var editing by remember { mutableStateOf(false) }
    if (editing) {
        LoopCountDialog(
            initial = lastCount,
            max = maxLoops,
            onConfirm = {
                editing = false
                setCount(it)
            },
            onDismiss = { editing = false },
        )
    }

    // Label above the controls, and the controls wrap, so large font sizes never squeeze it.
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.plays_per_tap),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.SpaceBetween,
            itemVerticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(
            onClick = { setCount(lastCount - 1) },
            enabled = !forever && lastCount > 1,
        ) {
            Icon(painterResource(R.drawable.ic_remove), contentDescription = stringResource(R.string.loops_fewer))
        }
        // Tap the number to type any count.
        val typeLabel = stringResource(R.string.loops_type)
        TextButton(
            onClick = { editing = true },
            contentPadding = PaddingValues(horizontal = 8.dp),
            modifier = Modifier
                .widthIn(min = 56.dp)
                .semantics { contentDescription = "$countDescription. $typeLabel" },
        ) {
            Text(
                if (forever) "∞" else "$lastCount×",
                style = MaterialTheme.typography.titleMedium,
                color = if (forever) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            )
        }
        FilledTonalIconButton(
            onClick = { setCount(lastCount + 1) },
            enabled = !forever && lastCount < maxLoops,
        ) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.loops_more))
        }
        }
        FilterChip(
            selected = forever,
            onClick = { if (forever) setCount(lastCount) else onLoopsChange(WidgetStore.FOREVER) },
            label = { Text(stringResource(R.string.loops_forever)) },
            leadingIcon = {
                Icon(
                    painterResource(R.drawable.ic_all_inclusive),
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            },
        )
        }
    }
}

@Composable
private fun LoopCountDialog(initial: Int, max: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(TextFieldValue(initial.toString(), TextRange(0, initial.toString().length))) }
    val value = text.text.toIntOrNull()
    val valid = value != null && value in 1..max
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.plays_per_tap)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { new -> if (new.text.length <= 3 && new.text.all(Char::isDigit)) text = new },
                singleLine = true,
                isError = text.text.isNotEmpty() && !valid,
                suffix = { Text("×") },
                supportingText = { Text(stringResource(R.string.loops_range, max)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (valid) onConfirm(value!!) }),
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value!!) }, enabled = valid) { Text(stringResource(R.string.loops_set)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun HowItWorks() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.padding(vertical = 8.dp)) { HowItems(MaterialTheme.colorScheme.surfaceContainerLow) }
    }
}

@Composable
private fun HowItems(containerColor: Color) {
    HowItem(R.drawable.ic_add, R.string.start_add_title, stringResource(R.string.start_add_body), containerColor)
    HowItem(
        R.drawable.ic_photo_library,
        R.string.start_pick_title,
        stringResource(R.string.start_pick_body, FrameDecoder.MAX_FILE_MB),
        containerColor,
    )
    HowItem(R.drawable.ic_touch_app, R.string.start_tap_title, stringResource(R.string.start_tap_body), containerColor)
    HowItem(R.drawable.ic_edit, R.string.start_change_title, stringResource(R.string.start_change_body), containerColor)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HowItWorksSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Text(
            stringResource(R.string.start_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 24.dp).semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        HowItems(Color.Transparent)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HowItem(@DrawableRes icon: Int, @StringRes title: Int, body: String, containerColor: Color) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = containerColor),
        leadingContent = {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painterResource(icon), contentDescription = null, Modifier.size(22.dp))
                }
            }
        },
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(body) },
    )
}

/** Full width at 16:10, but never taller than 280dp (keeps landscape usable). */
internal fun Modifier.previewSize() = layout { measurable, constraints ->
    val width = constraints.maxWidth
    val height = minOf(width * 10 / 16, 280.dp.roundToPx())
    val placeable = measurable.measure(Constraints.fixed(width, height))
    layout(width, height) { placeable.place(0, 0) }
}
