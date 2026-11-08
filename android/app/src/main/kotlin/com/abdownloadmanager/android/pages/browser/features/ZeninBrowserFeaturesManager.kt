package com.abdownloadmanager.android.pages.browser.features

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.print.PrintAttributes
import android.print.PrintManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.util.Rational
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.core.content.FileProvider
import com.abdownloadmanager.android.pages.browser.BrowserActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Serializable
data class SavedCredential(
    val id: String = UUID.randomUUID().toString(),
    val domain: String,
    val username: String,
    val encryptedPasswordBase64: String,
    val ivBase64: String,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class BrowserHistoryItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val url: String,
    val timestamp: Long = System.currentTimeMillis(),
) {
    fun formattedTime(): String {
        val sdf = SimpleDateFormat("MMM dd, hh:mm a", Locale.US)
        return sdf.format(Date(timestamp))
    }
}

@Serializable
data class SavedPageItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val url: String,
    val filePath: String,
    val sizeBytes: Long,
    val savedAt: Long = System.currentTimeMillis(),
) {
    fun formattedDate(): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.US)
        return sdf.format(Date(savedAt))
    }
}

@Serializable
data class UserscriptItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String,
    val matchPattern: String = "*",
    val code: String,
    val enabled: Boolean = true,
    val runAtStart: Boolean = false,
)

@Serializable
data class SiteSettingItem(
    val host: String,
    val enableJavaScript: Boolean = true,
    val disableAdBlock: Boolean = false,
    val requestDesktopSite: Boolean = false,
    val forceContextMenu: Boolean = false,
    val darkMode: Boolean = true,
)

@Serializable
data class BrowserShortcutItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val url: String,
)

@Serializable
data class RememberedTabSession(
    val urls: List<String> = emptyList(),
    val activeIndex: Int = 0,
)

enum class PageResourceType(val label: String) {
    MEDIA_VIDEO("Video"),
    MEDIA_AUDIO("Audio"),
    MEDIA_HLS("HLS/Stream"),
    IMAGE("Image"),
    SCRIPT("Script"),
    STYLESHEET("CSS"),
    DOCUMENT("Document/XHR"),
    BLOCKED_AD("Blocked Ad/Tracker"),
}

data class CapturedPageResource(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val pageUrl: String?,
    val type: PageResourceType,
    val mimeOrRule: String = "",
    val headers: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis(),
) {
    val fileNameGuess: String
        get() {
            val pathSeg = url.substringBefore('?').substringAfterLast('/')
            return pathSeg.ifEmpty {
                when (type) {
                    PageResourceType.MEDIA_HLS -> "stream_playlist.m3u8"
                    PageResourceType.MEDIA_VIDEO -> "captured_video.mp4"
                    PageResourceType.MEDIA_AUDIO -> "captured_audio.mp3"
                    else -> "resource"
                }
            }
        }
}

