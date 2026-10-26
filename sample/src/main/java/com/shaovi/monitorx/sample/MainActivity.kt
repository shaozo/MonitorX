package com.shaovi.monitorx.sample

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.shaovi.monitorx.api.MonitorX
import com.shaovi.monitorx.core.InspectorX
import com.shaovi.monitorx.sample.databinding.ActivityMainBinding
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // OkHttp Client (MonitorX Gradle 插件会在编译期自动插桩捕获，也可手动添加拦截器)
    private val httpClient = OkHttpClient.Builder().build()

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupInsets()
        initStatusCard()
        initNetworkButtons()
        initInspectorButtons()
        initSpButtons()
        initLogConsole()
    }

    private fun setupInsets() {
        val topInitial = binding.appBarLayout.paddingTop
        val scrollInitialBottom = binding.scrollView.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())

            binding.appBarLayout.updatePadding(top = topInitial + statusBars.top)
            binding.scrollView.updatePadding(bottom = scrollInitialBottom + navBars.bottom)

            insets
        }
    }

    private fun initStatusCard() {
        val isRuntimeReady = MonitorX.isRuntimeAvailable()
        if (isRuntimeReady) {
            binding.tvStatusTitle.text = "🟢 MonitorX 运行时已就绪 (Debug)"
            binding.tvStatusDesc.text = "Debug 变体已注入抓包、SP 诊断、时序规则与 PC 端本地控制台服务。"
        } else {
            binding.tvStatusTitle.text = "⚪ MonitorX 当前处于 No-Op 状态"
            binding.tvStatusDesc.text = "当前构建未引入 monitor-ui 依赖，API 调用安全直通且零开销。"
        }

        binding.btnOpenMonitor.setOnClickListener {
            if (MonitorX.isRuntimeAvailable()) {
                val opened = MonitorX.open(this)
                if (!opened) {
                    Toast.makeText(this, "MonitorX 尚未开启或已被禁用", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "当前环境未打包 monitor-ui 运行时", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun initNetworkButtons() {
        binding.btnReqGet.setOnClickListener {
            appendLog("发起 GET 请求: https://httpbin.org/get")
            val request = Request.Builder()
                .url("https://httpbin.org/get?source=monitorx_demo&time=${System.currentTimeMillis()}")
                .header("User-Agent", "MonitorX-Sample-Client/1.0")
                .build()
            executeHttp(request)
        }

        binding.btnReqPost.setOnClickListener {
            appendLog("发起 POST 请求: https://httpbin.org/post")
            val jsonBody = """
                {
                  "userId": "DEMO_USER_1001",
                  "action": "test_post_event",
                  "timestamp": ${System.currentTimeMillis()}
                }
            """.trimIndent()
            val request = Request.Builder()
                .url("https://httpbin.org/post")
                .post(jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            executeHttp(request)
        }

        binding.btnReq404.setOnClickListener {
            appendLog("发起 404 请求: https://httpbin.org/status/404")
            val request = Request.Builder().url("https://httpbin.org/status/404").build()
            executeHttp(request)
        }

        binding.btnReq500.setOnClickListener {
            appendLog("发起 500 请求: https://httpbin.org/status/500")
            val request = Request.Builder().url("https://httpbin.org/status/500").build()
            executeHttp(request)
        }

        binding.btnReqDelay.setOnClickListener {
            appendLog("发起延迟请求 (2秒): https://httpbin.org/delay/2")
            val request = Request.Builder().url("https://httpbin.org/delay/2").build()
            executeHttp(request)
        }

        binding.btnReqLarge.setOnClickListener {
            appendLog("发起大数据包请求 (50KB): https://httpbin.org/bytes/50000")
            val request = Request.Builder().url("https://httpbin.org/bytes/50000").build()
            executeHttp(request)
        }
    }

    private fun executeHttp(request: Request) {
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    appendLog("❌ 请求失败 [${request.url.encodedPath}]: ${e.localizedMessage}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val code = response.code
                val bodySnippet = response.body?.string()?.take(120)?.replace("\n", " ") ?: ""
                response.close()
                runOnUiThread {
                    appendLog("✅ 响应 [$code] [${request.url.encodedPath}]: $bodySnippet...")
                }
            }
        })
    }

    private fun initInspectorButtons() {
        binding.btnRuleLogin.setOnClickListener {
            InspectorX.resetSession("user_sample")
            val violations = InspectorX.emit("LOGIN_COMPLETED")
            appendLog("触发事件: LOGIN_COMPLETED (违规数量: ${violations.size})")
            Toast.makeText(this, "已标记登录完成！", Toast.LENGTH_SHORT).show()
        }

        binding.btnRuleCheckout.setOnClickListener {
            val orderId = "ORDER_" + UUID.randomUUID().toString().take(6)
            val violations = InspectorX.emit("CHECKOUT_STARTED", mapOf("orderId" to orderId))
            if (violations.isEmpty()) {
                appendLog("✅ 正常触发结算: CHECKOUT_STARTED ($orderId), 前置登录检查通过")
            } else {
                appendLog("🚨 触发规则违规! ${violations.first().ruleName} -> ${violations.first().message}")
            }
        }

        binding.btnRuleViolateDirect.setOnClickListener {
            // 重置状态模拟未登录
            InspectorX.resetSession("anonymous_user_" + System.currentTimeMillis())
            appendLog("⚡ 模拟场景: 用户未登录直接发起订单结算...")
            val orderId = "ORDER_" + UUID.randomUUID().toString().take(6)
            val violations = InspectorX.emit("CHECKOUT_STARTED", mapOf("orderId" to orderId))
            appendLog("🚨 违规告警捕获: ${violations.size} 项! 请在系统通知栏或 Monitor 业务问题页查看")
        }

        binding.btnRuleRate.setOnClickListener {
            val orderId = "REPEAT_ORDER_888"
            appendLog("⚡ 连续快速触发 CHECKOUT_SUBMITTED (同一 orderId)...")
            InspectorX.emit("CHECKOUT_SUBMITTED", mapOf("orderId" to orderId))
            val violations = InspectorX.emit("CHECKOUT_SUBMITTED", mapOf("orderId" to orderId))
            if (violations.isNotEmpty()) {
                appendLog("🚨 频率违规告警: ${violations.first().message}")
            }
        }
    }

    private fun initSpButtons() {
        binding.btnSpWrite.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val sp = getSharedPreferences("user_profile", Context.MODE_PRIVATE)
                val randomLevel = (1..99).random()
                val randomScore = (100..9999).random()
                sp.edit()
                    .putInt("user_level", randomLevel)
                    .putInt("score", randomScore)
                    .putString("last_update", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
                    .apply()

                withContext(Dispatchers.Main) {
                    appendLog("写入 SP user_profile: user_level=$randomLevel, score=$randomScore")
                    Toast.makeText(this@MainActivity, "SP 数据已更新，可打开看板查看", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun initLogConsole() {
        binding.tvClearLog.setOnClickListener {
            binding.tvConsole.text = "日志已清空\n"
        }
    }

    private fun appendLog(msg: String) {
        val time = timeFormat.format(Date())
        val line = "[$time] $msg\n"
        binding.tvConsole.append(line)
    }
}
