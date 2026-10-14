package com.apk.claw.android.utils

import android.content.Context
import com.tencent.mmkv.MMKV

/**
 * MMKV 键值存储工具类
 *
 * 使用方式：
 *   // 在 Application.onCreate 中初始化
 *   KVUtils.init(context)
 *
 *   // 存取数据
 *   KVUtils.putString("key", "value")
 *   val value = KVUtils.getString("key", "default")
 */
object KVUtils {


    // 钉钉配置
    const val KEY_DINGTALK_APP_KEY = "DEFAULT_DINGTALK_APP_KEY"
    const val KEY_DINGTALK_APP_SECRET = "DEFAULT_DINGTALK_APP_SECRET"
    // 飞书配置
    const val KEY_FEISHU_APP_ID = "DEFAULT_FEISHU_APP_ID"
    const val KEY_FEISHU_APP_SECRET = "DEFAULT_FEISHU_APP_SECRET"
    // QQ 机器人配置
    const val KEY_QQ_APP_ID = "DEFAULT_QQ_APP_ID"
    const val KEY_QQ_APP_SECRET = "DEFAULT_QQ_APP_SECRET"
    // Discord 机器人配置
    const val KEY_DISCORD_BOT_TOKEN = "DEFAULT_DISCORD_BOT_TOKEN"
    // Telegram 机器人配置
    const val KEY_TELEGRAM_BOT_TOKEN = "DEFAULT_TELEGRAM_BOT_TOKEN"
    // 微信 iLink Bot 配置
    const val KEY_WECHAT_BOT_TOKEN = "DEFAULT_WECHAT_BOT_TOKEN"
    const val KEY_WECHAT_API_BASE_URL = "DEFAULT_WECHAT_API_BASE_URL"
    const val KEY_WECHAT_UPDATES_CURSOR = "DEFAULT_WECHAT_UPDATES_CURSOR"

    private lateinit var mmkv: MMKV

    private const val DEFAULT_INT = 0
    private const val DEFAULT_LONG = 0L
    private const val DEFAULT_BOOL = false
    private const val DEFAULT_FLOAT = 0f
    private const val DEFAULT_DOUBLE = 0.0

    /**
     * 在 Application.onCreate 中调用初始化
     */
    fun init(context: Context) {
        MMKV.initialize(context)
        mmkv = MMKV.defaultMMKV()
    }

    // ==================== String ====================
    fun putString(key: String, value: String?): Boolean {
        return mmkv.encode(key, value)
    }

    fun getString(key: String, defaultValue: String = ""): String {
        return mmkv.decodeString(key, defaultValue) ?: defaultValue
    }

    // ==================== Int ====================
    fun putInt(key: String, value: Int): Boolean {
        return mmkv.encode(key, value)
    }

    fun getInt(key: String, defaultValue: Int = DEFAULT_INT): Int {
        return mmkv.decodeInt(key, defaultValue)
    }

    // ==================== Long ====================
    fun putLong(key: String, value: Long): Boolean {
        return mmkv.encode(key, value)
    }

    fun getLong(key: String, defaultValue: Long = DEFAULT_LONG): Long {
        return mmkv.decodeLong(key, defaultValue)
    }

    // ==================== Boolean ====================
    fun putBoolean(key: String, value: Boolean): Boolean {
        return mmkv.encode(key, value)
    }

    fun getBoolean(key: String, defaultValue: Boolean = DEFAULT_BOOL): Boolean {
        return mmkv.decodeBool(key, defaultValue)
    }

    // ==================== Float ====================
    fun putFloat(key: String, value: Float): Boolean {
        return mmkv.encode(key, value)
    }

    fun getFloat(key: String, defaultValue: Float = DEFAULT_FLOAT): Float {
        return mmkv.decodeFloat(key, defaultValue)
    }

    // ==================== Double ====================
    fun putDouble(key: String, value: Double): Boolean {
        return mmkv.encode(key, value)
    }

