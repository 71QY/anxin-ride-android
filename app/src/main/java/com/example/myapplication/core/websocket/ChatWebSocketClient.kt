package com.example.myapplication.core.websocket

import okhttp3.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

@Singleton
class ChatWebSocketClient @Inject constructor() {
    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    private val _messages = Channel<String>(Channel.UNLIMITED)
    val messages = _messages.receiveAsFlow()

    fun connect() {
        val request = Request.Builder()
            .url("ws://你的后端IP:8080/ws/agent/chat") // 替换为 A 的实际地址
            .build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                _messages.trySend(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                t.printStackTrace()
            }
        })
    }

    fun sendMessage(sessionId: String, content: String) {
        val json = """{"type":"user_message","sessionId":"$sessionId","content":"$content"}"""
        webSocket?.send(json)
    }

    fun disconnect() {
        webSocket?.close(1000, "正常关闭")
        client.dispatcher.executorService.shutdown()
    }
}