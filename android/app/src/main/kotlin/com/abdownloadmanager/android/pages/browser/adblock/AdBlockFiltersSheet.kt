package com.abdownloadmanager.android.pages.browser.adblock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.abdownloadmanager.shared.ui.widget.ActionButton
import com.abdownloadmanager.shared.ui.widget.CheckBox
import com.abdownloadmanager.shared.ui.widget.MyTextField
import com.abdownloadmanager.shared.ui.widget.RadioButton
import com.abdownloadmanager.shared.ui.widget.Switch
import com.abdownloadmanager.shared.ui.widget.Text
import com.abdownloadmanager.shared.ui.widget.TransparentIconActionButton
import com.abdownloadmanager.shared.util.div
import com.abdownloadmanager.shared.util.ui.icon.MyIcons
import com.abdownloadmanager.shared.util.ui.myColors
import com.abdownloadmanager.shared.util.ui.theme.myTextSizes
import com.abdownloadmanager.shared.util.ui.widget.MyIcon
import ir.amirab.util.compose.asStringSource

private enum class FilterSubDialog {
    NONE,
    OVERFLOW_MENU,
    ADD_SOURCE,
    MANUAL_RULES,
    FILTER_SETTINGS,
    WEBSITE_WHITELIST,
    HOSTS_WHITELIST,
    DISABLED_FILTERS,
    HIDE_ELEMENTS,
    TUTORIAL,
}