    fun getDouble(key: String, defaultValue: Double = DEFAULT_DOUBLE): Double {
        return mmkv.decodeDouble(key, defaultValue)
    }

    // ==================== Bytes ====================
    fun putBytes(key: String, value: ByteArray?): Boolean {
        return mmkv.encode(key, value)
    }

    fun getBytes(key: String): ByteArray? {
        return mmkv.decodeBytes(key)
    }

    // ==================== 常用操作 ====================
    fun contains(key: String): Boolean {
        return mmkv.containsKey(key)
    }

    fun remove(key: String) {
        mmkv.removeValueForKey(key)
    }

    fun remove(vararg keys: String) {
        mmkv.removeValuesForKeys(keys)
    }

    fun clear() {
        mmkv.clearAll()
    }

    fun getAllKeys(): Array<String> {
        return mmkv.allKeys() ?: emptyArray()
    }

    /**
     * 同步写入磁盘（默认是异步的）
     */
    fun sync() {
        mmkv.sync()
    }


    // ==================== 引导页 ====================
    private const val KEY_GUIDE_SHOWN = "KEY_GUIDE_SHOWN"

    fun isGuideShown(): Boolean = getBoolean(KEY_GUIDE_SHOWN, false)

    fun setGuideShown(shown: Boolean) = putBoolean(KEY_GUIDE_SHOWN, shown)

    // ==================== 钉钉配置 ====================
    fun getDingtalkAppKey(): String = getString(KEY_DINGTALK_APP_KEY, "")
    fun setDingtalkAppKey(value: String) = putString(KEY_DINGTALK_APP_KEY, value)
    fun getDingtalkAppSecret(): String = getString(KEY_DINGTALK_APP_SECRET, "")
    fun setDingtalkAppSecret(value: String) = putString(KEY_DINGTALK_APP_SECRET, value)

    // ==================== 飞书配置 ====================
    fun getFeishuAppId(): String = getString(KEY_FEISHU_APP_ID, DEFAULT_FEISHU_APP_ID).ifEmpty { DEFAULT_FEISHU_APP_ID }
    fun setFeishuAppId(value: String) = putString(KEY_FEISHU_APP_ID, value)
    fun getFeishuAppSecret(): String = getString(KEY_FEISHU_APP_SECRET, DEFAULT_FEISHU_APP_SECRET).ifEmpty { DEFAULT_FEISHU_APP_SECRET }
    fun setFeishuAppSecret(value: String) = putString(KEY_FEISHU_APP_SECRET, value)

    // ==================== QQ 机器人配置 ====================
    fun getQqAppId(): String = getString(KEY_QQ_APP_ID, DEFAULT_QQ_APP_ID).ifEmpty { DEFAULT_QQ_APP_ID }
    fun setQqAppId(value: String) = putString(KEY_QQ_APP_ID, value)
    fun getQqAppSecret(): String = getString(KEY_QQ_APP_SECRET, DEFAULT_QQ_APP_SECRET).ifEmpty { DEFAULT_QQ_APP_SECRET }
    fun setQqAppSecret(value: String) = putString(KEY_QQ_APP_SECRET, value)

    // ==================== Discord 机器人配置 ====================
    fun getDiscordBotToken(): String = getString(KEY_DISCORD_BOT_TOKEN, "")
    fun setDiscordBotToken(value: String) = putString(KEY_DISCORD_BOT_TOKEN, value)

    // ==================== Telegram 机器人配置 ====================
    fun getTelegramBotToken(): String = getString(KEY_TELEGRAM_BOT_TOKEN, "")
    fun setTelegramBotToken(value: String) = putString(KEY_TELEGRAM_BOT_TOKEN, value)