class ZeninBrowserFeaturesManager(
    private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val storageDir = File(context.filesDir, "zenin_browser_data").apply { mkdirs() }
    private val savedPagesDir = File(storageDir, "saved_pages").apply { mkdirs() }
    private val historyFile = File(storageDir, "history.json")
    private val passwordsFile = File(storageDir, "passwords_vault.json")
    private val savedPagesFile = File(storageDir, "saved_pages.json")
    private val userscriptsFile = File(storageDir, "userscripts.json")
    private val siteSettingsFile = File(storageDir, "site_settings.json")
    private val shortcutsFile = File(storageDir, "shortcuts.json")
    private val rememberedTabsFile = File(storageDir, "remembered_tabs.json")

    // Reactive states for UI
    private val _isIncognito = MutableStateFlow(false)
    val isIncognito: StateFlow<Boolean> = _isIncognito.asStateFlow()

    private val _captureMediaEnabled = MutableStateFlow(true)
    val captureMediaEnabled: StateFlow<Boolean> = _captureMediaEnabled.asStateFlow()

    private val _darkModeEnabled = MutableStateFlow(true)
    val darkModeEnabled: StateFlow<Boolean> = _darkModeEnabled.asStateFlow()

    private val _desktopSiteEnabled = MutableStateFlow(false)
    val desktopSiteEnabled: StateFlow<Boolean> = _desktopSiteEnabled.asStateFlow()

    private val _javaScriptEnabled = MutableStateFlow(true)
    val javaScriptEnabled: StateFlow<Boolean> = _javaScriptEnabled.asStateFlow()

    private val _backgroundPlayEnabled = MutableStateFlow(false)
    val backgroundPlayEnabled: StateFlow<Boolean> = _backgroundPlayEnabled.asStateFlow()

    private val _readerModeActive = MutableStateFlow(false)
    val readerModeActive: StateFlow<Boolean> = _readerModeActive.asStateFlow()

    private val _readerModeContent = MutableStateFlow<Pair<String, String>?>(null)
    val readerModeContent: StateFlow<Pair<String, String>?> = _readerModeContent.asStateFlow()

    private val _elementPickerActive = MutableStateFlow(false)
    val elementPickerActive: StateFlow<Boolean> = _elementPickerActive.asStateFlow()

    private val _findInPageVisible = MutableStateFlow(false)
    val findInPageVisible: StateFlow<Boolean> = _findInPageVisible.asStateFlow()

    private val _findMatchStatus = MutableStateFlow(0 to 0)
    val findMatchStatus: StateFlow<Pair<Int, Int>> = _findMatchStatus.asStateFlow()

    private val _isSpeakingTts = MutableStateFlow(false)
    val isSpeakingTts: StateFlow<Boolean> = _isSpeakingTts.asStateFlow()

    private val _history = MutableStateFlow<List<BrowserHistoryItem>>(emptyList())
    val history: StateFlow<List<BrowserHistoryItem>> = _history.asStateFlow()

    private val _credentials = MutableStateFlow<List<SavedCredential>>(emptyList())
    val credentials: StateFlow<List<SavedCredential>> = _credentials.asStateFlow()

    private val _savedPages = MutableStateFlow<List<SavedPageItem>>(emptyList())
    val savedPages: StateFlow<List<SavedPageItem>> = _savedPages.asStateFlow()

    private val _userscripts = MutableStateFlow<List<UserscriptItem>>(emptyList())
    val userscripts: StateFlow<List<UserscriptItem>> = _userscripts.asStateFlow()

    private val _siteSettings = MutableStateFlow<Map<String, SiteSettingItem>>(emptyMap())
    val siteSettings: StateFlow<Map<String, SiteSettingItem>> = _siteSettings.asStateFlow()

    private val _shortcuts = MutableStateFlow<List<BrowserShortcutItem>>(emptyList())
    val shortcuts: StateFlow<List<BrowserShortcutItem>> = _shortcuts.asStateFlow()

    private val _capturedResources = MutableStateFlow<List<CapturedPageResource>>(emptyList())
    val capturedResources: StateFlow<List<CapturedPageResource>> = _capturedResources.asStateFlow()

    private var ttsEngine: TextToSpeech? = null

    init {
        scope.launch {
            loadAllPersistedData()
        }
    }

    private fun loadAllPersistedData() {
        if (historyFile.exists()) {
            runCatching { _history.value = json.decodeFromString(historyFile.readText()) }
        }
        if (passwordsFile.exists()) {
            runCatching { _credentials.value = json.decodeFromString(passwordsFile.readText()) }
        }
        if (savedPagesFile.exists()) {
            runCatching { _savedPages.value = json.decodeFromString(savedPagesFile.readText()) }
        }
        if (siteSettingsFile.exists()) {
            runCatching { _siteSettings.value = json.decodeFromString(siteSettingsFile.readText()) }
        }
        if (userscriptsFile.exists()) {
            runCatching { _userscripts.value = json.decodeFromString(userscriptsFile.readText()) }
        } else {
            val defaults = defaultBuiltInUserscripts()
            _userscripts.value = defaults
            saveUserscripts(defaults)
        }
        if (shortcutsFile.exists()) {
            runCatching { _shortcuts.value = json.decodeFromString(shortcutsFile.readText()) }
        } else {
            val defaults = listOf(
                BrowserShortcutItem(title = "DuckDuckGo", url = "https://duckduckgo.com"),
                BrowserShortcutItem(title = "GitHub", url = "https://github.com"),
                BrowserShortcutItem(title = "YouTube", url = "https://m.youtube.com"),
                BrowserShortcutItem(title = "NextDNS Test", url = "https://test.nextdns.io"),
                BrowserShortcutItem(title = "AdBlock Tester", url = "https://d3ward.github.io/toolz/adblock.html"),
                BrowserShortcutItem(title = "SpeedTest", url = "https://fast.com"),
            )
            _shortcuts.value = defaults
            saveShortcuts(defaults)
        }
    }

    private fun defaultBuiltInUserscripts(): List<UserscriptItem> {
        return listOf(
            UserscriptItem(
                id = "zenin_yt_cleaner",
                name = "YouTube Ad-Skipper & Sponsor Cleaner",
                description = "Auto-clicks YouTube skip buttons, fast-forwards video ad overlays, and hides promoted feed cards",
                matchPattern = "*youtube.com*",
                enabled = true,
                code = """
                    (function() {
                        setInterval(function() {
                            var skipBtn = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button');
                            if (skipBtn) { skipBtn.click(); }
                            var adShowing = document.querySelector('.ad-showing video');
                            if (adShowing && !isNaN(adShowing.duration) && adShowing.duration > 0) {
                                adShowing.currentTime = adShowing.duration;
                            }
                            var overlays = document.querySelectorAll('.ytp-ad-overlay-container, ytd-promoted-sparkles-web-renderer, ytm-promoted-sparkles-web-renderer');
                            for (var i = 0; i < overlays.length; i++) {
                                overlays[i].style.display = 'none';
                            }
                        }, 800);
                    })();
                """.trimIndent(),
            ),
            UserscriptItem(
                id = "zenin_context_unlocker",
                name = "Right-Click, Copy & Selection Unlocker",
                description = "Restores text selection, copy/paste, and long-press context menus on restricted websites",
                matchPattern = "*",
                enabled = true,
                code = """
                    (function() {
                        var css = '* { -webkit-touch-callout: default !important; -webkit-user-select: text !important; user-select: text !important; }';
                        var s = document.createElement('style');
                        s.innerHTML = css;
                        (document.head || document.documentElement).appendChild(s);
                        ['contextmenu', 'copy', 'cut', 'selectstart', 'dragstart'].forEach(function(ev) {
                            window.addEventListener(ev, function(e) { e.stopPropagation(); }, true);
                        });
                    })();
                """.trimIndent(),
            ),
            UserscriptItem(
                id = "zenin_media_detector",
                name = "HTML5 Video & Audio Stream Extractor",
                description = "Detects dynamically attached HTML5 <video> and <audio> sources for Zen'in Grabber",
                matchPattern = "*",
                enabled = true,
                code = """
                    (function() {
                        function scanMedia() {
                            var els = document.querySelectorAll('video, audio, video source, audio source');
                            for (var i = 0; i < els.length; i++) {
                                var src = els[i].currentSrc || els[i].src;
                                if (src && src.indexOf('http') === 0 && window.ZeninBridge) {
                                    window.ZeninBridge.onMediaFound(src);
                                }
                            }
                        }
                        setInterval(scanMedia, 2000);
                        scanMedia();
                    })();
                """.trimIndent(),
            )
        )
    }

    // ==================== 1. Incognito Mode ====================

    fun setIncognitoMode(enabled: Boolean) {
        _isIncognito.value = enabled
        val cookieManager = CookieManager.getInstance()
        if (enabled) {
            cookieManager.flush()
        } else {
            cookieManager.removeSessionCookies(null)
            cookieManager.flush()
        }
    }

    // ==================== 2. Encrypted Password Manager (AES-GCM) ====================

    private fun getOrCreateAesKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "ZeninProVaultMasterKey"
        val existing = keyStore.getEntry(alias, null) as? KeyStore.Entry
        if (existing is KeyStore.SecretKeyEntry) {
            return existing.secretKey
        }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    fun encryptPassword(plainText: String): Pair<String, String> {
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateAesKey())
            val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
            val encrypted = Base64.encodeToString(cipher.doFinal(plainText.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
            encrypted to iv
        }.getOrElse {
            Base64.encodeToString(plainText.toByteArray(Charsets.UTF_8), Base64.NO_WRAP) to "FALLBACK"
        }
    }

    fun decryptPassword(credential: SavedCredential): String {
        return runCatching {
            if (credential.ivBase64 == "FALLBACK") {
                return String(Base64.decode(credential.encryptedPasswordBase64, Base64.NO_WRAP), Charsets.UTF_8)
            }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val ivBytes = Base64.decode(credential.ivBase64, Base64.NO_WRAP)
            val spec = GCMParameterSpec(128, ivBytes)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateAesKey(), spec)
            val decoded = Base64.decode(credential.encryptedPasswordBase64, Base64.NO_WRAP)
            String(cipher.doFinal(decoded), Charsets.UTF_8)
        }.getOrDefault("")
    }

    fun saveCredential(domain: String, username: String, rawPassword: String) {
        val cleanDomain = domain.trim().lowercase(Locale.ROOT).removePrefix("www.")
        if (cleanDomain.isEmpty() || username.isBlank()) return
        val (enc, iv) = encryptPassword(rawPassword)
        val item = SavedCredential(
            domain = cleanDomain,
            username = username.trim(),
            encryptedPasswordBase64 = enc,
            ivBase64 = iv,
        )
        _credentials.update { list ->
            val filtered = list.filterNot { it.domain == cleanDomain && it.username.equals(username.trim(), ignoreCase = true) }
            val updated = listOf(item) + filtered
            runCatching { passwordsFile.writeText(json.encodeToString(updated)) }
            updated
        }
    }

    fun deleteCredential(id: String) {
        _credentials.update { list ->
            val updated = list.filterNot { it.id == id }
            runCatching { passwordsFile.writeText(json.encodeToString(updated)) }
            updated
        }
    }

    fun autofillCredentialsIntoWebView(webView: WebView, currentUrl: String?) {
        val host = currentUrl?.let { runCatching { Uri.parse(it).host?.lowercase(Locale.ROOT)?.removePrefix("www.") }.getOrNull() }
            ?: return
        val cred = _credentials.value.firstOrNull { host.endsWith(it.domain) } ?: return
        val pass = decryptPassword(cred).replace("\\", "\\\\").replace("'", "\\'")
        val user = cred.username.replace("\\", "\\\\").replace("'", "\\'")
        val js = """
            (function() {
                var passInputs = document.querySelectorAll('input[type="password"]');
                if (passInputs.length > 0) {
                    passInputs[0].value = '$pass';
                    passInputs[0].dispatchEvent(new Event('input', { bubbles: true }));
                }
                var userInputs = document.querySelectorAll('input[type="email"], input[type="text"], input[name*="user"], input[name*="login"]');
                if (userInputs.length > 0) {
                    userInputs[0].value = '$user';
                    userInputs[0].dispatchEvent(new Event('input', { bubbles: true }));
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    // ==================== 3. Media Grabber & Network Resource Inspector ====================

    fun setCaptureMediaEnabled(enabled: Boolean) {
        _captureMediaEnabled.value = enabled
    }

    fun onInterceptedResource(
        request: WebResourceRequest,
        pageUrl: String?,
        wasBlocked: Boolean,
        matchedRule: String?,
    ) {
        val url = request.url?.toString() ?: return
        if (url.startsWith("data:") || url.startsWith("about:")) return

        if (wasBlocked) {
            recordResource(
                CapturedPageResource(
                    url = url,
                    pageUrl = pageUrl,
                    type = PageResourceType.BLOCKED_AD,
                    mimeOrRule = matchedRule ?: "Blocked",
                    headers = request.requestHeaders,
                )
            )
            return
        }

        val lowerPath = url.substringBefore('?').lowercase(Locale.ROOT)
        val accept = request.requestHeaders["Accept"]?.lowercase(Locale.ROOT).orEmpty()

        val resourceType = when {
            lowerPath.endsWith(".m3u8") || lowerPath.endsWith(".mpd") || lowerPath.contains(".m3u8?") || accept.contains("mpegurl") ->
                PageResourceType.MEDIA_HLS
            lowerPath.endsWith(".mp4") || lowerPath.endsWith(".webm") || lowerPath.endsWith(".mkv") || lowerPath.endsWith(".mov") || accept.startsWith("video/") ->
                PageResourceType.MEDIA_VIDEO
            lowerPath.endsWith(".mp3") || lowerPath.endsWith(".m4a") || lowerPath.endsWith(".aac") || lowerPath.endsWith(".ogg") || lowerPath.endsWith(".flac") || lowerPath.endsWith(".wav") || accept.startsWith("audio/") ->
                PageResourceType.MEDIA_AUDIO
            lowerPath.endsWith(".js") || accept.contains("javascript") ->
                PageResourceType.SCRIPT
            lowerPath.endsWith(".css") || accept.contains("text/css") ->
                PageResourceType.STYLESHEET
            lowerPath.endsWith(".png") || lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg") || lowerPath.endsWith(".webp") || lowerPath.endsWith(".gif") || lowerPath.endsWith(".svg") || accept.startsWith("image/") ->
                PageResourceType.IMAGE
            else -> PageResourceType.DOCUMENT
        }

        val isMedia = resourceType == PageResourceType.MEDIA_VIDEO ||
            resourceType == PageResourceType.MEDIA_AUDIO ||
            resourceType == PageResourceType.MEDIA_HLS

        if (isMedia && !_captureMediaEnabled.value) return

        recordResource(
            CapturedPageResource(
                url = url,
                pageUrl = pageUrl,
                type = resourceType,
                mimeOrRule = accept.take(40),
                headers = request.requestHeaders,
            )
        )
    }

    fun recordDetectedMediaUrl(url: String, pageUrl: String?) {
        if (!_captureMediaEnabled.value) return
        val lower = url.lowercase(Locale.ROOT)
        val type = when {
            lower.contains(".m3u8") || lower.contains(".mpd") -> PageResourceType.MEDIA_HLS
            lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".ogg") -> PageResourceType.MEDIA_AUDIO
            else -> PageResourceType.MEDIA_VIDEO
        }
        recordResource(
            CapturedPageResource(
                url = url,
                pageUrl = pageUrl,
                type = type,
                mimeOrRule = "HTML5 Media Tag",
            )
        )
    }

    private fun recordResource(item: CapturedPageResource) {
        _capturedResources.update { current ->
            if (current.any { it.url == item.url }) return@update current
            (listOf(item) + current).take(250)
        }
    }

    fun clearCapturedResources() {
        _capturedResources.value = emptyList()
    }

    fun getCapturedMediaList(): List<CapturedPageResource> {
        return _capturedResources.value.filter {
            it.type == PageResourceType.MEDIA_VIDEO ||
                it.type == PageResourceType.MEDIA_AUDIO ||
                it.type == PageResourceType.MEDIA_HLS
        }
    }

    // ==================== 4. Browsing History ====================

    fun recordPageVisit(title: String?, url: String?) {
        if (_isIncognito.value) return
        val cleanUrl = url?.trim().orEmpty()
        if (cleanUrl.isEmpty() || cleanUrl == "about:blank" || cleanUrl.startsWith("javascript:")) return
        val displayTitle = title?.trim()?.takeIf { it.isNotEmpty() } ?: cleanUrl
        val entry = BrowserHistoryItem(
            title = displayTitle,
            url = cleanUrl,
            timestamp = System.currentTimeMillis(),
        )
        _history.update { list ->
            val deduplicated = list.filterNot { it.url == cleanUrl && (System.currentTimeMillis() - it.timestamp) < 60_000L }
            val updated = (listOf(entry) + deduplicated).take(500)
            scope.launch {
                runCatching { historyFile.writeText(json.encodeToString(updated)) }
            }
            updated
        }
    }

    fun removeHistoryItem(id: String) {
        _history.update { list ->
            val updated = list.filterNot { it.id == id }
            scope.launch { runCatching { historyFile.writeText(json.encodeToString(updated)) } }
            updated
        }
    }

    fun clearAllHistory() {
        _history.value = emptyList()
        scope.launch { runCatching { historyFile.delete() } }
    }

    // ==================== 5. Clear Browsing Data ====================

    fun clearBrowsingData(
        clearHistory: Boolean,
        clearCookies: Boolean,
        clearCache: Boolean,
        clearWebStorage: Boolean,
        activeWebView: WebView?,
    ) {
        if (clearHistory) {
            clearAllHistory()
        }
        if (clearCookies) {
            val cm = CookieManager.getInstance()
            cm.removeAllCookies(null)
            cm.flush()
        }
        if (clearCache) {
            activeWebView?.clearCache(true)
            activeWebView?.clearFormData()
        }
        if (clearWebStorage) {
            WebStorage.getInstance().deleteAllData()
        }
    }

    // ==================== 6. Cookie Import ====================

    fun importCookiesRaw(domainOrUrl: String, rawCookieInput: String): Int {
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        var importedCount = 0
        val targetUrl = if (domainOrUrl.startsWith("http")) domainOrUrl else "https://${domainOrUrl.trim()}"

        rawCookieInput.lines().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            // Support Netscape cookies.txt (7 tab-separated fields) OR standard header key=value; key2=value2
            val tabParts = line.split('\t')
            if (tabParts.size >= 7) {
                val host = tabParts[0].removePrefix(".")
                val path = tabParts[2]
                val secure = tabParts[3].equals("TRUE", ignoreCase = true)
                val name = tabParts[5]
                val value = tabParts[6]
                val cookieUrl = (if (secure) "https://" else "http://") + host + path
                val cookieString = "$name=$value; domain=$host; path=$path" + (if (secure) "; Secure" else "")
                cm.setCookie(cookieUrl, cookieString)
                importedCount++
            } else {
                line.split(';').forEach { pair ->
                    val kv = pair.trim()
                    if (kv.contains('=')) {
                        cm.setCookie(targetUrl, kv)
                        importedCount++
                    }
                }
            }
        }
        cm.flush()
        return importedCount
    }

    // ==================== 7. Save Page Offline (WebArchive .mht) ====================

    fun saveCurrentPageOffline(webView: WebView, onDone: (Boolean) -> Unit = {}) {
        val title = webView.title?.takeIf { it.isNotBlank() } ?: "Saved_Page"
        val url = webView.url ?: "about:blank"
        val safeName = title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(40)
        val outFile = File(savedPagesDir, "${safeName}_${System.currentTimeMillis()}.mht")
        webView.saveWebArchive(outFile.absolutePath, false) { savedPath ->
            if (savedPath != null && outFile.exists()) {
                val item = SavedPageItem(
                    title = title,
                    url = url,
                    filePath = outFile.absolutePath,
                    sizeBytes = outFile.length(),
                )
                _savedPages.update { list ->
                    val updated = listOf(item) + list
                    scope.launch { runCatching { savedPagesFile.writeText(json.encodeToString(updated)) } }
                    updated
                }
                onDone(true)
            } else {
                onDone(false)
            }
        }
    }

    fun deleteSavedPage(id: String) {
        _savedPages.update { list ->
            val target = list.find { it.id == id }
            if (target != null) {
                runCatching { File(target.filePath).delete() }
            }
            val updated = list.filterNot { it.id == id }
            scope.launch { runCatching { savedPagesFile.writeText(json.encodeToString(updated)) } }
            updated
        }
    }

    // ==================== 8. Print Webpage to PDF / Printer ====================

    fun printCurrentPage(activityContext: Context, webView: WebView) {
        runCatching {
            val printManager = activityContext.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
            val jobName = "Zen'in Pro - ${webView.title ?: "Document"}"
            val adapter = webView.createPrintDocumentAdapter(jobName)
            printManager.print(jobName, adapter, PrintAttributes.Builder().build())
        }
    }

    // ==================== 9. Read Webpage Aloud (Android TTS) ====================

    fun toggleReadPageAloud(webView: WebView) {
        if (_isSpeakingTts.value) {
            stopReadAloud()
            return
        }
        webView.evaluateJavascript(
            "(function(){ return document.body ? document.body.innerText : ''; })();"
        ) { rawJsonText ->
            val cleaned = rawJsonText
                ?.removeSurrounding("\"")
                ?.replace("\\n", " ")
                ?.replace("\\t", " ")
                ?.replace("\\\"", "\"")
                ?.trim()
                .orEmpty()
            if (cleaned.isNotBlank()) {
                speakText(cleaned.take(3900))
            }
        }
    }

    private fun speakText(text: String) {
        if (ttsEngine == null) {
            ttsEngine = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    ttsEngine?.language = Locale.getDefault()
                    ttsEngine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "zenin_tts_utterance")
                    _isSpeakingTts.value = true
                }
            }
        } else {
            ttsEngine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "zenin_tts_utterance")
            _isSpeakingTts.value = true
        }
    }

    fun stopReadAloud() {
        ttsEngine?.stop()
        _isSpeakingTts.value = false
    }

    // ==================== 10. Video Fullscreen, Picture-in-Picture & Background Play ====================

    fun forceVideoFullscreen(webView: WebView) {
        val js = """
            (function() {
                var v = document.querySelector('video');
                if (v) {
                    if (v.requestFullscreen) v.requestFullscreen();
                    else if (v.webkitEnterFullscreen) v.webkitEnterFullscreen();
                    v.style.position = 'fixed';
                    v.style.top = '0';
                    v.style.left = '0';
                    v.style.width = '100vw';
                    v.style.height = '100vh';
                    v.style.zIndex = '2147483647';
                    v.style.background = '#000';
                    v.controls = true;
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    fun enterPictureInPicture(activity: Activity?, webView: WebView?) {
        if (activity == null) return
        webView?.let { forceVideoFullscreen(it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                activity.enterPictureInPictureMode(params)
            }
        }
    }

    fun setBackgroundPlayEnabled(enabled: Boolean) {
        _backgroundPlayEnabled.value = enabled
    }

    // ==================== 11. Reader Mode & Page Source ====================

    fun extractReaderMode(webView: WebView) {
        if (_readerModeActive.value) {
            _readerModeActive.value = false
            _readerModeContent.value = null
            return
        }
        val js = """
            (function() {
                var title = document.title || 'Article';
                var article = document.querySelector('article, main, [role="main"]') || document.body;
                var paragraphs = article ? article.querySelectorAll('h1, h2, h3, p, li, blockquote') : [];
                var out = [];
                for (var i = 0; i < paragraphs.length; i++) {
                    var t = (paragraphs[i].innerText || '').trim();
                    if (t.length > 25) out.push(t);
                }
                if (out.length === 0 && document.body) {
                    out.push(document.body.innerText);
                }
                return JSON.stringify({ title: title, body: out.join('\n\n') });
            })();
        """.trimIndent()
        webView.evaluateJavascript(js) { rawResult ->
            runCatching {
                val unescaped = json.decodeFromString<String>(rawResult)
                val obj = json.decodeFromString<Map<String, String>>(unescaped)
                _readerModeContent.value = (obj["title"] ?: "Reader View") to (obj["body"] ?: "")
                _readerModeActive.value = true
            }
        }
    }

    fun closeReaderMode() {
        _readerModeActive.value = false
        _readerModeContent.value = null
    }

    fun fetchPageHtmlSource(webView: WebView, onResult: (String) -> Unit) {
        webView.evaluateJavascript(
            "(function(){ return document.documentElement ? document.documentElement.outerHTML : ''; })();"
        ) { raw ->
            val decoded = runCatching { json.decodeFromString<String>(raw) }.getOrElse {
                raw?.removeSurrounding("\"")?.replace("\\n", "\n")?.replace("\\\"", "\"").orEmpty()
            }
            onResult(decoded)
        }
    }

    fun openPageSourceInExternalViewer(webView: WebView) {
        fetchPageHtmlSource(webView) { html ->
            runCatching {
                val cacheFile = File(context.cacheDir, "page_source_${System.currentTimeMillis()}.txt")
                cacheFile.writeText(html)
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    cacheFile
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "text/plain")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(intent, "View Page Source With").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        }
    }

    // ==================== 12. Interactive Hide Element Picker ====================

    fun activateElementHiderPicker(webView: WebView) {
        _elementPickerActive.value = true
        val js = """
            (function() {
                if (window.__zeninPickerActive) return;
                window.__zeninPickerActive = true;
                var banner = document.createElement('div');
                banner.id = '__zenin_picker_banner';
                banner.style.cssText = 'position:fixed;bottom:16px;left:16px;right:16px;z-index:2147483647;background:#141829;color:#00E5A3;padding:12px 16px;border-radius:12px;border:2px solid #5E6AD2;font-family:sans-serif;font-size:14px;font-weight:bold;text-align:center;box-shadow:0 8px 24px rgba(0,0,0,0.6);';
                banner.innerText = 'Zen\\'in Element Hider: Tap any ad/element on the page to block & hide it (Tap here to cancel)';
                document.body.appendChild(banner);

                function buildSelector(el) {
                    if (el.id) return '#' + CSS.escape(el.id);
                    var tag = el.tagName.toLowerCase();
                    if (el.className && typeof el.className === 'string') {
                        var classes = el.className.trim().split(/\\s+/).filter(Boolean).slice(0, 2);
                        if (classes.length > 0) {
                            return tag + '.' + classes.map(function(c){ return CSS.escape(c); }).join('.');
                        }
                    }
                    return tag;
                }

                function handler(e) {
                    e.preventDefault();
                    e.stopPropagation();
                    var target = e.target;
                    document.removeEventListener('click', handler, true);
                    window.__zeninPickerActive = false;
                    if (banner.parentNode) banner.parentNode.removeChild(banner);
                    if (target === banner) return;
                    var selector = buildSelector(target);
                    target.style.setProperty('display', 'none', 'important');
                    if (window.ZeninBridge && selector) {
                        window.ZeninBridge.onElementHidden(selector);
                    }
                }
                document.addEventListener('click', handler, true);
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    // ==================== 13. Userscripts & Per-Site Settings ====================

    private fun saveUserscripts(list: List<UserscriptItem>) {
        scope.launch { runCatching { userscriptsFile.writeText(json.encodeToString(list)) } }
    }

    fun addOrUpdateUserscript(script: UserscriptItem) {
        _userscripts.update { list ->
            val next = list.filterNot { it.id == script.id } + script
            saveUserscripts(next)
            next
        }
    }

    fun toggleUserscript(id: String, enabled: Boolean) {
        _userscripts.update { list ->
            val next = list.map { if (it.id == id) it.copy(enabled = enabled) else it }
            saveUserscripts(next)
            next
        }
    }

    fun deleteUserscript(id: String) {
        _userscripts.update { list ->
            val next = list.filterNot { it.id == id }
            saveUserscripts(next)
            next
        }
    }

    fun injectMatchingUserscripts(webView: WebView, url: String?) {
        if (url.isNullOrBlank() || url == "about:blank") return
        val activeScripts = _userscripts.value.filter { it.enabled && matchesPattern(url, it.matchPattern) }
        activeScripts.forEach { script ->
            webView.evaluateJavascript(script.code, null)
        }
    }

    private fun matchesPattern(url: String, pattern: String): Boolean {
        val p = pattern.trim()
        if (p == "*" || p == "<all_urls>" || p == "*://*/*") return true
        val regexStr = "^" + p.split("*").joinToString(".*") { Regex.escape(it) } + "$"
        return runCatching { Regex(regexStr, RegexOption.IGNORE_CASE).containsMatchIn(url) }.getOrDefault(false)
    }

    fun getSiteSettingForUrl(url: String?): SiteSettingItem {
        val host = url?.let { runCatching { Uri.parse(it).host?.lowercase(Locale.ROOT)?.removePrefix("www.") }.getOrNull() }
            .orEmpty()
        return _siteSettings.value[host] ?: SiteSettingItem(
            host = host,
            enableJavaScript = _javaScriptEnabled.value,
            requestDesktopSite = _desktopSiteEnabled.value,
            darkMode = _darkModeEnabled.value,
        )
    }

    fun updateSiteSetting(setting: SiteSettingItem) {
        if (setting.host.isBlank()) return
        _siteSettings.update { map ->
            val next = map + (setting.host to setting)
            scope.launch { runCatching { siteSettingsFile.writeText(json.encodeToString(next)) } }
            next
        }
    }

    fun removeSiteSetting(host: String) {
        _siteSettings.update { map ->
            val next = map - host
            scope.launch { runCatching { siteSettingsFile.writeText(json.encodeToString(next)) } }
            next
        }
    }

    fun setDarkMode(enabled: Boolean, webView: WebView?) {
        _darkModeEnabled.value = enabled
        if (webView != null) {
            applyDarkModeToWebView(webView, enabled)
        }
    }

    fun applyDarkModeToWebView(webView: WebView, enabled: Boolean) {
        val js = if (enabled) {
            """
                (function() {
                    if (!document.getElementById('__zenin_dark_css')) {
                        var style = document.createElement('style');
                        style.id = '__zenin_dark_css';
                        style.innerHTML = 'html { background-color: #10121A !important; color-scheme: dark !important; }';
                        (document.head || document.documentElement).appendChild(style);
                    }
                })();
            """.trimIndent()
        } else {
            """
                (function() {
                    var el = document.getElementById('__zenin_dark_css');
                    if (el && el.parentNode) el.parentNode.removeChild(el);
                })();
            """.trimIndent()
        }
        webView.evaluateJavascript(js, null)
    }

    fun setDesktopSite(enabled: Boolean, webView: WebView?) {
        _desktopSiteEnabled.value = enabled
        webView?.let { applyDesktopModeToWebView(it, enabled) }
    }

    fun applyDesktopModeToWebView(webView: WebView, desktop: Boolean) {
        val settings = webView.settings
        settings.userAgentString = if (desktop) {
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        } else {
            null
        }
        settings.useWideViewPort = desktop
        settings.loadWithOverviewMode = desktop
        webView.reload()
    }

    fun setJavaScriptEnabled(enabled: Boolean, webView: WebView?) {
        _javaScriptEnabled.value = enabled
        webView?.settings?.javaScriptEnabled = enabled
        webView?.reload()
    }

    // ==================== 14. Shortcuts, Launcher Pinning & Session Persistence ====================

    private fun saveShortcuts(list: List<BrowserShortcutItem>) {
        scope.launch { runCatching { shortcutsFile.writeText(json.encodeToString(list)) } }
    }

    fun addShortcut(title: String, url: String) {
        if (url.isBlank()) return
        val item = BrowserShortcutItem(
            title = title.ifBlank { url },
            url = url,
        )
        _shortcuts.update { list ->
            val next = (list.filterNot { it.url == url } + item)
            saveShortcuts(next)
            next
        }
    }

    fun removeShortcut(id: String) {
        _shortcuts.update { list ->
            val next = list.filterNot { it.id == id }
            saveShortcuts(next)
            next
        }
    }

    fun pinPageToAndroidHomeScreen(title: String, url: String, favicon: Bitmap?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return
                if (shortcutManager.isRequestPinShortcutSupported) {
                    val launchIntent = BrowserActivity.createIntent(context, url)
                    val icon = if (favicon != null) {
                        Icon.createWithBitmap(favicon)
                    } else {
                        Icon.createWithResource(context, context.applicationInfo.icon)
                    }
                    val pinShortcutInfo = ShortcutInfo.Builder(context, "zenin_pin_${url.hashCode()}")
                        .setShortLabel(title.take(20).ifBlank { "Web Shortcut" })
                        .setLongLabel(title.take(45).ifBlank { url })
                        .setIcon(icon)
                        .setIntent(launchIntent)
                        .build()
                    shortcutManager.requestPinShortcut(pinShortcutInfo, null)
                }
            }
        }
    }

    fun saveRememberedTabs(urls: List<String>, activeIndex: Int) {
        runCatching {
            val clean = urls.filter { it.isNotBlank() && it != "about:blank" }
            rememberedTabsFile.writeText(
                json.encodeToString(RememberedTabSession(urls = clean, activeIndex = activeIndex))
            )
        }
    }

    fun consumeRememberedTabs(): RememberedTabSession? {
        if (!rememberedTabsFile.exists()) return null
        return runCatching {
            val session = json.decodeFromString<RememberedTabSession>(rememberedTabsFile.readText())
            rememberedTabsFile.delete()
            session.takeIf { it.urls.isNotEmpty() }
        }.getOrNull()
    }

    fun setFindInPageVisible(visible: Boolean, webView: WebView?) {
        _findInPageVisible.value = visible
        if (!visible) {
            webView?.clearMatches()
            _findMatchStatus.value = 0 to 0
        }
    }

    fun updateFindInPageQuery(query: String, webView: WebView?) {
        if (webView == null) return
        if (query.isBlank()) {
            webView.clearMatches()
            _findMatchStatus.value = 0 to 0
            return
        }
        webView.setFindListener { activeMatchOrdinal, numberOfMatches, _ ->
            val current = if (numberOfMatches > 0) activeMatchOrdinal + 1 else 0
            _findMatchStatus.value = current to numberOfMatches
        }
        webView.findAllAsync(query)
    }

    fun extractUrlsFromText(text: String?): List<String> {
        if (text.isNullOrBlank()) return emptyList()
        val regex = Regex("""https?://[^\s"'<>]+""", RegexOption.IGNORE_CASE)
        return regex.findAll(text).map { it.value.trimEnd('.', ',', ';', ')') }.distinct().toList()
    }
}
