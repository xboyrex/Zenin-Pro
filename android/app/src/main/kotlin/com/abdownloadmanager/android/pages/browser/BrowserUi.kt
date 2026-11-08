package com.abdownloadmanager.android.pages.browser

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.abdownloadmanager.android.pages.browser.adblock.AdBlockFiltersManagerDialog
import com.abdownloadmanager.android.pages.browser.adblock.AdGuardProfile
import com.abdownloadmanager.android.pages.browser.bookmark.BookmarkList
import com.abdownloadmanager.android.pages.browser.bookmark.EditBookmarkSheet
import com.abdownloadmanager.android.pages.browser.features.ActiveBrowserModal
import com.abdownloadmanager.android.pages.browser.features.BrowserHistoryDialog
import com.abdownloadmanager.android.pages.browser.features.ClearBrowsingDataDialog
import com.abdownloadmanager.android.pages.browser.features.ImportCookiesDialog
import com.abdownloadmanager.android.pages.browser.features.MediaGrabberAndResourcesDialog
import com.abdownloadmanager.android.pages.browser.features.NextDnsConfigDialog
import com.abdownloadmanager.android.pages.browser.features.PageResourceType
import com.abdownloadmanager.android.pages.browser.features.PageSourceViewerDialog
import com.abdownloadmanager.android.pages.browser.features.PasswordVaultDialog
import com.abdownloadmanager.android.pages.browser.features.ReaderModeOverlay
import com.abdownloadmanager.android.pages.browser.features.SavedPagesOfflineDialog
import com.abdownloadmanager.android.pages.browser.features.SiteSettingsListDialog
import com.abdownloadmanager.android.pages.browser.features.UserscriptsManagerDialog
import com.abdownloadmanager.android.pages.browser.features.ZeninFullBrowserMenuSheet
import com.abdownloadmanager.android.storage.BrowserBookmark
import com.abdownloadmanager.android.ui.SheetHeader
import com.abdownloadmanager.android.ui.SheetTitle
import com.abdownloadmanager.android.ui.SheetUI
import com.abdownloadmanager.android.ui.menu.RenderMenuInSheet
import com.abdownloadmanager.android.ui.page.PageFooter
import com.abdownloadmanager.android.ui.page.PageUi
import com.abdownloadmanager.android.ui.widget.LoadingState
import com.abdownloadmanager.resources.Res
import com.abdownloadmanager.shared.ui.widget.ActionButton
import com.abdownloadmanager.shared.ui.widget.MyTextField
import com.abdownloadmanager.shared.ui.widget.Text
import com.abdownloadmanager.shared.ui.widget.TransparentIconActionButton
import com.abdownloadmanager.shared.util.ClipboardUtil
import com.abdownloadmanager.shared.util.ResponsiveDialog
import com.abdownloadmanager.shared.util.div
import com.abdownloadmanager.shared.util.rememberResponsiveDialogState
import com.abdownloadmanager.shared.util.ui.WithContentAlpha
import com.abdownloadmanager.shared.util.ui.WithContentColor
import com.abdownloadmanager.shared.util.ui.icon.MyIcons
import com.abdownloadmanager.shared.util.ui.myColors
import com.abdownloadmanager.shared.util.ui.theme.myShapes
import com.abdownloadmanager.shared.util.ui.theme.mySpacings
import com.abdownloadmanager.shared.util.ui.theme.myTextSizes
import com.abdownloadmanager.shared.util.ui.widget.MyIcon
import ir.amirab.util.compose.asStringSource
import ir.amirab.util.compose.resources.myStringResource
import ir.amirab.util.ifThen
import java.io.File

