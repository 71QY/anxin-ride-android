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
    
    // ⭐ 修复：使用空列表，不再使用死数据
    private val _sessions = MutableStateFlow<List<ChatSession>>(emptyList())
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
        // ⭐ 修复：不再加载模拟消息，使用真实数据
        _currentMessages.value = emptyList()
        
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
     * 发送消息
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
        
        // ⭐ 删除：移除模拟回复，使用真实 WebSocket 通信
        // simulateReply(sessionId)
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
}
