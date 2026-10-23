package com.shaovi.monitorx.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.shaovi.monitorx.MonitorHelper
import com.shaovi.monitorx.R
import com.shaovi.monitorx.adapter.MockRuleAdapter
import com.shaovi.monitorx.databinding.ActivityMonitorConfigBinding
import com.shaovi.monitorx.databinding.DialogEditMockRuleBinding
import com.shaovi.monitorx.enum.WeakNetworkType
import com.shaovi.monitorx.mock.MockHelper
import com.shaovi.monitorx.mock.MockRule
import com.shaovi.monitorx.utils.setupEdgeToEdge
import com.shaovi.monitorx.weaknetwork.WeakNetworkHelper

class MonitorConfigActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMonitorConfigBinding
    private var mockAdapter: MockRuleAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMonitorConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupEdgeToEdge(binding.root, topView = binding.appBarLayout, bottomView = binding.scrollView)

        initMonitorView()
        initWeakNetView()
        initMockView()
        initListener()
    }

    private fun initMonitorView() {
        binding.monitorSwitchBtn.isChecked = MonitorHelper.isOpenMonitor
    }

    private fun initWeakNetView() {
        binding.switchBtn.isChecked = WeakNetworkHelper.isOpen
        setWeakSwitchUI(WeakNetworkHelper.isOpen)
        binding.editSpeed.setText(WeakNetworkHelper.responseSpeedByte.toString())
        setCurrentNetDes(WeakNetworkHelper.weakNetType())
        setSpeedContainerVisible(WeakNetworkHelper.weakNetType())

        when (WeakNetworkHelper.weakNetType()) {
            WeakNetworkType.TIME_OUT -> binding.radioBtnTimeOut.isChecked = true
            WeakNetworkType.NO_NETWORK -> binding.radioBtnNoNet.isChecked = true
            WeakNetworkType.SPEED_LIMIT -> binding.radioBtnSpeedLimit.isChecked = true
        }
    }

    private fun initMockView() {
        binding.mockSwitchBtn.isChecked = MockHelper.isOpen
        setMockSwitchUI(MockHelper.isOpen)

        mockAdapter = MockRuleAdapter(
            onToggle = { rule, isChecked ->
                MockHelper.toggleRule(rule.id, isChecked)
                updateMockSummary()
            },
            onDelete = { rule ->
                MaterialAlertDialogBuilder(this)
                    .setTitle("删除 Mock 规则？")
                    .setMessage("确定删除规则「${rule.name.ifBlank { rule.path }}」？")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("删除") { _, _ ->
                        MockHelper.deleteRule(rule.id)
                        refreshMockList()
                    }
                    .show()
            },
            onEdit = { rule ->
                showEditRuleDialog(rule)
            }
        )

        binding.rvMockRules.layoutManager = LinearLayoutManager(this)
        binding.rvMockRules.adapter = mockAdapter

        refreshMockList()
    }

    private fun refreshMockList() {
        val list = MockHelper.rules.toList()
        mockAdapter?.submitList(list)
        binding.tvEmptyMockRules.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        binding.rvMockRules.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        updateMockSummary()
    }

    private fun updateMockSummary() {
        val total = MockHelper.rules.size
        val active = MockHelper.rules.count { it.isEnabled }
        binding.tvMockSubtitle.text = if (MockHelper.isOpen) {
            "已开启 · 共 $total 条规则 ($active 条生效中)"
        } else {
            "已配置 $total 条规则 · 开关处于关闭状态"
        }
    }

    private fun showEditRuleDialog(rule: MockRule?) {
        val dialogBinding = DialogEditMockRuleBinding.inflate(LayoutInflater.from(this))
        val isNew = (rule == null)

        val methods = listOf("ALL", "GET", "POST", "PUT", "DELETE", "PATCH")
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, methods)
        dialogBinding.spRuleMethod.adapter = spinnerAdapter

        if (rule != null) {
            dialogBinding.etRuleName.setText(rule.name)
            dialogBinding.etRulePath.setText(rule.path)
            val methodIdx = methods.indexOf(rule.method).coerceAtLeast(0)
            dialogBinding.spRuleMethod.setSelection(methodIdx)
            dialogBinding.etRuleCode.setText(rule.statusCode.toString())
            dialogBinding.etRuleBaseUrl.setText(rule.mockBaseUrl)
            dialogBinding.etRuleDelay.setText(rule.delayMs.toString())
            dialogBinding.etRuleResponse.setText(rule.responseBody)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(if (isNew) "添加 Mock 规则" else "编辑 Mock 规则")
            .setView(dialogBinding.root)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存") { _, _ ->
                val path = dialogBinding.etRulePath.text?.toString()?.trim().orEmpty()
                if (path.isBlank()) {
                    Toast.makeText(this, "匹配 Path 不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val savedRule = (rule ?: MockRule()).apply {
                    this.name = dialogBinding.etRuleName.text?.toString()?.trim().orEmpty()
                    this.path = path
                    this.method = dialogBinding.spRuleMethod.selectedItem?.toString() ?: "ALL"
                    this.statusCode = dialogBinding.etRuleCode.text?.toString()?.toIntOrNull() ?: 200
                    this.mockBaseUrl = dialogBinding.etRuleBaseUrl.text?.toString()?.trim().orEmpty()
                    this.delayMs = dialogBinding.etRuleDelay.text?.toString()?.toLongOrNull() ?: 0L
                    this.responseBody = dialogBinding.etRuleResponse.text?.toString().orEmpty()
                    this.isEnabled = true
                }

                MockHelper.addOrUpdateRule(savedRule)
                refreshMockList()
                Snackbar.make(binding.root, "Mock 规则已保存并同步至网页端", Snackbar.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun initListener() {
        binding.ivBack.setOnClickListener { finish() }

        // 卡片整体点击切换
        binding.cardMonitor.setOnClickListener {
            binding.monitorSwitchBtn.isChecked = !binding.monitorSwitchBtn.isChecked
        }
        binding.monitorSwitchBtn.setOnCheckedChangeListener { _, isChecked ->
            MonitorHelper.isOpenMonitor = isChecked
            Toast.makeText(this, if (isChecked) "抓包功能已开启" else "抓包功能已关闭", Toast.LENGTH_SHORT).show()
        }

        binding.headerWeak.setOnClickListener {
            binding.switchBtn.isChecked = !binding.switchBtn.isChecked
        }
        binding.switchBtn.setOnCheckedChangeListener { _, isChecked ->
            WeakNetworkHelper.isOpen = isChecked
            setWeakSwitchUI(isChecked)
        }

        binding.headerMock.setOnClickListener {
            binding.mockSwitchBtn.isChecked = !binding.mockSwitchBtn.isChecked
        }
        binding.mockSwitchBtn.setOnCheckedChangeListener { _, isChecked ->
            MockHelper.isOpen = isChecked
            setMockSwitchUI(isChecked)
            updateMockSummary()
        }

        binding.btnAddMockRule.setOnClickListener {
            showEditRuleDialog(null)
        }

        binding.btnClearAllData.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("清空全部调试数据？")
                .setMessage("此操作将同时清空网络记录、IM 消息和业务异常记录，清空后无法恢复。确定继续？")
                .setNegativeButton("取消", null)
                .setPositiveButton("确认清空") { _, _ ->
                    kotlin.concurrent.thread {
                        MonitorHelper.deleteAll()
                        com.shaovi.monitorx.im.ImMessageCollector.clear()
                        com.shaovi.monitorx.anomaly.AnomalyManager.clearRecords()
                        runOnUiThread {
                            Snackbar.make(binding.root, "已成功清空全部本地调试数据", Snackbar.LENGTH_SHORT).show()
                        }
                    }
                }
                .show()
        }

        binding.radioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radio_btn_time_out -> WeakNetworkHelper.setWeakType(WeakNetworkType.TIME_OUT)
                R.id.radio_btn_no_net -> WeakNetworkHelper.setWeakType(WeakNetworkType.NO_NETWORK)
                R.id.radio_btn_speed_limit -> WeakNetworkHelper.setWeakType(WeakNetworkType.SPEED_LIMIT)
            }
            setSpeedContainerVisible(WeakNetworkHelper.weakNetType())
            setCurrentNetDes(WeakNetworkHelper.weakNetType())
        }

        binding.editSpeed.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val speed = s?.toString()?.toLongOrNull() ?: 1024L
                WeakNetworkHelper.responseSpeedByte = speed
            }
        })
    }

    private fun setSpeedContainerVisible(type: WeakNetworkType) {
        binding.groupSpeed.visibility =
            if (type == WeakNetworkType.SPEED_LIMIT) View.VISIBLE else View.GONE
    }

    private fun setCurrentNetDes(type: WeakNetworkType) {
        val des = when (type) {
            WeakNetworkType.TIME_OUT -> "当前配置为：模拟网络<<请求超时>>"
            WeakNetworkType.NO_NETWORK -> "当前配置为：模拟网络<<请求断网>>"
            WeakNetworkType.SPEED_LIMIT -> "当前配置为：模拟网络<<请求限速>>"
        }
        binding.tvDes.text = des
        binding.tvWeakSubtitle.text = if (WeakNetworkHelper.isOpen) "已开启 · $des" else "已关闭 · 预设: $des"
    }

    private fun setWeakSwitchUI(checked: Boolean) {
        binding.clWeakConfigContainer.visibility = if (checked) View.VISIBLE else View.GONE
        setCurrentNetDes(WeakNetworkHelper.weakNetType())
    }

    private fun setMockSwitchUI(checked: Boolean) {
        binding.clMockConfigContainer.visibility = if (checked) View.VISIBLE else View.GONE
        updateMockSummary()
    }
}
