package com.hkjustar.monitor.okhttp.ui.im

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.hkjustar.monitor.okhttp.databinding.ActivityImMonitorDetailBinding
import com.hkjustar.monitor.okhttp.im.ImMessageCollector

class ImMessageDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImMonitorDetailBinding
    private var copyContent: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImMonitorDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.ivBack.setOnClickListener { finish() }
        binding.tvCopy.setOnClickListener { copyToClipboard() }
        render()
    }

    private fun render() {
        val id = intent.getLongExtra(KEY_ID, 0L)
        val record = ImMessageCollector.queryById(id)
        binding.tvTitle.text = record?.let { "TYPE(${it.type})" } ?: "IM详情"
        binding.tvMeta.text = record?.let { "id=${it.id}\ntime=${it.timestamp}\ntype=${it.type}" } ?: "记录不存在"
        copyContent = record?.content.orEmpty()
        binding.tvJson.text = copyContent.let(::formatJson)
    }

    private fun copyToClipboard() {
        if (copyContent.isBlank()) return
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboardManager.setPrimaryClip(ClipData.newPlainText("im_message", copyContent))
        Toast.makeText(this, "复制成功", Toast.LENGTH_SHORT).show()
    }

    private fun formatJson(content: String): String {
        if (content.isBlank()) return ""
        return runCatching {
            GsonBuilder().setPrettyPrinting().create()
                .toJson(JsonParser.parseString(content))
        }.getOrDefault(content)
    }

    companion object {
        private const val KEY_ID = "key_id"

        fun buildIntent(context: Context, id: Long): Intent {
            return Intent(context, ImMessageDetailActivity::class.java).apply {
                putExtra(KEY_ID, id)
            }
        }
    }
}
