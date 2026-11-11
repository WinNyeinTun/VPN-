package com.example.core

import com.example.data.GalaxyPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder

object XrayConfigBuilder {

    data class ParsedOutbound(
        val protocol: String,
        val address: String,
        val port: Int,
        val uuidOrPassword: String,
        val transport: String = "tcp",
        val security: String = "none",
        val sni: String = "",
        val host: String = "",
        val path: String = "",
        val flow: String = "",
        val fingerprint: String = "chrome",
        val serviceName: String = "",
        val encryptionMethod: String = "aes-128-gcm"
    )

    private val STANDARD_URI_REGEX = Regex("""^([a-zA-Z0-9]+)://([^@]+)@([^:/?#]+)(?::(\d+))?(?:\?([^#]*))?(?:#(.*))?$""")

    private fun decodeBase64(str: String): String {
        val clean = str.trim().replace("\n", "").replace("\r", "")
        return try {
            String(java.util.Base64.getUrlDecoder().decode(clean))
        } catch (_: Exception) {
            try {
                String(java.util.Base64.getDecoder().decode(clean))
            } catch (_: Exception) {
                clean
            }
        }
    }

    private fun parseQueryParams(queryStr: String?): Map<String, String> {
        if (queryStr.isNullOrBlank()) return emptyMap()
        val result = mutableMapOf<String, String>()
        queryStr.split("&").forEach { pair ->
            val idx = pair.indexOf('=')
            if (idx != -1) {
                val key = pair.substring(0, idx)
                val rawVal = pair.substring(idx + 1)
                val value = try { URLDecoder.decode(rawVal, "UTF-8") } catch (_: Exception) { rawVal }
                result[key] = value
            }
        }
        return result
    }

