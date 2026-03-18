package com.example.myapplication.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.model.ChatMessage
import com.example.myapplication.core.websocket.ChatWebSocketClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.util.UUID

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val webSocketClient: ChatWebSocketClient
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _sessionId = MutableStateFlow(UUID.randomUUID().toString())
    val sessionId: StateFlow<String> = _sessionId

    init {
        // 监听 WebSocket 接收的消息
        viewModelScope.launch {
            webSocketClient.messages.collect { serverMessage ->
                // 解析服务端消息并转换为 ChatMessage
                val chatMessage = parseServerMessage(serverMessage)
                _messages.value = _messages.value + chatMessage
            }
        }
        // 连接 WebSocket
        webSocketClient.connect()
    }

    fun sendMessage(content: String) {
        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            content = content,
            isUser = true,
            timestamp = System.currentTimeMillis()
        )
        _messages.value = _messages.value + userMessage
        webSocketClient.sendMessage(sessionId.value, content)
    }

    private fun parseServerMessage(json: String): ChatMessage {
        // 使用 kotlinx.serialization 解析 JSON，返回 ChatMessage
        // 暂时简单处理
        return ChatMessage(
            id = UUID.randomUUID().toString(),
            content = json, // 临时直接显示 JSON，后期解析
            isUser = false,
            timestamp = System.currentTimeMillis()
        )
    }

    override fun onCleared() {
        webSocketClient.disconnect()
        super.onCleared()
    }
}