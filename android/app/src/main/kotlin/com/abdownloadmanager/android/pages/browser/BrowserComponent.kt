package com.abdownloadmanager.android.pages.browser

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import androidx.compose.runtime.Stable
import com.abdownloadmanager.android.pages.add.multiple.AddMultiDownloadActivity
import com.abdownloadmanager.android.pages.add.single.AddSingleDownloadActivity
import com.abdownloadmanager.android.pages.browser.adblock.ZeninAdBlockEngine
import com.abdownloadmanager.android.pages.browser.adblock.ZeninDnsEngine
import com.abdownloadmanager.android.pages.browser.bookmark.EditBookmarkState
import com.abdownloadmanager.android.pages.browser.features.ActiveBrowserModal
import com.abdownloadmanager.android.pages.browser.features.CapturedPageResource
import com.abdownloadmanager.android.pages.browser.features.ZeninBrowserFeaturesManager
import com.abdownloadmanager.android.storage.BrowserBookmark
import com.abdownloadmanager.android.storage.BrowserBookmarksStorage
import com.abdownloadmanager.android.ui.MainActivity
import com.abdownloadmanager.android.ui.widget.WebContent
import com.abdownloadmanager.android.ui.widget.WebViewState
import com.abdownloadmanager.resources.Res
import com.abdownloadmanager.shared.pages.adddownload.AddDownloadConfig
import com.abdownloadmanager.shared.pages.adddownload.AddDownloadCredentialsInUiProps
import com.abdownloadmanager.shared.storage.IDNSSettingsStorage
import com.abdownloadmanager.shared.util.BaseComponent
import com.abdownloadmanager.shared.util.ClipboardUtil
import com.abdownloadmanager.shared.util.mvi.ContainsEffects
import com.abdownloadmanager.shared.util.mvi.supportEffects
import com.abdownloadmanager.shared.util.ui.icon.MyIcons
import com.arkivanov.decompose.ComponentContext
import ir.amirab.downloader.downloaditem.http.HttpDownloadCredentials
import ir.amirab.util.HttpUrlUtils
import ir.amirab.util.compose.action.AnAction
import ir.amirab.util.compose.action.MenuItem
import ir.amirab.util.compose.action.buildMenu
import ir.amirab.util.compose.action.simpleAction
import ir.amirab.util.compose.asStringSource
import ir.amirab.util.ifThen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

