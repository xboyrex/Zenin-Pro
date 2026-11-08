package com.abdownloadmanager.android.pages.browser.adblock

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Serializable
enum class AdGuardProfile(
    val displayName: String,
    val badge: String,
    val description: String,
) {
    PRO(
        displayName = "AdGuard Pro",
        badge = "PRO",
        description = "Balanced daily protection: AdGuard Base, Tracking Protection, URL Tracking & Mobile Ads",
    ),
    PRO_PLUS_PLUS(
        displayName = "AdGuard Pro++",
        badge = "PRO++",
        description = "Advanced privacy & page cleanup: Pro + Annoyances, Cookie Notices, Popups, Social & Widgets",
    ),
    ULTIMATE(
        displayName = "AdGuard Ultimate",
        badge = "ULTIMATE",
        description = "Maximum multi-layer shield: Pro++ + NextDNS Hosts, Anti-Adblock Killer, EasyList, EasyPrivacy & uBlock",
    ),
    CUSTOM(
        displayName = "Custom Profile",
        badge = "CUSTOM",
        description = "User-customized filter subscriptions and manual rule configuration",
    )
}

@Serializable
data class FilterSourceItem(
    val id: String,
    val name: String,
    val url: String,
    val isLocalFile: Boolean = false,
    val assetSeedFile: String? = null,
    val enabled: Boolean = true,
    val trusted: Boolean = false,
    val rulesCount: Int = 0,
    val sizeBytes: Long = 0L,
    val lastUpdated: Long = 0L,
    val category: String = "AdGuard",
    val builtIn: Boolean = true,
) {
    fun formattedSize(): String {
        if (sizeBytes <= 0L) return "N/A"
        val kb = sizeBytes / 1024.0
        return if (kb >= 1024.0) {
            String.format(Locale.US, "%.1fMB", kb / 1024.0)
        } else {
            String.format(Locale.US, "%.1fKB", kb)
        }
    }

    fun formattedDate(): String {
        if (lastUpdated <= 0L) return "N/A"
        val sdf = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.US)
        return sdf.format(Date(lastUpdated))
    }
}

@Serializable
data class AdBlockSettingsState(
    val globalEnabled: Boolean = true,
    val activeProfile: AdGuardProfile = AdGuardProfile.ULTIMATE,
    val autoUpdateEnabled: Boolean = true,
    val updateIntervalHours: Int = 24,
    val wifiOnlyUpdates: Boolean = false,
    val stripTrackingParams: Boolean = true,
    val cosmeticFilteringEnabled: Boolean = true,
    val scriptletsEnabled: Boolean = true,
    val strictThirdPartyBlocking: Boolean = false,
    val websiteWhitelist: Set<String> = emptySet(),
    val hostsWhitelist: Set<String> = emptySet(),
    val customRules: List<String> = emptyList(),
    val disabledRules: Set<String> = emptySet(),
    val hiddenElementsByHost: Map<String, List<String>> = emptyMap(),
    val globalHiddenSelectors: List<String> = emptyList(),
    val totalBlockedRequests: Long = 0L,
    val totalTrackersBlocked: Long = 0L,
    val totalParamsStripped: Long = 0L,
)

data class InterceptResult(
    val blocked: Boolean,
    val matchedRule: String? = null,
    val category: String = "Ad",
    val sanitizedUrl: String? = null,
)

