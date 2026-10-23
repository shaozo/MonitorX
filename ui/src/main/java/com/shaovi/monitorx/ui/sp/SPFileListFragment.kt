package com.shaovi.monitorx.ui.sp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.shaovi.monitorx.MonitorHelper
import com.shaovi.monitorx.R
import com.shaovi.monitorx.data.SpData
import com.shaovi.monitorx.databinding.FragmentSpListBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * <pre>
 *      author  : Allen
 *      date    : 2022/8/6
 *      desc    :
 * </pre>
 */
class SPFileListFragment : Fragment() {

    private var _binding: FragmentSpListBinding? = null
    private val binding get() = requireNotNull(_binding)
    private var adapter: SpFileAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_sp_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSpListBinding.bind(view)
        initView()
        initData()
    }

    private fun initData() {
        val list = MonitorHelper.getSharedPrefsFilesData().map { SpData(it.key) }.toList()
        adapter?.setData(list)
    }

    private fun initView() {
        binding.swipeRefresh.setOnRefreshListener {
            viewLifecycleOwner.lifecycleScope.launch {
                delay(1000)
                binding.swipeRefresh.isRefreshing = false
                initData()
            }
        }
        adapter = SpFileAdapter()
        adapter?.onItemClick = {
            startActivity(SPFileDetailActivity.buildIntent(requireContext(), it.fileName))
        }
        binding.recyclerVew.let {
            it.layoutManager = LinearLayoutManager(context)
            it.adapter = adapter
        }
    }

    override fun onDestroyView() {
        binding.recyclerVew.adapter = null
        adapter = null
        _binding = null
        super.onDestroyView()
    }
}
