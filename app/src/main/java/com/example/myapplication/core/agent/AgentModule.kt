package com.example.myapplication.core.agent

/**
 * 智能体模块 - 统一入口
 * 
 * 提供符合后端文档规范的智能体功能组件：
 * - AgentMessageHandler: 消息解析器
 * - AgentRequestBuilder: 请求构建器
 * - AgentStateManager: 状态管理器
 * 
 * 对齐后端文档 v1.0（2026-04-16）
 */

// 导出所有公共类
typealias MessageHandler = AgentMessageHandler
typealias RequestBuilder = AgentRequestBuilder
typealias StateManager = AgentStateManager

/**
 * 创建智能体模块实例
 * 建议在 ViewModel 中通过依赖注入使用
 */
fun createAgentModule(): AgentModule {
    return AgentModule(
        messageHandler = AgentMessageHandler(),
        requestBuilder = AgentRequestBuilder(),
        stateManager = AgentStateManager()
    )
}

/**
 * 智能体模块容器
 */
data class AgentModule(
    val messageHandler: AgentMessageHandler,
    val requestBuilder: AgentRequestBuilder,
    val stateManager: AgentStateManager
)

/**
 * 快速使用示例：
 * 
 * ```kotlin
 * // 在 ViewModel 中
 * private val agentModule = createAgentModule()
 * 
 * // 构建请求
 * val json = agentModule.requestBuilder.buildUserMessage(
 *     sessionId = sessionId,
 *     content = "帮我找附近的医院",
 *     lat = currentLat,
 *     lng = currentLng
 * )
 * 
 * // 发送消息
 * webSocketClient.sendRaw(json)
 * 
 * // 解析响应
 * val response = agentModule.messageHandler.parseMessage(serverMessage)
 * when (response?.type?.uppercase()) {
 *     "SEARCH" -> {
 *         val result = agentModule.messageHandler.handleSearchResponse(response)
 *         // 处理搜索结果
 *     }
 *     "CHAT" -> {
 *         val chatMessage = agentModule.messageHandler.handleChatResponse(response)
 *         // 显示聊天消息
 *     }
 * }
 * 
 * // 管理状态
 * agentModule.stateManager.onUserMessage("帮我找医院")
 * agentModule.stateManager.onSearchResult(pois, needConfirm = true)
 * ```
 */
