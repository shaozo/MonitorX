package com.shaovi.monitorx.ui.im

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.shaovi.monitorx.R
import com.shaovi.monitorx.adapter.ImMessageListAdapter
import com.shaovi.monitorx.databinding.FragmentMonitorMainBinding
import com.shaovi.monitorx.im.ImMessageCollector
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ImMessageFragment : Fragment() {

    private var _binding: FragmentMonitorMainBinding? = null
    private val binding get() = requireNotNull(_binding)
    private var adapter: ImMessageListAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.fragment_monitor_main, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentMonitorMainBinding.bind(view)
        binding.swipeRefresh.setOnRefreshListener {
            viewLifecycleOwner.lifecycleScope.launch {
                delay(300)
                binding.swipeRefresh.isRefreshing = false
                setData()
            }
        }
        initRecyclerView()
        setData()
    }

    private fun initRecyclerView() {
        adapter = ImMessageListAdapter()
        adapter?.itemClick = {
            startActivity(ImMessageDetailActivity.buildIntent(requireContext(), it.id))
        }
        binding.rvMonitor.layoutManager = LinearLayoutManager(context)
        binding.rvMonitor.adapter = adapter
    }

    private fun setData() {
        val records = ImMessageCollector.query(limit = 100)
        val isEmpty = records.isEmpty()

        binding.tvEmptyTitle.text = "暂无 IM 消息记录"
        binding.tvEmptySubtitle.text = "在应用中调用 ImMessageCollector.record()\n即可在此实时回溯聊天与长连接信令消息"
        binding.btnEmptyAction.visibility = View.GONE

        binding.llEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.rvMonitor.visibility = if (isEmpty) View.GONE else View.VISIBLE

        adapter?.setData(records)
    }

    override fun onDestroyView() {
        binding.rvMonitor.adapter = null
        adapter = null
        _binding = null
        super.onDestroyView()
    }
}
