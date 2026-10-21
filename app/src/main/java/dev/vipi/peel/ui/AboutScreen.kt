package dev.vipi.peel.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.vipi.peel.AboutActivity
import dev.vipi.peel.R

private class Library(val name: String, val url: String)

private val LIBRARIES = listOf(
    Library("Jetpack Compose", "https://developer.android.com/jetpack/androidx/releases/compose"),
    Library("Material 3 for Compose", "https://developer.android.com/jetpack/androidx/releases/compose-material3"),
    Library("AndroidX Activity", "https://developer.android.com/jetpack/androidx/releases/activity"),
    Library("AndroidX Lifecycle", "https://developer.android.com/jetpack/androidx/releases/lifecycle"),
    Library("AndroidX ProfileInstaller", "https://developer.android.com/jetpack/androidx/releases/profileinstaller"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(version: String, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var showLicense by rememberSaveable { mutableStateOf(false) }
    if (showLicense) LicenseDialog(onDismiss = { showLicense = false })

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.navigate_up))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                modifier = Modifier.widthIn(max = 640.dp).fillMaxSize(),
            ) {
                item { AppHeader(version) }
                item { Section(R.string.about_section_project) }
                item {
                    LinkGroup {
                        Link(R.drawable.ic_code, stringResource(R.string.about_source), "github.com/VipinVIP/Peel") {
                            onOpen(AboutActivity.REPO_URL)
                        }
                        Link(R.drawable.ic_bug, stringResource(R.string.about_issues), stringResource(R.string.about_issues_body)) {
                            onOpen("${AboutActivity.REPO_URL}/issues")
                        }
                        Link(R.drawable.ic_license, stringResource(R.string.about_license), stringResource(R.string.about_license_body), external = false) {
                            showLicense = true
                        }
                        Link(R.drawable.ic_person, stringResource(R.string.about_author), AboutActivity.AUTHOR) {
                            onOpen(AboutActivity.AUTHOR_URL)
                        }
                    }
                }
                item { Section(R.string.about_section_privacy) }
                item {
                    LinkGroup {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            leadingContent = { LeadingIcon(R.drawable.ic_shield) },
                            headlineContent = { Text(stringResource(R.string.about_privacy_title)) },
                            supportingContent = { Text(stringResource(R.string.about_privacy_body)) },
                        )
                    }
                }
                item { Section(R.string.about_section_libraries) }
                item {
                    LinkGroup {
                        LIBRARIES.forEach { lib ->
                            Link(null, lib.name, stringResource(R.string.about_apache)) { onOpen(lib.url) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppHeader(version: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The launcher icon's own layers, so the header always matches the real icon. Adaptive
        // icon layers are 108 units with only the middle 72 shown, hence the 1.5x zoom.
        Box(Modifier.size(96.dp).clip(CircleShape)) {
            val layer = Modifier.fillMaxSize().graphicsLayer(scaleX = 1.5f, scaleY = 1.5f)
            Image(painterResource(R.drawable.ic_launcher_bg), contentDescription = null, layer)
            Image(painterResource(R.drawable.ic_launcher_fg), contentDescription = null, layer)
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.app_tagline),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.about_version, version),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.about_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun Section(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 24.dp, bottom = 8.dp).semantics { heading() },
    )
}

@Composable
private fun LinkGroup(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.padding(vertical = 8.dp)) { content() }
    }
}

@Composable
private fun LeadingIcon(@DrawableRes icon: Int) {
    androidx.compose.material3.Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, Modifier.size(22.dp))
        }
    }
}

@Composable
private fun Link(
    @DrawableRes icon: Int?,
    title: String,
    subtitle: String,
    external: Boolean = true,
    onClick: () -> Unit,
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        leadingContent = icon?.let { { LeadingIcon(it) } },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = if (external) {
            {
                Icon(
                    painterResource(R.drawable.ic_open_in_new),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            null
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun LicenseDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_license_body)) },
        text = {
            Text(
                MIT_LICENSE,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

// Kept identical to the repository's LICENSE file.
private const val MIT_LICENSE = """Copyright (c) 2026 VipinVIP

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE."""
