package com.iancompany.voiceledger

internal object VoiceEndpoint {
    fun validate(endpoint: String, token: String, localDebug: Boolean) {
        if (localDebug) {
            require(endpoint == "http://127.0.0.1:18765/transcribe" && token.isEmpty())
        } else {
            require(endpoint.matches(Regex("https://[a-z0-9][a-z0-9.-]+/transcribe")))
            require(token.matches(Regex("[a-f0-9]{64}")))
        }
    }
}