    fun parseUri(configUri: String): ParsedOutbound? {
        val trimmed = configUri.trim()
        return try {
            when {
                trimmed.startsWith("vless://", ignoreCase = true) -> parseVless(trimmed)
                trimmed.startsWith("trojan://", ignoreCase = true) -> parseTrojan(trimmed)
                trimmed.startsWith("vmess://", ignoreCase = true) -> parseVmess(trimmed)
                trimmed.startsWith("ss://", ignoreCase = true) -> parseShadowsocks(trimmed)
                else -> null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun parseVless(uriStr: String): ParsedOutbound {
        val match = STANDARD_URI_REGEX.matchEntire(uriStr)
        if (match != null) {
            val uuid = match.groupValues[2]
            val host = match.groupValues[3]
            val port = match.groupValues[4].toIntOrNull() ?: 443
            val params = parseQueryParams(match.groupValues[5])

            return ParsedOutbound(
                protocol = "vless",
                address = host,
                port = port,
                uuidOrPassword = uuid,
                transport = params["type"] ?: "tcp",
                security = params["security"] ?: "none",
                sni = params["sni"] ?: params["peer"] ?: "",
                host = params["host"] ?: "",
                path = params["path"] ?: "/",
                flow = params["flow"] ?: "",
                fingerprint = params["fp"] ?: "chrome",
                serviceName = params["serviceName"] ?: ""
            )
        }
        return ParsedOutbound("vless", "", 443, "")
    }

    private fun parseTrojan(uriStr: String): ParsedOutbound {
        val match = STANDARD_URI_REGEX.matchEntire(uriStr)
        if (match != null) {
            val password = match.groupValues[2]
            val host = match.groupValues[3]
            val port = match.groupValues[4].toIntOrNull() ?: 443
            val params = parseQueryParams(match.groupValues[5])

            return ParsedOutbound(
                protocol = "trojan",
                address = host,
                port = port,
                uuidOrPassword = password,
                transport = params["type"] ?: "tcp",
                security = params["security"] ?: "tls",
                sni = params["sni"] ?: params["peer"] ?: host,
                host = params["host"] ?: "",
                path = params["path"] ?: "/",
                serviceName = params["serviceName"] ?: ""
            )
        }
        return ParsedOutbound("trojan", "", 443, "")
    }

    private fun parseVmess(uriStr: String): ParsedOutbound {
        val raw = uriStr.substring(8).split("#")[0].trim()
        val decoded = decodeBase64(raw)
        val json = JSONObject(decoded)

        val host = json.optString("add", "")
        val port = json.optInt("port", 443)
        val id = json.optString("id", "")
        val net = json.optString("net", "ws")
        val tls = json.optString("tls", "")
        val path = json.optString("path", "/")
        val wsHost = json.optString("host", "")
        val sni = json.optString("sni", "")

        return ParsedOutbound(
            protocol = "vmess",
            address = host,
            port = port,
            uuidOrPassword = id,
            transport = if (net.isEmpty()) "tcp" else net,
            security = if (tls.equals("tls", ignoreCase = true)) "tls" else "none",
            sni = if (sni.isNotEmpty()) sni else wsHost,
            host = wsHost,
            path = path
        )
    }

    private fun parseShadowsocks(uriStr: String): ParsedOutbound {
        val raw = uriStr.substring(5).split("#")[0].trim()
        return if (raw.contains("@")) {
            val parts = raw.split("@")
            val userInfo = parts[0]
            val decodedUserInfo = decodeBase64(userInfo)
            val userParts = decodedUserInfo.split(":")
            val serverParts = parts[1].split(":")
            ParsedOutbound(
                protocol = "shadowsocks",
                address = serverParts[0],
                port = serverParts[1].toIntOrNull() ?: 443,
                uuidOrPassword = userParts.getOrNull(1) ?: "",
                encryptionMethod = userParts.getOrNull(0) ?: "aes-128-gcm"
            )
        } else {
            val decoded = decodeBase64(raw)
            val atIdx = decoded.indexOf('@')
            val userParts = decoded.substring(0, atIdx).split(":")
            val serverParts = decoded.substring(atIdx + 1).split(":")
            ParsedOutbound(
                protocol = "shadowsocks",
                address = serverParts[0],
                port = serverParts[1].toIntOrNull() ?: 443,
                uuidOrPassword = userParts.getOrNull(1) ?: "",
                encryptionMethod = userParts.getOrNull(0) ?: "aes-128-gcm"
            )
        }
    }

    fun buildFullJsonConfig(configUri: String, prefs: GalaxyPreferences): String {
        val outbound = parseUri(configUri)
        val root = JSONObject()

        // 1. Log configuration
        root.put("log", JSONObject().apply {
            put("loglevel", "warning")
        })

        // 2. DNS configuration
        val dnsServers = JSONArray()
        if (prefs.remoteDns.isNotBlank()) {
            dnsServers.put(prefs.remoteDns.trim())
        } else {
            dnsServers.put("https://cloudflare-dns.com/dns-query")
        }
        prefs.directDns.split(",").forEach {
            val ip = it.trim()
            if (ip.isNotEmpty()) dnsServers.put(ip)
        }
        root.put("dns", JSONObject().apply {
            put("servers", dnsServers)
            put("queryStrategy", "UseIP")
        })

        // 3. Inbounds (Local SOCKS & HTTP proxy ports for TUN2SOCKS / Apps)
        val inbounds = JSONArray()
        val socksInbound = JSONObject().apply {
            put("tag", "socks-in")
            put("port", 10808)
            put("listen", "127.0.0.1")
            put("protocol", "socks")
            put("settings", JSONObject().apply {
                put("auth", "noauth")
                put("udp", true)
            })
            if (prefs.enableSniffing) {
                put("sniffing", JSONObject().apply {
                    put("enabled", true)
                    put("destOverride", JSONArray().apply {
                        put("http")
                        put("tls")
                        put("quic")
                    })
                })
            }
        }
        val httpInbound = JSONObject().apply {
            put("tag", "http-in")
            put("port", 10809)
            put("listen", "127.0.0.1")
            put("protocol", "http")
        }
        inbounds.put(socksInbound)
        inbounds.put(httpInbound)
        root.put("inbounds", inbounds)

        // 4. Outbounds (Proxy, Direct, Block)
        val outbounds = JSONArray()

        val proxyOutbound = JSONObject()
        if (outbound != null) {
            proxyOutbound.put("tag", "proxy")
            proxyOutbound.put("protocol", outbound.protocol)

            val settings = JSONObject()
            when (outbound.protocol) {
                "vless" -> {
                    val user = JSONObject().apply {
                        put("id", outbound.uuidOrPassword)
                        put("encryption", "none")
                        if (outbound.flow.isNotBlank()) put("flow", outbound.flow)
                    }
                    val vnext = JSONObject().apply {
                        put("address", outbound.address)
                        put("port", outbound.port)
                        put("users", JSONArray().apply { put(user) })
                    }
                    settings.put("vnext", JSONArray().apply { put(vnext) })
                }
                "trojan" -> {
                    val server = JSONObject().apply {
                        put("address", outbound.address)
                        put("port", outbound.port)
                        put("password", outbound.uuidOrPassword)
                    }
                    settings.put("servers", JSONArray().apply { put(server) })
                }
                "vmess" -> {
                    val user = JSONObject().apply {
                        put("id", outbound.uuidOrPassword)
                        put("alterId", 0)
                        put("security", "auto")
                    }
                    val vnext = JSONObject().apply {
                        put("address", outbound.address)
                        put("port", outbound.port)
                        put("users", JSONArray().apply { put(user) })
                    }
                    settings.put("vnext", JSONArray().apply { put(vnext) })
                }
                "shadowsocks" -> {
                    val server = JSONObject().apply {
                        put("address", outbound.address)
                        put("port", outbound.port)
                        put("method", outbound.encryptionMethod)
                        put("password", outbound.uuidOrPassword)
                    }
                    settings.put("servers", JSONArray().apply { put(server) })
                }
            }
            proxyOutbound.put("settings", settings)

            // StreamSettings
            val streamSettings = JSONObject().apply {
                put("network", outbound.transport)
                put("security", outbound.security)
                if (outbound.security == "tls" || outbound.security == "reality") {
                    val tlsSettings = JSONObject().apply {
                        put("allowInsecure", false)
                        if (outbound.sni.isNotBlank()) put("serverName", outbound.sni)
                        if (outbound.fingerprint.isNotBlank()) put("fingerprint", outbound.fingerprint)
                    }
                    put(if (outbound.security == "reality") "realitySettings" else "tlsSettings", tlsSettings)
                }
                if (outbound.transport == "ws") {
                    val wsSettings = JSONObject().apply {
                        if (outbound.path.isNotBlank()) put("path", outbound.path)
                        if (outbound.host.isNotBlank()) put("headers", JSONObject().apply { put("Host", outbound.host) })
                    }
                    put("wsSettings", wsSettings)
                }
                if (outbound.transport == "grpc") {
                    val grpcSettings = JSONObject().apply {
                        if (outbound.serviceName.isNotBlank()) put("serviceName", outbound.serviceName)
                        put("multiMode", true)
                    }
                    put("grpcSettings", grpcSettings)
                }
            }
            proxyOutbound.put("streamSettings", streamSettings)

            if (prefs.enableMux) {
                proxyOutbound.put("mux", JSONObject().apply {
                    put("enabled", true)
                    put("concurrency", 8)
                })
            }
        } else {
            // Fallback direct
            proxyOutbound.put("tag", "proxy")
            proxyOutbound.put("protocol", "freedom")
        }
        outbounds.put(proxyOutbound)

        // Freedom (Direct)
        val directOutbound = JSONObject().apply {
            put("tag", "direct")
            put("protocol", "freedom")
        }
        outbounds.put(directOutbound)

        // Blackhole (Block)
        val blockOutbound = JSONObject().apply {
            put("tag", "block")
            put("protocol", "blackhole")
        }
        outbounds.put(blockOutbound)

        root.put("outbounds", outbounds)

        // 5. Routing configuration
        val routing = JSONObject().apply {
            put("domainStrategy", prefs.domainStrategy)
            val rules = JSONArray()

            // Bypass LAN
            if (prefs.bypassMode == "LAN" || prefs.bypassMode == "LAN_CHINA") {
                rules.put(JSONObject().apply {
                    put("type", "field")
                    put("outboundTag", "direct")
                    put("ip", JSONArray().apply {
                        put("geoip:private")
                        put("10.0.0.0/8")
                        put("172.16.0.0/12")
                        put("192.168.0.0/16")
                        put("127.0.0.0/8")
                    })
                })
            }

            // Bypass Mainland China
            if (prefs.bypassMode == "LAN_CHINA") {
                rules.put(JSONObject().apply {
                    put("type", "field")
                    put("outboundTag", "direct")
                    put("ip", JSONArray().apply { put("geoip:cn") })
                })
                rules.put(JSONObject().apply {
                    put("type", "field")
                    put("outboundTag", "direct")
                    put("domain", JSONArray().apply { put("geosite:cn") })
                })
            }

            // Default route to proxy
            rules.put(JSONObject().apply {
                put("type", "field")
                put("outboundTag", "proxy")
                put("network", "tcp,udp")
            })

            put("rules", rules)
        }
        root.put("routing", routing)

        return root.toString(2)
    }
}
