package com.example.mobile_licenta

interface VoiceStreamListener {
    fun onVoiceStreaming(data: ByteArray?, size: Int)
}