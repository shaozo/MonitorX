package com.hkjustar.monitor.okhttp.ui.request

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.hkjustar.monitor.okhttp.MonitorHelper
import com.hkjustar.monitor.okhttp.R
import com.hkjustar.monitor.okhttp.adapter.MonitorListAdapter
import com.hkjustar.monitor.okhttp.data.MonitorData
import com.hkjustar.monitor.okhttp.databinding.FragmentMonitorMainBinding
import com.hkjustar.monitor.okhttp.ui.MonitorDetailActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * <pre>
 *      author  : Allen
 *      date    : 2022/8/6
 *      desc    :
 * </pre>
 */
class MonitorMainFragment : Fragment() {

    private var _binding: FragmentMonitorMainBinding? = null
    private val binding get() = requireNotNull(_binding)

    private var adapter: MonitorListAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_monitor_main, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentMonitorMainBinding.bind(view)

        binding.swipeRefresh.setOnRefreshListener {
            viewLifecycleOwner.lifecycleScope.launch {
                delay(1000)
                binding.swipeRefresh.isRefreshing = false
                setData()
            }
        }

        initRv()
        setData()

    }

    private fun setData() {
        MonitorHelper.getMonitorDataListForAndroid(limit = 100)
            ?.observe(viewLifecycleOwner, Observer {
                adapter?.setData(it)
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
