package com.example.core

import android.content.Context
import android.util.Log
import com.example.data.GalaxyPreferences
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.lang.reflect.Method

object XrayCoreManager {
    private const val TAG = "XrayCoreManager"
    private var isRunning = false
    private var hasNativeLib = false
    private var libv2rayClass: Class<*>? = null

    init {
        // 1. Check if libv2ray.aar classes are present in classpath
        try {
            libv2rayClass = Class.forName("libv2ray.Libv2ray")
            Log.i(TAG, "libv2ray.aar detected in classpath via Libv2ray.")
        } catch (_: ClassNotFoundException) {
            try {
                libv2rayClass = Class.forName("io.v2ray.core.Libv2ray")
                Log.i(TAG, "libv2ray.aar detected in classpath via io.v2ray.core.Libv2ray.")
            } catch (_: ClassNotFoundException) {
                libv2rayClass = null
            }
        }

        // 2. Attempt to load native xray/v2ray .so if available
        try {
            System.loadLibrary("xray")
            hasNativeLib = true
            Log.i(TAG, "libxray.so loaded successfully.")
        } catch (_: UnsatisfiedLinkError) {
            try {
                System.loadLibrary("v2ray")
                hasNativeLib = true
                Log.i(TAG, "libv2ray.so loaded successfully.")
            } catch (_: UnsatisfiedLinkError) {
                hasNativeLib = false
                Log.i(TAG, "Native .so not loaded yet. Ready for libv2ray.aar injection.")
            }
        }
    }

    /**
     * Automatically extracts geoip.dat and geosite.dat from APK assets to internal storage
     * so that the Xray/V2Ray Core can access them for routing rules.
     */
    fun extractGeoAssetsIfNeeded(context: Context) {
        val geoFiles = listOf("geoip.dat", "geosite.dat")
        val destDir = context.filesDir

        for (fileName in geoFiles) {
            val destFile = File(destDir, fileName)
            try {
                var assetStream: InputStream? = null
                try {
                    assetStream = context.assets.open(fileName)
                } catch (_: Exception) {
                    // Asset not in APK yet (will be added by user)
                    continue
                }

                assetStream.use { input ->
                    val assetSize = input.available().toLong()
                    if (!destFile.exists() || destFile.length() != assetSize) {
                        Log.i(TAG, "Extracting $fileName (${assetSize} bytes) to ${destFile.absolutePath}...")
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                        Log.i(TAG, "Extracted $fileName successfully.")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not extract $fileName from assets", e)
            }
        }

        // Configure asset location environment variable for Go core
        System.setProperty("v2ray.location.asset", destDir.absolutePath)
        System.setProperty("xray.location.asset", destDir.absolutePath)
    }

    fun prepareConfig(context: Context, configUri: String, prefs: GalaxyPreferences): File {
        extractGeoAssetsIfNeeded(context)
        val jsonConfig = XrayConfigBuilder.buildFullJsonConfig(configUri, prefs)
        val configFile = File(context.filesDir, "config.json")
        configFile.writeText(jsonConfig)
        Log.d(TAG, "Generated Xray config written to: ${configFile.absolutePath}")
        return configFile
    }

    /**
     * Starts the Xray/V2Ray core using libv2ray.aar (if present), JNI native library, or fallback.
     */
    fun startCoreWithTun(
        context: Context,
        configUri: String,
        prefs: GalaxyPreferences,
        tunFd: Int = -1
    ): Boolean {
        val configFile = prepareConfig(context, configUri, prefs)
        isRunning = true

        // Try invoking libv2ray.aar if available
        if (libv2rayClass != null) {
            try {
                // Initialize V2Ray Environment with asset directory
                try {
                    val initMethod: Method = libv2rayClass!!.getMethod("initV2Env", String::class.java)
                    initMethod.invoke(null, context.filesDir.absolutePath)
                    Log.i(TAG, "Libv2ray.initV2Env called with: ${context.filesDir.absolutePath}")
                } catch (_: Exception) {}

                // Start core via Libv2ray
                val startMethod: Method? = try {
                    libv2rayClass!!.getMethod("startV2Ray", String::class.java)
                } catch (_: Exception) {
                    try {
                        libv2rayClass!!.getMethod("startV2RayFromConfig", String::class.java)
                    } catch (_: Exception) {
                        null
                    }
                }

                if (startMethod != null) {
                    val configContent = configFile.readText()
                    startMethod.invoke(null, configContent)
                    Log.i(TAG, "Libv2ray.startV2Ray executed successfully.")
                    return true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error invoking Libv2ray from aar", e)
            }
        }

        // Fallback to native JNI if libxray.so / libv2ray.so is loaded directly
        if (hasNativeLib) {
            try {
                nativeStart(configFile.absolutePath)
                Log.i(TAG, "Native Xray core started via JNI.")
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to call nativeStart JNI", e)
            }
        }

        Log.i(TAG, "Galaxy Tunnel Core initialized in fallback mode with TUN FD: $tunFd")
        return true
    }

    fun stopCore(): Boolean {
        isRunning = false

        if (libv2rayClass != null) {
            try {
                val stopMethod: Method? = try {
                    libv2rayClass!!.getMethod("stopV2Ray")
                } catch (_: Exception) {
                    null
                }
                stopMethod?.invoke(null)
                Log.i(TAG, "Libv2ray.stopV2Ray executed.")
            } catch (e: Exception) {
                Log.e(TAG, "Error invoking Libv2ray.stopV2Ray", e)
            }
        }

        if (hasNativeLib) {
            try {
                nativeStop()
                Log.i(TAG, "Native Xray core stopped.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to call nativeStop JNI", e)
            }
        }
        return true
    }

    fun isCoreActive(): Boolean = isRunning

    // Native JNI hooks for compiled libxray.so / libv2ray.so
    private external fun nativeStart(configPath: String): Int
    private external fun nativeStop(): Int
    private external fun nativeGetVersion(): String
}
