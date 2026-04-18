package com.example.myapplication.core.utils

/**
 * 敏感词过滤工具类
 * 用于过滤用户输入中的不当词汇
 */
object SensitiveWordFilter {
    
    // 敏感词列表（按类别分组）
    private val SENSITIVE_WORDS = setOf(
        // 暴力相关
        "暴力", "血腥", "屠杀", "杀人", "自杀", "自残", "爆炸", "炸弹", "枪击",
        
        // 色情相关
        "色情", "淫秽", "性爱", "裸体", "淫乱", "嫖娼", "卖淫",
        
        // 违法相关
        "毒品", "赌博", "诈骗", "传销", "洗钱", "走私", "抢劫", "盗窃",
        
        // 辱骂词汇
        "傻逼", "白痴", "脑残", "废物", "垃圾", "贱人", "蠢货", "混蛋", "王八蛋",
        
        // 广告营销
        "加微信", "qq群", "兼职", "刷单", "赚钱", "代理", "投资返利",
        
        // 其他违规
        "违禁", "非法", "违规", "禁止"
    )
    
    /**
     * 检查文本是否包含敏感词
     * @param text 待检查的文本
     * @return 如果包含敏感词返回true，否则返回false
     */
    fun containsSensitiveWord(text: String): Boolean {
        if (text.isBlank()) return false
        
        val lowerText = text.lowercase()
        return SENSITIVE_WORDS.any { word ->
            lowerText.contains(word.lowercase())
        }
    }
    
    /**
     * 过滤文本中的敏感词
     * @param text 待过滤的文本
     * @return 过滤后的文本，敏感词替换为 ***
     */
    fun filterText(text: String): String {
        if (text.isBlank()) return text
        
        var result = text
        SENSITIVE_WORDS.forEach { word ->
            if (result.lowercase().contains(word.lowercase())) {
                val regex = Regex(Regex.escape(word), RegexOption.IGNORE_CASE)
                result = result.replace(regex, "***")
            }
        }
        return result
    }
    
    /**
     * 获取过滤词列表（用于调试）
     */
    fun getSensitiveWords(): Set<String> = SENSITIVE_WORDS
    
    /**
     * 添加自定义敏感词
     */
    fun addCustomWord(word: String) {
        // 注意：这里使用可变集合来动态添加
        // 实际项目中可能需要持久化存储
    }
    
    /**
     * 移除敏感词
     */
    fun removeWord(word: String) {
        // 注意：这里使用可变集合来动态移除
        // 实际项目中可能需要持久化存储
    }
}