@Composable
fun AdBlockFiltersManagerDialog(
    visible: Boolean,
    engine: ZeninAdBlockEngine,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    val settings by engine.settings.collectAsState()
    val sources by engine.filterSources.collectAsState()
    val totalRules by engine.totalActiveRules.collectAsState()
    val isUpdating by engine.isUpdating.collectAsState()
    val statusMsg by engine.statusMessage.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var activeSubDialog by remember { mutableStateOf(FilterSubDialog.NONE) }

    val filteredSources = remember(sources, searchQuery) {
        if (searchQuery.isBlank()) sources
        else sources.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                it.url.contains(searchQuery, ignoreCase = true) ||
                it.category.contains(searchQuery, ignoreCase = true)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0C14))
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF121626))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TransparentIconActionButton(
                        icon = MyIcons.back,
                        contentDescription = "Back".asStringSource(),
                        onClick = onDismiss,
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Hosts & filters",
                            fontSize = myTextSizes.lg,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Text(
                            text = "AdGuard + uBlock + NextDNS Engine",
                            fontSize = myTextSizes.xs,
                            color = Color(0xFF00E5A3),
                        )
                    }
                    TransparentIconActionButton(
                        icon = MyIcons.search,
                        contentDescription = "Search".asStringSource(),
                        onClick = { isSearching = !isSearching },
                    )
                    TransparentIconActionButton(
                        icon = MyIcons.question,
                        contentDescription = "Tutorial".asStringSource(),
                        onClick = { activeSubDialog = FilterSubDialog.TUTORIAL },
                    )
                    TransparentIconActionButton(
                        icon = MyIcons.menu,
                        contentDescription = "Options".asStringSource(),
                        onClick = { activeSubDialog = FilterSubDialog.OVERFLOW_MENU },
                    )
                }

                if (isSearching) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF121626))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        MyTextField(
                            text = searchQuery,
                            onTextChange = { searchQuery = it },
                            placeholder = "Search filters by name or URL...",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                // Active Filters Counter & Protection Profile Strip
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F1322))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Total active filters: $totalRules",
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFD5DEFF),
                            fontSize = myTextSizes.base,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Blocked: ${settings.totalBlockedRequests}",
                                fontSize = myTextSizes.xs,
                                color = Color(0xFF00E5A3),
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.width(10.dp))
                            Switch(
                                checked = settings.globalEnabled,
                                onCheckedChange = { engine.setGlobalEnabled(it) },
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // AdGuard Profile Selector Pills (Pro / Pro++ / Ultimate)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AdGuardProfile.entries.forEach { profile ->
                            val selected = settings.activeProfile == profile
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (selected) Brush.horizontalGradient(
                                            listOf(Color(0xFF4D7CFE), Color(0xFF9B51E0))
                                        ) else Brush.horizontalGradient(
                                            listOf(Color(0xFF1B2036), Color(0xFF1B2036))
                                        )
                                    )
                                    .border(
                                        1.dp,
                                        if (selected) Color(0xFF7B9CFF) else Color(0xFF2B3252),
                                        RoundedCornerShape(10.dp),
                                    )
                                    .clickable { engine.applyProfile(profile) }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = profile.badge,
                                    fontSize = myTextSizes.xs,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) Color.White else Color(0xFF9BA8CC),
                                    maxLines = 1,
                                )
                            }
                        }
                    }

                    statusMsg?.let { msg ->
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (isUpdating) "⟳ $msg" else msg,
                            fontSize = myTextSizes.xs,
                            color = Color(0xFF70A1FF),
                        )
                    }
                }

                // Filter Sources List (Matching screenshot 18649.png)
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(filteredSources, key = { it.id }) { item ->
                        FilterSourceCard(
                            item = item,
                            onToggle = { engine.toggleFilterSource(item.id, it) },
                            onClick = {
                                if (item.id == "manual_filters") {
                                    activeSubDialog = FilterSubDialog.MANUAL_RULES
                                } else {
                                    engine.updateFilters(force = true, targetId = item.id)
                                }
                            },
                            onDelete = if (!item.builtIn) {
                                { engine.removeFilterSource(item.id) }
                            } else null,
                        )
                    }
                }
            }

            // Floating Action Button (Save / Force Sync) matching screenshot bottom-right teal circle
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF00E5A3), Color(0xFF4D7CFE))
                        )
                    )
                    .clickable {
                        engine.updateFilters(force = false)
                    },
                contentAlignment = Alignment.Center,
            ) {
                MyIcon(
                    icon = MyIcons.refresh,
                    contentDescription = "Update Filters",
                    modifier = Modifier.size(26.dp),
                    tint = Color(0xFF081018),
                )
            }

            // Overflow Menu & Sub-Dialogs
            when (activeSubDialog) {
                FilterSubDialog.OVERFLOW_MENU -> {
                    OverflowMenuPopup(
                        onDismiss = { activeSubDialog = FilterSubDialog.NONE },
                        onSelect = { chosen ->
                            when (chosen) {
                                "add_source" -> activeSubDialog = FilterSubDialog.ADD_SOURCE
                                "filter_settings" -> activeSubDialog = FilterSubDialog.FILTER_SETTINGS
                                "update_filters" -> {
                                    activeSubDialog = FilterSubDialog.NONE
                                    engine.updateFilters(force = false)
                                }
                                "force_update" -> {
                                    activeSubDialog = FilterSubDialog.NONE
                                    engine.updateFilters(force = true)
                                }
                                "website_whitelist" -> activeSubDialog = FilterSubDialog.WEBSITE_WHITELIST
                                "hosts_whitelist" -> activeSubDialog = FilterSubDialog.HOSTS_WHITELIST
                                "disabled_filters" -> activeSubDialog = FilterSubDialog.DISABLED_FILTERS
                                "hide_elements" -> activeSubDialog = FilterSubDialog.HIDE_ELEMENTS
                                "delete_cache" -> {
                                    activeSubDialog = FilterSubDialog.NONE
                                    engine.deleteFilterCache()
                                }
                                "reset_default" -> {
                                    activeSubDialog = FilterSubDialog.NONE
                                    engine.resetToDefaults()
                                }
                                "tutorial" -> activeSubDialog = FilterSubDialog.TUTORIAL
                            }
                        }
                    )
                }

                FilterSubDialog.ADD_SOURCE -> {
                    AddFilterSourceDialog(
                        activeListCount = sources.count { it.enabled },
                        totalRulesCount = totalRules,
                        onDismiss = { activeSubDialog = FilterSubDialog.NONE },
                        onAdd = { name, url, isLocal, enable, download, trusted ->
                            engine.addFilterSource(name, url, isLocal, enable, download, trusted)
                            activeSubDialog = FilterSubDialog.NONE
                        }
                    )
                }

                FilterSubDialog.MANUAL_RULES -> {
                    ManualRulesEditorDialog(
                        initialRules = settings.customRules,
                        onDismiss = { activeSubDialog = FilterSubDialog.NONE },
                        onSave = { rules ->
                            engine.saveCustomRules(rules)
                            activeSubDialog = FilterSubDialog.NONE
                        }
                    )
                }

                FilterSubDialog.FILTER_SETTINGS -> {
                    FilterSettingsSubDialog(
                        settings = settings,
                        onDismiss = { activeSubDialog = FilterSubDialog.NONE },
                        onSave = { auto, hours, wifi, strip, cosmetic, scriptlets ->
                            engine.updateFilterSettings(auto, hours, wifi, strip, cosmetic, scriptlets)
                            activeSubDialog = FilterSubDialog.NONE
                        }
                    )
                }

                FilterSubDialog.WEBSITE_WHITELIST -> {
                    StringSetManagerDialog(
                        title = "Manage website whitelist",
                        subtitle = "AdBlocker is completely disabled when visiting these websites:",
                        placeholder = "example.com",
                        items = settings.websiteWhitelist.toList(),
                        onAdd = { engine.addWebsiteToWhitelist(it) },
                        onRemove = { engine.removeWebsiteFromWhitelist(it) },
                        onDismiss = { activeSubDialog = FilterSubDialog.NONE },
                    )
                }

                FilterSubDialog.HOSTS_WHITELIST -> {
                    StringSetManagerDialog(
                        title = "Manage hosts whitelist",
                        subtitle = "Requests to these domains will never be blocked on any site:",
                        placeholder = "cdn.example.net",
                        items = settings.hostsWhitelist.toList(),
                        onAdd = { engine.addHostToWhitelist(it) },
                        onRemove = { engine.removeHostFromWhitelist(it) },
                        onDismiss = { activeSubDialog = FilterSubDialog.NONE },
                    )
                }

                FilterSubDialog.DISABLED_FILTERS -> {
                    val disabledSources = sources.filter { !it.enabled }
                    DisabledFiltersDialog(
                        disabledSources = disabledSources,
                        onEnableSource = { engine.toggleFilterSource(it, true) },
                        onDismiss = { activeSubDialog = FilterSubDialog.NONE },
                    )
                }

                FilterSubDialog.HIDE_ELEMENTS -> {
                    HideElementsManagerDialog(
                        globalSelectors = settings.globalHiddenSelectors,
                        perHostSelectors = settings.hiddenElementsByHost,
                        onAdd = { host, sel -> engine.addHiddenElementSelector(host, sel) },
                        onRemove = { host, sel -> engine.removeHiddenElementSelector(host, sel) },
                        onDismiss = { activeSubDialog = FilterSubDialog.NONE },
                    )
                }

                FilterSubDialog.TUTORIAL -> {
                    AdBlockTutorialDialog(
                        onDismiss = { activeSubDialog = FilterSubDialog.NONE }
                    )
                }

                FilterSubDialog.NONE -> {}
            }
        }
    }
}

