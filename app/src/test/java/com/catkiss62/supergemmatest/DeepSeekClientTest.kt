package com.catkiss62.supergemmatest

import org.junit.Assert.assertEquals
import org.junit.Test

class DeepSeekClientTest {
    @Test
    fun normalizesBaseAndFullUrls() {
        assertEquals(
            "https://api.deepseek.com/chat/completions",
            DeepSeekClient.normalizeEndpoint("https://api.deepseek.com/"),
        )
        assertEquals(
            "https://example.com/v1/chat/completions",
            DeepSeekClient.normalizeEndpoint("https://example.com/v1"),
        )
        assertEquals(
            "https://example.com/chat/completions",
            DeepSeekClient.normalizeEndpoint("https://example.com/chat/completions"),
        )
    }
}
