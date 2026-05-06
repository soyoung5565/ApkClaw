package com.apk.claw.android.ui.settings

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.apk.claw.android.utils.LogRecorder
import com.apk.claw.android.utils.XLog
import com.apk.claw.android.widget.CommonToolbar
import com.apk.claw.android.widget.KButton
import java.io.File
import java.util.Timer
import java.util.TimerTask

/**
 * HTTP 日志查看器 + 实时录制工具
 */
class LogViewerActivity : BaseActivity() {

    private lateinit var logDir: File
    private lateinit var rvLogs: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var tvLogCount: TextView
    private lateinit var btnStartRecording: KButton
    private lateinit var recordingBar: View
    private lateinit var tvRecordingStatus: TextView
    private var refreshTimer: Timer? = null

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
        btnStartRecording = findViewById(R.id.btnStartRecording)
        recordingBar = findViewById(R.id.recordingBar)
        tvRecordingStatus = findViewById(R.id.tvRecordingStatus)

        rvLogs.layoutManager = LinearLayoutManager(this)

        btnStartRecording.setOnClickListener { startRecording() }
        findViewById<KButton>(R.id.btnStopRecording).setOnClickListener { stopRecording() }
        findViewById<KButton>(R.id.btnShare).setOnClickListener { shareAllLogs() }
        findViewById<KButton>(R.id.btnClear).setOnClickListener { clearLogs() }

        updateRecordingUI()
        loadLogs()
    }

    override fun onResume() {
        super.onResume()
        // Auto-refresh every 2s when recording
        refreshTimer = Timer()
        refreshTimer?.schedule(object : TimerTask() {
            override fun run() {
                if (LogRecorder.isRecording()) {
                    runOnUiThread { loadLogs() }
                }
            }
        }, 2000, 2000)
    }

    override fun onPause() {
        refreshTimer?.cancel()
        refreshTimer = null
        super.onPause()
    }

    private fun startRecording() {
        val file = LogRecorder.start(cacheDir)
        XLog.i("LogViewer", "Recording started: ${file.absolutePath}")
        Toast.makeText(this, getString(R.string.log_recorder_started), Toast.LENGTH_SHORT).show()
        updateRecordingUI()
        loadLogs()
    }

    private fun stopRecording() {
        val file = LogRecorder.stop()
        if (file != null) {
            Toast.makeText(this, getString(R.string.log_recorder_stopped), Toast.LENGTH_SHORT).show()
            XLog.i("LogViewer", "Recording stopped: ${file.absolutePath}")
        }
        updateRecordingUI()
        loadLogs()
    }

    private fun updateRecordingUI() {
        val isRecording = LogRecorder.isRecording()
        recordingBar.visibility = if (isRecording) View.VISIBLE else View.GONE
        btnStartRecording.visibility = if (isRecording) View.GONE else View.VISIBLE
        if (isRecording) {
            // Animate recording dot
            tvRecordingStatus.text = getString(R.string.log_recorder_recording)
        }
    }

    private fun loadLogs() {
        val files = if (logDir.exists()) {
            logDir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".txt") }
                ?.sortedByDescending { it.lastModified() }
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
            val recording = if (LogRecorder.isRecording()) " 🔴 Recording" else ""
            tvLogCount.text = "${files.size} log files$recording"
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

        val mergedFile = File(cacheDir, "myclaw_all_logs.txt")
        mergedFile.bufferedWriter().use { writer ->
            files.sortedByDescending { it.lastModified() }.forEach { file ->
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
        if (LogRecorder.isRecording()) {
            Toast.makeText(this, "Stop recording first", Toast.LENGTH_SHORT).show()
            return
        }
        if (logDir.exists()) {
            logDir.listFiles()?.forEach { it.delete() }
        }
        loadLogs()
        Toast.makeText(this, getString(R.string.log_viewer_cleared), Toast.LENGTH_SHORT).show()
    }

    // ==================== Adapter ====================

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
            val content = try { file.readText() } catch (_: Exception) { "(read error)" }
            val isRecording = file.name.startsWith("recording_")

            val header = if (isRecording) "🔴 ${file.name}" else file.nameWithoutExtension
            val sizeKB = file.length() / 1024

            holder.tvHeader.text = header
            holder.tvMeta.text = "Size: ${sizeKB}KB"
            holder.tvContent.text = content
            holder.tvContent.maxLines = 20

            holder.itemView.setOnClickListener {
                holder.tvContent.maxLines =
                    if (holder.tvContent.maxLines == 20) Int.MAX_VALUE else 20
            }
        }

        override fun getItemCount() = files.size
    }
}
