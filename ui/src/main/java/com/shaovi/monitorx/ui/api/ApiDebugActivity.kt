package com.shaovi.monitorx.ui.api

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.shaovi.monitorx.databinding.ActivityCommonContainerBinding
import com.shaovi.monitorx.utils.setupEdgeToEdge

class ApiDebugActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCommonContainerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCommonContainerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupEdgeToEdge(binding.root, topView = binding.appBarLayout)

        binding.tvTitle.text = "接口重放与调试"
        binding.ivBack.setOnClickListener { finish() }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(binding.fragmentContainer.id, ApiDebugFragment())
                .commit()
        }
    }
}
