package com.shaovi.monitorx.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.shaovi.monitorx.databinding.ItemMockRuleBinding
import com.shaovi.monitorx.mock.MockRule

class MockRuleAdapter(
    private val onToggle: (MockRule, Boolean) -> Unit,
    private val onDelete: (MockRule) -> Unit,
    private val onEdit: (MockRule) -> Unit
) : RecyclerView.Adapter<MockRuleAdapter.RuleViewHolder>() {

    private val rules = mutableListOf<MockRule>()

    fun submitList(list: List<MockRule>) {
        rules.clear()
        rules.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RuleViewHolder {
        val binding = ItemMockRuleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RuleViewHolder(binding)
    }

    override fun getItemCount(): Int = rules.size

    override fun onBindViewHolder(holder: RuleViewHolder, position: Int) {
        holder.bind(rules[position])
    }

    inner class RuleViewHolder(private val binding: ItemMockRuleBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(rule: MockRule) {
            binding.tvMockMethod.text = rule.method
            binding.tvMockName.text = rule.name.ifBlank { "未命名规则" }
            binding.tvMockPath.text = rule.path

            val desc = if (rule.mockBaseUrl.isNotBlank()) {
                "重定向至: ${rule.mockBaseUrl}"
            } else {
                "状态码: ${rule.statusCode} · ${if (rule.delayMs > 0) "延迟 ${rule.delayMs}ms · " else ""}自定义响应 (${rule.responseBody.length} 字节)"
            }
            binding.tvMockDesc.text = desc

            binding.switchRule.setOnCheckedChangeListener(null)
            binding.switchRule.isChecked = rule.isEnabled
            binding.switchRule.setOnCheckedChangeListener { _, isChecked ->
                onToggle(rule, isChecked)
            }

            binding.ivDelete.setOnClickListener {
                onDelete(rule)
            }

            binding.root.setOnClickListener {
                onEdit(rule)
            }
        }
    }
}
