package com.shaovi.monitorx.ui.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.shaovi.monitorx.MonitorHelper
import com.shaovi.monitorx.databinding.FragmentToolsBinding
import com.shaovi.monitorx.ui.MonitorConfigActivity
import com.shaovi.monitorx.utils.getPhoneWifiIpAddress

class ToolsFragment : Fragment() {

    private var _binding: FragmentToolsBinding? = null
    private val binding get() = requireNotNull(_binding)

    var onOpenApiDebug: (() -> Unit)? = null
    var onOpenIm: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentToolsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. 接口调试卡片
        binding.cardApiDebug.setOnClickListener {
            onOpenApiDebug?.invoke()
        }

        // 2. IM 消息监控卡片
        binding.cardIm.setOnClickListener {
            onOpenIm?.invoke()
        }

        // 3. PC 网页控制台卡片
        val ip = getPhoneWifiIpAddress()
        if (ip != null) {
            val url = "http://$ip:${MonitorHelper.port}/index"
            binding.tvPcUrl.text = url
            binding.btnCopyUrl.setOnClickListener {
                val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cm?.setPrimaryClip(ClipData.newPlainText("MonitorX_PC_URL", url))
                Toast.makeText(requireContext(), "已复制 PC 控制台地址", Toast.LENGTH_SHORT).show()
            }
        } else {
            binding.tvPcUrl.text = "手机未连接 Wi-Fi，无法生成局域网控制台地址"
            binding.btnCopyUrl.isEnabled = false
        }

        // 4. 配置与 Mock 卡片
        binding.cardConfig.setOnClickListener {
            startActivity(Intent(requireContext(), MonitorConfigActivity::class.java))
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
