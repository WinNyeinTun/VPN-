package com.example.bridge

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import com.example.MainActivity
import com.example.data.GalaxyPreferences
import com.example.net.InstalledAppsManager
import com.example.net.NetworkPingTester
import com.example.vpn.GalaxyVpnService
import com.example.vpn.GalaxyVpnStateHolder
import com.example.vpn.VpnStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

class GalaxyWebAppInterface(
    private val activity: MainActivity,
    private val webView: WebView
) {
    private val prefs = GalaxyPreferences(activity)
    private val scope = CoroutineScope(Dispatchers.Main)

    @JavascriptInterface
    fun toggleVPN(name: String, config: String) {
        val currentStatus = GalaxyVpnStateHolder.telemetry.value.status
        if (currentStatus == VpnStatus.CONNECTED || currentStatus == VpnStatus.CONNECTING) {
            val stopIntent = Intent(activity, GalaxyVpnService::class.java).apply {
                action = GalaxyVpnService.ACTION_DISCONNECT
            }
            activity.startService(stopIntent)
        } else {
            activity.requestVpnPermissionAndConnect(name, config)
        }
    }

    @JavascriptInterface
    fun syncState() {
        val tel = GalaxyVpnStateHolder.telemetry.value
        val stateStr = when (tel.status) {
            VpnStatus.CONNECTED -> "CONNECTED"
            VpnStatus.CONNECTING -> "CONNECTING"
            VpnStatus.OFFLINE -> "OFFLINE"
        }
        val js = "window.updateFromAndroid('$stateStr', '${tel.durationFormatted}', '${tel.downloadSpeedFormatted}', '${tel.uploadSpeedFormatted}', '${tel.serverRemark}');"
        activity.runOnUiThread {
            webView.evaluateJavascript(js, null)
        }
    }

    @JavascriptInterface
    fun testPing(config: String): String {
        return runBlocking(Dispatchers.IO) {
            val ping = NetworkPingTester.testPing(config)
            ping.toString()
        }
    }

    @JavascriptInterface
    fun getDnsSettings(): String {
        val json = JSONObject().apply {
            put("remoteDns", prefs.remoteDns)
            put("directDns", prefs.directDns)
        }
        return json.toString()
    }

    @JavascriptInterface
    fun saveDnsSettings(remoteDns: String, directDns: String) {
        prefs.remoteDns = remoteDns
        prefs.directDns = directDns
    }

    @JavascriptInterface
    fun saveDns(remoteDns: String) {
        prefs.remoteDns = remoteDns
    }

    @JavascriptInterface
    fun getInstalledApps(): String {
        return InstalledAppsManager.getInstalledAppsJson(activity)
    }

    @JavascriptInterface
    fun saveAppFilter(jsonString: String) {
        prefs.setSelectedAppsJson(jsonString)
    }

    @JavascriptInterface
    fun getCoreSettings(): String {
        val json = JSONObject().apply {
            put("connectionMode", prefs.connectionMode)
            put("bypassMode", prefs.bypassMode)
            put("enableSniffing", prefs.enableSniffing)
            put("enableMux", prefs.enableMux)
            put("domainStrategy", prefs.domainStrategy)
        }
        return json.toString()
    }

    @JavascriptInterface
    fun saveCoreSettings(
        bypassMode: String,
        enableSniffing: Boolean,
        enableMux: Boolean,
        domainStrategy: String,
        connectionMode: String
    ) {
        prefs.bypassMode = bypassMode
        prefs.enableSniffing = enableSniffing
        prefs.enableMux = enableMux
        prefs.domainStrategy = domainStrategy
        prefs.connectionMode = connectionMode
    }

    @JavascriptInterface
    fun getClipboardContent(): String {
        return try {
            val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val item = cm.primaryClip?.getItemAt(0)
            item?.text?.toString() ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    @JavascriptInterface
    fun scanQrCode() {
        activity.runOnUiThread {
            Toast.makeText(
                activity,
                "Point your camera at a V2Ray QR code or paste link from clipboard.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
