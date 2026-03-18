package com.example.myapplication.core.websocket

import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import okhttp3.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatWebSocketClient @Inject constructor() {
    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    private val _messages = Channel<String>(Channel.UNLIMITED)
    val messages = _messages.receiveAsFlow()

    private var isConnected = false

    fun connect(sessionId: String, token: String) {
        // 1. 使用 /ws/native 路径，并将 token 作为 URL 参数
        // 请将 10.120.253.80 替换为 A 电脑的实际 IP（例如 192.168.45.146）
        val url = "ws://10.120.253.80:8080/ws/native?token=$token"
        val request = Request.Builder()
            .url(url)
            .build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                super.onOpen(webSocket, response)
                isConnected = true
                Log.d("WebSocket", "连接成功")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                super.onMessage(webSocket, text)
                _messages.trySend(text)
                Log.d("WebSocket", "收到消息: $text")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                super.onFailure(webSocket, t, response)
                isConnected = false
                Log.e("WebSocket", "连接失败", t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosed(webSocket, code, reason)
                isConnected = false
                Log.d("WebSocket", "连接关闭: $reason")
            }
        })
    }

    fun sendMessage(sessionId: String, content: String) {
        if (!isConnected) {
            Log.e("WebSocket", "发送消息失败：未连接")
            return
        }
        val json = """{"type":"user_message","sessionId":"$sessionId","content":"$content"}"""
        webSocket?.send(json)
    }

    fun sendRaw(json: String) {
        if (!isConnected) {
            Log.e("WebSocket", "发送原始消息失败：未连接")
            return
        }
        webSocket?.send(json)
    }

    fun disconnect() {
        webSocket?.close(1000, "正常关闭")
        // client.dispatcher.executorService.shutdown()
    }
}