    // ==================== 微信 iLink Bot 配置 ====================
    fun getWechatBotToken(): String = getString(KEY_WECHAT_BOT_TOKEN, "")
    fun setWechatBotToken(value: String) = putString(KEY_WECHAT_BOT_TOKEN, value)
    fun getWechatApiBaseUrl(): String = getString(KEY_WECHAT_API_BASE_URL, "")
    fun setWechatApiBaseUrl(value: String) = putString(KEY_WECHAT_API_BASE_URL, value)
    fun getWechatUpdatesCursor(): String = getString(KEY_WECHAT_UPDATES_CURSOR, "")
    fun setWechatUpdatesCursor(value: String) = putString(KEY_WECHAT_UPDATES_CURSOR, value)

    // ==================== 局域网配置服务 ====================
    private const val KEY_CONFIG_SERVER_ENABLED = "KEY_CONFIG_SERVER_ENABLED"
    fun isConfigServerEnabled(): Boolean = getBoolean(KEY_CONFIG_SERVER_ENABLED, false)
    fun setConfigServerEnabled(enabled: Boolean) = putBoolean(KEY_CONFIG_SERVER_ENABLED, enabled)

    // ===== 默认配置（首次启动自动填充；密钥请勿硬编码提交，留空由用户在设置页填写） =====
    // LLM (minimax, OpenAI-compatible)
    private const val DEFAULT_LLM_API_KEY = ""
    private const val DEFAULT_LLM_BASE_URL = "https://api.minimaxi.com/v1"
    private const val DEFAULT_LLM_MODEL_NAME = "MiniMax-M2.7-highspeed"
    private const val DEFAULT_LLM_PROVIDER = "openai"
    // QQ bot (用户最新, 2026-07-31 截图)
    private const val DEFAULT_QQ_APP_ID = "102865813"
    private const val DEFAULT_QQ_APP_SECRET = ""
    // Feishu (保留旧默认, 暂未使用)
    private const val DEFAULT_FEISHU_APP_ID = "cli_a970b3c039385bc4"
    private const val DEFAULT_FEISHU_APP_SECRET = ""

    private const val KEY_LLM_API_KEY = "KEY_LLM_API_KEY"
    private const val KEY_LLM_BASE_URL = "KEY_LLM_BASE_URL"
    private const val KEY_LLM_MODEL_NAME = "KEY_LLM_MODEL_NAME"
    private const val KEY_LLM_PROVIDER = "KEY_LLM_PROVIDER"

    fun getLlmApiKey(): String = getString(KEY_LLM_API_KEY, DEFAULT_LLM_API_KEY).ifEmpty { DEFAULT_LLM_API_KEY }
    fun setLlmApiKey(value: String) = putString(KEY_LLM_API_KEY, value)
    fun getLlmBaseUrl(): String = getString(KEY_LLM_BASE_URL, DEFAULT_LLM_BASE_URL).ifEmpty { DEFAULT_LLM_BASE_URL }
    fun setLlmBaseUrl(value: String) = putString(KEY_LLM_BASE_URL, value)
    fun getLlmModelName(): String = getString(KEY_LLM_MODEL_NAME, DEFAULT_LLM_MODEL_NAME).ifEmpty { DEFAULT_LLM_MODEL_NAME }
    fun setLlmModelName(value: String) = putString(KEY_LLM_MODEL_NAME, value)

    /** provider: "openai" 或 "anthropic"，默认 openai */
    fun getLlmProvider(): String = getString(KEY_LLM_PROVIDER, DEFAULT_LLM_PROVIDER).ifEmpty { DEFAULT_LLM_PROVIDER }
    fun setLlmProvider(value: String) = putString(KEY_LLM_PROVIDER, value)

    /** 是否已配置 LLM（API Key 非空即视为已配置） */
    fun hasLlmConfig(): Boolean = getLlmApiKey().isNotEmpty()

