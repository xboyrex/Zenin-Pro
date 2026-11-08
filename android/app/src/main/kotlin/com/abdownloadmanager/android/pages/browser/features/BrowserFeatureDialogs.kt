package com.abdownloadmanager.android.pages.browser.features

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.abdownloadmanager.android.pages.browser.adblock.DnsProviderPreset
import com.abdownloadmanager.android.pages.browser.adblock.ZeninAdBlockEngine
import com.abdownloadmanager.android.pages.browser.adblock.ZeninDnsEngine
import com.abdownloadmanager.shared.ui.widget.ActionButton
import com.abdownloadmanager.shared.ui.widget.CheckBox
import com.abdownloadmanager.shared.ui.widget.MyTextField
import com.abdownloadmanager.shared.ui.widget.RadioButton
import com.abdownloadmanager.shared.ui.widget.Switch
import com.abdownloadmanager.shared.ui.widget.Text
import com.abdownloadmanager.shared.ui.widget.TransparentIconActionButton
import com.abdownloadmanager.shared.util.ClipboardUtil
import com.abdownloadmanager.shared.util.ui.icon.MyIcons
import com.abdownloadmanager.shared.util.ui.theme.myTextSizes
import com.abdownloadmanager.shared.util.ui.widget.MyIcon
import ir.amirab.util.compose.IconSource
import ir.amirab.util.compose.asStringSource

enum class ActiveBrowserModal {
    NONE,
    MAIN_MENU,
    HOSTS_AND_FILTERS,
    CUSTOM_DNS,
    PASSWORD_MANAGER,
    GRABBER_MEDIA,
    PAGE_RESOURCES,
    HISTORY,
    CLEAR_BROWSING_DATA,
    IMPORT_COOKIES,
    VIEW_PAGE_SOURCE,
    SAVED_PAGES,
    SITE_SETTINGS_LIST,
    USERSCRIPTS,
    ABOUT_ZENIN,
}

