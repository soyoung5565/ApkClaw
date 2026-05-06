package com.apk.claw.android.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.apk.claw.android.R
import com.apk.claw.android.base.BaseActivity
import com.apk.claw.android.widget.CommonToolbar
import com.apk.claw.android.widget.KButton
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * HTTP 日志查看器 — 查看、分享、清空网络请求日志
 */
class LogViewerActivity : BaseActivity() {

    private lateinit var logDir: File
    private lateinit var rvLogs: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var tvLogCount: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_viewer)

        logDir = File(cacheDir, "http_logs")

        findViewById<CommonToolbar>(R.id.toolbar).apply {
            setTitle(getString(R.string.log_viewer_title))
            showBackButton(true) { finish() }
        }

        rvLogs = findViewById(R.id.rvLogs)
        tvEmpty = findViewById(R.id.tvEmpty)
        tvLogCount = findViewById(R.id.tvLogCount)

        rvLogs.layoutManager = LinearLayoutManager(this)

        findViewById<KButton>(R.id.btnShare).setOnClickListener { shareAllLogs() }
        findViewById<KButton>(R.id.btnClear).setOnClickListener { clearLogs() }

        loadLogs()
    }

    private fun loadLogs() {
        val files = if (logDir.exists()) {
            logDir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".txt") }
                ?.sortedByDescending { it.name }
                ?: emptyList()
        } else {
            emptyList()
        }

        if (files.isEmpty()) {
            rvLogs.visibility = View.GONE
            tvEmpty.visibility = View.VISIBLE
            tvLogCount.text = ""
        } else {
            rvLogs.visibility = View.VISIBLE
            tvEmpty.visibility = View.GONE
            tvLogCount.text = "${files.size} log files"
            rvLogs.adapter = LogAdapter(files)
        }
    }

    private fun shareAllLogs() {
        val files = if (logDir.exists()) {
            logDir.listFiles()?.filter { it.isFile && it.name.endsWith(".txt") } ?: emptyList()
        } else {
            emptyList()
        }

        if (files.isEmpty()) {
            Toast.makeText(this, getString(R.string.log_viewer_empty), Toast.LENGTH_SHORT).show()
            return
        }

        // Merge all logs into a single file for sharing
        val mergedFile = File(cacheDir, "myclaw_all_logs.txt")
        mergedFile.bufferedWriter().use { writer ->
            files.sortedByDescending { it.name }.forEach { file ->
                writer.write("========== ${file.name} ==========\n")
                writer.write(file.readText())
                writer.write("\n\n")
            }
        }

        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", mergedFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "MyClaw HTTP Logs")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.log_viewer_share_chooser)))
    }

    private fun clearLogs() {
        if (logDir.exists()) {
            logDir.listFiles()?.forEach { it.delete() }
        }
        loadLogs()
        Toast.makeText(this, getString(R.string.log_viewer_cleared), Toast.LENGTH_SHORT).show()
    }

    // ==================== RecyclerView Adapter ====================

    private inner class LogAdapter(private val files: List<File>) :
        RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

        inner class LogViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvHeader: TextView = view.findViewById(R.id.tvLogHeader)
            val tvMeta: TextView = view.findViewById(R.id.tvLogMeta)
            val tvContent: TextView = view.findViewById(R.id.tvLogContent)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_log_entry, parent, false)
            return LogViewHolder(view)
        }

        override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
            val file = files[position]
            val content = file.readText()
            val name = file.nameWithoutExtension // e.g. 20260506_210130045_GET

            // Parse timestamp and method from filename
            val parts = name.split("_", limit = 2)
            val method = if (parts.size > 1) parts[1].substringAfter("_").ifEmpty { "REQUEST" } else "REQUEST"

            // Extract URL from content
            val url = content.lineSequence()
                .firstOrNull { it.startsWith("URL: ") }
                ?.removePrefix("URL: ") ?: ""

            // Extract status from content
            val status = content.lineSequence()
                .firstOrNull { it.startsWith("Status: ") }
                ?.removePrefix("Status: ") ?: ""

            // Extract duration
            val duration = content.lineSequence()
                .firstOrNull { it.startsWith("Duration: ") }
                ?.removePrefix("Duration: ") ?: ""

            val header = if (url.isNotBlank()) {
                "${method.take(10)}  ${url.take(80)}"
            } else {
                file.name
            }

            val meta = buildString {
                if (status.isNotBlank()) append("Status: $status  ")
                if (duration.isNotBlank()) append("Duration: $duration  ")
                append("Size: ${file.length() / 1024}KB")
            }

            holder.tvHeader.text = header
            holder.tvMeta.text = meta
            holder.tvContent.text = content

            // Toggle expand/collapse on click
            holder.tvContent.maxLines = 20
            holder.itemView.setOnClickListener {
                if (holder.tvContent.maxLines == 20) {
                    holder.tvContent.maxLines = Int.MAX_VALUE
                } else {
                    holder.tvContent.maxLines = 20
                }
            }
        }

        override fun getItemCount() = files.size
    }
}