    // ==================== Agent 最大迭代次数 ====================
    private const val KEY_MAX_ITERATIONS = "KEY_MAX_ITERATIONS"
    // 默认 500：足以跑"三家 app 搜喜茶价格"这种 60+ 步的复合任务。
    // 用户可在 H5 配置页改，也可在 logcat 里看 KEY_MAX_ITERATIONS 触发。
    private const val DEFAULT_MAX_ITERATIONS = 500
    /** 读 agent 最大迭代次数。最小 20，最大 9999。 */
    fun getMaxIterations(): Int {
        val raw = getInt(KEY_MAX_ITERATIONS, DEFAULT_MAX_ITERATIONS)
        return raw.coerceIn(20, 9999)
    }
    fun setMaxIterations(value: Int) = putInt(KEY_MAX_ITERATIONS, value.coerceIn(20, 9999))

    // ==================== LLM maxTokens（单轮输出上限） ====================
    private const val KEY_MAX_TOKENS = "KEY_MAX_TOKENS"
    // 默认 8192。minimax M2.7-highspeed context window 是 200K，但 OpenAI 兼容
    // API 默认 maxTokens 是 4096 (单轮输出上限)，不显式设的话 agent 跑长任务
    // 会频繁撞到 4096 上限被截断。8192 给 agent 足够的 thinking + tool-call 输出空间。
    // 用户可在 H5 配置页改。
    private const val DEFAULT_MAX_TOKENS = 8192
    /** 读 LLM 单轮输出 token 上限。最小 256，最大 32768。 */
    fun getMaxTokens(): Int {
        val raw = getInt(KEY_MAX_TOKENS, DEFAULT_MAX_TOKENS)
        return raw.coerceIn(256, 32768)
    }
    fun setMaxTokens(value: Int) = putInt(KEY_MAX_TOKENS, value.coerceIn(256, 32768))

    // ==================== 思考深度 (Hermes, 2026-10-14) ====================
    // 背景：长任务 agent 每轮都要推理，之前没有任何思考参数，模型思考内容也从不读回历史。
    // 官方依据：
    //   火山 DeepSeek-V4.1-Flash (Chat API) —— reasoning_effort 支持 none/low/high/max，
    //     none 关闭思考；thinking.type=disabled 关闭思考，且此时不应同时发 reasoning_effort。
    //   MiniMax-M3.1-Flash-Preview —— 强制开启 thinking，传 disabled 返回 400；
    //     reasoning_effort 支持 low/medium/high/xhigh/max，省略时默认 max。
    //   M2.x —— thinking 无法关闭，自适应深度。
    private const val KEY_THINKING_ENABLED = "KEY_THINKING_ENABLED"
    private const val KEY_REASONING_EFFORT = "KEY_REASONING_EFFORT"

    /** 默认开启思考。 */
    private const val DEFAULT_THINKING_ENABLED = true
    /** 默认最大思考深度（用户 2026-10-14 明确要求：打开即 max）。 */
    private const val DEFAULT_REASONING_EFFORT = "max"

    /** 合法档位集合，两家模型的并集；实际下发时按模型族过滤。 */
    val REASONING_EFFORT_VALUES = setOf("none", "minimal", "low", "medium", "high", "xhigh", "max")

    fun isThinkingEnabled(): Boolean =
        getBoolean(KEY_THINKING_ENABLED, DEFAULT_THINKING_ENABLED)

    fun setThinkingEnabled(enabled: Boolean) =
        putBoolean(KEY_THINKING_ENABLED, enabled)

    /** 读思考档位；非法值回落到默认 max。 */
    fun getReasoningEffort(): String {
        val raw = getString(KEY_REASONING_EFFORT, DEFAULT_REASONING_EFFORT)
            .trim().lowercase()
        return if (raw in REASONING_EFFORT_VALUES) raw else DEFAULT_REASONING_EFFORT
    }

    fun setReasoningEffort(value: String) {
        val normalized = value.trim().lowercase()
        putString(KEY_REASONING_EFFORT, if (normalized in REASONING_EFFORT_VALUES) normalized else DEFAULT_REASONING_EFFORT)
    }
}
