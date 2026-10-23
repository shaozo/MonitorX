package com.hkjustar.monitor.okhttp.ui.anomaly

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hkjustar.monitor.okhttp.R
import com.hkjustar.monitor.okhttp.anomaly.AnomalyManager
import com.hkjustar.monitor.okhttp.anomaly.AnomalyRecord
import com.hkjustar.monitor.okhttp.databinding.FragmentBusinessIssuesBinding

/** Generic history UI. Product-specific rules remain in the host app's rule packs. */
class BusinessIssuesFragment : Fragment() {
    private var _binding: FragmentBusinessIssuesBinding? = null
    private val binding get() = requireNotNull(_binding)
    private val adapter = IssueAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        FragmentBusinessIssuesBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, state: Bundle?) {
        binding.rvIssues.layoutManager = LinearLayoutManager(requireContext())
        binding.rvIssues.adapter = adapter
        binding.swipeRefresh.setOnRefreshListener { refresh() }
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = refresh()
            override fun afterTextChanged(s: Editable?) = Unit
        })
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) refresh()
    }

    private fun refresh() {
        val all = AnomalyManager.getAllRecords()
        val query = binding.etSearch.text?.toString().orEmpty().trim()
        val visible = if (query.isBlank()) all else all.filter { record ->
            listOf(record.ruleName, record.ruleId, record.path, record.paramKey, record.activityName)
                .any { it.contains(query, ignoreCase = true) }
        }
        val status = AnomalyManager.getStatus()
        binding.tvSummary.text = "业务问题 ${all.size} · 严重 ${status.criticalRecords} · 警告 ${status.warningRecords} · 规则 ${status.activeRules}"
        adapter.submit(visible)
        binding.swipeRefresh.isRefreshing = false
    }

    override fun onDestroyView() {
        binding.rvIssues.adapter = null
        _binding = null
        super.onDestroyView()
    }
}

private class IssueAdapter : RecyclerView.Adapter<IssueAdapter.Holder>() {
    private val items = mutableListOf<AnomalyRecord>()

    fun submit(records: List<AnomalyRecord>) {
        items.clear()
        items.addAll(records)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_business_issue, parent, false)
    )

    override fun getItemCount(): Int = items.size
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val title = view.findViewById<TextView>(R.id.tv_title)
        private val meta = view.findViewById<TextView>(R.id.tv_meta)
        private val path = view.findViewById<TextView>(R.id.tv_path)
        private val message = view.findViewById<TextView>(R.id.tv_message)
        private val solution = view.findViewById<TextView>(R.id.tv_solution)

        fun bind(item: AnomalyRecord) {
            title.text = "[${item.severity}] ${item.ruleName}"
            meta.text = listOf(item.timeString, item.activityName).filter { it.isNotBlank() }.joinToString(" · ")
            path.text = "${item.method} ${item.path}\n${item.paramKey} = ${item.actualValue ?: "null"}"
            message.text = item.alertMessage
            solution.text = "建议：${item.solution.ifBlank { "暂无" }}"
        }
    }
}