@Composable
private fun FilterSourceCard(
    item: FilterSourceItem,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xFF171C2E))
            .border(1.dp, Color(0xFF262E4A), shape)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color(0xFF222A45)),
            contentAlignment = Alignment.Center,
        ) {
            MyIcon(
                icon = if (item.id == "manual_filters") MyIcons.edit else MyIcons.earth,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (item.enabled) Color(0xFF00E5A3) else Color(0xFF7A86A8),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = myTextSizes.base,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (item.trusted) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF00E5A3).copy(alpha = 0.16f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "TRUSTED",
                            fontSize = myTextSizes.xs,
                            color = Color(0xFF00E5A3),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Filters: ${item.rulesCount}    Size: ${item.formattedSize()}",
                fontSize = myTextSizes.sm,
                color = Color(0xFFABB6D6),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = item.formattedDate(),
                fontSize = myTextSizes.xs,
                color = Color(0xFF7B86A6),
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = item.enabled,
            onCheckedChange = onToggle,
        )
        if (onDelete != null) {
            Spacer(Modifier.width(4.dp))
            TransparentIconActionButton(
                icon = MyIcons.remove,
                contentDescription = "Delete".asStringSource(),
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun OverflowMenuPopup(
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val menuItems = listOf(
        "add_source" to "Add filter source",
        "filter_settings" to "Filter settings",
        "update_filters" to "Update filters",
        "force_update" to "Force update filters",
        "website_whitelist" to "Manage website whitelist",
        "hosts_whitelist" to "Manage hosts whitelist",
        "disabled_filters" to "Manage disabled filters",
        "hide_elements" to "Manage hide elements",
        "delete_cache" to "Delete filter cache",
        "reset_default" to "Reset Adblocker to default",
        "tutorial" to "Watch Tutorial",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable { onDismiss() }
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 48.dp, end = 12.dp)
                .width(265.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF1C2033))
                .border(1.dp, Color(0xFF323B5E), RoundedCornerShape(18.dp))
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
        ) {
            menuItems.forEach { (key, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(key) }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        fontSize = myTextSizes.base,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun AddFilterSourceDialog(
    activeListCount: Int,
    totalRulesCount: Int,
    onDismiss: () -> Unit,
    onAdd: (name: String, url: String, isLocal: Boolean, enable: Boolean, download: Boolean, trusted: Boolean) -> Unit,
) {
    var filterName by remember { mutableStateOf("") }
    var isLocalFile by remember { mutableStateOf(false) }
    var urlOrPath by remember { mutableStateOf("") }
    var enableFilter by remember { mutableStateOf(true) }
    var downloadFilter by remember { mutableStateOf(true) }
    var trustedSource by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF1E2235))
                .border(1.dp, Color(0xFF343D63), RoundedCornerShape(22.dp))
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Add filter source",
                fontSize = myTextSizes.lg,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Only add lists you trust. Too many lists slow down browsing and updates (now $activeListCount lists, $totalRulesCount filters).",
                fontSize = myTextSizes.sm,
                color = Color(0xFFE57348),
            )
            Spacer(Modifier.height(14.dp))
            MyTextField(
                text = filterName,
                onTextChange = { filterName = it },
                placeholder = "Filter name (optional)",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Leave the name empty to use the list's own title.",
                fontSize = myTextSizes.xs,
                color = Color(0xFF9AA5C4),
            )
            Spacer(Modifier.height(14.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { isLocalFile = false }
                        .padding(end = 24.dp),
                ) {
                    RadioButton(value = !isLocalFile, onValueChange = { isLocalFile = false })
                    Spacer(Modifier.width(8.dp))
                    Text("Online file", color = Color.White)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { isLocalFile = true },
                ) {
                    RadioButton(value = isLocalFile, onValueChange = { isLocalFile = true })
                    Spacer(Modifier.width(8.dp))
                    Text("Local file", color = Color.White)
                }
            }
            Spacer(Modifier.height(12.dp))
            MyTextField(
                text = urlOrPath,
                onTextChange = { urlOrPath = it },
                placeholder = if (isLocalFile) "/sdcard/Download/custom_filter.txt" else "URL (https://...)",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { enableFilter = !enableFilter }
                ) {
                    CheckBox(value = enableFilter, onValueChange = { enableFilter = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Enable filter", color = Color.White)
                }
                if (!isLocalFile) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { downloadFilter = !downloadFilter }
                    ) {
                        CheckBox(value = downloadFilter, onValueChange = { downloadFilter = it })
                        Spacer(Modifier.width(8.dp))
                        Text("Download filter", color = Color.White)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { trustedSource = !trustedSource }
            ) {
                CheckBox(value = trustedSource, onValueChange = { trustedSource = it })
                Spacer(Modifier.width(8.dp))
                Text("Trusted filter source", color = Color.White)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Trusted sources may run powerful scriptlets and raw JavaScript. Enable this only when you trust who controls the source.",
                fontSize = myTextSizes.xs,
                color = Color(0xFF9AA5C4),
            )
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                ActionButton(
                    text = "CANCEL",
                    onClick = onDismiss,
                )
                Spacer(Modifier.width(12.dp))
                ActionButton(
                    text = "ADD",
                    enabled = urlOrPath.isNotBlank(),
                    onClick = {
                        onAdd(filterName, urlOrPath, isLocalFile, enableFilter, downloadFilter, trustedSource)
                    }
                )
            }
        }
    }
}