@Composable
fun BrowserPage(
    browserComponent: BrowserComponent,
) {
    val scope = rememberCoroutineScope()
    val viewRegistry = remember {
        WebViewRegistry(scope, browserComponent)
    }
    DisposableEffect(viewRegistry) {
        onDispose {
            viewRegistry.disposeAll()
        }
    }
    val tabs by browserComponent.tabs.collectAsState()
    val tab = tabs.activeTab
    val tabWebViewHolder = remember(tab?.tabId) {
        tab?.let {
            viewRegistry.getWebViewHolder(it)
        }
    }

    val adBlockSettings by browserComponent.adBlockEngine.settings.collectAsState()
    val totalActiveRules by browserComponent.adBlockEngine.totalActiveRules.collectAsState()
    val dnsState by browserComponent.dnsEngine.state.collectAsState()
    val isIncognito by browserComponent.featuresManager.isIncognito.collectAsState()
    val captureMedia by browserComponent.featuresManager.captureMediaEnabled.collectAsState()
    val darkMode by browserComponent.featuresManager.darkModeEnabled.collectAsState()
    val desktopSite by browserComponent.featuresManager.desktopSiteEnabled.collectAsState()
    val jsEnabled by browserComponent.featuresManager.javaScriptEnabled.collectAsState()
    val bgPlay by browserComponent.featuresManager.backgroundPlayEnabled.collectAsState()
    val findVisible by browserComponent.featuresManager.findInPageVisible.collectAsState()
    val findStatus by browserComponent.featuresManager.findMatchStatus.collectAsState()
    val isSpeakingTts by browserComponent.featuresManager.isSpeakingTts.collectAsState()
    val readerActive by browserComponent.featuresManager.readerModeActive.collectAsState()
    val readerContent by browserComponent.featuresManager.readerModeContent.collectAsState()
    val capturedResources by browserComponent.featuresManager.capturedResources.collectAsState()
    val activeModal by browserComponent.activeModal.collectAsState()
    val pageSourceHtml by browserComponent.pageSourceHtml.collectAsState()

    val mediaListCount = remember(capturedResources) {
        capturedResources.count {
            it.type == PageResourceType.MEDIA_VIDEO ||
                it.type == PageResourceType.MEDIA_AUDIO ||
                it.type == PageResourceType.MEDIA_HLS
        }
    }

    val currentUrl = tab?.tabState?.lastLoadedUrl
    val currentHost = remember(currentUrl) {
        currentUrl?.let { runCatching { Uri.parse(it).host?.removePrefix("www.") }.getOrNull() }.orEmpty()
    }
    val siteSetting = remember(currentUrl, browserComponent.featuresManager.siteSettings.collectAsState().value) {
        browserComponent.featuresManager.getSiteSettingForUrl(currentUrl)
    }

    BackHandler(tabs.tabsSize > 1) {
        tab?.let {
            browserComponent.closeTab(it.tabId)
        }
    }
    BackHandler(tabWebViewHolder?.navigator?.canGoBack ?: false) {
        tabWebViewHolder?.webView?.goBack()
    }
    LaunchedEffect(tabs) {
        viewRegistry.onTabsUpdated(tabs)
    }

    PageUi(
        header = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0D101B))
                    .statusBarsPadding()
            ) {
                // Zen'in Pro Cyber-Shield HUD Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MyIcon(
                        MyIcons.appIcon,
                        null,
                        Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isIncognito) "Zen'in Incognito" else "Zen'in Pro",
                        fontWeight = FontWeight.Bold,
                        fontSize = myTextSizes.base,
                        color = if (isIncognito) Color(0xFFD1A6FF) else Color.White,
                    )
                    Spacer(Modifier.weight(1f))

                    // AdGuard Shield Status Pill
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (adBlockSettings.globalEnabled) Color(0xFF00E5A3).copy(alpha = 0.15f)
                                else Color(0xFFEA4C3C).copy(alpha = 0.15f)
                            )
                            .border(
                                1.dp,
                                if (adBlockSettings.globalEnabled) Color(0xFF00E5A3) else Color(0xFFEA4C3C),
                                RoundedCornerShape(50)
                            )
                            .clickable {
                                browserComponent.openModal(ActiveBrowserModal.HOSTS_AND_FILTERS)
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (adBlockSettings.globalEnabled) {
                                "Shield ${adBlockSettings.activeProfile.badge} · ${adBlockSettings.totalBlockedRequests}"
                            } else {
                                "Shield OFF"
                            },
                            fontSize = myTextSizes.xs,
                            fontWeight = FontWeight.Bold,
                            color = if (adBlockSettings.globalEnabled) Color(0xFF00E5A3) else Color(0xFFFF6B6B),
                        )
                    }

                    Spacer(Modifier.width(6.dp))

                    // NextDNS Status Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFF4D7CFE).copy(alpha = 0.18f))
                            .border(1.dp, Color(0xFF4D7CFE), RoundedCornerShape(50))
                            .clickable {
                                browserComponent.openModal(ActiveBrowserModal.CUSTOM_DNS)
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "NextDNS",
                            fontSize = myTextSizes.xs,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF82B1FF),
                        )
                    }

                    if (mediaListCount > 0) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFFFF8C42).copy(alpha = 0.22f))
                                .border(1.dp, Color(0xFFFF8C42), RoundedCornerShape(50))
                                .clickable {
                                    browserComponent.openModal(ActiveBrowserModal.GRABBER_MEDIA)
                                }
                                .padding(horizontal = 9.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Grab ($mediaListCount)",
                                fontSize = myTextSizes.xs,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFB07C),
                            )
                        }
                    }
                }

                // Find in Page Bar
                if (findVisible) {
                    var findQuery by remember { mutableStateOf("") }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF161B2E))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MyTextField(
                            text = findQuery,
                            onTextChange = {
                                findQuery = it
                                browserComponent.featuresManager.updateFindInPageQuery(it, tabWebViewHolder?.webView)
                            },
                            placeholder = "Find in page...",
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${findStatus.first}/${findStatus.second}",
                            fontSize = myTextSizes.xs,
                            color = Color(0xFF00E5A3),
                        )
                        TransparentIconActionButton(
                            icon = MyIcons.up,
                            contentDescription = "Previous".asStringSource(),
                            onClick = { tabWebViewHolder?.webView?.findNext(false) }
                        )
                        TransparentIconActionButton(
                            icon = MyIcons.down,
                            contentDescription = "Next".asStringSource(),
                            onClick = { tabWebViewHolder?.webView?.findNext(true) }
                        )
                        TransparentIconActionButton(
                            icon = MyIcons.close,
                            contentDescription = "Close".asStringSource(),
                            onClick = {
                                browserComponent.featuresManager.setFindInPageVisible(false, tabWebViewHolder?.webView)
                            }
                        )
                    }
                }

                // Read Aloud (TTS) Active Banner
                if (isSpeakingTts) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E2949))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Speaking webpage aloud (Android TTS)...",
                            fontSize = myTextSizes.xs,
                            color = Color(0xFF00E5A3),
                            fontWeight = FontWeight.Bold,
                        )
                        ActionButton(
                            text = "Stop TTS",
                            onClick = { browserComponent.featuresManager.stopReadAloud() }
                        )
                    }
                }
            }
        },
        footer = {
            PageFooter {
                Column(
                    Modifier
                        .background(Color(0xFF0E121E))
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    Spacer(
                        Modifier
                            .height(1.dp)
                            .fillMaxWidth()
                            .background(Color(0xFF232B47))
                    )
                    tab?.tabState?.loadingState?.let {
                        if (it is LoadingState.Loading) {
                            Box(
                                Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Box(
                                    Modifier
                                        .height(2.dp)
                                        .fillMaxWidth(it.progress)
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(Color(0xFF00E5A3), Color(0xFF4D7CFE), Color(0xFF9B51E0))
                                            )
                                        ),
                                )
                            }
                        }
                    }
                    WithContentColor(myColors.onSurface) {
                        AddressBar(
                            browserComponent = browserComponent,
                            currentWebViewHolder = tabWebViewHolder,
                            tabs = tabs,
                            mediaCount = mediaListCount,
                            modifier = Modifier,
                        )
                    }
                }
            }
        }
    ) {
        if (tabWebViewHolder != null && tab.tabState.lastLoadedUrl != ABDMBrowserTab.blankPage && tab.tabState.lastLoadedUrl != null) {
            ABDMWebView(
                modifier = Modifier
                    .fillMaxSize()
                    .background(myColors.background)
                    .padding(it.paddingValues),
                webViewHolder = tabWebViewHolder,
            )
        } else {
            ZeninStartDashboard(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF090B12))
                    .padding(it.paddingValues),
                browserComponent = browserComponent,
                totalActiveRules = totalActiveRules,
                adsBlocked = adBlockSettings.totalBlockedRequests,
                trackersBlocked = adBlockSettings.totalTrackersBlocked,
                activeProfile = adBlockSettings.activeProfile,
                dnsStatus = dnsState.preset.displayName,
                onNavigateUrl = { targetUrl ->
                    val resolved = browserComponent.createNewUrlFor(targetUrl)
                    tabWebViewHolder?.navigator?.loadUrl(resolved)
                        ?: browserComponent.newTab(resolved)
                },
                onOpenBookmarks = {
                    browserComponent.setShowBookmarkList(true)
                },
            )
        }
    }

    // Reader Mode Overlay
    if (readerActive && readerContent != null) {
        ReaderModeOverlay(
            visible = true,
            title = readerContent!!.first,
            body = readerContent!!.second,
            onClose = { browserComponent.featuresManager.closeReaderMode() }
        )
    }

    // Long-press link context menu
    RenderMenuInSheet(
        browserComponent.contextMenu.collectAsState().value,
        browserComponent::closeContextMenu
    )

    // Bookmarks Sheet
    BookmarkList(
        visible = browserComponent.showBookmarkList.collectAsState().value,
        onDismissRequest = {
            browserComponent.setShowBookmarkList(false)
        },
        onRemoveBookmarkRequest = {
            browserComponent.removeBookmark(it.url)
        },
        onBookmarkClick = {
            browserComponent.setShowBookmarkList(false)
            val newLink = browserComponent.createNewUrlFor(it.url)
            tabWebViewHolder
                ?.navigator
                ?.loadUrl(newLink)
                ?: browserComponent.newTab(newLink)
        },
        bookmarks = browserComponent.bookmarks.collectAsState().value,
        onRequestEditBookmark = browserComponent::promptEditBookmark,
        onRequestNewBookmark = {
            browserComponent.promptAddBookmark(BrowserBookmark("", ""))
        },
    )

    val editBookmarkState by browserComponent.editBookmarkState.collectAsState()
    editBookmarkState?.let { s ->
        EditBookmarkSheet(
            state = s,
            onSave = {
                browserComponent.addToBookmarks(
                    it,
                    if (s.editMode) s.initialValue else null,
                )
                browserComponent.dismissEditBookmark()
            },
            onCancel = {
                browserComponent.dismissEditBookmark()
            }
        )
    }

    // ==================== All 50+ Browser Feature Modals ====================

    ZeninFullBrowserMenuSheet(
        visible = activeModal == ActiveBrowserModal.MAIN_MENU,
        currentHost = currentHost,
        isIncognito = isIncognito,
        blockAdsEnabled = adBlockSettings.globalEnabled,
        disableAdblockOnSite = browserComponent.adBlockEngine.isWebsiteWhitelisted(currentUrl),
        captureMediaEnabled = captureMedia,
        darkModeEnabled = darkMode,
        desktopSiteEnabled = desktopSite,
        javaScriptEnabled = jsEnabled,
        forceContextMenuOnHost = siteSetting.forceContextMenu,
        backgroundPlayEnabled = bgPlay,
        capturedMediaCount = mediaListCount,
        onDismiss = { browserComponent.closeModal() },
        onAction = { actionKey ->
            browserComponent.handleMenuAction(actionKey, tabWebViewHolder?.webView)
        }
    )

    AdBlockFiltersManagerDialog(
        visible = activeModal == ActiveBrowserModal.HOSTS_AND_FILTERS,
        engine = browserComponent.adBlockEngine,
        onDismiss = { browserComponent.closeModal() }
    )

    NextDnsConfigDialog(
        visible = activeModal == ActiveBrowserModal.CUSTOM_DNS,
        dnsEngine = browserComponent.dnsEngine,
        onDismiss = { browserComponent.closeModal() }
    )

    PasswordVaultDialog(
        visible = activeModal == ActiveBrowserModal.PASSWORD_MANAGER,
        featuresManager = browserComponent.featuresManager,
        currentUrl = currentUrl,
        onAutofillRequested = {
            tabWebViewHolder?.webView?.let { wv ->
                browserComponent.featuresManager.autofillCredentialsIntoWebView(wv, currentUrl)
            }
        },
        onDismiss = { browserComponent.closeModal() }
    )

    MediaGrabberAndResourcesDialog(
        visible = activeModal == ActiveBrowserModal.GRABBER_MEDIA || activeModal == ActiveBrowserModal.PAGE_RESOURCES,
        initialMediaOnly = activeModal == ActiveBrowserModal.GRABBER_MEDIA,
        featuresManager = browserComponent.featuresManager,
        adBlockEngine = browserComponent.adBlockEngine,
        onDownloadResource = { res ->
            browserComponent.closeModal()
            browserComponent.downloadCapturedResource(res)
        },
        onDismiss = { browserComponent.closeModal() }
    )

    BrowserHistoryDialog(
        visible = activeModal == ActiveBrowserModal.HISTORY,
        featuresManager = browserComponent.featuresManager,
        onOpenUrl = { url ->
            tabWebViewHolder?.navigator?.loadUrl(url) ?: browserComponent.newTab(url)
        },
        onDismiss = { browserComponent.closeModal() }
    )

    ClearBrowsingDataDialog(
        visible = activeModal == ActiveBrowserModal.CLEAR_BROWSING_DATA,
        onConfirmClear = { hist, cookies, cache, storage ->
            browserComponent.featuresManager.clearBrowsingData(
                clearHistory = hist,
                clearCookies = cookies,
                clearCache = cache,
                clearWebStorage = storage,
                activeWebView = tabWebViewHolder?.webView,
            )
        },
        onDismiss = { browserComponent.closeModal() }
    )

    ImportCookiesDialog(
        visible = activeModal == ActiveBrowserModal.IMPORT_COOKIES,
        currentUrl = currentUrl,
        featuresManager = browserComponent.featuresManager,
        onImported = { tabWebViewHolder?.webView?.reload() },
        onDismiss = { browserComponent.closeModal() }
    )

    PageSourceViewerDialog(
        visible = activeModal == ActiveBrowserModal.VIEW_PAGE_SOURCE,
        htmlSource = pageSourceHtml,
        onOpenExternal = {
            tabWebViewHolder?.webView?.let { wv ->
                browserComponent.featuresManager.openPageSourceInExternalViewer(wv)
            }
        },
        onDismiss = { browserComponent.closeModal() }
    )

    SavedPagesOfflineDialog(
        visible = activeModal == ActiveBrowserModal.SAVED_PAGES,
        featuresManager = browserComponent.featuresManager,
        onOpenSavedArchive = { saved ->
            val fileUri = Uri.fromFile(File(saved.filePath)).toString()
            tabWebViewHolder?.navigator?.loadUrl(fileUri) ?: browserComponent.newTab(fileUri)
        },
        onDismiss = { browserComponent.closeModal() }
    )

    SiteSettingsListDialog(
        visible = activeModal == ActiveBrowserModal.SITE_SETTINGS_LIST,
        featuresManager = browserComponent.featuresManager,
        currentUrl = currentUrl,
        onDismiss = { browserComponent.closeModal() }
    )

    UserscriptsManagerDialog(
        visible = activeModal == ActiveBrowserModal.USERSCRIPTS,
        featuresManager = browserComponent.featuresManager,
        onDismiss = { browserComponent.closeModal() }
    )

    if (activeModal == ActiveBrowserModal.ABOUT_ZENIN) {
        ZeninBrowserAboutDialog(
            totalRules = totalActiveRules,
            onDismiss = { browserComponent.closeModal() }
        )
    }
}

