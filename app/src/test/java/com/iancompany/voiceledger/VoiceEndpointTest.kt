package com.iancompany.voiceledger

import org.junit.Assert.assertThrows
import org.junit.Test

class VoiceEndpointTest {
    private val token = "a".repeat(64)

    @Test fun cloudRequiresHttpsAndRelayToken() {
        VoiceEndpoint.validate("https://voice.example.com/transcribe", token, false)
        for (endpoint in listOf("http://voice.example.com/transcribe", "https://user:pass@voice.example.com/transcribe",
            "https://voice.example.com/transcribe?key=secret", "https://voice.example.com/other",
            "http://127.0.0.1:18765/transcribe")) {
            assertThrows(IllegalArgumentException::class.java) { VoiceEndpoint.validate(endpoint, token, false) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            VoiceEndpoint.validate("https://voice.example.com/transcribe", "not-a-relay-token", false)
        }
    }

    @Test fun developmentModeOnlyAllowsExactLoopbackWithoutToken() {
        VoiceEndpoint.validate("http://127.0.0.1:18765/transcribe", "", true)
        assertThrows(IllegalArgumentException::class.java) {
            VoiceEndpoint.validate("http://192.168.1.2:18765/transcribe", "", true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            VoiceEndpoint.validate("http://127.0.0.1:18765/transcribe", token, true)
        }
    }
}
