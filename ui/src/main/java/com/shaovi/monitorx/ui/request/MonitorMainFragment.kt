package com.shaovi.monitorx.ui.request

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.shaovi.monitorx.MonitorHelper
import com.shaovi.monitorx.R
import com.shaovi.monitorx.adapter.MonitorListAdapter
import com.shaovi.monitorx.data.MonitorData
import com.shaovi.monitorx.databinding.FragmentMonitorMainBinding
import com.shaovi.monitorx.ui.MonitorConfigActivity
import com.shaovi.monitorx.ui.MonitorDetailActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MonitorMainFragment : Fragment() {

    private var _binding: FragmentMonitorMainBinding? = null
    private val binding get() = requireNotNull(_binding)

    private var adapter: MonitorListAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_monitor_main, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentMonitorMainBinding.bind(view)

        binding.swipeRefresh.setOnRefreshListener {
            viewLifecycleOwner.lifecycleScope.launch {
                delay(800)
                binding.swipeRefresh.isRefreshing = false
                setData()
            }
        }

        binding.btnEmptyAction.setOnClickListener {
            startActivity(Intent(requireContext(), MonitorConfigActivity::class.java))
        }

        initRv()
        setData()
    }

    private fun setData() {
        MonitorHelper.getMonitorDataListForAndroid(limit = 100)
            ?.observe(viewLifecycleOwner, Observer { list ->
                val isEmpty = list.isNullOrEmpty()
                binding.llEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
                binding.rvMonitor.visibility = if (isEmpty) View.GONE else View.VISIBLE
                adapter?.setData(list)
            })
    }

    private fun initRv() {
        adapter = MonitorListAdapter()
        adapter?.itemClick = { gotoMonitorDetail(it) }
        binding.rvMonitor.layoutManager = LinearLayoutManager(context)
        binding.rvMonitor.adapter = adapter
    }

    private fun gotoMonitorDetail(monitorData: MonitorData) {
        startActivity(MonitorDetailActivity.buildIntent(requireContext(), monitorData))
    }

    override fun onDestroyView() {
        binding.rvMonitor.adapter = null
        adapter = null
        _binding = null
        super.onDestroyView()
    }
}
