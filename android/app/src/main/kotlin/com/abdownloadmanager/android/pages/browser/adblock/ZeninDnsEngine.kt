package com.abdownloadmanager.android.pages.browser.adblock

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.abdownloadmanager.shared.storage.DnsSettings
import com.abdownloadmanager.shared.storage.IDNSSettingsStorage
import com.abdownloadmanager.shared.util.dns.DnsModes
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
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.io.File
import java.net.InetAddress
import java.util.Locale

@Serializable
enum class DnsProviderPreset(
    val displayName: String,
    val dohUrl: String,
    val dotHostname: String,
    val description: String,
) {
    SYSTEM(
        displayName = "System Default DNS",
        dohUrl = "",
        dotHostname = "",
        description = "Uses Android network/system resolver",
    ),
    NEXTDNS_PROFILE(
        displayName = "NextDNS Custom Profile",
        dohUrl = "https://dns.nextdns.io/",
        dotHostname = ".dns.nextdns.io",
        description = "Encrypted NextDNS DoH/DoT with custom Profile ID & blocklist sync",
    ),
    ADGUARD_DNS(
        displayName = "AdGuard DNS (AdBlock + Privacy)",
        dohUrl = "https://dns.adguard-dns.com/dns-query",
        dotHostname = "dns.adguard-dns.com",
        description = "Blocks ads, trackers, and malicious domains at the DNS level",
    ),
    CLOUDFLARE_SECURITY(
        displayName = "Cloudflare 1.1.1.2 (Malware Block)",
        dohUrl = "https://security.cloudflare-dns.com/dns-query",
        dotHostname = "security.cloudflare-dns.com",
        description = "Ultra-fast DNS with anti-malware & phishing filtering",
    ),
    QUAD9_SECURE(
        displayName = "Quad9 9.9.9.9 (Threat Protection)",
        dohUrl = "https://dns.quad9.net/dns-query",
        dotHostname = "dns.quad9.net",
        description = "Swiss privacy-first DNS blocking known threat domains",
    ),
    CUSTOM_DOH(
        displayName = "Custom DoH Endpoint",
        dohUrl = "https://",
        dotHostname = "",
        description = "User-specified RFC 8484 DNS-over-HTTPS endpoint URL",
    )
}

@Serializable
data class NextDnsBlocklistFeed(
    val id: String,
    val name: String,
    val description: String,
    val url: String,
    val enabled: Boolean,
)

@Serializable
data class ZeninDnsConfigState(
    val preset: DnsProviderPreset = DnsProviderPreset.ADGUARD_DNS,
    val nextDnsProfileId: String = "",
    val customDohUrl: String = "https://dns.adguard-dns.com/dns-query",
    val syncNextDnsListsToBrowser: Boolean = true,
    val lastVerifiedLatencyMs: Long = -1L,
    val lastResolvedIp: String = "",
    val connectionVerified: Boolean = false,
    val statusText: String = "Ready",
) {
    fun effectiveDohUrl(): String {
        return when (preset) {
            DnsProviderPreset.SYSTEM -> ""
            DnsProviderPreset.NEXTDNS_PROFILE -> {
                val cleanId = nextDnsProfileId.trim().lowercase(Locale.ROOT)
                if (cleanId.isNotEmpty()) "https://dns.nextdns.io/$cleanId"
                else "https://dns.nextdns.io"
            }
            DnsProviderPreset.ADGUARD_DNS -> DnsProviderPreset.ADGUARD_DNS.dohUrl
            DnsProviderPreset.CLOUDFLARE_SECURITY -> DnsProviderPreset.CLOUDFLARE_SECURITY.dohUrl
            DnsProviderPreset.QUAD9_SECURE -> DnsProviderPreset.QUAD9_SECURE.dohUrl
            DnsProviderPreset.CUSTOM_DOH -> customDohUrl.trim()
        }
    }

    fun effectiveDotHostname(): String {
        return when (preset) {
            DnsProviderPreset.SYSTEM -> ""
            DnsProviderPreset.NEXTDNS_PROFILE -> {
                val cleanId = nextDnsProfileId.trim().lowercase(Locale.ROOT)
                if (cleanId.isNotEmpty()) "$cleanId.dns.nextdns.io"
                else "dns.nextdns.io"
            }
            else -> preset.dotHostname
        }
    }
}