@Composable
fun ZeninFullBrowserMenuSheet(
    visible: Boolean,
    currentHost: String,
    isIncognito: Boolean,
    blockAdsEnabled: Boolean,
    disableAdblockOnSite: Boolean,
    captureMediaEnabled: Boolean,
    darkModeEnabled: Boolean,
    desktopSiteEnabled: Boolean,
    javaScriptEnabled: Boolean,
    forceContextMenuOnHost: Boolean,
    backgroundPlayEnabled: Boolean,
    capturedMediaCount: Int,
    onDismiss: () -> Unit,
    onAction: (String) -> Unit,
) {
    if (!visible) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable { onDismiss() }
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(325.dp)
                    .background(Color(0xFF131624))
                    .border(1.dp, Color(0xFF28304E))
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .clickable(enabled = false) {}
            ) {
                // Menu Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF1E2440), Color(0xFF171B30))
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isIncognito) "Zen'in Pro · Incognito" else "Zen'in Pro Browser",
                            fontWeight = FontWeight.Bold,
                            fontSize = myTextSizes.base,
                            color = if (isIncognito) Color(0xFFD1A6FF) else Color(0xFF00E5A3),
                        )
                        Text(
                            text = currentHost.ifBlank { "Ready" },
                            fontSize = myTextSizes.xs,
                            color = Color(0xFF9AA5C4),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TransparentIconActionButton(
                        icon = MyIcons.close,
                        contentDescription = "Close".asStringSource(),
                        onClick = onDismiss,
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 6.dp)
                ) {
                    MenuRowItem(MyIcons.add, "New Tab") { onAction("new_tab") }
                    MenuRowItem(
                        MyIcons.permission,
                        if (isIncognito) "Exit Incognito Mode" else "Switch to Incognito",
                        badgeText = if (isIncognito) "ON" else null
                    ) { onAction("toggle_incognito") }
                    MenuRowItem(MyIcons.download, "Return to Zen'in DM+") { onAction("return_to_dm") }
                    MenuRowItem(MyIcons.lock, "Password manager") { onAction("password_manager") }
                    MenuRowItem(MyIcons.network, "Connect custom DNS", badgeText = "NextDNS") { onAction("connect_dns") }
                    MenuRowItem(MyIcons.exit, "Remember tabs & Exit Browser") { onAction("remember_tabs_exit") }
                    MenuRowItem(MyIcons.windowClose, "Exit Browser") { onAction("exit_browser") }

                    MenuDivider()

                    MenuCheckboxItem(
                        icon = MyIcons.permission,
                        label = "Block Ads",
                        checked = blockAdsEnabled,
                        onToggle = { onAction("toggle_block_ads") }
                    )
                    MenuCheckboxItem(
                        icon = MyIcons.earth,
                        label = "Disable Adblocker on this website",
                        checked = disableAdblockOnSite,
                        onToggle = { onAction("toggle_site_adblock") }
                    )
                    MenuRowItem(MyIcons.clear, "Hide element") { onAction("hide_element") }
                    MenuCheckboxItem(
                        icon = MyIcons.videoFile,
                        label = "Capture audio/video",
                        checked = captureMediaEnabled,
                        onToggle = { onAction("toggle_capture_media") }
                    )
                    MenuRowItem(MyIcons.data, "View Page Resources") { onAction("view_resources") }
                    MenuRowItem(MyIcons.language, "Translate page") { onAction("translate_page") }
                    MenuRowItem(MyIcons.externalLink, "Open in App") { onAction("open_in_app") }
                    MenuRowItem(MyIcons.qrCode, "Scan QR code") { onAction("scan_qr") }
                    MenuRowItem(MyIcons.file, "Import cookies") { onAction("import_cookies") }
                    MenuRowItem(MyIcons.browserGoogleChrome, "Login using external browser") { onAction("external_login") }
                    MenuRowItem(MyIcons.copy, "Clone Tab") { onAction("clone_tab") }
                    MenuRowItem(
                        MyIcons.download,
                        "Grabber",
                        badgeText = if (capturedMediaCount > 0) "$capturedMediaCount" else null
                    ) { onAction("grabber") }
                    MenuRowItem(MyIcons.clock, "History") { onAction("history") }
                    MenuRowItem(MyIcons.paste, "Open tab for all links available in clipboard") { onAction("open_clipboard_links") }
                    MenuRowItem(MyIcons.remove, "Clear browsing data") { onAction("clear_data") }
                    MenuRowItem(MyIcons.speaker, "Read webpage aloud") { onAction("read_aloud") }
                    MenuRowItem(MyIcons.windowMaximize, "Force video fullscreen") { onAction("force_fullscreen") }
                    MenuRowItem(MyIcons.windowFloating, "Enter picture-in-picture") { onAction("enter_pip") }
                    MenuCheckboxItem(
                        icon = MyIcons.musicFile,
                        label = "Background play",
                        checked = backgroundPlayEnabled,
                        onToggle = { onAction("toggle_background_play") }
                    )
                    MenuRowItem(MyIcons.documentFile, "View page source") { onAction("view_source") }
                    MenuRowItem(MyIcons.edit, "View page source with") { onAction("view_source_with") }
                    MenuRowItem(MyIcons.file, "Print page") { onAction("print_page") }
                    MenuRowItem(MyIcons.folderFinished, "Save page") { onAction("save_page") }
                    MenuRowItem(MyIcons.folder, "Saved pages") { onAction("saved_pages") }
                    MenuRowItem(MyIcons.search, "Find in Page") { onAction("find_in_page") }
                    MenuRowItem(MyIcons.copy, "Copy link") { onAction("copy_link") }
                    MenuRowItem(MyIcons.copy, "Copy all tab links") { onAction("copy_all_tab_links") }
                    MenuRowItem(MyIcons.add, "Add to Homescreen") { onAction("add_to_homescreen") }
                    MenuRowItem(MyIcons.hearth, "Bookmarks") { onAction("bookmarks") }
                    MenuRowItem(MyIcons.share, "Share link") { onAction("share_link") }
                    MenuRowItem(MyIcons.externalLink, "Share link to browser") { onAction("share_to_browser") }

                    MenuDivider()

                    MenuCheckboxItem(
                        icon = MyIcons.appearance,
                        label = "Dark mode",
                        checked = darkModeEnabled,
                        onToggle = { onAction("toggle_dark_mode") }
                    )
                    MenuCheckboxItem(
                        icon = MyIcons.windowMaximize,
                        label = "Request desktop site",
                        checked = desktopSiteEnabled,
                        onToggle = { onAction("toggle_desktop_site") }
                    )
                    MenuCheckboxItem(
                        icon = MyIcons.fast,
                        label = "Enable JavaScript",
                        checked = javaScriptEnabled,
                        onToggle = { onAction("toggle_javascript") }
                    )
                    MenuRowItem(MyIcons.add, "Add Bookmark") { onAction("add_bookmark") }
                    MenuRowItem(MyIcons.link, "Add to Shortcuts") { onAction("add_to_shortcuts") }
                    MenuRowItem(MyIcons.hearth, "Bookmark all tabs") { onAction("bookmark_all_tabs") }
                    MenuRowItem(MyIcons.documentFile, "Reader Mode") { onAction("reader_mode") }

                    val displayHost = currentHost.ifBlank { "this site" }
                    MenuCheckboxItem(
                        icon = MyIcons.menu,
                        label = "Force context menu (long press) on $displayHost",
                        checked = forceContextMenuOnHost,
                        onToggle = { onAction("toggle_force_context_menu") }
                    )

                    MenuDivider()

                    MenuRowItem(
                        MyIcons.permission,
                        "Manage adblock hosts & filters",
                        badgeText = "AdGuard"
                    ) { onAction("manage_adblock_filters") }
                    MenuRowItem(MyIcons.settings, "Site settings list") { onAction("site_settings_list") }
                    MenuRowItem(MyIcons.applicationFile, "Userscripts") { onAction("userscripts") }
                    MenuRowItem(MyIcons.settings, "Settings") { onAction("app_settings") }
                    MenuRowItem(MyIcons.info, "About") { onAction("about") }
                }
            }
        }
    }
}