@Composable
private fun ZeninStartDashboard(
    modifier: Modifier,
    browserComponent: BrowserComponent,
    totalActiveRules: Int,
    adsBlocked: Long,
    trackersBlocked: Long,
    activeProfile: AdGuardProfile,
    dnsStatus: String,
    onNavigateUrl: (String) -> Unit,
    onOpenBookmarks: () -> Unit,
) {
    val shortcuts by browserComponent.featuresManager.shortcuts.collectAsState()
    var searchInput by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(10.dp))

        // Hero Brand Header
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF4D7CFE), Color(0xFF9B51E0))
                    )
                )
                .padding(14.dp),
            contentAlignment = Alignment.Center,
        ) {
            MyIcon(
                icon = MyIcons.appIcon,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Zen'in Pro Browser",
            fontSize = myTextSizes.xl,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Text(
            text = "AdGuard ${activeProfile.badge} Engine · NextDNS · 16-Thread Download Core",
            fontSize = myTextSizes.xs,
            color = Color(0xFF00E5A3),
        )

        Spacer(Modifier.height(16.dp))

        // Search / URL Hero Box
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF15192B))
                .border(1.dp, Color(0xFF333E68), RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MyIcon(MyIcons.search, null, Modifier.size(20.dp), tint = Color(0xFF70A1FF))
            Spacer(Modifier.width(8.dp))
            MyTextField(
                text = searchInput,
                onTextChange = { searchInput = it },
                placeholder = "Search securely or enter URL...",
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(
                    onGo = {
                        if (searchInput.isNotBlank()) onNavigateUrl(searchInput)
                    }
                ),
            )
            if (searchInput.isNotBlank()) {
                Spacer(Modifier.width(6.dp))
                ActionButton(
                    text = "Go",
                    onClick = { onNavigateUrl(searchInput) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Live AdGuard & NextDNS Shield Telemetry Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF151B30), Color(0xFF1B1635))
                    )
                )
                .border(1.dp, Color(0xFF35416E), RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Real-Time Protection Shield",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = myTextSizes.base,
                )
                Text(
                    text = dnsStatus,
                    fontSize = myTextSizes.xs,
                    color = Color(0xFF00E5A3),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatMetricCard(
                    modifier = Modifier.weight(1f),
                    value = "$adsBlocked",
                    label = "Ads & Requests Blocked",
                    accent = Color(0xFF00E5A3),
                )
                StatMetricCard(
                    modifier = Modifier.weight(1f),
                    value = "$trackersBlocked",
                    label = "Trackers Blocked",
                    accent = Color(0xFF70A1FF),
                )
                StatMetricCard(
                    modifier = Modifier.weight(1f),
                    value = "$totalActiveRules",
                    label = "Compiled Filter Rules",
                    accent = Color(0xFFD1A6FF),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ActionButton(
                    text = "Hosts & Filters (${activeProfile.badge})",
                    modifier = Modifier.weight(1f),
                    onClick = { browserComponent.openModal(ActiveBrowserModal.HOSTS_AND_FILTERS) }
                )
                ActionButton(
                    text = "NextDNS Setup",
                    modifier = Modifier.weight(1f),
                    onClick = { browserComponent.openModal(ActiveBrowserModal.CUSTOM_DNS) }
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // Speed Dial Shortcuts
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Quick Launch Shortcuts",
                fontWeight = FontWeight.Bold,
                fontSize = myTextSizes.sm,
                color = Color(0xFFD5DEFF),
            )
            Text(
                text = "Bookmarks →",
                fontSize = myTextSizes.xs,
                color = Color(0xFF70A1FF),
                modifier = Modifier.clickable { onOpenBookmarks() }
            )
        }
        Spacer(Modifier.height(10.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            shortcuts.chunked(3).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowItems.forEach { sc ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF15192B))
                                .border(1.dp, Color(0xFF262F4F), RoundedCornerShape(12.dp))
                                .clickable { onNavigateUrl(sc.url) }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = sc.title,
                                fontSize = myTextSizes.xs,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    repeat(3 - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Quick Tools Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActionButton(
                text = "Paste & Go",
                modifier = Modifier.weight(1f),
                onClick = {
                    ClipboardUtil.read()?.let { onNavigateUrl(it) }
                }
            )
            ActionButton(
                text = "History",
                modifier = Modifier.weight(1f),
                onClick = { browserComponent.openModal(ActiveBrowserModal.HISTORY) }
            )
            ActionButton(
                text = "Saved Pages",
                modifier = Modifier.weight(1f),
                onClick = { browserComponent.openModal(ActiveBrowserModal.SAVED_PAGES) }
            )
        }
    }
}