class ZeninDnsEngine(
    private val context: Context,
    private val dnsSettingsStorage: IDNSSettingsStorage?,
    private val adBlockEngine: ZeninAdBlockEngine,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }
    private val configFile = File(context.filesDir, "zenin_adblock/zenin_dns_config.json")

    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<ZeninDnsConfigState> = _state.asStateFlow()

    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    val nextDnsCatalogFeeds = listOf(
        NextDnsBlocklistFeed(
            id = "nextdns_ads_trackers",
            name = "NextDNS Ads & Trackers Blocklist",
            description = "Comprehensive blocklist recommended by NextDNS for ads, trackers & native telemetry",
            url = "https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt",
            enabled = true,
        ),
        NextDnsBlocklistFeed(
            id = "hagezi_pro_plus",
            name = "HaGeZi's Pro++ DNS Blocklist",
            description = "Aggressive sweeper blocking Ads, Tracking, Telemetry, Phishing, Malware & Scam domains",
            url = "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/adblock/pro.plus.mini.txt",
            enabled = true,
        ),
        NextDnsBlocklistFeed(
            id = "oisd_big",
            name = "OISD Blocklist (NextDNS Primary)",
            description = "Industry-standard low-false-positive domain blocklist for mobile & desktop browsers",
            url = "https://abp.oisd.nl/basic/",
            enabled = true,
        ),
        NextDnsBlocklistFeed(
            id = "stevenblack_hosts",
            name = "StevenBlack Unified Hosts",
            description = "Unified hosts file consolidating adware and malware domains",
            url = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
            enabled = false,
        ),
    )

    init {
        applyToSharedDnsStorage(_state.value)
    }

    private fun loadState(): ZeninDnsConfigState {
        if (configFile.exists()) {
            runCatching {
                return json.decodeFromString<ZeninDnsConfigState>(configFile.readText())
            }
        }
        return ZeninDnsConfigState()
    }

    private fun saveState(newState: ZeninDnsConfigState) {
        runCatching {
            configFile.parentFile?.mkdirs()
            configFile.writeText(json.encodeToString(newState))
        }
    }

    private fun applyToSharedDnsStorage(cfg: ZeninDnsConfigState) {
        val dohUrl = cfg.effectiveDohUrl()
        if (dohUrl.isBlank() || cfg.preset == DnsProviderPreset.SYSTEM) {
            dnsSettingsStorage?.dns?.value = DnsSettings(
                mode = DnsModes.System,
                address = ""
            )
        } else {
            dnsSettingsStorage?.dns?.value = DnsSettings(
                mode = DnsModes.DnsOverHttps,
                address = dohUrl
            )
        }
    }

    fun updateDnsConfiguration(
        preset: DnsProviderPreset,
        profileId: String,
        customDohUrl: String,
        syncBlocklists: Boolean,
    ) {
        val updated = _state.value.copy(
            preset = preset,
            nextDnsProfileId = profileId.trim(),
            customDohUrl = customDohUrl.trim(),
            syncNextDnsListsToBrowser = syncBlocklists,
            statusText = "Saved & applied",
        )
        _state.value = updated
        saveState(updated)
        applyToSharedDnsStorage(updated)
        verifyAndMeasureDnsLatency()
    }

    fun verifyAndMeasureDnsLatency(testHost: String = "example.com") {
        if (_isTesting.value) return
        scope.launch {
            _isTesting.value = true
            val current = _state.value
            val dohUrl = current.effectiveDohUrl()
            _state.update { it.copy(statusText = "Testing DNS resolver...") }

            val startNs = System.nanoTime()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (current.preset == DnsProviderPreset.SYSTEM || dohUrl.isBlank()) {
                        val addresses = InetAddress.getAllByName(testHost)
                        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
                        Pair(elapsedMs, addresses.firstOrNull()?.hostAddress ?: "Resolved")
                    } else {
                        val bootstrapClient = OkHttpClient.Builder().build()
                        val doh = DnsOverHttps.Builder()
                            .client(bootstrapClient)
                            .url(dohUrl.toHttpUrl())
                            .bootstrapDnsHosts(
                                InetAddress.getByName("1.1.1.1"),
                                InetAddress.getByName("8.8.8.8"),
                                InetAddress.getByName("9.9.9.9"),
                            )
                            .build()
                        val addresses = doh.lookup(testHost)
                        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
                        Pair(elapsedMs, addresses.firstOrNull()?.hostAddress ?: "Resolved")
                    }
                }
            }

            result.onSuccess { (latency, ip) ->
                val next = _state.value.copy(
                    lastVerifiedLatencyMs = latency,
                    lastResolvedIp = ip,
                    connectionVerified = true,
                    statusText = "Connected (${latency}ms · $ip)",
                )
                _state.value = next
                saveState(next)
            }.onFailure { err ->
                val next = _state.value.copy(
                    connectionVerified = false,
                    statusText = "Check endpoint: ${err.message?.take(45) ?: "Connection failed"}",
                )
                _state.value = next
                saveState(next)
            }
            _isTesting.value = false
        }
    }

    fun syncNextDnsFeedToAdBlocker(feed: NextDnsBlocklistFeed) {
        adBlockEngine.addFilterSource(
            name = feed.name,
            urlOrPath = feed.url,
            isLocalFile = false,
            enableFilter = true,
            downloadNow = true,
            trusted = false,
        )
    }

    fun openAndroidPrivateDnsSettings() {
        runCatching {
            val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure {
            runCatching {
                val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
            }
        }
    }
}
