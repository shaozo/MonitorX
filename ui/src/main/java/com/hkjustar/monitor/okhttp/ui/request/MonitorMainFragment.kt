package com.hkjustar.monitor.okhttp.ui.request

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.hkjustar.monitor.okhttp.MonitorHelper
import com.hkjustar.monitor.okhttp.R
import com.hkjustar.monitor.okhttp.adapter.MonitorListAdapter
import com.hkjustar.monitor.okhttp.data.MonitorData
import com.hkjustar.monitor.okhttp.databinding.FragmentMonitorMainBinding
import com.hkjustar.monitor.okhttp.ui.MonitorDetailActivity

/**
 * <pre>
 *      author  : Allen
 *      date    : 2022/8/6
 *      desc    :
 * </pre>
 */
class MonitorMainFragment : Fragment() {

    private lateinit var binding: FragmentMonitorMainBinding

    private var adapter: MonitorListAdapter? = null

    private var handle: Handler = Handler(Looper.getMainLooper())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_monitor_main, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentMonitorMainBinding.bind(view)

        binding.swipeRefresh.setOnRefreshListener {
            handle.postDelayed({
                binding.swipeRefresh.isRefreshing = false
                setData()
            }, 1000)
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
}