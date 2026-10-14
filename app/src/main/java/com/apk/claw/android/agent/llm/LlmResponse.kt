package com.apk.claw.android.agent.llm

import dev.langchain4j.agent.tool.ToolExecutionRequest
import dev.langchain4j.model.output.TokenUsage

data class LlmResponse(
    val text: String?,
    val toolExecutionRequests: List<ToolExecutionRequest>,
    val tokenUsage: TokenUsage? = null,
    /**
     * 模型思考内容（Hermes 2026-10-14）。
     * 来自 OpenAI 兼容响应的 reasoning_content 字段，需 returnThinking(true) 才会被解析。
     * 之前这个字段被整个丢弃，导致模型每轮失忆 —— 长任务跑到 20+ 步开始出现幻觉的直接原因。
     */
    val thinking: String? = null
) {
    fun hasToolExecutionRequests(): Boolean = toolExecutionRequests.isNotEmpty()
}
