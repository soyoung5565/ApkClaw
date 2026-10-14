package com.apk.claw.android.ui.settings

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import com.apk.claw.android.ClawApplication
import com.apk.claw.android.R
import com.apk.claw.android.base.BaseActivity
import com.apk.claw.android.utils.KVUtils
import com.apk.claw.android.widget.CommonToolbar
import com.apk.claw.android.widget.KButton

/**
 * LLM 配置页（自行选择 Provider 类型、填写 API Key、Base URL、模型名）
 */
class LlmConfigActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_llm_config)

        findViewById<CommonToolbar>(R.id.toolbar).apply {
            setTitle(getString(R.string.llm_config_title))
            showBackButton(true) { finish() }
        }

        val spinnerProvider = findViewById<Spinner>(R.id.spinnerProvider)
        val etApiKey = findViewById<EditText>(R.id.etApiKey)
        val etBaseUrl = findViewById<EditText>(R.id.etBaseUrl)
        val etModelName = findViewById<EditText>(R.id.etModelName)
        val swThinking = findViewById<SwitchCompat>(R.id.swThinking)
        val spinnerEffort = findViewById<Spinner>(R.id.spinnerEffort)
        val tvEffortLabel = findViewById<android.widget.TextView>(R.id.tvEffortLabel)

        // 设置 Provider 下拉
        val providerOptions = listOf(
            getString(R.string.llm_config_provider_openai),
            getString(R.string.llm_config_provider_anthropic)
        )
        val providerKeys = listOf("openai", "anthropic")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, providerOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerProvider.adapter = adapter

        // 深度思考开关 + 档位（Hermes 2026-10-14）
        val efforts = listOf("max", "xhigh", "high", "medium", "low", "minimal", "none")
        val effortAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, efforts)
        effortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerEffort.adapter = effortAdapter
        spinnerEffort.setSelection(efforts.indexOf(KVUtils.getReasoningEffort()).coerceAtLeast(0))

        swThinking.isChecked = KVUtils.isThinkingEnabled()
        // 关闭思考时档位无意义，灰掉避免误解
        fun refreshEffortEnabled() {
            val on = swThinking.isChecked
            spinnerEffort.isEnabled = on
            tvEffortLabel.isEnabled = on
            spinnerEffort.alpha = if (on) 1.0f else 0.4f
            tvEffortLabel.alpha = if (on) 1.0f else 0.4f
        }
        swThinking.setOnCheckedChangeListener { _, _ -> refreshEffortEnabled() }
        refreshEffortEnabled()

        // 恢复已保存的值
        val savedProvider = KVUtils.getLlmProvider()
        val savedIndex = providerKeys.indexOf(savedProvider).coerceAtLeast(0)
        spinnerProvider.setSelection(savedIndex)

        etApiKey.setText(KVUtils.getLlmApiKey())
        etBaseUrl.setText(KVUtils.getLlmBaseUrl())
        etModelName.setText(KVUtils.getLlmModelName())

        findViewById<KButton>(R.id.btnSave).setOnClickListener {
            val apiKey = etApiKey.text.toString().trim()
            val baseUrl = etBaseUrl.text.toString().trim()
            val modelName = etModelName.text.toString().trim().ifEmpty { "" }
            val providerKey = providerKeys[spinnerProvider.selectedItemPosition]

            if (apiKey.isEmpty()) {
                Toast.makeText(this, getString(R.string.llm_config_api_key_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            KVUtils.setLlmProvider(providerKey)
            KVUtils.setLlmApiKey(apiKey)
            KVUtils.setLlmBaseUrl(baseUrl)
            KVUtils.setLlmModelName(modelName)
            KVUtils.setThinkingEnabled(swThinking.isChecked)
            val effort = efforts[spinnerEffort.selectedItemPosition]
            KVUtils.setReasoningEffort(effort)

            ClawApplication.appViewModelInstance.updateAgentConfig()
            ClawApplication.appViewModelInstance.initAgent()
            ClawApplication.appViewModelInstance.afterInit()
            Toast.makeText(this, getString(R.string.llm_config_saved), Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
