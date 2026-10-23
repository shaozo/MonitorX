package com.shaovi.monitorx.utils

import android.os.Build
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Modern Edge-to-Edge helper utility for MonitorX activities and views.
 */
fun ComponentActivity.setupEdgeToEdge(
    rootView: View,
    topView: View? = null,
    bottomView: View? = null,
    extraInsetsHandler: ((insets: WindowInsetsCompat) -> Unit)? = null
) {
    enableEdgeToEdge()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isNavigationBarContrastEnforced = false
    }

    val topInitialPadding = topView?.paddingTop ?: 0
    val bottomInitialPadding = bottomView?.paddingBottom ?: 0

    ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
        val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
        val navigationBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())

        topView?.updatePadding(top = topInitialPadding + statusBarInsets.top)
        bottomView?.updatePadding(bottom = bottomInitialPadding + navigationBarInsets.bottom)

        extraInsetsHandler?.invoke(insets)

        insets
    }
}