@Composable
private fun ManualRulesEditorDialog(
    initialRules: List<String>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit,
) {
    var rawText by remember { mutableStateOf(initialRules.joinToString("\n")) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1A1E30))
                .border(1.dp, Color(0xFF343D63), RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Text(
                text = "Manual Custom Filter Rules",
                fontSize = myTextSizes.lg,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Enter one AdGuard / ABP / Hosts rule per line (e.g. ||ads.example.com^, @@||trusted.com^, or example.com##.banner):",
                fontSize = myTextSizes.xs,
                color = Color(0xFF9AA5C4),
            )
            Spacer(Modifier.height(12.dp))
            MyTextField(
                text = rawText,
                onTextChange = { rawText = it },
                placeholder = "||doubleclick.net^\nexample.com##.ad-slot",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                ActionButton(text = "Cancel", onClick = onDismiss)
                Spacer(Modifier.width(10.dp))
                ActionButton(
                    text = "Save & Compile",
                    onClick = { onSave(rawText.lines()) }
                )
            }
        }
    }
}

@Composable
private fun FilterSettingsSubDialog(
    settings: AdBlockSettingsState,
    onDismiss: () -> Unit,
    onSave: (Boolean, Int, Boolean, Boolean, Boolean, Boolean) -> Unit,
) {
    var autoUpdate by remember { mutableStateOf(settings.autoUpdateEnabled) }
    var intervalHours by remember { mutableStateOf(settings.updateIntervalHours.toString()) }
    var wifiOnly by remember { mutableStateOf(settings.wifiOnlyUpdates) }
    var stripParams by remember { mutableStateOf(settings.stripTrackingParams) }
    var cosmetic by remember { mutableStateOf(settings.cosmeticFilteringEnabled) }
    var scriptlets by remember { mutableStateOf(settings.scriptletsEnabled) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1A1E30))
                .border(1.dp, Color(0xFF343D63), RoundedCornerShape(20.dp))
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Filter Engine Settings",
                fontSize = myTextSizes.lg,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            SettingToggleRow("Auto-update filter subscriptions", autoUpdate) { autoUpdate = it }
            SettingToggleRow("Update over Wi-Fi only", wifiOnly) { wifiOnly = it }
            SettingToggleRow("AdGuard URL Tracking parameter stripping", stripParams) { stripParams = it }
            SettingToggleRow("Cosmetic element hiding (CSS ## rules)", cosmetic) { cosmetic = it }
            SettingToggleRow("Trusted anti-adblock scriptlets", scriptlets) { scriptlets = it }

            Text("Update interval (hours):", fontSize = myTextSizes.sm, color = Color(0xFFABB6D6))
            MyTextField(
                text = intervalHours,
                onTextChange = { intervalHours = it.filter { c -> c.isDigit() } },
                placeholder = "24",
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                ActionButton(text = "Cancel", onClick = onDismiss)
                Spacer(Modifier.width(10.dp))
                ActionButton(
                    text = "Save",
                    onClick = {
                        onSave(
                            autoUpdate,
                            intervalHours.toIntOrNull() ?: 24,
                            wifiOnly,
                            stripParams,
                            cosmetic,
                            scriptlets,
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = myTextSizes.sm,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun StringSetManagerDialog(
    title: String,
    subtitle: String,
    placeholder: String,
    items: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1A1E30))
                .border(1.dp, Color(0xFF343D63), RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Text(title, fontSize = myTextSizes.lg, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(6.dp))
            Text(subtitle, fontSize = myTextSizes.xs, color = Color(0xFF9AA5C4))
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MyTextField(
                    text = input,
                    onTextChange = { input = it },
                    placeholder = placeholder,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                ActionButton(
                    text = "Add",
                    enabled = input.isNotBlank(),
                    onClick = {
                        onAdd(input)
                        input = ""
                    }
                )
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                items(items) { domain ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(domain, color = Color.White, modifier = Modifier.weight(1f))
                        TransparentIconActionButton(
                            icon = MyIcons.remove,
                            contentDescription = "Remove".asStringSource(),
                            onClick = { onRemove(domain) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                ActionButton(text = "Close", onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun DisabledFiltersDialog(
    disabledSources: List<FilterSourceItem>,
    onEnableSource: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1A1E30))
                .border(1.dp, Color(0xFF343D63), RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Text("Disabled filters", fontSize = myTextSizes.lg, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(8.dp))
            if (disabledSources.isEmpty()) {
                Text("All configured filter lists are currently active!", color = Color(0xFF00E5A3))
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                    items(disabledSources) { src ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(src.name, color = Color.White, modifier = Modifier.weight(1f))
                            ActionButton(text = "Enable", onClick = { onEnableSource(src.id) })
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                ActionButton(text = "Close", onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun HideElementsManagerDialog(
    globalSelectors: List<String>,
    perHostSelectors: Map<String, List<String>>,
    onAdd: (String?, String) -> Unit,
    onRemove: (String?, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var hostInput by remember { mutableStateOf("") }
    var selectorInput by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1A1E30))
                .border(1.dp, Color(0xFF343D63), RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Text("Manage hide elements", fontSize = myTextSizes.lg, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(6.dp))
            Text(
                "Add CSS selectors to hide unwanted page elements (or use 'Hide element' in the browser menu to tap-to-hide):",
                fontSize = myTextSizes.xs,
                color = Color(0xFF9AA5C4),
            )
            Spacer(Modifier.height(10.dp))
            MyTextField(
                text = hostInput,
                onTextChange = { hostInput = it },
                placeholder = "Domain (optional, leave empty for all sites)",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MyTextField(
                    text = selectorInput,
                    onTextChange = { selectorInput = it },
                    placeholder = "CSS Selector (e.g. .ad-banner, #popup)",
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                ActionButton(
                    text = "Add",
                    enabled = selectorInput.isNotBlank(),
                    onClick = {
                        onAdd(hostInput.takeIf { it.isNotBlank() }, selectorInput)
                        selectorInput = ""
                    }
                )
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                items(globalSelectors) { sel ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("*##$sel", color = Color(0xFF00E5A3), modifier = Modifier.weight(1f))
                        TransparentIconActionButton(
                            icon = MyIcons.remove,
                            contentDescription = "Delete".asStringSource(),
                            onClick = { onRemove(null, sel) }
                        )
                    }
                }
                perHostSelectors.forEach { (host, list) ->
                    items(list) { sel ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("$host##$sel", color = Color.White, modifier = Modifier.weight(1f))
                            TransparentIconActionButton(
                                icon = MyIcons.remove,
                                contentDescription = "Delete".asStringSource(),
                                onClick = { onRemove(host, sel) }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                ActionButton(text = "Close", onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun AdBlockTutorialDialog(
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1A1E30))
                .border(1.dp, Color(0xFF343D63), RoundedCornerShape(20.dp))
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Zen'in Pro Shield Guide",
                fontSize = myTextSizes.lg,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00E5A3),
            )
            Text(
                text = "1. Protection Profiles (Pro / Pro++ / Ultimate):\n" +
                    "• PRO enables AdGuard Base, Tracking, URL Tracking & Mobile Ads.\n" +
                    "• PRO++ adds Annoyances (Cookie notices, Popups, Social widgets).\n" +
                    "• ULTIMATE enables all AdGuard, EasyList, uBlock & NextDNS lists.",
                fontSize = myTextSizes.sm,
                color = Color.White,
            )
            Text(
                text = "2. Custom Rules Syntax:\n" +
                    "• ||example.com^ — Blocks domain and all subdomains.\n" +
                    "• @@||example.com^ — Whitelists domain.\n" +
                    "• example.com##.ad-box — Hides CSS element on example.com.",
                fontSize = myTextSizes.sm,
                color = Color(0xFFD0DAFF),
            )
            Text(
                text = "3. NextDNS Integration:\n" +
                    "Open 'Connect custom DNS' from the browser menu to connect your NextDNS Profile ID (DoH/DoT) and sync NextDNS blocklists locally.",
                fontSize = myTextSizes.sm,
                color = Color.White,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                ActionButton(text = "Got It", onClick = onDismiss)
            }
        }
    }
}
