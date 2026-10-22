package com.hkjustar.monitor.okhttp.performance

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import java.lang.ref.WeakReference

object AppPageTracker : Application.ActivityLifecycleCallbacks {

    private data class PageSnapshot(
        val activityName: String = "",
        val activitySimpleName: String = "",
        val fragmentName: String = "",
        val updatedAt: Long = System.currentTimeMillis()
    )

    @Volatile
    private var currentActivityRef: WeakReference<Activity>? = null
    @Volatile
    private var currentPage = PageSnapshot()

    private var isRegistered = false

    private val fragmentCallbacks = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentResumed(fm: FragmentManager, fragment: Fragment) {
            val activity = fragment.activity ?: return
            if (currentActivityRef?.get() === activity) {
                updateSnapshot(activity, fragment.javaClass.name)
            }
        }
    }

    @Synchronized
    fun register(application: Application) {
        if (isRegistered) return
        application.registerActivityLifecycleCallbacks(this)
        isRegistered = true
    }

    fun getTopActivity(): Activity? = currentActivityRef?.get()

    @Synchronized
    fun unregister(application: Application) {
        if (!isRegistered) return
        application.unregisterActivityLifecycleCallbacks(this)
        isRegistered = false
    }

    fun getCurrentPageInfo(): Map<String, Any> {
        val snapshot = currentPage
        if (snapshot.activityName.isBlank()) {
            return mapOf(
                "activity" to "未知 / 前台无活跃Activity (请操作App切换界面或刷新)",
                "activitySimple" to "",
                "fragment" to "",
                "updatedAt" to System.currentTimeMillis()
            )
        }

        return mapOf(
            "activity" to snapshot.activityName,
            "activitySimple" to snapshot.activitySimpleName,
            "fragment" to snapshot.fragmentName,
            "updatedAt" to snapshot.updatedAt
        )
    }

    override fun onActivityResumed(activity: Activity) {
        currentActivityRef = WeakReference(activity)
        updateSnapshot(activity)
    }

    override fun onActivityStarted(activity: Activity) {
        currentActivityRef = WeakReference(activity)
        updateSnapshot(activity)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        currentActivityRef = WeakReference(activity)
        if (activity is FragmentActivity) {
            activity.supportFragmentManager.registerFragmentLifecycleCallbacks(fragmentCallbacks, true)
        }
        updateSnapshot(activity)
    }

    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {
        if (currentActivityRef?.get() === activity) clearSnapshot()
    }
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {
        if (activity is FragmentActivity) {
            activity.supportFragmentManager.unregisterFragmentLifecycleCallbacks(fragmentCallbacks)
        }
        if (currentActivityRef?.get() == activity) {
            clearSnapshot()
        }
    }

    private fun updateSnapshot(activity: Activity, resumedFragmentName: String? = null) {
        // Lifecycle callbacks run on main; create an immutable snapshot for HTTP worker reads.
        val fragmentName = resumedFragmentName ?: if (activity is FragmentActivity) {
            findVisibleFragment(activity.supportFragmentManager)?.javaClass?.name.orEmpty()
        } else ""
        currentPage = PageSnapshot(
            activityName = activity.javaClass.name,
            activitySimpleName = activity.javaClass.simpleName,
            fragmentName = fragmentName,
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun findVisibleFragment(manager: FragmentManager): Fragment? {
        return manager.fragments.asReversed().firstNotNullOfOrNull { fragment ->
            when {
                !fragment.isAdded || fragment.isHidden -> null
                fragment.childFragmentManager.fragments.isNotEmpty() ->
                    findVisibleFragment(fragment.childFragmentManager) ?: fragment
                else -> fragment
            }
        }
    }

    private fun clearSnapshot() {
        currentActivityRef = null
        currentPage = PageSnapshot()
    }
}