@Composable
private fun MenuRowItem(
    icon: IconSource,
    label: String,
    badgeText: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MyIcon(
            icon = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = Color(0xFFC8D3F5),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            fontSize = myTextSizes.sm,
            color = Color(0xFFF0F4FF),
            modifier = Modifier.weight(1f),
        )
        if (badgeText != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF00E5A3).copy(alpha = 0.2f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = badgeText,
                    fontSize = myTextSizes.xs,
                    color = Color(0xFF00E5A3),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun MenuCheckboxItem(
    icon: IconSource,
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MyIcon(
            icon = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (checked) Color(0xFF00E5A3) else Color(0xFFC8D3F5),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            fontSize = myTextSizes.sm,
            color = Color(0xFFF0F4FF),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        CheckBox(
            value = checked,
            onValueChange = { onToggle() },
        )
    }
}

@Composable
private fun MenuDivider() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(1.dp)
            .background(Color(0xFF252D47))
    )
}

// ==================== NextDNS & Custom DNS Dialog ====================

@Composable
fun NextDnsConfigDialog(
    visible: Boolean,
    dnsEngine: ZeninDnsEngine,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    val state by dnsEngine.state.collectAsState()
    val isTesting by dnsEngine.isTesting.collectAsState()

    var selectedPreset by remember(state.preset) { mutableStateOf(state.preset) }
    var profileId by remember(state.nextDnsProfileId) { mutableStateOf(state.nextDnsProfileId) }
    var customDoh by remember(state.customDohUrl) { mutableStateOf(state.customDohUrl) }
    var syncLists by remember(state.syncNextDnsListsToBrowser) { mutableStateOf(state.syncNextDnsListsToBrowser) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF141829))
                .border(1.dp, Color(0xFF323C66), RoundedCornerShape(22.dp))
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MyIcon(
                    icon = MyIcons.network,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = Color(0xFF00E5A3),
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "NextDNS & Encrypted DoH/DoT",
                        fontSize = myTextSizes.lg,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        text = state.statusText,
                        fontSize = myTextSizes.xs,
                        color = if (state.connectionVerified) Color(0xFF00E5A3) else Color(0xFF90A4AE),
                    )
                }
            }

            // Provider Presets
            DnsProviderPreset.entries.forEach { preset ->
                val isSelected = selectedPreset == preset
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) Color(0xFF20294A) else Color(0xFF191F34))
                        .border(
                            1.dp,
                            if (isSelected) Color(0xFF5E6AD2) else Color.Transparent,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { selectedPreset = preset }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(value = isSelected, onValueChange = { selectedPreset = preset })
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(preset.displayName, fontWeight = FontWeight.Bold, color = Color.White, fontSize = myTextSizes.sm)
                        Text(preset.description, fontSize = myTextSizes.xs, color = Color(0xFF9AA5C4))
                    }
                }
            }

            if (selectedPreset == DnsProviderPreset.NEXTDNS_PROFILE) {
                Text("NextDNS Profile ID (e.g. abc123):", fontSize = myTextSizes.sm, color = Color(0xFFD5DEFF))
                MyTextField(
                    text = profileId,
                    onTextChange = { profileId = it },
                    placeholder = "Enter NextDNS Profile ID (e.g. a1b2c3)",
                    modifier = Modifier.fillMaxWidth(),
                )
                val previewId = profileId.ifBlank { "YOUR_ID" }
                Text(
                    text = "• DoH: https://dns.nextdns.io/$previewId\n• DoT (Private DNS): $previewId.dns.nextdns.io",
                    fontSize = myTextSizes.xs,
                    color = Color(0xFF00E5A3),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ActionButton(
                        text = "Copy DoT Host & Open Android Private DNS",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            if (profileId.isNotBlank()) {
                                ClipboardUtil.copy("${profileId.trim()}.dns.nextdns.io")
                            }
                            dnsEngine.openAndroidPrivateDnsSettings()
                        }
                    )
                }
            }

            if (selectedPreset == DnsProviderPreset.CUSTOM_DOH) {
                Text("Custom DoH Endpoint URL:", fontSize = myTextSizes.sm, color = Color(0xFFD5DEFF))
                MyTextField(
                    text = customDoh,
                    onTextChange = { customDoh = it },
                    placeholder = "https://dns.adguard-dns.com/dns-query",
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // NextDNS Blocklist Feeds Direct Browser Sync
            Text(
                text = "NextDNS Blocklist Feeds (One-Tap Browser Sync):",
                fontWeight = FontWeight.Bold,
                fontSize = myTextSizes.sm,
                color = Color.White,
            )
            dnsEngine.nextDnsCatalogFeeds.forEach { feed ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1B2138))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(feed.name, fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = myTextSizes.sm)
                        Text(feed.description, fontSize = myTextSizes.xs, color = Color(0xFF9AA5C4))
                    }
                    Spacer(Modifier.width(8.dp))
                    ActionButton(
                        text = "Sync",
                        onClick = { dnsEngine.syncNextDnsFeedToAdBlocker(feed) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionButton(
                    text = if (isTesting) "Testing..." else "Test Latency",
                    onClick = {
                        dnsEngine.updateDnsConfiguration(selectedPreset, profileId, customDoh, syncLists)
                    }
                )
                Row {
                    ActionButton(text = "Cancel", onClick = onDismiss)
                    Spacer(Modifier.width(8.dp))
                    ActionButton(
                        text = "Save & Apply",
                        onClick = {
                            dnsEngine.updateDnsConfiguration(selectedPreset, profileId, customDoh, syncLists)
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

// ==================== Encrypted Password Manager Dialog ====================

@Composable
fun PasswordVaultDialog(
    visible: Boolean,
    featuresManager: ZeninBrowserFeaturesManager,
    currentUrl: String?,
    onAutofillRequested: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    val credentials by featuresManager.credentials.collectAsState()
    var domainInput by remember { mutableStateOf(currentUrl?.substringAfter("://")?.substringBefore("/").orEmpty()) }
    var userInput by remember { mutableStateOf("") }
    var passInput by remember { mutableStateOf("") }
    var revealedIds by remember { mutableStateOf(setOf<String>()) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF141829))
                .border(1.dp, Color(0xFF323C66), RoundedCornerShape(22.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MyIcon(MyIcons.lock, null, Modifier.size(24.dp), tint = Color(0xFF00E5A3))
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Zen'in Password Vault", fontSize = myTextSizes.lg, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("AES-256-GCM Hardware Keystore Encrypted", fontSize = myTextSizes.xs, color = Color(0xFF00E5A3))
                }
            }

            MyTextField(
                text = domainInput,
                onTextChange = { domainInput = it },
                placeholder = "Website domain (e.g. github.com)",
                modifier = Modifier.fillMaxWidth(),
            )
            MyTextField(
                text = userInput,
                onTextChange = { userInput = it },
                placeholder = "Username or Email",
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                MyTextField(
                    text = passInput,
                    onTextChange = { passInput = it },
                    placeholder = "Password",
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                ActionButton(
                    text = "Save",
                    enabled = domainInput.isNotBlank() && userInput.isNotBlank() && passInput.isNotBlank(),
                    onClick = {
                        featuresManager.saveCredential(domainInput, userInput, passInput)
                        userInput = ""
                        passInput = ""
                    }
                )
            }

            LazyColumn(
                modifier = Modifier.heightIn(max = 240.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(credentials, key = { it.id }) { cred ->
                    val isRevealed = cred.id in revealedIds
                    val decrypted = remember(cred, isRevealed) {
                        if (isRevealed) featuresManager.decryptPassword(cred) else "••••••••••••"
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1C223A))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(cred.domain, fontWeight = FontWeight.Bold, color = Color(0xFF70A1FF))
                            Row {
                                ActionButton(
                                    text = if (isRevealed) "Hide" else "Show",
                                    onClick = {
                                        revealedIds = if (isRevealed) revealedIds - cred.id else revealedIds + cred.id
                                    }
                                )
                                Spacer(Modifier.width(6.dp))
                                ActionButton(
                                    text = "Copy",
                                    onClick = { ClipboardUtil.copy(featuresManager.decryptPassword(cred)) }
                                )
                                Spacer(Modifier.width(6.dp))
                                TransparentIconActionButton(
                                    icon = MyIcons.remove,
                                    contentDescription = "Delete".asStringSource(),
                                    onClick = { featuresManager.deleteCredential(cred.id) }
                                )
                            }
                        }
                        Text("User: ${cred.username}", fontSize = myTextSizes.sm, color = Color.White)
                        Text("Pass: $decrypted", fontSize = myTextSizes.xs, color = Color(0xFF9AA5C4))
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ActionButton(text = "Autofill Current Page", onClick = {
                    onAutofillRequested()
                    onDismiss()
                })
                ActionButton(text = "Close", onClick = onDismiss)
            }
        }
    }
}

// ==================== Media Grabber & Page Resources Inspector ====================

@Composable
fun MediaGrabberAndResourcesDialog(
    visible: Boolean,
    initialMediaOnly: Boolean,
    featuresManager: ZeninBrowserFeaturesManager,
    adBlockEngine: ZeninAdBlockEngine,
    onDownloadResource: (CapturedPageResource) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    val resources by featuresManager.capturedResources.collectAsState()
    var filterTab by remember(initialMediaOnly) {
        mutableStateOf(if (initialMediaOnly) "MEDIA" else "ALL")
    }

    val filtered = remember(resources, filterTab) {
        when (filterTab) {
            "MEDIA" -> resources.filter {
                it.type == PageResourceType.MEDIA_VIDEO ||
                    it.type == PageResourceType.MEDIA_AUDIO ||
                    it.type == PageResourceType.MEDIA_HLS
            }
            "ADS" -> resources.filter { it.type == PageResourceType.BLOCKED_AD }
            "SCRIPTS" -> resources.filter { it.type == PageResourceType.SCRIPT || it.type == PageResourceType.STYLESHEET }
            "IMAGES" -> resources.filter { it.type == PageResourceType.IMAGE }
            else -> resources
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B0E17))
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF141829))
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TransparentIconActionButton(MyIcons.back, "Back".asStringSource(), onClick = onDismiss)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (filterTab == "MEDIA") "Zen'in Media & Stream Grabber" else "Page Resources & Network Log",
                        fontWeight = FontWeight.Bold,
                        fontSize = myTextSizes.lg,
                        color = Color.White,
                    )
                    Text(
                        text = "${filtered.size} items captured on active session",
                        fontSize = myTextSizes.xs,
                        color = Color(0xFF00E5A3),
                    )
                }
                ActionButton(text = "Clear", onClick = { featuresManager.clearCapturedResources() })
            }

            // Filter Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111524))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    "MEDIA" to "Media/HLS",
                    "ALL" to "All (${resources.size})",
                    "ADS" to "Blocked Ads",
                    "SCRIPTS" to "JS/CSS",
                    "IMAGES" to "Images",
                ).forEach { (key, label) ->
                    val selected = filterTab == key
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) Color(0xFF4D7CFE) else Color(0xFF1D233B))
                            .clickable { filterTab = key }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(label, fontSize = myTextSizes.xs, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (filterTab == "MEDIA") {
                            "No audio/video streams detected yet.\nPlay a video on the page to capture MP4 / HLS (.m3u8) streams."
                        } else {
                            "No network resources recorded yet."
                        },
                        color = Color(0xFF9AA5C4),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filtered, key = { it.id }) { item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF161B2E))
                                .border(1.dp, Color(0xFF262F4D), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            when (item.type) {
                                                PageResourceType.BLOCKED_AD -> Color(0xFFEA4C3C).copy(alpha = 0.2f)
                                                PageResourceType.MEDIA_HLS, PageResourceType.MEDIA_VIDEO, PageResourceType.MEDIA_AUDIO ->
                                                    Color(0xFF00E5A3).copy(alpha = 0.2f)
                                                else -> Color(0xFF4D7CFE).copy(alpha = 0.2f)
                                            }
                                        )
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = item.type.label,
                                        fontSize = myTextSizes.xs,
                                        fontWeight = FontWeight.Bold,
                                        color = when (item.type) {
                                            PageResourceType.BLOCKED_AD -> Color(0xFFFF6B6B)
                                            PageResourceType.MEDIA_HLS, PageResourceType.MEDIA_VIDEO, PageResourceType.MEDIA_AUDIO ->
                                                Color(0xFF00E5A3)
                                            else -> Color(0xFF82B1FF)
                                        }
                                    )
                                }
                                Text(item.fileNameGuess, fontSize = myTextSizes.xs, color = Color(0xFFABB6D6), maxLines = 1)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = item.url,
                                fontSize = myTextSizes.xs,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                ActionButton(
                                    text = "Copy URL",
                                    onClick = { ClipboardUtil.copy(item.url) }
                                )
                                Spacer(Modifier.width(8.dp))
                                if (item.type != PageResourceType.BLOCKED_AD) {
                                    ActionButton(
                                        text = "Block Domain",
                                        onClick = {
                                            val host = item.url.substringAfter("://").substringBefore("/")
                                            adBlockEngine.saveCustomRules(adBlockEngine.settings.value.customRules + "||$host^")
                                        }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    ActionButton(
                                        text = "Download (Zen'in DM)",
                                        onClick = { onDownloadResource(item) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== History, Clear Data, Cookie Import, Page Source, Saved Pages, Reader Mode, Site Settings, Userscripts ====================

@Composable
fun BrowserHistoryDialog(
    visible: Boolean,
    featuresManager: ZeninBrowserFeaturesManager,
    onOpenUrl: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    val history by featuresManager.history.collectAsState()
    var query by remember { mutableStateOf("") }
    val filtered = remember(history, query) {
        if (query.isBlank()) history
        else history.filter { it.title.contains(query, true) || it.url.contains(query, true) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF141829))
                .border(1.dp, Color(0xFF323C66), RoundedCornerShape(22.dp))
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Browsing History", fontSize = myTextSizes.lg, fontWeight = FontWeight.Bold, color = Color.White)
                ActionButton(text = "Clear All", onClick = { featuresManager.clearAllHistory() })
            }
            Spacer(Modifier.height(10.dp))
            MyTextField(
                text = query,
                onTextChange = { query = it },
                placeholder = "Search history...",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            LazyColumn(modifier = Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(filtered, key = { it.id }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1C223A))
                            .clickable {
                                onOpenUrl(item.url)
                                onDismiss()
                            }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(item.url, fontSize = myTextSizes.xs, color = Color(0xFF70A1FF), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(item.formattedTime(), fontSize = myTextSizes.xs, color = Color(0xFF8C98B8))
                        }
                        TransparentIconActionButton(
                            icon = MyIcons.remove,
                            contentDescription = "Delete".asStringSource(),
                            onClick = { featuresManager.removeHistoryItem(item.id) }
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
fun ClearBrowsingDataDialog(
    visible: Boolean,
    onConfirmClear: (history: Boolean, cookies: Boolean, cache: Boolean, webStorage: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    var clearHist by remember { mutableStateOf(true) }
    var clearCookies by remember { mutableStateOf(true) }
    var clearCache by remember { mutableStateOf(true) }
    var clearStorage by remember { mutableStateOf(true) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF141829))
                .border(1.dp, Color(0xFF323C66), RoundedCornerShape(20.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Clear browsing data", fontSize = myTextSizes.lg, fontWeight = FontWeight.Bold, color = Color.White)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { clearHist = !clearHist }) {
                CheckBox(value = clearHist, onValueChange = { clearHist = it })
                Spacer(Modifier.width(10.dp))
                Text("Browsing history", color = Color.White)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { clearCookies = !clearCookies }) {
                CheckBox(value = clearCookies, onValueChange = { clearCookies = it })
                Spacer(Modifier.width(10.dp))
                Text("Cookies and site data", color = Color.White)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { clearCache = !clearCache }) {
                CheckBox(value = clearCache, onValueChange = { clearCache = it })
                Spacer(Modifier.width(10.dp))
                Text("Cached images and files", color = Color.White)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { clearStorage = !clearStorage }) {
                CheckBox(value = clearStorage, onValueChange = { clearStorage = it })
                Spacer(Modifier.width(10.dp))
                Text("DOM & HTML5 Web Storage", color = Color.White)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                ActionButton(text = "Cancel", onClick = onDismiss)
                Spacer(Modifier.width(10.dp))
                ActionButton(
                    text = "Clear Data",
                    onClick = {
                        onConfirmClear(clearHist, clearCookies, clearCache, clearStorage)
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
fun ImportCookiesDialog(
    visible: Boolean,
    currentUrl: String?,
    featuresManager: ZeninBrowserFeaturesManager,
    onImported: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    var domain by remember { mutableStateOf(currentUrl ?: "https://") }
    var rawCookies by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF141829))
                .border(1.dp, Color(0xFF323C66), RoundedCornerShape(20.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Import Cookies", fontSize = myTextSizes.lg, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                "Paste Netscape cookies.txt lines or standard Cookie header pairs (key=value; key2=value2):",
                fontSize = myTextSizes.xs,
                color = Color(0xFF9AA5C4),
            )
            MyTextField(
                text = domain,
                onTextChange = { domain = it },
                placeholder = "Target URL / Domain",
                modifier = Modifier.fillMaxWidth(),
            )
            MyTextField(
                text = rawCookies,
                onTextChange = { rawCookies = it },
                placeholder = "session_id=xyz123; auth_token=abc987",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                ActionButton(text = "Cancel", onClick = onDismiss)
                Spacer(Modifier.width(10.dp))
                ActionButton(
                    text = "Import",
                    enabled = rawCookies.isNotBlank(),
                    onClick = {
                        featuresManager.importCookiesRaw(domain, rawCookies)
                        onImported()
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
fun PageSourceViewerDialog(
    visible: Boolean,
    htmlSource: String,
    onOpenExternal: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0C14))
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Page HTML Source", fontSize = myTextSizes.lg, fontWeight = FontWeight.Bold, color = Color.White)
                Row {
                    ActionButton(text = "Copy All", onClick = { ClipboardUtil.copy(htmlSource) })
                    Spacer(Modifier.width(8.dp))
                    ActionButton(text = "Open With", onClick = onOpenExternal)
                    Spacer(Modifier.width(8.dp))
                    ActionButton(text = "Close", onClick = onDismiss)
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF121626))
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = htmlSource.ifBlank { "Loading page HTML source..." },
                    fontSize = myTextSizes.xs,
                    color = Color(0xFFD5DEFF),
                )
            }
        }
    }
}

@Composable
fun SavedPagesOfflineDialog(
    visible: Boolean,
    featuresManager: ZeninBrowserFeaturesManager,
    onOpenSavedArchive: (SavedPageItem) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    val savedPages by featuresManager.savedPages.collectAsState()

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF141829))
                .border(1.dp, Color(0xFF323C66), RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Text("Saved Offline Pages (.mht)", fontSize = myTextSizes.lg, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(10.dp))
            if (savedPages.isEmpty()) {
                Text("No offline pages saved yet. Use 'Save page' in the browser menu to archive any webpage for offline viewing.", color = Color(0xFF9AA5C4))
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(savedPages, key = { it.id }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1C223A))
                                .clickable {
                                    onOpenSavedArchive(item)
                                    onDismiss()
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                                Text("${item.sizeBytes / 1024} KB · ${item.formattedDate()}", fontSize = myTextSizes.xs, color = Color(0xFF00E5A3))
                            }
                            TransparentIconActionButton(
                                icon = MyIcons.remove,
                                contentDescription = "Delete".asStringSource(),
                                onClick = { featuresManager.deleteSavedPage(item.id) }
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
fun ReaderModeOverlay(
    visible: Boolean,
    title: String,
    body: String,
    onClose: () -> Unit,
) {
    if (!visible) return
    var fontSizeSp by remember { mutableStateOf(17) }
    var paperTheme by remember { mutableStateOf("DARK") }

    val bg = when (paperTheme) {
        "SEPIA" -> Color(0xFFF4ECD8)
        "LIGHT" -> Color(0xFFFFFFFF)
        else -> Color(0xFF0E111B)
    }
    val fg = when (paperTheme) {
        "SEPIA" -> Color(0xFF3B2E1E)
        "LIGHT" -> Color(0xFF1B1E23)
        else -> Color(0xFFE4E9F7)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF15192B))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Zen'in Reader Mode",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00E5A3),
                    modifier = Modifier.weight(1f),
                )
                ActionButton(text = "A-", onClick = { fontSizeSp = (fontSizeSp - 2).coerceAtLeast(13) })
                Spacer(Modifier.width(6.dp))
                ActionButton(text = "A+", onClick = { fontSizeSp = (fontSizeSp + 2).coerceAtMost(28) })
                Spacer(Modifier.width(6.dp))
                ActionButton(
                    text = paperTheme,
                    onClick = {
                        paperTheme = when (paperTheme) {
                            "DARK" -> "SEPIA"
                            "SEPIA" -> "LIGHT"
                            else -> "DARK"
                        }
                    }
                )
                Spacer(Modifier.width(6.dp))
                ActionButton(text = "Exit", onClick = onClose)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 18.dp)
            ) {
                Text(
                    text = title,
                    fontSize = (fontSizeSp + 5).sp,
                    fontWeight = FontWeight.Bold,
                    color = fg,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = body,
                    fontSize = fontSizeSp.sp,
                    color = fg,
                )
            }
        }
    }
}

@Composable
fun SiteSettingsListDialog(
    visible: Boolean,
    featuresManager: ZeninBrowserFeaturesManager,
    currentUrl: String?,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    val siteSettingsMap by featuresManager.siteSettings.collectAsState()
    val currentSetting = remember(currentUrl, siteSettingsMap) {
        featuresManager.getSiteSettingForUrl(currentUrl)
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF141829))
                .border(1.dp, Color(0xFF323C66), RoundedCornerShape(20.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Site settings list", fontSize = myTextSizes.lg, fontWeight = FontWeight.Bold, color = Color.White)
            if (currentSetting.host.isNotBlank()) {
                Text("Current Site: ${currentSetting.host}", fontWeight = FontWeight.SemiBold, color = Color(0xFF00E5A3))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("JavaScript Allowed", color = Color.White)
                    Switch(
                        checked = currentSetting.enableJavaScript,
                        onCheckedChange = {
                            featuresManager.updateSiteSetting(currentSetting.copy(enableJavaScript = it))
                        }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Desktop Mode on this host", color = Color.White)
                    Switch(
                        checked = currentSetting.requestDesktopSite,
                        onCheckedChange = {
                            featuresManager.updateSiteSetting(currentSetting.copy(requestDesktopSite = it))
                        }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Force Context Menu (Long Press)", color = Color.White)
                    Switch(
                        checked = currentSetting.forceContextMenu,
                        onCheckedChange = {
                            featuresManager.updateSiteSetting(currentSetting.copy(forceContextMenu = it))
                        }
                    )
                }
            }
            LazyColumn(modifier = Modifier.heightIn(max = 220.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(siteSettingsMap.values.toList(), key = { it.host }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1C223A))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.host, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(
                                "JS: ${if (item.enableJavaScript) "ON" else "OFF"} · Desktop: ${if (item.requestDesktopSite) "ON" else "OFF"} · ForceMenu: ${if (item.forceContextMenu) "ON" else "OFF"}",
                                fontSize = myTextSizes.xs,
                                color = Color(0xFF9AA5C4),
                            )
                        }
                        TransparentIconActionButton(
                            icon = MyIcons.remove,
                            contentDescription = "Remove".asStringSource(),
                            onClick = { featuresManager.removeSiteSetting(item.host) }
                        )
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                ActionButton(text = "Close", onClick = onDismiss)
            }
        }
    }
}

@Composable
fun UserscriptsManagerDialog(
    visible: Boolean,
    featuresManager: ZeninBrowserFeaturesManager,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    val scripts by featuresManager.userscripts.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf("") }
    var matchInput by remember { mutableStateOf("*") }
    var codeInput by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF141829))
                .border(1.dp, Color(0xFF323C66), RoundedCornerShape(20.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Userscripts Manager", fontSize = myTextSizes.lg, fontWeight = FontWeight.Bold, color = Color.White)
                ActionButton(text = if (showAdd) "Cancel" else "+ New Script", onClick = { showAdd = !showAdd })
            }

            if (showAdd) {
                MyTextField(text = nameInput, onTextChange = { nameInput = it }, placeholder = "Script Name", modifier = Modifier.fillMaxWidth())
                MyTextField(text = matchInput, onTextChange = { matchInput = it }, placeholder = "Match pattern (e.g. * or *youtube.com*)", modifier = Modifier.fillMaxWidth())
                MyTextField(
                    text = codeInput,
                    onTextChange = { codeInput = it },
                    placeholder = "(function() { /* JS code */ })();",
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                )
                ActionButton(
                    text = "Save Userscript",
                    enabled = nameInput.isNotBlank() && codeInput.isNotBlank(),
                    onClick = {
                        featuresManager.addOrUpdateUserscript(
                            UserscriptItem(
                                name = nameInput.trim(),
                                description = "Custom user script (${matchInput.trim()})",
                                matchPattern = matchInput.trim().ifEmpty { "*" },
                                code = codeInput,
                                enabled = true,
                            )
                        )
                        nameInput = ""
                        codeInput = ""
                        showAdd = false
                    }
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(scripts, key = { it.id }) { script ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1C223A))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(script.name, fontWeight = FontWeight.Bold, color = Color.White)
                                Text(script.description, fontSize = myTextSizes.xs, color = Color(0xFF9AA5C4))
                                Text("Match: ${script.matchPattern}", fontSize = myTextSizes.xs, color = Color(0xFF00E5A3))
                            }
                            Switch(
                                checked = script.enabled,
                                onCheckedChange = { featuresManager.toggleUserscript(script.id, it) }
                            )
                            Spacer(Modifier.width(4.dp))
                            TransparentIconActionButton(
                                icon = MyIcons.remove,
                                contentDescription = "Delete".asStringSource(),
                                onClick = { featuresManager.deleteUserscript(script.id) }
                            )
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                ActionButton(text = "Close", onClick = onDismiss)
            }
        }
    }
}
