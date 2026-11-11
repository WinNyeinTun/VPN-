package com.example.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

class GalaxyPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("galaxy_tunnel_prefs", Context.MODE_PRIVATE)

    var remoteDns: String
        get() = prefs.getString("remote_dns", "https://cloudflare-dns.com/dns-query")
            ?: "https://cloudflare-dns.com/dns-query"
        set(value) = prefs.edit().putString("remote_dns", value).apply()

    var directDns: String
        get() = prefs.getString("direct_dns", "1.1.1.1, 1.0.0.1") ?: "1.1.1.1, 1.0.0.1"
        set(value) = prefs.edit().putString("direct_dns", value).apply()

    var bypassMode: String
        get() = prefs.getString("bypass_mode", "GLOBAL") ?: "GLOBAL"
        set(value) = prefs.edit().putString("bypass_mode", value).apply()

    var domainStrategy: String
        get() = prefs.getString("domain_strategy", "IPIfNonMatch") ?: "IPIfNonMatch"
        set(value) = prefs.edit().putString("domain_strategy", value).apply()

    var enableSniffing: Boolean
        get() = prefs.getBoolean("enable_sniffing", true)
        set(value) = prefs.edit().putBoolean("enable_sniffing", value).apply()

    var enableMux: Boolean
        get() = prefs.getBoolean("enable_mux", false)
        set(value) = prefs.edit().putBoolean("enable_mux", value).apply()

    var connectionMode: String
        get() = prefs.getString("connection_mode", "VPN") ?: "VPN"
        set(value) = prefs.edit().putString("connection_mode", value).apply()

    var selectedApps: Set<String>
        get() = prefs.getStringSet("selected_apps", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("selected_apps", value).apply()

    fun setSelectedAppsJson(jsonArrayString: String) {
        val set = mutableSetOf<String>()
        try {
            val arr = JSONArray(jsonArrayString)
            for (i in 0 until arr.length()) {
                set.add(arr.getString(i))
            }
        } catch (_: Exception) {}
        selectedApps = set
    }

    fun getSelectedAppsJson(): String {
        val arr = JSONArray()
        selectedApps.forEach { arr.put(it) }
        return arr.toString()
    }

    var lastServerName: String
        get() = prefs.getString("last_server_name", "SG-01 (VLESS)") ?: "SG-01 (VLESS)"
        set(value) = prefs.edit().putString("last_server_name", value).apply()

    var lastServerConfig: String
        get() = prefs.getString("last_server_config", "") ?: ""
        set(value) = prefs.edit().putString("last_server_config", value).apply()
}
