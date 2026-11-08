package com.abdownloadmanager.android.pages.browser

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import com.abdownloadmanager.android.pages.browser.adblock.ZeninAdBlockEngine
import com.abdownloadmanager.android.pages.browser.features.ZeninBrowserFeaturesManager
import com.abdownloadmanager.android.ui.widget.AccompanistWebChromeClient
import com.abdownloadmanager.android.ui.widget.AccompanistWebViewClient
import com.abdownloadmanager.android.ui.widget.WebContent
import com.abdownloadmanager.android.ui.widget.WebViewNavigator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class ZeninJsBridge(
    private val scope: CoroutineScope,
    private val adBlockEngine: ZeninAdBlockEngine,
    private val featuresManager: ZeninBrowserFeaturesManager,
    private val pageUrlProvider: () -> String?,
) {
    @JavascriptInterface
    fun onElementHidden(selector: String?) {
        if (selector.isNullOrBlank()) return
        scope.launch(Dispatchers.Main) {
            adBlockEngine.addHiddenElementSelector(pageUrlProvider(), selector)
        }
    }

    @JavascriptInterface
    fun onMediaFound(mediaUrl: String?) {
        if (mediaUrl.isNullOrBlank()) return
        scope.launch(Dispatchers.Main) {
            featuresManager.recordDetectedMediaUrl(mediaUrl, pageUrlProvider())
        }
    }
}

class WebViewRegistry(
    private val scope: CoroutineScope,
    private val browserComponent: BrowserComponent,
) : WebViewFactory {
    val viewHolders = mutableMapOf<ABDMBrowserTabId, WebViewHolder>()

    fun onTabsUpdated(
        webViewStates: ABDMTabs,
    ) {
        val webViewStateIds = webViewStates.tabs.map { it.tabId }.toSet()
        for (viewHolderKey in viewHolders.keys.toList()) {
            if (viewHolderKey !in webViewStateIds) {
                removeViewHolder(viewHolderKey)
            }
        }
    }

    fun getWebViewHolder(
        tab: ABDMBrowserTab
    ): WebViewHolder {
        return viewHolders.getOrPut(tab.tabId) {
            WebViewHolder(
                tab = tab,
                navigator = WebViewNavigator(scope),
                webView = null,
                client = ABDMWebViewClient(
                    requestInterceptor = browserComponent.downloadInterceptor,
                    adBlockEngine = browserComponent.adBlockEngine,
                    featuresManager = browserComponent.featuresManager,
                    tabId = tab.tabId,
                    scope = scope,
                ),
                chromeClient = ABDMChromeClient(browserComponent, ::getWebViewHolder),
                webViewFactory = this,
                featuresManager = browserComponent.featuresManager,
            )
        }
    }

    fun removeViewHolder(id: String) {
        viewHolders.remove(id)?.release()
    }

    fun disposeAll() {
        viewHolders.forEach { (_, holder) ->
            holder.release()
        }
        viewHolders.clear()
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun createWebView(
        context: Context,
        tab: ABDMBrowserTab,
    ): ABDMWebView {
        return ABDMWebView(context).apply {
            val webView = this
            val isIncognito = browserComponent.featuresManager.isIncognito.value
            webView.settings.javaScriptEnabled = browserComponent.featuresManager.javaScriptEnabled.value
            webView.settings.domStorageEnabled = !isIncognito
            webView.settings.databaseEnabled = !isIncognito
            webView.settings.setSupportZoom(true)
            webView.settings.builtInZoomControls = true
            webView.settings.displayZoomControls = false
            webView.settings.setSupportMultipleWindows(true)
            webView.settings.mediaPlaybackRequiresUserGesture = false
            webView.settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

            if (isIncognito) {
                webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
                webView.clearHistory()
                webView.clearFormData()
            }

            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, !isIncognito)

            webView.addJavascriptInterface(
                ZeninJsBridge(
                    scope = scope,
                    adBlockEngine = browserComponent.adBlockEngine,
                    featuresManager = browserComponent.featuresManager,
                    pageUrlProvider = { tab.tabState.lastLoadedUrl },
                ),
                "ZeninBridge"
            )

            webView.isLongClickable = true
            webView.setOnLongClickListener {
                val hit = webView.hitTestResult
                if (hit.type == WebView.HitTestResult.SRC_ANCHOR_TYPE ||
                    hit.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE ||
                    hit.type == WebView.HitTestResult.IMAGE_TYPE
                ) {
                    val url = hit.extra ?: return@setOnLongClickListener false
                    browserComponent.onLinkSelected(url, tab)
                    true
                } else {
                    false
                }
            }
            webView.setDownloadListener { url, userAgent, _, _, _ ->
                scope.launch(Dispatchers.Main) {
                    if (!webView.canGoBack() && webView.originalUrl == null) {
                        browserComponent.closeTab(tab.tabId)
                    }
                    browserComponent.downloadInterceptor.onDownloadStart(
                        url,
                        userAgent,
                        webView.originalUrl ?: webView.openedBy,
                        tab,
                    )
                }
            }
            webView.tabId = tab.tabId
        }
    }
}