@Composable
private fun StatMetricCard(
    modifier: Modifier,
    value: String,
    label: String,
    accent: Color,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF101424))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            fontSize = myTextSizes.base,
            color = accent,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = myTextSizes.xs,
            color = Color(0xFF9AA5C4),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun AddressBar(
    browserComponent: BrowserComponent,
    currentWebViewHolder: WebViewHolder?,
    tabs: ABDMTabs,
    mediaCount: Int,
    modifier: Modifier,
) {
    val webViewState = currentWebViewHolder?.tab?.tabState
    val navigator = currentWebViewHolder?.navigator
    val canGoBack = navigator?.canGoBack ?: false
    val canGoForward = navigator?.canGoForward ?: false
    val currentURL = webViewState?.lastLoadedUrl
    val currentTitle = webViewState?.pageTitle
    var isTabListVisible by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        AddressField(
            currentPageIcon = remember(webViewState?.pageIcon) {
                webViewState?.pageIcon?.asImageBitmap()
            },
            currentPageURL = currentURL,
            currentPageTitle = currentTitle,
            onNewPageRequested = {
                it?.let { text ->
                    val newLink = browserComponent.createNewUrlFor(text)
                    navigator
                        ?.loadUrl(newLink)
                        ?: browserComponent.newTab(newLink)
                }
            }
        )
        Spacer(Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            TransparentIconActionButton(
                enabled = canGoBack,
                icon = MyIcons.back,
                contentDescription = Res.string.back.asStringSource()
            ) {
                navigator?.navigateBack()
            }
            TransparentIconActionButton(
                enabled = canGoForward,
                icon = MyIcons.next,
                contentDescription = Res.string.next.asStringSource()
            ) {
                navigator?.navigateForward()
            }
            webViewState?.let {
                TransparentIconActionButton(
                    icon = if (webViewState.isLoading) MyIcons.close else MyIcons.refresh,
                    contentDescription = Res.string.refresh.asStringSource()
                ) {
                    if (webViewState.isLoading) {
                        navigator?.stopLoading()
                    } else {
                        navigator?.reload()
                    }
                }
            }
            TransparentIconActionButton(
                icon = MyIcons.permission,
                contentDescription = "AdGuard Hosts & Filters".asStringSource()
            ) {
                browserComponent.openModal(ActiveBrowserModal.HOSTS_AND_FILTERS)
            }
            TransparentIconActionButton(
                icon = MyIcons.download,
                contentDescription = "Media Grabber".asStringSource()
            ) {
                browserComponent.openModal(ActiveBrowserModal.GRABBER_MEDIA)
            }
            Spacer(Modifier.weight(1f))
            val shape = RoundedCornerShape(10.dp)
            Box(
                Modifier
                    .sizeIn(mySpacings.thumbSize, mySpacings.thumbSize)
                    .clip(shape)
                    .background(Color(0xFF1B223B))
                    .border(1.dp, Color(0xFF4D7CFE), shape)
                    .clickable(
                        role = Role.Button,
                        onClick = { isTabListVisible = !isTabListVisible },
                    )
                    .padding(vertical = 6.dp, horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${tabs.tabsSize}",
                    maxLines = 1,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            Spacer(Modifier.width(4.dp))
            TransparentIconActionButton(
                MyIcons.menu,
                contentDescription = Res.string.menu.asStringSource()
            ) {
                browserComponent.openMainMenu()
            }
        }
    }
    TabList(
        visible = isTabListVisible,
        onDismissRequest = { isTabListVisible = false },
        onCloseTabRequest = { browserComponent.closeTab(it.tabId) },
        onTabClick = {
            isTabListVisible = false
            browserComponent.switchTab(it.tabId)
        },
        onRequestNewTab = { requestedUrl ->
            isTabListVisible = false
            browserComponent.newTab(
                requestedUrl?.let { browserComponent.createNewUrlFor(it) }
            )
        },
        tabs = tabs,
        currentTabId = currentWebViewHolder?.tab?.tabId,
    )
}

@Composable
private fun TabList(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    tabs: ABDMTabs,
    onRequestNewTab: (String?) -> Unit,
    onTabClick: (ABDMBrowserTab) -> Unit,
    onCloseTabRequest: (ABDMBrowserTab) -> Unit,
    currentTabId: String?,
) {
    val responsiveState = rememberResponsiveDialogState(visible)
    LaunchedEffect(visible) {
        if (visible) responsiveState.show() else responsiveState.hide()
    }
    ResponsiveDialog(
        state = responsiveState,
        onDismiss = onDismissRequest
    ) {
        SheetUI(
            header = {
                SheetHeader(
                    headerTitle = {
                        SheetTitle(myStringResource(Res.string.browser_tabs))
                    },
                    headerActions = {
                        TransparentIconActionButton(
                            MyIcons.paste,
                            Res.string.paste.asStringSource(),
                        ) {
                            onRequestNewTab(ClipboardUtil.read())
                        }
                        TransparentIconActionButton(
                            MyIcons.add,
                            Res.string.add.asStringSource(),
                        ) {
                            onRequestNewTab(null)
                        }
                        TransparentIconActionButton(
                            MyIcons.close,
                            Res.string.close.asStringSource(),
                        ) {
                            onDismissRequest()
                        }
                    }
                )
            }
        ) {
            LazyColumn {
                items(tabs.tabs) { tabItem ->
                    val isSelected = tabItem.tabId == currentTabId
                    Row(
                        modifier = Modifier
                            .heightIn(mySpacings.thumbSize)
                            .ifThen(isSelected) {
                                background(myColors.onBackground / 0.1f)
                            }
                            .clickable { onTabClick(tabItem) }
                            .padding(vertical = 8.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val websiteIconBitmap = remember(tabItem.tabState.pageIcon) {
                            tabItem.tabState.pageIcon?.asImageBitmap()
                        }
                        val modifier = Modifier.size(24.dp)
                        if (websiteIconBitmap != null) {
                            Image(
                                bitmap = websiteIconBitmap,
                                contentDescription = null,
                                modifier = modifier,
                            )
                        } else {
                            MyIcon(
                                MyIcons.earth,
                                contentDescription = null,
                                modifier = modifier,
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = tabItem.tabState.let {
                                it.pageTitle ?: it.lastLoadedUrl
                            }.orEmpty().ifBlank { "Zen'in Start Tab" },
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        WithContentAlpha(0.5f) {
                            TransparentIconActionButton(
                                MyIcons.close,
                                Res.string.close.asStringSource(),
                            ) {
                                onCloseTabRequest(tabItem)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddressField(
    currentPageIcon: ImageBitmap?,
    currentPageURL: String?,
    currentPageTitle: String?,
    onNewPageRequested: (String?) -> Unit,
) {
    val title = currentPageTitle ?: currentPageURL ?: "Search or enter address"
    val url = currentPageURL ?: ""
    val isSecure = remember(url) { url.startsWith("https://") }
    var isEditing by remember { mutableStateOf(false) }
    BackHandler(enabled = isEditing) {
        isEditing = false
    }
    val textFieldInteractionSource = remember { MutableInteractionSource() }
    val isFocused by textFieldInteractionSource.collectIsFocusedAsState()
    LaunchedEffect(isFocused) {
        isEditing = isFocused
    }
    if (isEditing) {
        val fr = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            fr.requestFocus()
        }
        var editingText by remember {
            mutableStateOf(if (url == "about:blank") "" else url)
        }
        MyTextField(
            text = editingText,
            onTextChange = { editingText = it },
            interactionSource = textFieldInteractionSource,
            placeholder = "Search or enter URL...",
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(fr),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(
                onGo = {
                    isEditing = false
                    onNewPageRequested(editingText)
                },
            ),
            end = {
                MyIcon(
                    MyIcons.paste,
                    contentDescription = myStringResource(Res.string.paste),
                    modifier = Modifier
                        .clickable {
                            ClipboardUtil.read()?.let { editingText = it }
                        }
                        .fillMaxHeight()
                        .padding(horizontal = 10.dp),
                )
                MyIcon(
                    MyIcons.clear,
                    contentDescription = null,
                    modifier = Modifier
                        .clickable {
                            if (editingText.isNotEmpty()) {
                                editingText = ""
                            } else {
                                isEditing = false
                            }
                        }
                        .fillMaxHeight()
                        .padding(horizontal = 10.dp),
                )
            },
        )
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(mySpacings.thumbSize)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF171C2E))
                .border(1.dp, Color(0xFF283254), RoundedCornerShape(14.dp))
                .clickable { isEditing = true }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (currentPageIcon != null) {
                Image(
                    bitmap = currentPageIcon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
            } else {
                MyIcon(
                    MyIcons.search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = Color(0xFF70A1FF),
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(
                title,
                modifier = Modifier
                    .weight(1f)
                    .wrapContentHeight(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color.White,
            )
            if (isSecure) {
                Spacer(Modifier.width(8.dp))
                MyIcon(
                    MyIcons.lock,
                    "HTTPS",
                    modifier = Modifier.size(18.dp),
                    tint = Color(0xFF00E5A3),
                )
            }
        }
    }
}

@Composable
private fun ZeninBrowserAboutDialog(
    totalRules: Int,
    onDismiss: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF131726))
                .border(1.dp, Color(0xFF35416E), RoundedCornerShape(22.dp))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF4D7CFE), Color(0xFF9B51E0))
                        )
                    )
                    .padding(12.dp)
            ) {
                MyIcon(MyIcons.appIcon, null, Modifier.fillMaxSize())
            }
            Text("Zen'in Pro", fontSize = myTextSizes.xl, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                "Privacy Browser · AdGuard Pro++/Ultimate · NextDNS · Download Engine",
                fontSize = myTextSizes.xs,
                color = Color(0xFF00E5A3),
            )
            Text(
                "Active Filter Rules Compiled: $totalRules",
                fontSize = myTextSizes.sm,
                color = Color(0xFFD5DEFF),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1B2138))
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Owner & Lead Architect", fontSize = myTextSizes.xs, color = Color(0xFF9AA5C4))
                Spacer(Modifier.height(4.dp))
                Text("ㅤᏞᴀᴢʏㅤᎷꫝʏᴀɴк まやんく", fontWeight = FontWeight.Bold, color = Color.White, fontSize = myTextSizes.base)
                Text("@RoranoaxZORO", color = Color(0xFF70A1FF), fontSize = myTextSizes.sm)
                Spacer(Modifier.height(8.dp))
                ActionButton(
                    text = "Telegram: t.me/RoranoaxZORO",
                    onClick = { uriHandler.openUri("https://t.me/RoranoaxZORO") }
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                ActionButton(text = "Close", onClick = onDismiss)
            }
        }
    }
}
