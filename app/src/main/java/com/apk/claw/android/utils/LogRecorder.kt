package com.apk.claw.android.utils

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 实时日志录制器 — 录制期间所有 HTTP 请求/响应写入单个文件
 */
object LogRecorder {

    private val recording = AtomicBoolean(false)
    private val entries = CopyOnWriteArrayList<String>()
    @Volatile
    private var outputFile: File? = null
    @Volatile
    private var startTime: Long = 0

    /** 是否正在录制 */
    fun isRecording(): Boolean = recording.get()

    /** 开始录制 */
    fun start(cacheDir: File): File {
        entries.clear()
        startTime = System.currentTimeMillis()
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val dir = File(cacheDir, "http_logs")
        if (!dir.exists()) dir.mkdirs()
        outputFile = File(dir, "recording_${ts}.txt")
        recording.set(true)
        XLog.i("LogRecorder", "录制开始: ${outputFile?.absolutePath}")
        return outputFile!!
    }

    /** 停止录制，返回生成的文件 */
    fun stop(): File? {
        recording.set(false)
        val file = outputFile ?: return null
        val duration = System.currentTimeMillis() - startTime

        file.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("========== MyClaw HTTP Log Recording ==========\n")
            writer.write("Start: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(startTime))}\n")
            writer.write("Duration: ${duration}ms (${duration / 1000}s)\n")
            writer.write("Entries: ${entries.size}\n")
            writer.write("================================================\n\n")

            for (entry in entries) {
                writer.write(entry)
                writer.write("\n${"─".repeat(60)}\n\n")
            }

            writer.write("========== END OF RECORDING ==========\n")
        }

        val result = outputFile
        outputFile = null
        entries.clear()
        XLog.i("LogRecorder", "录制结束: ${result?.absolutePath}, ${entries.size} entries")
        return result
    }

    /** 记录一条 HTTP 请求/响应 */
    fun record(url: String, method: String, statusCode: Int, durationMs: Long,
               requestBody: String, responseBody: String, curl: String, error: String? = null) {
        if (!recording.get()) return

        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val sb = StringBuilder()
        sb.append("[$ts] $method $url\n")
        sb.append("Status: $statusCode  Duration: ${durationMs}ms\n")
        if (requestBody.isNotBlank()) {
            val truncated = if (requestBody.length > 4000) requestBody.substring(0, 4000) + "...[TRUNCATED]" else requestBody
            sb.append("\n--- Request Body ---\n$truncated\n")
        }
        if (curl.isNotBlank()) {
            val truncated = if (curl.length > 2000) curl.substring(0, 2000) + "..." else curl
            sb.append("\n--- cURL ---\n$truncated\n")
        }
        if (error != null) {
            sb.append("\n--- ERROR ---\n$error\n")
        } else if (responseBody.isNotBlank()) {
            val truncated = if (responseBody.length > 4000) responseBody.substring(0, 4000) + "...[TRUNCATED]" else responseBody
            sb.append("\n--- Response Body ---\n$truncated\n")
        }
        entries.add(sb.toString())
    }
}