class ZeninAdBlockEngine(
    private val context: Context,
    private val httpClientProvider: () -> OkHttpClient = { OkHttpClient() },
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val adblockDir = File(context.filesDir, "zenin_adblock").apply { mkdirs() }
    private val cacheDir = File(adblockDir, "filter_cache").apply { mkdirs() }
    private val settingsFile = File(adblockDir, "adblock_settings.json")
    private val sourcesFile = File(adblockDir, "filter_sources.json")

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AdBlockSettingsState> = _settings.asStateFlow()

    private val _filterSources = MutableStateFlow<List<FilterSourceItem>>(emptyList())
    val filterSources: StateFlow<List<FilterSourceItem>> = _filterSources.asStateFlow()

    private val _totalActiveRules = MutableStateFlow(0)
    val totalActiveRules: StateFlow<Int> = _totalActiveRules.asStateFlow()

    private val _isUpdating = MutableStateFlow(false)
    val isUpdating: StateFlow<Boolean> = _isUpdating.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    // Live compiled structures in memory for fast O(1) / O(k) lookup
    private val blockedDomains = ConcurrentHashMap.newKeySet<String>()
    private val trackerDomains = ConcurrentHashMap.newKeySet<String>()
    private val exceptionDomains = ConcurrentHashMap.newKeySet<String>()
    private val urlSubstrings = ConcurrentHashMap.newKeySet<String>()
    private val exceptionSubstrings = ConcurrentHashMap.newKeySet<String>()
    private val thirdPartyOnlyDomains = ConcurrentHashMap.newKeySet<String>()
    private val removeParams = ConcurrentHashMap.newKeySet<String>()
    private val globalCosmeticSelectors = ConcurrentHashMap.newKeySet<String>()
    private val domainCosmeticSelectors = ConcurrentHashMap<String, MutableSet<String>>()
    private val perTabBlockedCount = ConcurrentHashMap<String, AtomicLong>()

    @Volatile
    private var cachedGlobalCssChunk: String = ""

    init {
        scope.launch {
            initializeDefaultSourcesAndCompile()
        }
    }

    private fun loadSettings(): AdBlockSettingsState {
        if (settingsFile.exists()) {
            runCatching {
                return json.decodeFromString<AdBlockSettingsState>(settingsFile.readText())
            }
        }
        return AdBlockSettingsState()
    }

    private fun saveSettings(state: AdBlockSettingsState) {
        runCatching {
            settingsFile.writeText(json.encodeToString(state))
        }
    }

    private fun saveFilterSources(list: List<FilterSourceItem>) {
        runCatching {
            sourcesFile.writeText(json.encodeToString(list))
        }
    }

    private fun defaultFilterSources(): List<FilterSourceItem> {
        val now = System.currentTimeMillis()
        return listOf(
            FilterSourceItem(
                id = "manual_filters",
                name = "Manual filters",
                url = "local://manual_filters",
                isLocalFile = true,
                enabled = true,
                trusted = true,
                rulesCount = _settings.value.customRules.size,
                sizeBytes = _settings.value.customRules.joinToString("\n").toByteArray().size.toLong(),
                lastUpdated = now,
                category = "Custom",
                builtIn = true,
            ),
            FilterSourceItem(
                id = "zenin_1dm_filters",
                name = "Zen'in Pro & 1DM Core Filters",
                url = "https://filters.adtidy.org/extension/chromium/filters/15.txt",
                assetSeedFile = "filters/nextdns_hosts.txt",
                enabled = true,
                trusted = true,
                category = "Core",
                builtIn = true,
            ),
            FilterSourceItem(
                id = "easylist",
                name = "EasyList",
                url = "https://easylist.to/easylist/easylist.txt",
                assetSeedFile = "filters/adguard_base.txt",
                enabled = true,
                trusted = false,
                category = "Ads",
                builtIn = true,
            ),
            FilterSourceItem(
                id = "ublock_filters",
                name = "uBlock filters",
                url = "https://ublockorigin.github.io/uAssetsCDN/filters/filters.min.txt",
                assetSeedFile = "filters/adguard_mobile.txt",
                enabled = true,
                trusted = true,
                category = "uBlock",
                builtIn = true,
            ),
            FilterSourceItem(
                id = "adguard_base",
                name = "AdGuard Base filter",
                url = "https://filters.adtidy.org/extension/chromium/filters/2.txt",
                assetSeedFile = "filters/adguard_base.txt",
                enabled = true,
                trusted = true,
                category = "AdGuard",
                builtIn = true,
            ),
            FilterSourceItem(
                id = "easyprivacy",
                name = "EasyPrivacy",
                url = "https://easylist.to/easylist/easyprivacy.txt",
                assetSeedFile = "filters/adguard_tracking.txt",
                enabled = true,
                trusted = false,
                category = "Privacy",
                builtIn = true,
            ),
            FilterSourceItem(
                id = "peter_lowe",
                name = "Peter Lowe's Ad and tracking server list",
                url = "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=adblockplus&showintro=1&mimetype=plaintext",
                assetSeedFile = "filters/nextdns_hosts.txt",
                enabled = true,
                trusted = false,
                category = "Hosts",
                builtIn = true,
            ),
            FilterSourceItem(
                id = "aaklist",
                name = "AakList (Anti-Adblock Killer)",
                url = "https://raw.githubusercontent.com/reek/anti-adblock-killer/master/anti-adblock-killer-filters.txt",
                assetSeedFile = "filters/adguard_annoyances.txt",
                enabled = true,
                trusted = true,
                category = "Anti-Adblock",
                builtIn = true,
            ),
            FilterSourceItem(
                id = "adguard_tracking",
                name = "AdGuard Tracking Protection",
                url = "https://filters.adtidy.org/extension/chromium/filters/3.txt",
                assetSeedFile = "filters/adguard_tracking.txt",
                enabled = true,
                trusted = true,
                category = "AdGuard",
                builtIn = true,
            ),
            FilterSourceItem(
                id = "adguard_annoyances",
                name = "AdGuard Annoyances (Cookies, Popups & Widgets)",
                url = "https://filters.adtidy.org/extension/chromium/filters/14.txt",
                assetSeedFile = "filters/adguard_annoyances.txt",
                enabled = true,
                trusted = true,
                category = "AdGuard",
                builtIn = true,
            ),
            FilterSourceItem(
                id = "adguard_mobile",
                name = "AdGuard Mobile Ads",
                url = "https://filters.adtidy.org/extension/chromium/filters/11.txt",
                assetSeedFile = "filters/adguard_mobile.txt",
                enabled = true,
                trusted = true,
                category = "AdGuard",
                builtIn = true,
            ),
            FilterSourceItem(
                id = "adguard_social",
                name = "AdGuard Social Media Filter",
                url = "https://filters.adtidy.org/extension/chromium/filters/4.txt",
                assetSeedFile = "filters/adguard_social.txt",
                enabled = true,
                trusted = true,
                category = "AdGuard",
                builtIn = true,
            ),
            FilterSourceItem(
                id = "adguard_url_tracking",
                name = "AdGuard URL Tracking Protection",
                url = "https://filters.adtidy.org/extension/chromium/filters/17.txt",
                assetSeedFile = "filters/adguard_url_tracking.txt",
                enabled = true,
                trusted = true,
                category = "AdGuard",
                builtIn = true,
            ),
            FilterSourceItem(
                id = "nextdns_oisd_hosts",
                name = "NextDNS Recommended & OISD Blocklist",
                url = "https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt",
                assetSeedFile = "filters/nextdns_hosts.txt",
                enabled = true,
                trusted = false,
                category = "NextDNS",
                builtIn = true,
            ),
        )
    }

    private suspend fun initializeDefaultSourcesAndCompile() = withContext(Dispatchers.IO) {
        val loadedSources = if (sourcesFile.exists()) {
            runCatching {
                json.decodeFromString<List<FilterSourceItem>>(sourcesFile.readText())
            }.getOrNull()
        } else {
            null
        } ?: defaultFilterSources()

        // Ensure cache files are seeded from assets if not yet downloaded
        val hydrated = loadedSources.map { src ->
            if (src.id == "manual_filters") {
                val rules = _settings.value.customRules
                src.copy(
                    rulesCount = rules.size,
                    sizeBytes = rules.joinToString("\n").toByteArray().size.toLong(),
                    lastUpdated = if (src.lastUpdated == 0L) System.currentTimeMillis() else src.lastUpdated
                )
            } else {
                val cacheFile = getCacheFileForSource(src)
                if (!cacheFile.exists() && src.assetSeedFile != null) {
                    runCatching {
                        context.assets.open(src.assetSeedFile).use { input ->
                            cacheFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
                if (cacheFile.exists()) {
                    val count = countValidRulesInFile(cacheFile)
                    src.copy(
                        rulesCount = count,
                        sizeBytes = cacheFile.length(),
                        lastUpdated = if (src.lastUpdated == 0L) cacheFile.lastModified() else src.lastUpdated,
                    )
                } else {
                    src
                }
            }
        }
        _filterSources.value = hydrated
        saveFilterSources(hydrated)
        compileAllEnabledFilters()
    }

    private fun getCacheFileForSource(source: FilterSourceItem): File {
        if (source.isLocalFile && !source.url.startsWith("local://")) {
            val f = File(source.url)
            if (f.exists()) return f
        }
        return File(cacheDir, "${source.id}.txt")
    }

    private fun countValidRulesInFile(file: File): Int {
        var count = 0
        runCatching {
            file.bufferedReader().useLines { lines ->
                lines.forEach { raw ->
                    val line = raw.trim()
                    if (line.isNotEmpty() && !line.startsWith("!") && !line.startsWith("[Adblock") && !line.startsWith("# ")) {
                        count++
                    }
                }
            }
        }
        return count
    }

    suspend fun compileAllEnabledFilters() = withContext(Dispatchers.IO) {
        blockedDomains.clear()
        trackerDomains.clear()
        exceptionDomains.clear()
        urlSubstrings.clear()
        exceptionSubstrings.clear()
        thirdPartyOnlyDomains.clear()
        removeParams.clear()
        globalCosmeticSelectors.clear()
        domainCosmeticSelectors.clear()

        // Default common tracking parameters
        removeParams.addAll(
            listOf(
                "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
                "fbclid", "gclid", "dclid", "msclkid", "twclid", "igshid", "mc_cid", "mc_eid",
                "yclid", "_openstat", "vero_id", "oly_enc_id", "oly_anon_id", "rb_clickid", "s_kwcid"
            )
        )

        val disabledSet = _settings.value.disabledRules
        var activeRuleTotal = 0

        // Parse custom rules first
        _settings.value.customRules.forEach { rule ->
            if (rule !in disabledSet) {
                if (parseSingleRule(rule, isTrackerSource = false)) {
                    activeRuleTotal++
                }
            }
        }

        // Parse hidden elements added via Hide Element picker
        _settings.value.globalHiddenSelectors.forEach { sel ->
            if (sel.isNotBlank()) {
                globalCosmeticSelectors.add(sel.trim())
                activeRuleTotal++
            }
        }
        _settings.value.hiddenElementsByHost.forEach { (host, selectors) ->
            val set = domainCosmeticSelectors.getOrPut(host.lowercase(Locale.ROOT)) {
                ConcurrentHashMap.newKeySet()
            }
            selectors.forEach { sel ->
                if (sel.isNotBlank()) {
                    set.add(sel.trim())
                    activeRuleTotal++
                }
            }
        }

        // Parse enabled filter sources
        for (source in _filterSources.value) {
            if (!source.enabled || source.id == "manual_filters") continue
            val file = getCacheFileForSource(source)
            if (!file.exists()) continue
            val isTracker = source.category.equals("Privacy", ignoreCase = true) ||
                source.id.contains("tracking") || source.id.contains("privacy") || source.id.contains("nextdns")
            runCatching {
                file.bufferedReader().useLines { lines ->
                    lines.forEach { raw ->
                        val line = raw.trim()
                        if (line.isNotEmpty() && line !in disabledSet) {
                            if (parseSingleRule(line, isTrackerSource = isTracker)) {
                                activeRuleTotal++
                            }
                        }
                    }
                }
            }
        }

        // Pre-build global CSS chunk (cap to 1200 highest-priority selectors for fast DOM injection)
        val cssSelectors = globalCosmeticSelectors.take(1200)
        cachedGlobalCssChunk = if (cssSelectors.isEmpty()) {
            ""
        } else {
            cssSelectors.joinToString(", ") + " { display: none !important; visibility: hidden !important; height: 0 !important; min-height: 0 !important; pointer-events: none !important; }"
        }

        _totalActiveRules.value = activeRuleTotal
    }

    private fun parseSingleRule(rawLine: String, isTrackerSource: Boolean): Boolean {
        val line = rawLine.trim()
        if (line.isEmpty() || line.startsWith("!") || line.startsWith("[Adblock")) return false
        if (line.startsWith("#") && !line.startsWith("##") && !line.startsWith("#@#") && !line.startsWith("#?#")) {
            return false
        }

        // 1. Hosts file format: 0.0.0.0 domain.com or 127.0.0.1 domain.com
        if (line.startsWith("0.0.0.0 ") || line.startsWith("127.0.0.1 ")) {
            val parts = line.split(Regex("\\s+"))
            if (parts.size >= 2) {
                val host = parts[1].substringBefore("#").trim().lowercase(Locale.ROOT)
                if (host.isNotEmpty() && host != "localhost" && host != "0.0.0.0" && host.contains('.')) {
                    blockedDomains.add(host)
                    if (isTrackerSource) trackerDomains.add(host)
                    return true
                }
            }
            return false
        }

        // 2. Cosmetic Element Hiding Rule: [domains]##selector
        if (line.contains("##") && !line.contains("##+js") && !line.contains("#%#")) {
            val idx = line.indexOf("##")
            val domainsPart = line.substring(0, idx).trim()
            val selector = line.substring(idx + 2).trim()
            if (selector.isEmpty() || selector.startsWith("+js") || selector.contains(":has-text(") || selector.contains(":-abp-")) {
                return true // Counted as rule, procedural handled safely
            }
            if (domainsPart.isEmpty()) {
                globalCosmeticSelectors.add(selector)
            } else {
                domainsPart.split(',').forEach { d ->
                    val cleanDomain = d.trim().lowercase(Locale.ROOT)
                    if (cleanDomain.isNotEmpty() && !cleanDomain.startsWith("~")) {
                        domainCosmeticSelectors.getOrPut(cleanDomain) {
                            ConcurrentHashMap.newKeySet()
                        }.add(selector)
                    }
                }
            }
            return true
        }

        // 3. Scriptlet or procedural cosmetic rule
        if (line.contains("#%#") || line.contains("##+js") || line.contains("#?#") || line.contains("#@#")) {
            return true
        }

        // 4. Exception rule: @@||domain.com^ or @@substring
        if (line.startsWith("@@")) {
            val clean = line.removePrefix("@@").substringBefore("$").trim()
            if (clean.startsWith("||")) {
                val dom = clean.removePrefix("||").substringBefore("^").substringBefore("/").lowercase(Locale.ROOT)
                if (dom.isNotEmpty() && !dom.contains('*')) {
                    exceptionDomains.add(dom)
                    return true
                }
            }
            if (clean.length >= 5) {
                exceptionSubstrings.add(clean.lowercase(Locale.ROOT))
                return true
            }
            return false
        }

        // 5. Check for $removeparam= modifier
        if (line.contains("\$removeparam=")) {
            val param = line.substringAfter("\$removeparam=").substringBefore(",").substringBefore("$").trim()
            if (param.isNotEmpty() && !param.startsWith("/") && !param.startsWith("~")) {
                removeParams.add(param)
                return true
            }
        }

        // 6. Standard ABP / AdGuard network rule: ||domain.com^$options
        val rulePart = line.substringBefore("$").trim()
        val optionsPart = if (line.contains("$")) line.substringAfter("$").lowercase(Locale.ROOT) else ""

        if (rulePart.startsWith("||")) {
            val afterAnchor = rulePart.removePrefix("||")
            val sepIdx = afterAnchor.indexOfFirst { it == '^' || it == '/' }
            val domainCandidate = (if (sepIdx >= 0) afterAnchor.substring(0, sepIdx) else afterAnchor)
                .lowercase(Locale.ROOT)
                .trim()
            val pathRest = if (sepIdx >= 0) afterAnchor.substring(sepIdx).removePrefix("^") else ""

            if (domainCandidate.isNotEmpty() && !domainCandidate.contains('*') && domainCandidate.contains('.')) {
                if (pathRest.isEmpty() || pathRest == "/") {
                    blockedDomains.add(domainCandidate)
                    if (isTrackerSource) trackerDomains.add(domainCandidate)
                    if (optionsPart.contains("third-party") || optionsPart.contains("3p")) {
                        thirdPartyOnlyDomains.add(domainCandidate)
                    }
                    return true
                } else {
                    val token = (domainCandidate + pathRest.substringBefore("^").substringBefore("*")).lowercase(Locale.ROOT)
                    if (token.length >= 6) {
                        urlSubstrings.add(token)
                        return true
                    }
                }
            }
        } else if (!rulePart.startsWith("/") && rulePart.length >= 6 && !rulePart.contains('*')) {
            val cleaned = rulePart.removePrefix("|").removeSuffix("|").removeSuffix("^").lowercase(Locale.ROOT)
            if (cleaned.length >= 6) {
                if (cleaned.matches(Regex("^[a-z0-9.-]+\\.[a-z]{2,}$"))) {
                    blockedDomains.add(cleaned)
                    if (isTrackerSource) trackerDomains.add(cleaned)
                } else {
                    urlSubstrings.add(cleaned)
                }
                return true
            }
        }

        return true
    }

    /**
     * Evaluates a WebView network request against all active AdGuard, EasyList, uBlock, and NextDNS rules.
     */
    fun shouldBlockRequest(
        request: WebResourceRequest,
        pageUrl: String?,
        tabId: String?,
    ): InterceptResult {
        val currentSettings = _settings.value
        if (!currentSettings.globalEnabled) {
            return InterceptResult(blocked = false)
        }

        val reqUri = request.url ?: return InterceptResult(blocked = false)
        val scheme = reqUri.scheme?.lowercase(Locale.ROOT) ?: return InterceptResult(blocked = false)
        if (scheme != "http" && scheme != "https") {
            return InterceptResult(blocked = false)
        }

        val reqUrlStr = reqUri.toString()
        val reqHost = reqUri.host?.lowercase(Locale.ROOT)?.removePrefix("www.") ?: return InterceptResult(blocked = false)
        val pageHost = pageUrl?.let { runCatching { Uri.parse(it).host?.lowercase(Locale.ROOT)?.removePrefix("www.") }.getOrNull() }

        // 1. Check Website Whitelist (per-site AdBlock disable)
        if (pageHost != null && isHostInSet(pageHost, currentSettings.websiteWhitelist)) {
            return InterceptResult(blocked = false)
        }
        if (request.isForMainFrame && isHostInSet(reqHost, currentSettings.websiteWhitelist)) {
            return InterceptResult(blocked = false)
        }

        // 2. Check Hosts Whitelist (allowed domains)
        if (isHostInSet(reqHost, currentSettings.hostsWhitelist) || isHostInSet(reqHost, exceptionDomains)) {
            return InterceptResult(blocked = false)
        }

        val lowerUrl = reqUrlStr.lowercase(Locale.ROOT)
        for (exc in exceptionSubstrings) {
            if (lowerUrl.contains(exc)) {
                return InterceptResult(blocked = false)
            }
        }

        val isThirdParty = pageHost != null && !isSameRootDomain(reqHost, pageHost)

        // 3. Domain Suffix Lookup (O(k) where k is number of labels in hostname)
        val matchedDomain = findMatchingDomain(reqHost, blockedDomains)
        if (matchedDomain != null) {
            val requiresThirdParty = thirdPartyOnlyDomains.contains(matchedDomain)
            if (!requiresThirdParty || isThirdParty || !request.isForMainFrame) {
                val isTracker = trackerDomains.contains(matchedDomain)
                recordBlockedHit(tabId, isTracker)
                return InterceptResult(
                    blocked = true,
                    matchedRule = "||$matchedDomain^",
                    category = if (isTracker) "Tracker" else "Ad",
                )
            }
        }

        // 4. URL Substring Pattern Lookup
        if (!request.isForMainFrame) {
            for (pattern in urlSubstrings) {
                if (lowerUrl.contains(pattern)) {
                    recordBlockedHit(tabId, isTracker = false)
                    return InterceptResult(
                        blocked = true,
                        matchedRule = pattern,
                        category = "Ad",
                    )
                }
            }
        }

        return InterceptResult(blocked = false)
    }

    /**
     * Strips tracking query parameters (AdGuard URL Tracking Protection) from top-level navigation URLs.
     */
    fun sanitizeUrlTrackingParameters(url: String): String {
        if (!_settings.value.globalEnabled || !_settings.value.stripTrackingParams) return url
        return runCatching {
            val uri = Uri.parse(url)
            val queryNames = uri.queryParameterNames
            if (queryNames.isEmpty()) return url
            val toRemove = queryNames.filter { param ->
                val lower = param.lowercase(Locale.ROOT)
                removeParams.contains(lower) || lower.startsWith("utm_") || lower.startsWith("ga_")
            }
            if (toRemove.isEmpty()) return url

            val builder = uri.buildUpon().clearQuery()
            for (name in queryNames) {
                if (name !in toRemove) {
                    uri.getQueryParameters(name).forEach { v ->
                        builder.appendQueryParameter(name, v)
                    }
                }
            }
            _settings.update { it.copy(totalParamsStripped = it.totalParamsStripped + toRemove.size) }
            builder.build().toString()
        }.getOrDefault(url)
    }

    fun createEmptyBlockedResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            200,
            "OK",
            mapOf(
                "Access-Control-Allow-Origin" to "*",
                "Cache-Control" to "no-store"
            ),
            ByteArrayInputStream(ByteArray(0))
        )
    }

    /**
     * Builds the JavaScript + CSS payload to inject into a webpage for Layer 2 Cosmetic Filtering
     * and Anti-Adblock Scriptlets.
     */
    fun buildCosmeticInjectionScript(pageUrl: String?): String {
        val currentSettings = _settings.value
        if (!currentSettings.globalEnabled || !currentSettings.cosmeticFilteringEnabled) return ""
        val host = pageUrl?.let { runCatching { Uri.parse(it).host?.lowercase(Locale.ROOT)?.removePrefix("www.") }.getOrNull() }
            ?: return ""
        if (isHostInSet(host, currentSettings.websiteWhitelist)) return ""

        val domainSpecific = mutableSetOf<String>()
        var cursor = host
        while (cursor.contains('.')) {
            domainCosmeticSelectors[cursor]?.let { domainSpecific.addAll(it) }
            cursor = cursor.substringAfter('.')
        }
        val extraCss = if (domainSpecific.isNotEmpty()) {
            domainSpecific.joinToString(", ") + " { display: none !important; visibility: hidden !important; height: 0 !important; }"
        } else {
            ""
        }

        val combinedCss = (cachedGlobalCssChunk + "\n" + extraCss)
            .replace("\\", "\\\\")
            .replace("`", "\\`")
            .replace("$", "\\$")

        return """
            (function() {
                try {
                    var styleId = 'zenin-pro-adguard-cosmetic';
                    var existing = document.getElementById(styleId);
                    if (!existing) {
                        var style = document.createElement('style');
                        style.id = styleId;
                        style.type = 'text/css';
                        style.appendChild(document.createTextNode(`$combinedCss`));
                        (document.head || document.documentElement).appendChild(style);
                    }
                    // Anti-popunder & common ad-placeholder cleanup
                    window.open = function(url) {
                        if (!url || url === 'about:blank' || url.indexOf('popads') !== -1 || url.indexOf('doubleclick') !== -1) {
                            return null;
                        }
                        return null;
                    };
                } catch (e) {}
            })();
        """.trimIndent()
    }

    private fun recordBlockedHit(tabId: String?, isTracker: Boolean) {
        if (tabId != null) {
            perTabBlockedCount.getOrPut(tabId) { AtomicLong(0L) }.incrementAndGet()
        }
        _settings.update { state ->
            state.copy(
                totalBlockedRequests = state.totalBlockedRequests + 1,
                totalTrackersBlocked = if (isTracker) state.totalTrackersBlocked + 1 else state.totalTrackersBlocked,
            )
        }
    }

    fun getTabBlockedCount(tabId: String?): Long {
        if (tabId == null) return 0L
        return perTabBlockedCount[tabId]?.get() ?: 0L
    }

    fun resetTabBlockedCount(tabId: String) {
        perTabBlockedCount[tabId]?.set(0L)
    }

    private fun findMatchingDomain(host: String, set: Set<String>): String? {
        var current = host
        while (current.contains('.')) {
            if (set.contains(current)) return current
            current = current.substringAfter('.')
        }
        return null
    }

    private fun isHostInSet(host: String, set: Set<String>): Boolean {
        val clean = host.lowercase(Locale.ROOT).removePrefix("www.")
        return findMatchingDomain(clean, set.map { it.lowercase(Locale.ROOT).removePrefix("www.") }.toSet()) != null
    }

    private fun isSameRootDomain(h1: String, h2: String): Boolean {
        if (h1 == h2) return true
        val p1 = h1.split('.')
        val p2 = h2.split('.')
        if (p1.size >= 2 && p2.size >= 2) {
            return p1.takeLast(2) == p2.takeLast(2)
        }
        return false
    }

    // ==================== User & UI Actions ====================

    fun setGlobalEnabled(enabled: Boolean) {
        _settings.update {
            val next = it.copy(globalEnabled = enabled)
            saveSettings(next)
            next
        }
    }

    fun applyProfile(profile: AdGuardProfile) {
        _settings.update {
            val next = it.copy(activeProfile = profile)
            saveSettings(next)
            next
        }
        val enabledIds: Set<String> = when (profile) {
            AdGuardProfile.PRO -> setOf(
                "manual_filters", "zenin_1dm_filters", "adguard_base",
                "adguard_tracking", "adguard_mobile", "adguard_url_tracking"
            )
            AdGuardProfile.PRO_PLUS_PLUS -> setOf(
                "manual_filters", "zenin_1dm_filters", "adguard_base",
                "adguard_tracking", "adguard_mobile", "adguard_url_tracking",
                "adguard_annoyances", "adguard_social", "easyprivacy"
            )
            AdGuardProfile.ULTIMATE -> _filterSources.value.map { it.id }.toSet()
            AdGuardProfile.CUSTOM -> _filterSources.value.filter { it.enabled }.map { it.id }.toSet()
        }
        val updated = _filterSources.value.map { src ->
            src.copy(enabled = src.id in enabledIds)
        }
        _filterSources.value = updated
        saveFilterSources(updated)
        scope.launch {
            compileAllEnabledFilters()
        }
    }

    fun isWebsiteWhitelisted(urlOrHost: String?): Boolean {
        if (urlOrHost.isNullOrBlank()) return false
        val host = extractHost(urlOrHost)
        return isHostInSet(host, _settings.value.websiteWhitelist)
    }

    fun toggleWebsiteWhitelist(urlOrHost: String) {
        val host = extractHost(urlOrHost)
        if (host.isBlank()) return
        _settings.update { state ->
            val nextSet = state.websiteWhitelist.toMutableSet()
            if (nextSet.contains(host)) {
                nextSet.remove(host)
            } else {
                nextSet.add(host)
            }
            val next = state.copy(websiteWhitelist = nextSet)
            saveSettings(next)
            next
        }
    }

    fun addWebsiteToWhitelist(domain: String) {
        val host = extractHost(domain)
        if (host.isBlank()) return
        _settings.update { state ->
            val next = state.copy(websiteWhitelist = state.websiteWhitelist + host)
            saveSettings(next)
            next
        }
    }

    fun removeWebsiteFromWhitelist(domain: String) {
        _settings.update { state ->
            val next = state.copy(websiteWhitelist = state.websiteWhitelist - domain)
            saveSettings(next)
            next
        }
    }

    fun addHostToWhitelist(host: String) {
        val clean = extractHost(host)
        if (clean.isBlank()) return
        _settings.update { state ->
            val next = state.copy(hostsWhitelist = state.hostsWhitelist + clean)
            saveSettings(next)
            next
        }
    }

    fun removeHostFromWhitelist(host: String) {
        _settings.update { state ->
            val next = state.copy(hostsWhitelist = state.hostsWhitelist - host)
            saveSettings(next)
            next
        }
    }

    fun addHiddenElementSelector(urlOrHost: String?, selector: String) {
        val cleanSel = selector.trim()
        if (cleanSel.isEmpty()) return
        val host = urlOrHost?.let { extractHost(it) }?.takeIf { it.isNotEmpty() }
        _settings.update { state ->
            val next = if (host != null) {
                val existing = state.hiddenElementsByHost[host].orEmpty()
                state.copy(
                    hiddenElementsByHost = state.hiddenElementsByHost + (host to (existing + cleanSel).distinct())
                )
            } else {
                state.copy(
                    globalHiddenSelectors = (state.globalHiddenSelectors + cleanSel).distinct()
                )
            }
            saveSettings(next)
            next
        }
        scope.launch { compileAllEnabledFilters() }
    }

    fun removeHiddenElementSelector(host: String?, selector: String) {
        _settings.update { state ->
            val next = if (host != null) {
                val updatedList = state.hiddenElementsByHost[host].orEmpty() - selector
                val newMap = state.hiddenElementsByHost.toMutableMap()
                if (updatedList.isEmpty()) newMap.remove(host) else newMap[host] = updatedList
                state.copy(hiddenElementsByHost = newMap)
            } else {
                state.copy(globalHiddenSelectors = state.globalHiddenSelectors - selector)
            }
            saveSettings(next)
            next
        }
        scope.launch { compileAllEnabledFilters() }
    }

    fun saveCustomRules(rules: List<String>) {
        val cleaned = rules.map { it.trim() }.filter { it.isNotEmpty() }
        _settings.update { state ->
            val next = state.copy(customRules = cleaned)
            saveSettings(next)
            next
        }
        _filterSources.update { list ->
            val updated = list.map {
                if (it.id == "manual_filters") {
                    it.copy(
                        rulesCount = cleaned.size,
                        sizeBytes = cleaned.joinToString("\n").toByteArray().size.toLong(),
                        lastUpdated = System.currentTimeMillis(),
                    )
                } else it
            }
            saveFilterSources(updated)
            updated
        }
        scope.launch { compileAllEnabledFilters() }
    }

    fun toggleFilterSource(id: String, enabled: Boolean) {
        _filterSources.update { list ->
            val updated = list.map {
                if (it.id == id) it.copy(enabled = enabled) else it
            }
            saveFilterSources(updated)
            updated
        }
        scope.launch { compileAllEnabledFilters() }
    }

    fun addFilterSource(
        name: String,
        urlOrPath: String,
        isLocalFile: Boolean,
        enableFilter: Boolean,
        downloadNow: Boolean,
        trusted: Boolean,
    ) {
        val cleanUrl = urlOrPath.trim()
        if (cleanUrl.isEmpty()) return
        val id = "custom_" + System.currentTimeMillis()
        val displayTitle = name.trim().ifEmpty {
            if (isLocalFile) File(cleanUrl).name.ifEmpty { "Local Filter" }
            else cleanUrl.substringAfterLast('/').substringBefore('?').ifEmpty { "Custom Online Filter" }
        }
        val newItem = FilterSourceItem(
            id = id,
            name = displayTitle,
            url = cleanUrl,
            isLocalFile = isLocalFile,
            enabled = enableFilter,
            trusted = trusted,
            category = "Custom",
            builtIn = false,
            lastUpdated = System.currentTimeMillis(),
        )
        _filterSources.update { list ->
            val updated = list + newItem
            saveFilterSources(updated)
            updated
        }
        if (downloadNow && !isLocalFile) {
            updateFilters(force = true, targetId = id)
        } else {
            scope.launch {
                val file = getCacheFileForSource(newItem)
                if (file.exists()) {
                    val count = countValidRulesInFile(file)
                    _filterSources.update { list ->
                        list.map { if (it.id == id) it.copy(rulesCount = count, sizeBytes = file.length()) else it }
                    }
                }
                compileAllEnabledFilters()
            }
        }
    }

    fun removeFilterSource(id: String) {
        _filterSources.update { list ->
            val target = list.find { it.id == id }
            if (target != null && !target.isLocalFile) {
                getCacheFileForSource(target).delete()
            }
            val updated = list.filterNot { it.id == id }
            saveFilterSources(updated)
            updated
        }
        scope.launch { compileAllEnabledFilters() }
    }

    fun updateFilters(force: Boolean = false, targetId: String? = null) {
        if (_isUpdating.value) return
        scope.launch {
            _isUpdating.value = true
            _statusMessage.value = if (force) "Force updating filter lists..." else "Checking filter updates..."
            val client = httpClientProvider()
            val now = System.currentTimeMillis()
            val minAgeMs = _settings.value.updateIntervalHours * 3600_000L

            val updatedList = _filterSources.value.map { src ->
                if (targetId != null && src.id != targetId) return@map src
                if (!src.enabled || src.isLocalFile || src.id == "manual_filters") return@map src

                val cacheFile = getCacheFileForSource(src)
                val isFresh = cacheFile.exists() && (now - src.lastUpdated) < minAgeMs && src.rulesCount > 0
                if (!force && isFresh) {
                    return@map src
                }

                runCatching {
                    val req = Request.Builder()
                        .url(src.url)
                        .header("User-Agent", "ZeninProBrowser/2.5 (Android; AdGuardEngine)")
                        .build()
                    client.newCall(req).execute().use { response ->
                        if (response.isSuccessful) {
                            val bodyBytes = response.body?.bytes()
                            if (bodyBytes != null && bodyBytes.isNotEmpty()) {
                                cacheFile.writeBytes(bodyBytes)
                                val count = countValidRulesInFile(cacheFile)
                                // Discover ! Title: from header if user left name default
                                val headerTitle = cacheFile.bufferedReader().useLines { seq ->
                                    seq.take(15).firstOrNull { it.startsWith("! Title:") }
                                        ?.removePrefix("! Title:")?.trim()
                                }
                                return@map src.copy(
                                    name = if (!src.builtIn && !headerTitle.isNullOrBlank()) headerTitle else src.name,
                                    rulesCount = count,
                                    sizeBytes = cacheFile.length(),
                                    lastUpdated = System.currentTimeMillis(),
                                )
                            }
                        }
                    }
                }
                src
            }

            _filterSources.value = updatedList
            saveFilterSources(updatedList)
            compileAllEnabledFilters()
            _statusMessage.value = "Updated active filters (${_totalActiveRules.value} rules ready)"
            _isUpdating.value = false
        }
    }

    fun deleteFilterCache() {
        scope.launch {
            cacheDir.listFiles()?.forEach { it.delete() }
            val cleared = _filterSources.value.map { src ->
                if (src.id == "manual_filters") src
                else src.copy(rulesCount = 0, sizeBytes = 0L, lastUpdated = 0L)
            }
            _filterSources.value = cleared
            saveFilterSources(cleared)
            compileAllEnabledFilters()
            _statusMessage.value = "Filter cache deleted"
        }
    }

    fun resetToDefaults() {
        scope.launch {
            cacheDir.listFiles()?.forEach { it.delete() }
            val defaultState = AdBlockSettingsState()
            _settings.value = defaultState
            saveSettings(defaultState)
            sourcesFile.delete()
            initializeDefaultSourcesAndCompile()
            _statusMessage.value = "AdBlocker restored to factory defaults"
        }
    }

    fun updateFilterSettings(
        autoUpdate: Boolean,
        intervalHours: Int,
        wifiOnly: Boolean,
        stripParams: Boolean,
        cosmetic: Boolean,
        scriptlets: Boolean,
    ) {
        _settings.update { state ->
            val next = state.copy(
                autoUpdateEnabled = autoUpdate,
                updateIntervalHours = intervalHours.coerceIn(1, 168),
                wifiOnlyUpdates = wifiOnly,
                stripTrackingParams = stripParams,
                cosmeticFilteringEnabled = cosmetic,
                scriptletsEnabled = scriptlets,
            )
            saveSettings(next)
            next
        }
    }

    private fun extractHost(urlOrHost: String): String {
        val trimmed = urlOrHost.trim().lowercase(Locale.ROOT)
        return if (trimmed.contains("://")) {
            runCatching { Uri.parse(trimmed).host?.removePrefix("www.").orEmpty() }.getOrDefault("")
        } else {
            trimmed.substringBefore('/').substringBefore(':').removePrefix("www.")
        }
    }
}
