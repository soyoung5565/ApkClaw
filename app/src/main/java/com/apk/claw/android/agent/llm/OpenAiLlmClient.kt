package com.apk.claw.android.agent.llm

import com.apk.claw.android.agent.AgentConfig
import com.apk.claw.android.agent.langchain.http.OkHttpClientBuilderAdapter
import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.StreamingChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler
import dev.langchain4j.model.openai.OpenAiChatModel
import dev.langchain4j.model.openai.OpenAiChatRequestParameters
import dev.langchain4j.model.openai.OpenAiStreamingChatModel
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

class OpenAiLlmClient(
    private val config: AgentConfig,
    private val httpClientBuilder: OkHttpClientBuilderAdapter
) : LlmClient {

    private val chatModel: ChatModel by lazy { buildChatModel() }
    private val streamingChatModel: StreamingChatModel by lazy { buildStreamingChatModel() }

    /**
     * 思考参数方案（Hermes 2026-10-14）。
     * 各家模型语义不同：火山 DeepSeek 支持 none 关思考、Doubao 最高只到 high、
     * MiniMax 强制思考（传 disabled 直接 400）。必须按模型族分流，硬套会失败。
     */
    private val reasoningPlan: ReasoningPolicy.Plan by lazy {
        ReasoningPolicy.planFor(config.modelName, config.thinkingEnabled, config.reasoningEffort)
            .also { ReasoningPolicy.logPlan(it, config.modelName) }
    }

    /** 构造带思考参数的 defaultRequestParameters；无参数时返回 null（不覆盖 builder 设置）。 */
    private fun thinkingParams(): OpenAiChatRequestParameters? {
        val thinkingType = reasoningPlan.thinkingType
        val effort = reasoningPlan.reasoningEffort
        if (thinkingType == null && effort == null) return null

        val builder = OpenAiChatRequestParameters.builder()
            .modelName(config.modelName)
        if (effort != null) builder.reasoningEffort(effort)
        if (thinkingType != null) {
            builder.customParameters(mapOf("thinking" to mapOf("type" to thinkingType)))
        }
        return builder.build()
    }

    private fun buildChatModel(): ChatModel {
        val builder = OpenAiChatModel.builder()
            .httpClientBuilder(httpClientBuilder)
            .apiKey(config.apiKey)
            .modelName(config.modelName)
            .temperature(config.temperature)
            .maxTokens(config.maxTokens)
        if (config.baseUrl.isNotEmpty()) {
            builder.baseUrl(config.baseUrl)
        }
        // 解析 reasoning_content -> AiMessage.thinking()，并把上一轮思考回传给模型
        builder.returnThinking(true)
        builder.sendThinking(true)
        thinkingParams()?.let { builder.defaultRequestParameters(it) }
        return builder.build()
    }

    private fun buildStreamingChatModel(): StreamingChatModel {
        val builder = OpenAiStreamingChatModel.builder()
            .httpClientBuilder(httpClientBuilder)
            .apiKey(config.apiKey)
            .modelName(config.modelName)
            .temperature(config.temperature)
            .maxTokens(config.maxTokens)
        if (config.baseUrl.isNotEmpty()) {
            builder.baseUrl(config.baseUrl)
        }
        builder.returnThinking(true)
        builder.sendThinking(true)
        thinkingParams()?.let { builder.defaultRequestParameters(it) }
        return builder.build()
    }

    override fun chat(messages: List<ChatMessage>, toolSpecs: List<ToolSpecification>): LlmResponse {
        val request = ChatRequest.builder()
            .messages(messages)
            .toolSpecifications(toolSpecs)
            .build()
        val response = chatModel.chat(request)
        return response.toLlmResponse()
    }

    override fun chatStreaming(
        messages: List<ChatMessage>,
        toolSpecs: List<ToolSpecification>,
        listener: StreamingListener
    ): LlmResponse {
        val request = ChatRequest.builder()
            .messages(messages)
            .toolSpecifications(toolSpecs)
            .build()

        val latch = CountDownLatch(1)
        val resultRef = AtomicReference<LlmResponse>()
        val errorRef = AtomicReference<Throwable>()

        streamingChatModel.chat(request, object : StreamingChatResponseHandler {
            override fun onPartialResponse(token: String) {
                listener.onPartialText(token)
            }

            override fun onCompleteResponse(response: ChatResponse) {
                val llmResponse = response.toLlmResponse()
                resultRef.set(llmResponse)
                listener.onComplete(llmResponse)
                latch.countDown()
            }

            override fun onError(error: Throwable) {
                errorRef.set(error)
                listener.onError(error)
                latch.countDown()
            }
        })

        latch.await()
        errorRef.get()?.let { throw it }
        return resultRef.get()
    }
}

internal fun ChatResponse.toLlmResponse(): LlmResponse {
    val aiMessage = aiMessage()
    return LlmResponse(
        text = aiMessage.text(),
        toolExecutionRequests = aiMessage.toolExecutionRequests() ?: emptyList(),
        tokenUsage = tokenUsage(),
        thinking = aiMessage.thinking()?.toString()
    )
}
