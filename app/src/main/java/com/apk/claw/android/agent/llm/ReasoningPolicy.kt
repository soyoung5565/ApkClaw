package com.apk.claw.android.agent.llm

import com.apk.claw.android.utils.XLog

/**
 * 思考参数策略（Hermes 2026-10-14）。
 *
 * 各家模型的思考参数语义完全不同，硬套会直接 400，所以必须按模型族分流：
 *
 * **火山方舟 DeepSeek-V4.1-Flash（Chat API）** —— 官方文档原文：
 *   - `thinking.type` 为 adaptive，`reasoning_effort` 才生效
 *   - Chat API 支持档位 none、low、high、max，**传入 none 将关闭思考**
 *   - 默认取值 high
 *   - 官方明确：需要关闭思考时请显式设置 `thinking.type: "disabled"`，
 *     不要用 `reasoning_effort: "none"` 代替；且已设 disabled 时不要同时发 reasoning_effort
 *
 * **火山 Doubao Seed 系列** —— `thinking` 支持 enabled/disabled，
 *   `reasoning_effort` 只支持 minimal/low/medium/high（**没有 max/xhigh/none**），
 *   默认 high。传 max 会 400，必须降级到 high。
 *
 * **MiniMax-M3.1-Flash-Preview** —— 官方文档原文：
 *   - 强制开启 thinking，`thinking.type` 只接受 adaptive，**传 disabled 返回 400**
 *   - `reasoning_effort` 支持 low/medium/high/xhigh/max，省略时默认 max，**不接受 none**
 *   - `reasoning_split=true` 时思考内容进 `reasoning_content` 字段
 *
 * **MiniMax-M2.x** —— thinking 无法关闭，传 disabled 被接收但不生效；深度自适应。
 *
 * 用户要求（2026-10-14）：默认 max 深度；提供思考开关。
 * 因此本策略保证：开关开 → 该模型能表达的最深档位；开关关 → 能关的就关，关不掉的就保持默认。
 */
object ReasoningPolicy {

    private const val TAG = "ReasoningPolicy"

    enum class Family {
        /** 火山方舟 DeepSeek 系列 */
        ARK_DEEPSEEK,

        /** 火山方舟 Doubao Seed 系列 */
        ARK_DOUBAO,

        /** MiniMax 系列（M3.1 / M3 / M2.x） */
        MINIMAX,

        /** 未知模型：不下发任何思考参数，走服务端默认 */
        UNKNOWN
    }

    /** 下发给 LLM 的思考参数。null 表示该键不出现。 */
    data class Plan(
        val family: Family,
        val thinkingType: String?,
        val reasoningEffort: String?,
        val note: String
    ) {
        /** 是否会真正带上 customParameters（两个键都为空则不需要下发）。 */
        val hasCustom: Boolean get() = thinkingType != null || reasoningEffort != null
    }

    /** 按模型名识别模型族。 */
    fun detectFamily(modelName: String): Family {
        val m = modelName.trim().lowercase()
        return when {
            // 先判 base url 无关的模型名特征；ARK 的 doubao/seed-2.1-pro
            m.startsWith("doubao") || m.startsWith("seed-") || m.startsWith("seed2") ->
                Family.ARK_DOUBAO
            m.startsWith("deepseek") -> Family.ARK_DEEPSEEK
            m.startsWith("minimax") || m.startsWith("abab") || m.contains("m2.7") ||
                m.contains("m2-7") || m.contains("m3") || m.contains("m3.1") ->
                Family.MINIMAX
            else -> Family.UNKNOWN
        }
    }

    /**
     * 生成思考参数方案。
     *
     * @param thinkingEnabled 用户开关（KVUtils.isThinkingEnabled）
     * @param effort 用户档位（KVUtils.getReasoningEffort，默认 max）
     */
    fun plan(family: Family, thinkingEnabled: Boolean, effort: String): Plan {
        val requested = effort.trim().lowercase().ifEmpty { "max" }
        return when (family) {
            Family.ARK_DEEPSEEK -> {
                if (!thinkingEnabled) {
                    // 官方：关闭思考用 thinking.type=disabled，且不要同时发 reasoning_effort
                    Plan(family, "disabled", null, "思考已关闭（thinking.type=disabled）")
                } else {
                    val mapped = when (requested) {
                        "none" -> "max"          // 开关已开，none 视为最深
                        "minimal" -> "low"        // 官方兼容映射
                        "medium" -> "high"        // 官方兼容映射
                        "xhigh" -> "high"         // 官方兼容映射
                        else -> requested         // low / high / max 原样
                    }.let { if (it in setOf("low", "high", "max")) it else "max" }
                    Plan(family, null, mapped, "思考开启 effort=$mapped")
                }
            }

            Family.ARK_DOUBAO -> {
                if (!thinkingEnabled) {
                    Plan(family, "disabled", null, "思考已关闭（thinking.type=disabled）")
                } else {
                    // doubao 只支持 minimal/low/medium/high，没有 max/xhigh/none
                    val mapped = when (requested) {
                        "none", "minimal" -> "minimal"
                        "low" -> "low"
                        "medium" -> "medium"
                        "high" -> "high"
                        // max / xhigh → 该模型最高档是 high
                        else -> "high"
                    }
                    Plan(family, null, mapped, "思考开启 effort=$mapped（doubao 最高档为 high）")
                }
            }

            Family.MINIMAX -> {
                if (!thinkingEnabled) {
                    // MiniMax 强制思考，传 disabled 会 400 —— 只能不发参数，保持服务端默认
                    Plan(
                        family, null, null,
                        "⚠ 该模型强制开启思考（传 disabled 会 400），关闭指令已忽略"
                    )
                } else {
                    val mapped = when (requested) {
                        // none 在 MiniMax 会被 400 拒绝，等同于"最深"
                        "none" -> "max"
                        "minimal" -> "low"        // MiniMax 无 minimal 档
                        else -> requested
                    }.let { if (it in setOf("low", "medium", "high", "xhigh", "max")) it else "max" }
                    Plan(family, null, mapped, "思考开启 effort=$mapped")
                }
            }

            Family.UNKNOWN -> {
                if (!thinkingEnabled) {
                    Plan(family, null, null, "未知模型族，未下发思考参数")
                } else {
                    Plan(family, null, null, "未知模型族，未下发思考参数（走服务端默认）")
                }
            }
        }
    }

    /** 便捷入口：直接按模型名出方案。 */
    fun planFor(modelName: String, thinkingEnabled: Boolean, effort: String): Plan =
        plan(detectFamily(modelName), thinkingEnabled, effort)

    fun logPlan(plan: Plan, modelName: String) {
        if (plan.hasCustom || plan.note.startsWith("⚠")) {
            XLog.i(
                TAG,
                "model=$modelName family=${plan.family} thinking=${plan.thinkingType} " +
                    "reasoning_effort=${plan.reasoningEffort} — ${plan.note}"
            )
        }
    }
}