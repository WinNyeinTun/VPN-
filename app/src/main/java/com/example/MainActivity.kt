package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.bridge.GalaxyWebAppInterface
import com.example.ui.theme.MyApplicationTheme
import com.example.vpn.GalaxyVpnService
import com.example.vpn.GalaxyVpnStateHolder
import com.example.vpn.VpnStatus
import kotlinx.coroutines.launch
import android.net.Uri
import android.webkit.PermissionRequest
import android.webkit.ValueCallback

class MainActivity : ComponentActivity() {

    private var targetServerName = ""
    private var targetServerConfig = ""
    private var webViewInstance: WebView? = null
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (filePathCallback == null) return@registerForActivityResult
        val uris = if (result.resultCode == RESULT_OK && result.data != null) {
            val clipData = result.data?.clipData
            val dataString = result.data?.dataString
            if (clipData != null) {
                val list = mutableListOf<Uri>()
                for (i in 0 until clipData.itemCount) {
                    list.add(clipData.getItemAt(i).uri)
                }
                list.toTypedArray()
            } else if (dataString != null) {
                arrayOf(Uri.parse(dataString))
            } else null
        } else null
        filePathCallback?.onReceiveValue(uris)
        filePathCallback = null
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            webViewInstance?.post {
                webViewInstance?.evaluateJavascript("if(window.startQrImportModal) window.startQrImportModal();", null)
            }
        }
    }

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            connectVpn(targetServerName, targetServerConfig)
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Permission handled
    }

    fun requestCameraPermissionAndScan() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            webViewInstance?.post {
                webViewInstance?.evaluateJavascript("if(window.startQrImportModal) window.startQrImportModal();", null)
            }
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestNotificationPermission()

        setContent {
            MyApplicationTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(androidx.compose.ui.graphics.Color(0xFF0A0A09))
                        .statusBarsPadding()
                ) {
                    GalaxyWebViewContainer(
                        onWebViewCreated = { webView ->
                            webViewInstance = webView
                        }
                    )
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val wv = webViewInstance
                if (wv != null && wv.canGoBack()) {
                    wv.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        observeVpnState()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    fun requestVpnPermissionAndConnect(serverName: String, config: String) {
        targetServerName = serverName
        targetServerConfig = config

        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            vpnPermissionLauncher.launch(prepareIntent)
        } else {
            connectVpn(serverName, config)
        }
    }

    private fun connectVpn(serverName: String, config: String) {
        val startIntent = Intent(this, GalaxyVpnService::class.java).apply {
            action = GalaxyVpnService.ACTION_CONNECT
            putExtra(GalaxyVpnService.EXTRA_SERVER_NAME, serverName)
            putExtra(GalaxyVpnService.EXTRA_CONFIG, config)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(startIntent)
        } else {
            startService(startIntent)
        }
    }

    private fun observeVpnState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                GalaxyVpnStateHolder.telemetry.collect { tel ->
                    val stateStr = when (tel.status) {
                        VpnStatus.CONNECTED -> "CONNECTED"
                        VpnStatus.CONNECTING -> "CONNECTING"
                        VpnStatus.OFFLINE -> "OFFLINE"
                    }
                    val js = "window.updateFromAndroid('$stateStr', '${tel.durationFormatted}', '${tel.downloadSpeedFormatted}', '${tel.uploadSpeedFormatted}', '${tel.serverRemark}');"
                    webViewInstance?.post {
                        webViewInstance?.evaluateJavascript(js, null)
                    }
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    private fun GalaxyWebViewContainer(
        onWebViewCreated: (WebView) -> Unit
    ) {
        val webAppInterface = remember {
            GalaxyWebAppInterface(this@MainActivity, WebView(this@MainActivity))
        }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(Color.parseColor("#0A0A09"))

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onShowFileChooser(
                            webView: WebView?,
                            filePathCallback: ValueCallback<Array<Uri>>?,
                            fileChooserParams: FileChooserParams?
                        ): Boolean {
                            this@MainActivity.filePathCallback?.onReceiveValue(null)
                            this@MainActivity.filePathCallback = filePathCallback

                            val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "*/*"
                            }
                            try {
                                filePickerLauncher.launch(intent)
                            } catch (_: Exception) {
                                this@MainActivity.filePathCallback = null
                                return false
                            }
                            return true
                        }

                        override fun onPermissionRequest(request: PermissionRequest?) {
                            request?.grant(request.resources)
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            return false
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            // Initial state sync after page load
                            val tel = GalaxyVpnStateHolder.telemetry.value
                            val stateStr = when (tel.status) {
                                VpnStatus.CONNECTED -> "CONNECTED"
                                VpnStatus.CONNECTING -> "CONNECTING"
                                VpnStatus.OFFLINE -> "OFFLINE"
                            }
                            val js = "if(window.updateFromAndroid) { window.updateFromAndroid('$stateStr', '${tel.durationFormatted}', '${tel.downloadSpeedFormatted}', '${tel.uploadSpeedFormatted}', '${tel.serverRemark}'); }"
                            view?.evaluateJavascript(js, null)
                        }
                    }

                    val webInterface = GalaxyWebAppInterface(this@MainActivity, this)
                    addJavascriptInterface(webInterface, "Android")

                    loadUrl("file:///android_asset/galaxy_tunnel.html")
                    onWebViewCreated(this)
                }
            }
        )
    }

    override fun onDestroy() {
        webViewInstance?.destroy()
        webViewInstance = null
        super.onDestroy()
    }
}