data class WebViewHolder(
    val tab: ABDMBrowserTab,
    var webView: ABDMWebView? = null,
    val navigator: WebViewNavigator,
    val client: ABDMWebViewClient,
    val chromeClient: ABDMChromeClient,
    private val webViewFactory: WebViewFactory,
    private val featuresManager: ZeninBrowserFeaturesManager,
) {
    fun activate(context: Context): ABDMWebView {
        return if (webView != null) {
            (webView!!).also {
                it.onResume()
            }
        } else {
            webViewFactory.createWebView(context, tab).also { webView = it }
        }
    }

    fun deactivate() {
        if (!featuresManager.backgroundPlayEnabled.value) {
            webView?.onPause()
        }
        tab.tabState.content = WebContent.NavigatorOnly
    }

    fun release() {
        webView?.onPause()
        webView?.destroy()
        webView = null
    }
}

interface WebViewFactory {
    fun createWebView(
        context: Context,
        tab: ABDMBrowserTab,
    ): ABDMWebView
}

class ABDMWebViewClient(
    private val requestInterceptor: DownloadInterceptor,
    private val adBlockEngine: ZeninAdBlockEngine,
    private val featuresManager: ZeninBrowserFeaturesManager,
    private val tabId: String,
    private val scope: CoroutineScope,
) : AccompanistWebViewClient() {

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        val siteSetting = featuresManager.getSiteSettingForUrl(url)
        view.settings.javaScriptEnabled = siteSetting.enableJavaScript
        val cosmeticJs = adBlockEngine.buildCosmeticInjectionScript(url)
        if (cosmeticJs.isNotEmpty()) {
            view.evaluateJavascript(cosmeticJs, null)
        }
        if (featuresManager.darkModeEnabled.value) {
            featuresManager.applyDarkModeToWebView(view, true)
        }
    }

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        val cosmeticJs = adBlockEngine.buildCosmeticInjectionScript(url)
        if (cosmeticJs.isNotEmpty()) {
            view.evaluateJavascript(cosmeticJs, null)
        }
        featuresManager.injectMatchingUserscripts(view, url)
        if (featuresManager.darkModeEnabled.value) {
            featuresManager.applyDarkModeToWebView(view, true)
        }
        featuresManager.recordPageVisit(view.title, url)
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        if (request != null) {
            val currentPageUrl = state.lastLoadedUrl
            val blockResult = adBlockEngine.shouldBlockRequest(
                request = request,
                pageUrl = currentPageUrl,
                tabId = tabId,
            )

            featuresManager.onInterceptedResource(
                request = request,
                pageUrl = currentPageUrl,
                wasBlocked = blockResult.blocked,
                matchedRule = blockResult.matchedRule,
            )

            if (blockResult.blocked) {
                return adBlockEngine.createEmptyBlockedResponse()
            }

            scope.launch(Dispatchers.Main) {
                requestInterceptor.interceptRequest(
                    ABDMWebRequest(
                        url = request.url.toString(),
                        headers = request.requestHeaders,
                        page = view?.originalUrl ?: view?.url ?: currentPageUrl
                    )
                )
            }
        }
        return super.shouldInterceptRequest(view, request)
    }

    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest
    ): Boolean {
        val rawUrl = request.url.toString()

        if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
            val sanitized = adBlockEngine.sanitizeUrlTrackingParameters(rawUrl)
            if (sanitized != rawUrl) {
                view.loadUrl(sanitized)
                return true
            }
            return false
        }

        if (rawUrl.startsWith("intent://")) {
            try {
                val intent = Intent.parseUri(rawUrl, Intent.URI_INTENT_SCHEME)
                val pm = view.context.packageManager
                if (intent.resolveActivity(pm) != null) {
                    view.context.startActivity(intent)
                } else {
                    intent.getStringExtra("browser_fallback_url")?.let {
                        view.loadUrl(it)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return true
        }

        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(rawUrl))
            val pm = view.context.packageManager
            if (intent.resolveActivity(pm) != null) {
                view.context.startActivity(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return true
    }
}

class ABDMChromeClient(
    private val browserComponent: BrowserComponent,
    private val createWebViewHolder: (tab: ABDMBrowserTab) -> WebViewHolder,
) : AccompanistWebChromeClient() {
    override fun onCreateWindow(
        view: WebView?,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message?
    ): Boolean {
        if (view == null) return false
        val currentPage = view.url
        // Block popup windows on AdBlock-enabled sites unless user explicitly whitelisted the site
        if (browserComponent.adBlockEngine.settings.value.globalEnabled &&
            !browserComponent.adBlockEngine.isWebsiteWhitelisted(currentPage) &&
            !isUserGesture
        ) {
            return false
        }

        val transport = (resultMsg?.obj as? WebView.WebViewTransport) ?: return false
        val newTab = browserComponent.newTab(
            id = UUID.randomUUID().toString(),
            switch = true,
            url = null,
            openedBy = (view as? ABDMWebView)?.tabId
        )
        val newWebView = createWebViewHolder(newTab).activate(view.context)
        newWebView.openedBy = view.originalUrl ?: view.url
        transport.webView = newWebView
        resultMsg.sendToTarget()
        return true
    }
}

class ABDMWebView(
    context: Context,
) : WebView(context) {
    var openedBy: String? = null
    var tabId: String? = null

    override fun onWindowVisibilityChanged(visibility: Int) {
        // Keep playing HTML5 media in background when Background Play is enabled
        super.onWindowVisibilityChanged(android.view.View.VISIBLE)
    }
}