class BrowserComponent(
    componentContext: ComponentContext,
    private val context: Context,
    private val json: Json,
    private val browserBookmarksStorage: BrowserBookmarksStorage,
    dnsSettingsStorage: IDNSSettingsStorage? = null,
    httpClientProvider: () -> OkHttpClient = { OkHttpClient() },
) : BaseComponent(
    componentContext,
), ContainsEffects<BrowserComponent.Effects> by supportEffects() {

    val adBlockEngine = ZeninAdBlockEngine(
        context = context,
        httpClientProvider = httpClientProvider,
    )

    val dnsEngine = ZeninDnsEngine(
        context = context,
        dnsSettingsStorage = dnsSettingsStorage,
        adBlockEngine = adBlockEngine,
    )

    val featuresManager = ZeninBrowserFeaturesManager(
        context = context,
    )

    val downloadInterceptor = DownloadInterceptor(
        scope,
        onNewDownload = {
            val intent = when (it.size) {
                0 -> null
                1 -> AddSingleDownloadActivity.createIntent(
                    context,
                    AddDownloadConfig.SingleAddConfig(it.first()),
                    json,
                )
                else -> AddMultiDownloadActivity.createIntent(
                    context,
                    AddDownloadConfig.MultipleAddConfig(it),
                    json
                )
            }
            intent?.let { i ->
                sendEffect(Effects.StartActivity(i))
            }
        }
    )

    private val _currentSearchEngine = MutableStateFlow<SearchEngines>(SearchEngines.DuckDuckGo)
    val currentSearchEngine = _currentSearchEngine.asStateFlow()

    fun setSearchEngine(engine: SearchEngines) {
        _currentSearchEngine.value = engine
    }

    val tabs = MutableStateFlow(ABDMTabs.createDefault())
    val bookmarks = browserBookmarksStorage.bookmarksFlow

    private val _activeModal = MutableStateFlow(ActiveBrowserModal.NONE)
    val activeModal = _activeModal.asStateFlow()

    private val _pageSourceHtml = MutableStateFlow("")
    val pageSourceHtml = _pageSourceHtml.asStateFlow()

    private val _mainMenu: MutableStateFlow<MenuItem.SubMenu?> = MutableStateFlow(null)
    val mainMenu = _mainMenu.asStateFlow()

    init {
        // Restore remembered tabs if user previously clicked "Remember tabs & Exit Browser"
        featuresManager.consumeRememberedTabs()?.let { session ->
            val restored = session.urls.map { url ->
                ABDMBrowserTab(
                    tabId = UUID.randomUUID().toString(),
                    tabState = WebViewState(WebContent.Url(url)),
                )
            }
            if (restored.isNotEmpty()) {
                tabs.value = ABDMTabs(
                    tabs = restored,
                    activeTabIndex = session.activeIndex.coerceIn(restored.indices),
                )
            }
        }
    }

    fun openMainMenu() {
        _activeModal.value = ActiveBrowserModal.MAIN_MENU
    }

    fun closeMainMenu() {
        _mainMenu.value = null
        if (_activeModal.value == ActiveBrowserModal.MAIN_MENU) {
            _activeModal.value = ActiveBrowserModal.NONE
        }
    }

    fun openModal(modal: ActiveBrowserModal) {
        _activeModal.value = modal
    }

    fun closeModal() {
        _activeModal.value = ActiveBrowserModal.NONE
    }

    fun downloadCapturedResource(resource: CapturedPageResource) {
        val creds = HttpDownloadCredentials(
            link = resource.url,
            headers = resource.headers,
            downloadPage = resource.pageUrl,
        )
        val intent = AddSingleDownloadActivity.createIntent(
            context,
            AddDownloadConfig.SingleAddConfig(
                AddDownloadCredentialsInUiProps(
                    credentials = creds,
                    extraConfig = AddDownloadCredentialsInUiProps.Configs()
                )
            ),
            json,
        )
        sendEffect(Effects.StartActivity(intent))
    }

    fun handleMenuAction(
        actionKey: String,
        activeWebView: WebView?,
    ) {
        val currentTab = tabs.value.activeTab
        val currentUrl = currentTab?.tabState?.lastLoadedUrl
        val currentTitle = currentTab?.tabState?.pageTitle

        when (actionKey) {
            "new_tab" -> {
                closeModal()
                newTab(url = null, switch = true)
            }
            "toggle_incognito" -> {
                closeModal()
                val nextIncognito = !featuresManager.isIncognito.value
                featuresManager.setIncognitoMode(nextIncognito)
                newTab(url = null, switch = true)
            }
            "return_to_dm" -> {
                closeModal()
                val intent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                sendEffect(Effects.StartActivity(intent))
            }
            "password_manager" -> openModal(ActiveBrowserModal.PASSWORD_MANAGER)
            "connect_dns" -> openModal(ActiveBrowserModal.CUSTOM_DNS)
            "remember_tabs_exit" -> {
                closeModal()
                val allUrls = tabs.value.tabs.mapNotNull { it.tabState.lastLoadedUrl }
                featuresManager.saveRememberedTabs(allUrls, tabs.value.activeTabIndex)
                sendEffect(Effects.ExitBrowser)
            }
            "exit_browser" -> {
                closeModal()
                sendEffect(Effects.ExitBrowser)
            }
            "toggle_block_ads" -> {
                adBlockEngine.setGlobalEnabled(!adBlockEngine.settings.value.globalEnabled)
                activeWebView?.reload()
            }
            "toggle_site_adblock" -> {
                if (!currentUrl.isNullOrBlank()) {
                    adBlockEngine.toggleWebsiteWhitelist(currentUrl)
                    activeWebView?.reload()
                }
            }
            "hide_element" -> {
                closeModal()
                if (activeWebView != null) {
                    featuresManager.activateElementHiderPicker(activeWebView)
                }
            }
            "toggle_capture_media" -> {
                featuresManager.setCaptureMediaEnabled(!featuresManager.captureMediaEnabled.value)
            }
            "view_resources" -> openModal(ActiveBrowserModal.PAGE_RESOURCES)
            "translate_page" -> {
                closeModal()
                if (!currentUrl.isNullOrBlank()) {
                    val encoded = URLEncoder.encode(currentUrl, StandardCharsets.UTF_8.toString())
                    val translateUrl = "https://translate.google.com/translate?sl=auto&tl=en&u=$encoded"
                    activeWebView?.loadUrl(translateUrl) ?: newTab(translateUrl)
                }
            }
            "open_in_app" -> {
                closeModal()
                if (!currentUrl.isNullOrBlank()) {
                    runCatching {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        sendEffect(Effects.StartActivity(Intent.createChooser(intent, "Open in App")))
                    }
                }
            }
            "scan_qr" -> {
                closeModal()
                sendEffect(Effects.LaunchQrScanner)
            }
            "import_cookies" -> openModal(ActiveBrowserModal.IMPORT_COOKIES)
            "external_login" -> {
                closeModal()
                if (!currentUrl.isNullOrBlank()) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    sendEffect(Effects.StartActivity(Intent.createChooser(intent, "Login using External Browser")))
                }
            }
            "clone_tab" -> {
                closeModal()
                newTab(url = currentUrl, switch = true, openedBy = currentTab?.tabId)
            }
            "grabber" -> openModal(ActiveBrowserModal.GRABBER_MEDIA)
            "history" -> openModal(ActiveBrowserModal.HISTORY)
            "open_clipboard_links" -> {
                closeModal()
                val clip = ClipboardUtil.read()
                val urls = featuresManager.extractUrlsFromText(clip)
                if (urls.isNotEmpty()) {
                    urls.forEach { u -> newTab(url = u, switch = true) }
                } else if (!clip.isNullOrBlank()) {
                    newTab(url = createNewUrlFor(clip), switch = true)
                }
            }
            "clear_data" -> openModal(ActiveBrowserModal.CLEAR_BROWSING_DATA)
            "read_aloud" -> {
                closeModal()
                if (activeWebView != null) {
                    featuresManager.toggleReadPageAloud(activeWebView)
                }
            }
            "force_fullscreen" -> {
                closeModal()
                if (activeWebView != null) {
                    featuresManager.forceVideoFullscreen(activeWebView)
                }
            }
            "enter_pip" -> {
                closeModal()
                sendEffect(Effects.EnterPictureInPicture)
            }
            "toggle_background_play" -> {
                featuresManager.setBackgroundPlayEnabled(!featuresManager.backgroundPlayEnabled.value)
            }
            "view_source" -> {
                if (activeWebView != null) {
                    featuresManager.fetchPageHtmlSource(activeWebView) { html ->
                        _pageSourceHtml.value = html
                        openModal(ActiveBrowserModal.VIEW_PAGE_SOURCE)
                    }
                } else {
                    closeModal()
                }
            }
            "view_source_with" -> {
                closeModal()
                if (activeWebView != null) {
                    featuresManager.openPageSourceInExternalViewer(activeWebView)
                }
            }
            "print_page" -> {
                closeModal()
                sendEffect(Effects.PrintPage)
            }
            "save_page" -> {
                closeModal()
                if (activeWebView != null) {
                    featuresManager.saveCurrentPageOffline(activeWebView)
                }
            }
            "saved_pages" -> openModal(ActiveBrowserModal.SAVED_PAGES)
            "find_in_page" -> {
                closeModal()
                featuresManager.setFindInPageVisible(true, activeWebView)
            }
            "copy_link" -> {
                closeModal()
                currentUrl?.let { ClipboardUtil.copy(it) }
            }
            "copy_all_tab_links" -> {
                closeModal()
                val joined = tabs.value.tabs.mapNotNull { it.tabState.lastLoadedUrl }.joinToString("\n")
                if (joined.isNotBlank()) ClipboardUtil.copy(joined)
            }
            "add_to_homescreen" -> {
                closeModal()
                if (!currentUrl.isNullOrBlank()) {
                    featuresManager.pinPageToAndroidHomeScreen(
                        title = currentTitle ?: currentUrl,
                        url = currentUrl,
                        favicon = currentTab.tabState.pageIcon,
                    )
                }
            }
            "bookmarks" -> {
                closeModal()
                setShowBookmarkList(true)
            }
            "share_link" -> {
                closeModal()
                currentUrl?.let { sendEffect(Effects.ShareText(it)) }
            }
            "share_to_browser" -> {
                closeModal()
                if (!currentUrl.isNullOrBlank()) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))
                    sendEffect(Effects.StartActivity(Intent.createChooser(intent, "Share link to browser")))
                }
            }
            "toggle_dark_mode" -> {
                featuresManager.setDarkMode(!featuresManager.darkModeEnabled.value, activeWebView)
            }
            "toggle_desktop_site" -> {
                featuresManager.setDesktopSite(!featuresManager.desktopSiteEnabled.value, activeWebView)
            }
            "toggle_javascript" -> {
                featuresManager.setJavaScriptEnabled(!featuresManager.javaScriptEnabled.value, activeWebView)
            }
            "add_bookmark" -> {
                closeModal()
                if (!currentUrl.isNullOrBlank()) {
                    promptAddBookmark(BrowserBookmark(url = currentUrl, title = currentTitle.orEmpty()))
                }
            }
            "add_to_shortcuts" -> {
                closeModal()
                if (!currentUrl.isNullOrBlank()) {
                    featuresManager.addShortcut(title = currentTitle ?: currentUrl, url = currentUrl)
                }
            }
            "bookmark_all_tabs" -> {
                closeModal()
                tabs.value.tabs.forEach { t ->
                    val u = t.tabState.lastLoadedUrl
                    if (!u.isNullOrBlank() && !isBookmarked(u)) {
                        addToBookmarks(BrowserBookmark(url = u, title = t.tabState.pageTitle ?: u), null)
                    }
                }
            }
            "reader_mode" -> {
                closeModal()
                if (activeWebView != null) {
                    featuresManager.extractReaderMode(activeWebView)
                }
            }
            "toggle_force_context_menu" -> {
                val setting = featuresManager.getSiteSettingForUrl(currentUrl)
                if (setting.host.isNotBlank()) {
                    featuresManager.updateSiteSetting(setting.copy(forceContextMenu = !setting.forceContextMenu))
                }
            }
            "manage_adblock_filters" -> openModal(ActiveBrowserModal.HOSTS_AND_FILTERS)
            "site_settings_list" -> openModal(ActiveBrowserModal.SITE_SETTINGS_LIST)
            "userscripts" -> openModal(ActiveBrowserModal.USERSCRIPTS)
            "app_settings" -> {
                closeModal()
                val intent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                sendEffect(Effects.StartActivity(intent))
            }
            "about" -> openModal(ActiveBrowserModal.ABOUT_ZENIN)
        }
    }

    fun newTab(
        url: String? = ABDMBrowserTab.blankPage,
        switch: Boolean = true,
        id: String = UUID.randomUUID().toString(),
        openedBy: ABDMBrowserTabId? = null,
    ): ABDMBrowserTab {
        val browserTab = ABDMBrowserTab(
            tabId = id,
            tabState = WebViewState(WebContent.fromNullableUrl(url)),
        )
        tabs.update { currentTabState ->
            val newTabPosition = openedBy?.let {
                currentTabState.tabs
                    .indexOfFirst { it.tabId == openedBy }
                    .takeIf { it >= 0 }
                    ?.plus(1)
            } ?: currentTabState.tabs.size
            val newItems = buildList {
                addAll(currentTabState.tabs)
                add(newTabPosition, browserTab)
            }
            val newIndex = if (switch) {
                newTabPosition
            } else {
                currentTabState.activeTabIndex
            }
            currentTabState.copy(
                tabs = newItems,
                activeTabIndex = newIndex
            )
        }
        return browserTab
    }

    fun closeTab(tabId: ABDMBrowserTabId) {
        adBlockEngine.resetTabBlockedCount(tabId)
        tabs.update {
            val newItems = it.tabs.filterNot { it.tabId == tabId }
            it.copy(
                tabs = newItems,
                activeTabIndex = runCatching {
                    it.activeTabIndex.coerceIn(newItems.indices)
                }.getOrElse { -1 },
            )
        }
    }

    fun addToBookmarks(
        bookmark: BrowserBookmark,
        replaceWith: BrowserBookmark?,
    ) {
        browserBookmarksStorage.bookmarksFlow.update { currentBookmarks ->
            if (replaceWith != null) {
                currentBookmarks.map { item ->
                    item.ifThen(item == replaceWith) {
                        bookmark
                    }
                }
            } else {
                currentBookmarks.plus(bookmark)
            }
        }
    }

    private val _showBookmarkList: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val showBookmarkList = _showBookmarkList.asStateFlow()
    fun setShowBookmarkList(show: Boolean) {
        _showBookmarkList.value = show
    }

    private val _editBookmarkState = MutableStateFlow<EditBookmarkState?>(null)
    val editBookmarkState = _editBookmarkState.asStateFlow()
    fun promptAddBookmark(
        bookmark: BrowserBookmark,
    ) {
        _editBookmarkState.value = EditBookmarkState(
            initialValue = bookmark,
            editMode = false,
        )
    }

    fun promptEditBookmark(
        bookmark: BrowserBookmark
    ) {
        _editBookmarkState.value = EditBookmarkState(
            initialValue = bookmark,
            editMode = true,
        )
    }

    fun dismissEditBookmark() {
        _editBookmarkState.value = null
    }

    fun removeBookmark(url: String) {
        browserBookmarksStorage.bookmarksFlow.update {
            it.filterNot { bookmark -> bookmark.url == url }
        }
    }

    fun clearBookmarks() {
        browserBookmarksStorage.bookmarksFlow.value = emptyList()
    }

    fun isBookmarked(url: String): Boolean {
        return browserBookmarksStorage.bookmarksFlow.value.find {
            it.url == url
        } != null
    }

    fun switchTab(tabId: ABDMBrowserTabId) {
        tabs.update {
            val tabIndex = it.tabs.indexOfFirst { it.tabId == tabId }
            val newIndex = if (tabIndex < 0) {
                it.activeTabIndex
            } else {
                tabIndex
            }
            it.copy(
                activeTabIndex = newIndex,
            )
        }
    }

    private val websiteAndTLD by lazy {
        """^[\w.-]+\.[a-zA-Z]{2,}(:\d{1,5})?$""".toRegex()
    }

    fun createNewUrlFor(urlOrSearch: String): String {
        val value = urlOrSearch.trim()
        if (value.startsWith("file://")) return value
        if (value.contains(' ')) {
            return createSearchEngineUrl(value)
        }
        if (HttpUrlUtils.isValidUrl(value)) {
            return adBlockEngine.sanitizeUrlTrackingParameters(value)
        }
        if (websiteAndTLD.matches(value)) {
            val withHttpScheme = "https://$value"
            if (HttpUrlUtils.isValidUrl(withHttpScheme)) {
                return withHttpScheme
            }
        }
        return createSearchEngineUrl(value)
    }

    private fun createSearchEngineUrl(searchText: String): String {
        return _currentSearchEngine.value.createSearchUrl(searchText)
    }

    val contextMenu: MutableStateFlow<MenuItem.SubMenu?> = MutableStateFlow(null)

    fun closeContextMenu() {
        contextMenu.value = null
    }

    fun onLinkSelected(
        link: String,
        tab: ABDMBrowserTab,
    ) {
        contextMenu.value = MenuItem.SubMenu(
            title = link.asStringSource(),
            items = buildMenu {
                +simpleAction(
                    Res.string.browser_open_in_new_tab.asStringSource(),
                    MyIcons.file,
                ) {
                    newTab(
                        url = link,
                        switch = true,
                        openedBy = tab.tabId,
                    )
                }
                +simpleAction(
                    Res.string.browser_open_in_new_background_tab.asStringSource(),
                    MyIcons.file,
                ) {
                    newTab(
                        url = link,
                        switch = false,
                        openedBy = tab.tabId,
                    )
                }
                +simpleAction(
                    Res.string.share.asStringSource(),
                    MyIcons.share,
                ) {
                    sendEffect(Effects.ShareText(link))
                }
                +simpleAction(
                    Res.string.copy.asStringSource(),
                    MyIcons.copy,
                ) {
                    ClipboardUtil.copy(link)
                }
                +simpleAction(
                    Res.string.download.asStringSource(),
                    MyIcons.download,
                ) {
                    downloadInterceptor.onDownloadStart(
                        url = link,
                        userAgent = null,
                        page = null,
                        tab = tab,
                    )
                }
                if (isBookmarked(link)) {
                    +createRemoveFromBookmarkAction(link)
                } else {
                    +createAddToBookmarkAction(link, null)
                }
            }
        )
    }

    fun createNewTabAction(): AnAction {
        return simpleAction(
            title = Res.string.browser_new_tab.asStringSource(),
            icon = MyIcons.file,
        ) {
            newTab(
                url = null,
                switch = true,
            )
        }
    }

    fun createAddToBookmarkAction(
        url: String,
        title: String?,
    ): AnAction {
        return simpleAction(
            Res.string.browser_add_to_bookmarks.asStringSource(),
            MyIcons.add,
        ) {
            promptAddBookmark(
                BrowserBookmark(
                    url = url,
                    title = title.orEmpty(),
                )
            )
        }
    }

    fun createRemoveFromBookmarkAction(
        url: String,
    ): AnAction {
        return simpleAction(
            Res.string.browser_remove_from_bookmarks.asStringSource(),
            MyIcons.remove,
        ) {
            removeBookmark(url)
        }
    }

    fun createCloseTabAction(tab: ABDMBrowserTab): AnAction {
        return simpleAction(
            title = Res.string.browser_close_tab.asStringSource(),
            icon = MyIcons.close,
        ) {
            closeTab(tab.tabId)
        }
    }

    fun createShowBookmarksAction(): AnAction {
        return simpleAction(
            title = Res.string.browser_bookmarks.asStringSource(),
            icon = MyIcons.hearth,
        ) {
            setShowBookmarkList(true)
        }
    }

    sealed interface Effects {
        data class StartActivity(
            val intent: Intent
        ) : Effects

        data class ShareText(
            val text: String,
        ) : Effects

        data object ExitBrowser : Effects
        data object EnterPictureInPicture : Effects
        data object PrintPage : Effects
        data object LaunchQrScanner : Effects
    }
}

typealias ABDMBrowserTabId = String

@Stable
data class ABDMBrowserTab(
    val tabId: ABDMBrowserTabId,
    val tabState: WebViewState,
) {
    companion object {
        fun createDefaultTab(
            page: String = blankPage
        ) = ABDMBrowserTab(
            tabId = UUID.randomUUID().toString(),
            tabState = WebViewState(WebContent.Url(page)),
        )

        val blankPage = "about:blank"
    }
}

@Stable
data class ABDMTabs(
    val tabs: List<ABDMBrowserTab>,
    val activeTabIndex: Int,
) {
    val tabsSize = tabs.size
    val activeTab get() = if (activeTabIndex == -1) null else tabs.getOrNull(activeTabIndex)

    companion object {
        fun createDefault(): ABDMTabs = ABDMTabs(
            listOf(),
            -1,
        )
    }
}
