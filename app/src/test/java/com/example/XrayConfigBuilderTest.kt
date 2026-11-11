package com.example

import com.example.core.XrayConfigBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class XrayConfigBuilderTest {

    @Test
    fun testParseVlessUri() {
        val uri = "vless://b831381d-6324-4d53-ad4f-8cda48b30811@sg01.galaxytunnel.xyz:443?type=ws&security=tls&path=%2Fgalaxy#SG-01"
        val parsed = XrayConfigBuilder.parseUri(uri)
        assertNotNull(parsed)
        assertEquals("vless", parsed?.protocol)
        assertEquals("sg01.galaxytunnel.xyz", parsed?.address)
        assertEquals(443, parsed?.port)
        assertEquals("b831381d-6324-4d53-ad4f-8cda48b30811", parsed?.uuidOrPassword)
        assertEquals("ws", parsed?.transport)
        assertEquals("tls", parsed?.security)
    }

    @Test
    fun testParseTrojanUri() {
        val uri = "trojan://password123@jp02.galaxytunnel.xyz:443?type=ws&security=tls&path=%2Ftrojan#JP-02"
        val parsed = XrayConfigBuilder.parseUri(uri)
        assertNotNull(parsed)
        assertEquals("trojan", parsed?.protocol)
        assertEquals("jp02.galaxytunnel.xyz", parsed?.address)
        assertEquals(443, parsed?.port)
        assertEquals("password123", parsed?.uuidOrPassword)
    }
}
