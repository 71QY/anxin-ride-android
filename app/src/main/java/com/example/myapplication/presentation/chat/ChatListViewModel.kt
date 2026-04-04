package com.example.myapplication.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.model.ChatMessage
import com.example.myapplication.data.model.ChatSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatListViewModel @Inject constructor() : ViewModel() {
    
    private val _sessions = MutableStateFlow(ChatSession.getMockSessions())
    val sessions: StateFlow<List<ChatSession>> = _sessions.asStateFlow()
    
    private val _selectedSessionId = MutableStateFlow<String?>(null)
    val selectedSessionId: StateFlow<String?> = _selectedSessionId.asStateFlow()
    
    private val _currentMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val currentMessages: StateFlow<List<ChatMessage>> = _currentMessages.asStateFlow()
    
    /**
     * 选择聊天会话
     */
    fun selectSession(sessionId: String) {
        _selectedSessionId.value = sessionId
        
        // 加载该会话的消息
        val messages = ChatSession.getMockMessages(sessionId)
        _currentMessages.value = messages
        
        // 清除未读数
        clearUnreadCount(sessionId)
    }
    
    /**
     * 返回聊天列表
     */
    fun backToList() {
        _selectedSessionId.value = null
        _currentMessages.value = emptyList()
    }
    
    /**
     * 发送消息（模拟）
     */
    fun sendMessage(content: String) {
        val sessionId = _selectedSessionId.value ?: return
        
        val newMessage = ChatMessage(
            id = "msg_${System.currentTimeMillis()}",
            content = content,
            isUser = true,
            timestamp = System.currentTimeMillis()
        )
        
        _currentMessages.value = _currentMessages.value + newMessage
        
        // 更新最后一条消息
        updateLastMessage(sessionId, content)
        
        // 模拟对方回复（1秒后）
        simulateReply(sessionId)
    }
    
    /**
     * 清除未读数
     */
    private fun clearUnreadCount(sessionId: String) {
        _sessions.value = _sessions.value.map { session ->
            if (session.id == sessionId) {
                session.copy(unreadCount = 0)
            } else {
                session
            }
        }
    }
    
    /**
     * 更新最后一条消息
     */
    private fun updateLastMessage(sessionId: String, message: String) {
        _sessions.value = _sessions.value.map { session ->
            if (session.id == sessionId) {
                session.copy(
                    lastMessage = message,
                    lastMessageTime = System.currentTimeMillis()
                )
            } else {
                session
            }
        }
    }
    
    /**
     * 模拟对方回复
     */
    private fun simulateReply(sessionId: String) {
        val replies = listOf(
            "好的",
            "收到",
            "明白了",
            "没问题",
            "稍等一下"
        )
        
        val randomReply = replies.random()
        
        viewModelScope.launch {
            delay(1000)
            
            val replyMessage = ChatMessage(
                id = "msg_reply_${System.currentTimeMillis()}",
                content = randomReply,
                isUser = false,
                timestamp = System.currentTimeMillis()
            )
            
            _currentMessages.value = _currentMessages.value + replyMessage
            updateLastMessage(sessionId, randomReply)
        }
    }
}
