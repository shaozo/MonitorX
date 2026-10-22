package com.hkjustar.monitor.okhttp.ui.im

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.hkjustar.monitor.okhttp.R
import com.hkjustar.monitor.okhttp.adapter.ImMessageListAdapter
import com.hkjustar.monitor.okhttp.databinding.FragmentMonitorMainBinding
import com.hkjustar.monitor.okhttp.im.ImMessageCollector

class ImMessageFragment : Fragment() {

    private lateinit var binding: FragmentMonitorMainBinding
    private val handler = Handler(Looper.getMainLooper())
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
        binding = FragmentMonitorMainBinding.bind(view)
        binding.swipeRefresh.setOnRefreshListener {
            handler.postDelayed({
                binding.swipeRefresh.isRefreshing = false
                setData()
            }, 300)
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
        adapter?.setData(ImMessageCollector.query(limit = 100))
    }
}
