package com.shaovi.monitorx.ui.im

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.shaovi.monitorx.R
import com.shaovi.monitorx.databinding.ActivityCommonContainerBinding
import com.shaovi.monitorx.im.ImMessageCollector
import com.shaovi.monitorx.utils.setupEdgeToEdge

class ImMessageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCommonContainerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCommonContainerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupEdgeToEdge(binding.root, topView = binding.appBarLayout)

        binding.tvTitle.text = "IM 消息流监控"
        binding.ivBack.setOnClickListener { finish() }

        // 清空本页 IM 消息操作
        binding.ivAction.visibility = View.VISIBLE
        binding.ivAction.setImageResource(R.drawable.ic_delete_sweep)
        binding.ivAction.contentDescription = "清空 IM 记录"
        binding.ivAction.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("清空 IM 消息记录？")
                .setMessage("确定清空全部已捕获的即时通讯消息流历史？此操作不可撤回。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空") { _, _ ->
                    ImMessageCollector.clear()
                    Snackbar.make(binding.root, "已成功清空 IM 消息记录", Snackbar.LENGTH_SHORT).show()
                    supportFragmentManager.beginTransaction()
                        .replace(binding.fragmentContainer.id, ImMessageFragment())
                        .commit()
                }
                .show()
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(binding.fragmentContainer.id, ImMessageFragment())
                .commit()
        }
    }
}
