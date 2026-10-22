package com.hkjustar.monitor.okhttp

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.room.Room
import com.android.local.service.core.ALSHelper
import com.android.local.service.core.data.ServiceConfig
import com.hkjustar.monitor.okhttp.data.MonitorData
import com.hkjustar.monitor.okhttp.data.SpValueInfo
import com.hkjustar.monitor.okhttp.enum.SPValueType
import com.hkjustar.monitor.okhttp.interceptor.MonitorInterceptor
import com.hkjustar.monitor.okhttp.interceptor.MonitorMockInterceptor
import com.hkjustar.monitor.okhttp.interceptor.MonitorMockResponseInterceptor
import com.hkjustar.monitor.okhttp.interceptor.MonitorWeakNetworkInterceptor
import com.hkjustar.monitor.okhttp.room.MonitorDao
import com.hkjustar.monitor.okhttp.room.MonitorDatabase
import com.hkjustar.monitor.okhttp.utils.MonitorProperties
import com.hkjustar.monitor.okhttp.utils.SPUtils
import com.hkjustar.monitor.okhttp.utils.defaultContentTypes
import com.hkjustar.monitor.okhttp.utils.lastUpdateDataId
import com.google.gson.Gson
import java.io.File
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.concurrent.thread
import okhttp3.Interceptor
import kotlin.runCatching


@SuppressLint("StaticFieldLeak")
object MonitorHelper {
    const val TAG = "MonitorHelper"

    var context: Context? = null
    var monitorDb: MonitorDatabase? = null

    var port = 0

    //有从来ASM修改字节码对OKHTTP进行hook用的
    val hookInterceptors = listOf(
        MonitorMockInterceptor(),
        MonitorInterceptor(),
        MonitorWeakNetworkInterceptor(),
        MonitorMockResponseInterceptor()
    )

    @JvmName("installHookInterceptors")
    fun installHookInterceptors(
        interceptors: MutableList<Interceptor>,
        insertMode: String = "before",
        anchorInterceptorClassName: String = "",
        builder: okhttp3.OkHttpClient.Builder? = null,
        existingEventListenerFactory: okhttp3.EventListener.Factory? = null
    ) {
        if (builder != null && existingEventListenerFactory != null) {
            builder.eventListenerFactory(
                com.hkjustar.monitor.okhttp.listener.MonitorEventListener.factory(existingEventListenerFactory)
            )
        }

        val hookInterceptorClassNames = hookInterceptors.mapTo(hashSetOf()) { it.javaClass.name }
        interceptors.removeAll { it.javaClass.name in hookInterceptorClassNames }

        val normalizedMode = insertMode.trim().lowercase(Locale.ROOT)
        val anchorClassName = anchorInterceptorClassName.trim()

        // 1. 先尝试精确匹配用户配置的锚点类名（支持全限定名或简单类名）
        var anchorIndex = if (anchorClassName.isNotBlank()) {
            interceptors.indexOfFirst {
                it.javaClass.name == anchorClassName || it.javaClass.simpleName == anchorClassName
            }
        } else -1

        // 2. 如果未指定锚点或未命中，自动识别解密拦截器（确保在解密外侧，使响应能先解密再进入监控，拿到纯明文）
        if (anchorIndex < 0) {
            val knownCryptoClassNames = listOf(
                "EncryptInterceptor",
                "OptionalCryptoV2Interceptor",
                "CryptoV2Interceptor"
            )
            anchorIndex = interceptors.indexOfFirst { inter ->
                val name = inter.javaClass.name
                val simpleName = inter.javaClass.simpleName
                knownCryptoClassNames.any { it == simpleName || name.endsWith(".$it") }
            }
        }

        // 3. 如果仍未找到解密拦截器，尝试在 CommonInterceptor 之后插入（以获取完整公参）
        if (anchorIndex < 0) {
            val commonIndex = interceptors.indexOfFirst {
                it.javaClass.simpleName == "CommonInterceptor" || it.javaClass.name.endsWith(".CommonInterceptor")
            }
            if (commonIndex >= 0) {
                anchorIndex = commonIndex + 1
            }
        }

        when (normalizedMode) {
            "prepend", "first" -> interceptors.addAll(0, hookInterceptors)
            "append", "last" -> interceptors.addAll(hookInterceptors)
            "after" -> {
                val insertIndex = if (anchorIndex >= 0) (anchorIndex + 1).coerceAtMost(interceptors.size) else 0
                interceptors.addAll(insertIndex, hookInterceptors)
            }
            else -> {
                // 默认 before：插在解密拦截器前面（应用层一侧），若完全未找到则默认插在头部 0，杜绝追加到末尾网络裸层
                val insertIndex = if (anchorIndex >= 0) anchorIndex else 0
                interceptors.addAll(insertIndex, hookInterceptors)
            }
        }
    }

    var whiteContentTypes: String? = null
    var whiteHosts: String? = null
    var blackHosts: String? = null
    var isFilterIPAddressHost: Boolean = false

    var isOpenMonitor = true

    private var singleThreadExecutor: ExecutorService? = null

    private fun threadExecutor(action: () -> Unit) {
        if (singleThreadExecutor == null || singleThreadExecutor?.isShutdown == true) {
            singleThreadExecutor = Executors.newSingleThreadExecutor()
        }
        singleThreadExecutor?.execute(action)
    }

    fun init(context: Context) {
        MonitorHelper.context = context
        // Rule assets from the app and all feature AARs are merged into this directory.
        // Loading is synchronous so malformed rule packs fail early and never leave monitoring
        // silently half-configured.
        com.hkjustar.monitor.okhttp.anomaly.AnomalyManager.loadRulePacks(context.applicationContext)
        thread {
            val propertiesData = MonitorProperties().paramsProperties()
            val dbName: String = propertiesData?.dbName ?: "monitor_room_db"
            val contentTypes = propertiesData?.whiteContentTypes
            whiteContentTypes = if (contentTypes.isNullOrBlank()) defaultContentTypes else contentTypes
            whiteHosts = propertiesData?.whiteHosts
            blackHosts = propertiesData?.blackHosts
            port = propertiesData?.port?.toInt() ?: 0
            isFilterIPAddressHost = propertiesData?.isFilterIPAddressHost ?: false
            initMonitorDataDao(context, dbName)
            initPCService(
                context,
                port
            )
        }
    }

    private fun initPCService(context: Context, port: Int = 0) {
        val serviceClass = runCatching {
            Class.forName("com.hkjustar.monitor.okhttp.service.MonitorService")
        }.getOrNull() ?: return
        ALSHelper.init(context)
        ALSHelper.startService(
            if (port > 0) ServiceConfig(
                serviceClass,
                port
            ) else ServiceConfig(serviceClass)
        )
        MonitorHelper.port = ALSHelper.serviceList.firstOrNull()?.port ?: 0
    }

    private fun initMonitorDataDao(context: Context, dbName: String) {
        if (monitorDb == null) {
            monitorDb = Room
                .databaseBuilder(context.applicationContext, MonitorDatabase::class.java, dbName)
                .addMigrations(
                    MonitorDatabase.MIGRATION_1_2,
                    MonitorDatabase.MIGRATION_2_3,
                    MonitorDatabase.MIGRATION_3_4
                )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }
    }

    private fun getMonitorDataDao(): MonitorDao? {
        return monitorDb?.monitorDao()
    }

    private const val MAX_MONITOR_COUNT = 3000
    private const val TRIM_TRIGGER_THRESHOLD = 100 // 每插入 100 条触发一次 SQLite 裁剪清理
    private val unTrimmedInsertCount = java.util.concurrent.atomic.AtomicInteger(0)

    fun insert(monitorData: MonitorData) {
        lastUpdateDataId = monitorData.id
        getMonitorDataDao()?.insert(monitorData)
        if (unTrimmedInsertCount.incrementAndGet() >= TRIM_TRIGGER_THRESHOLD) {
            unTrimmedInsertCount.set(0)
            threadExecutor {
                try {
                    getMonitorDataDao()?.trimExceedingLimit(MAX_MONITOR_COUNT)
                } catch (e: Exception) {
                    Log.d(TAG, "trimExceedingLimit error: ${e.message}")
                }
            }
        }
    }

    fun insertAsync(map: Map<String, Any>?) {
        if (map == null || map.isEmpty()) return
        threadExecutor {
            try {
                val monitor = Gson().fromJson(Gson().toJson(map), MonitorData::class.java)
                insert(monitor)
            } catch (e: Exception) {
                Log.d(TAG, "insertAsync--${e.message}")
            }
        }
    }

    fun update(monitorData: MonitorData) {
        getMonitorDataDao()?.update(monitorData)
    }

    fun deleteAll() {
        lastUpdateDataId = 0L
        getMonitorDataDao()?.deleteAll()
    }

    fun getMonitorDataListForAndroid(
        limit: Int = 50,
        offset: Int = 0
    ): LiveData<MutableList<MonitorData>>? {
        return getMonitorDataDao()
            ?.queryByOffsetForAndroid(limit, offset)
    }

    fun getMonitorDataList(keyword: String = "", content: String = "", limit: Int = 50, offset: Int = 0): MutableList<MonitorData> {
        return if (content.isNotBlank()) {
            getMonitorDataDao()?.queryByOffsetWithContent(keyword.trim(), content.trim(), limit, offset) ?: mutableListOf()
        } else {
            getMonitorDataDao()?.queryByOffset(keyword, limit, offset) ?: mutableListOf()
        }
    }

    fun getMonitorDataByLastIdForAndroid(lastUpdateDataId: Long): LiveData<MutableList<MonitorData>>? {
        return getMonitorDataDao()?.queryByLastIdForAndroid(lastUpdateDataId)
    }

    fun getMonitorDataByLastId(lastUpdateDataId: Long): MutableList<MonitorData> {
        return getMonitorDataDao()?.queryByLastId(lastUpdateDataId) ?: mutableListOf()
    }

    fun getMonitorDataByLastId(keyword: String = "", content: String = "", lastUpdateDataId: Long): MutableList<MonitorData> {
        return if (content.isNotBlank()) {
            getMonitorDataDao()?.queryByLastIdWithContent(keyword.trim(), content.trim(), lastUpdateDataId) ?: mutableListOf()
        } else {
            getMonitorDataDao()?.queryByLastId(keyword, lastUpdateDataId) ?: mutableListOf()
        }
    }

    fun getMonitorDataByLastId(keyword: String = "", lastUpdateDataId: Long): MutableList<MonitorData> {
        return getMonitorDataByLastId(keyword, "", lastUpdateDataId)
    }

    fun getSharedPrefsFilesData(): HashMap<String, HashMap<String, SpValueInfo?>> {
        val ctx = context ?: return hashMapOf()
        val map = hashMapOf<String, HashMap<String, SpValueInfo?>>()

        // 1. 读取 SharedPreferences (xml)
        val spDir = File("${ctx.cacheDir.parentFile?.absolutePath}/shared_prefs")
        if (spDir.exists() && spDir.isDirectory) {
            spDir.listFiles()?.forEach { spFile ->
                val fileName = spFile.name
                if (!fileName.isNullOrBlank()) {
                    val name = if (fileName.endsWith(".xml")) fileName.split(".xml")[0] else fileName
                    val value = getSpFile(name)
                    if (value.isNotEmpty()) {
                        map["[SP] $name"] = value
                    }
                }
            }
        }

        // 2. 读取 MMKV 文件 (files/mmkv)
        val mmkvDir = File("${ctx.cacheDir.parentFile?.absolutePath}/files/mmkv")
        if (mmkvDir.exists() && mmkvDir.isDirectory) {
            mmkvDir.listFiles()?.forEach { mmkvFile ->
                val fileName = mmkvFile.name
                // 排除 .crc 校验文件
                if (!fileName.isNullOrBlank() && !fileName.endsWith(".crc")) {
                    val name = fileName
                    val value = getMmkvData(name)
                    if (value.isNotEmpty()) {
                        map["[MMKV] $name"] = value
                    }
                }
            }
        }

        // 3. 读取 defaultMMKV 兜底 (若未生成独立文件)
        val defaultMmkvData = getMmkvData(null)
        if (defaultMmkvData.isNotEmpty() && !map.containsKey("[MMKV] defaultMMKV")) {
            map["[MMKV] defaultMMKV"] = defaultMmkvData
        }

        return map
    }

    fun getSpFile(name: String?): HashMap<String, SpValueInfo?> {
        if (name.isNullOrBlank()) return hashMapOf()
        // 判断是否为 MMKV 标签名
        if (name.startsWith("[MMKV] ")) {
            val mmkvMmapId = name.removePrefix("[MMKV] ").trim()
            return getMmkvData(if (mmkvMmapId == "defaultMMKV") null else mmkvMmapId)
        }

        val realSpName = name.removePrefix("[SP] ").trim()
        val map = hashMapOf<String, SpValueInfo?>()
        context?.getSharedPreferences(realSpName, Context.MODE_PRIVATE)?.all?.entries?.forEach {
            val valueType = when (it.value) {
                is Int -> SPValueType.Int
                is Double -> SPValueType.Double
                is Float -> SPValueType.Float
                is Long -> SPValueType.Long
                is Boolean -> SPValueType.Boolean
                is String -> SPValueType.String
                else -> SPValueType.String
            }
            val key = it.key
            if (!key.isNullOrBlank()) {
                map[key] = SpValueInfo(it.value, valueType)
            }
        }
        return map
    }

    private fun getMmkvData(mmapID: String?): HashMap<String, SpValueInfo?> {
        val map = hashMapOf<String, SpValueInfo?>()
        val mmkvObj = getMmkvInstance(mmapID) ?: return map

        try {
            val allKeysMethod = mmkvObj.javaClass.getMethod("allKeys")
            val keysArray = allKeysMethod.invoke(mmkvObj) as? Array<*> ?: return map

            val decodeStringMethod = mmkvObj.javaClass.getMethod("decodeString", String::class.java, String::class.java)
            val decodeBoolMethod = mmkvObj.javaClass.getMethod("decodeBool", String::class.java, Boolean::class.javaPrimitiveType)
            val decodeIntMethod = mmkvObj.javaClass.getMethod("decodeInt", String::class.java, Int::class.javaPrimitiveType)

            for (k in keysArray) {
                val key = k as? String ?: continue
                if (key.isBlank()) continue

                val strVal = decodeStringMethod.invoke(mmkvObj, key, null) as? String
                if (strVal != null) {
                    map[key] = SpValueInfo(strVal, SPValueType.String)
                    continue
                }

                val boolVal = decodeBoolMethod.invoke(mmkvObj, key, false) as? Boolean ?: false
                val boolCheck = decodeBoolMethod.invoke(mmkvObj, key, true) as? Boolean ?: true
                // A stored Boolean ignores both supplied defaults, so the results match
                // for both true and false. A type mismatch returns the two distinct defaults.
                if (boolVal == boolCheck) {
                    map[key] = SpValueInfo(boolVal, SPValueType.Boolean)
                    continue
                }

                val intVal = decodeIntMethod.invoke(mmkvObj, key, 0) as? Int ?: 0
                map[key] = SpValueInfo(intVal, SPValueType.Int)
            }
        } catch (e: Exception) {
            Log.e(TAG, "getMmkvData reflect error", e)
        }

        return map
    }

    private fun getMmkvInstance(mmapID: String?): Any? {
        return try {
            val mmkvClazz = Class.forName("com.tencent.mmkv.MMKV")
            if (mmapID.isNullOrBlank() || mmapID == "defaultMMKV") {
                val defaultMethod = mmkvClazz.getMethod("defaultMMKV")
                defaultMethod.invoke(null)
            } else {
                val mmkvWithIdMethod = mmkvClazz.getMethod("mmkvWithID", String::class.java, Int::class.javaPrimitiveType)
                val multiProcessMode = mmkvClazz.getField("MULTI_PROCESS_MODE").getInt(null)
                mmkvWithIdMethod.invoke(null, mmapID, multiProcessMode)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun updateSpValue(fileName: String, key: String, value: Any?) {
        if (fileName.startsWith("[MMKV] ")) {
            val mmapID = fileName.removePrefix("[MMKV] ").trim()
            val mmkvObj = getMmkvInstance(mmapID) ?: return
            try {
                when (value) {
                    is Boolean -> {
                        val encodeMethod = mmkvObj.javaClass.getMethod("encode", String::class.java, Boolean::class.javaPrimitiveType)
                        encodeMethod.invoke(mmkvObj, key, value)
                    }
                    is Int -> {
                        val encodeMethod = mmkvObj.javaClass.getMethod("encode", String::class.java, Int::class.javaPrimitiveType)
                        encodeMethod.invoke(mmkvObj, key, value)
                    }
                    is Long -> {
                        val encodeMethod = mmkvObj.javaClass.getMethod("encode", String::class.java, Long::class.javaPrimitiveType)
                        encodeMethod.invoke(mmkvObj, key, value)
                    }
                    is Float -> {
                        val encodeMethod = mmkvObj.javaClass.getMethod("encode", String::class.java, Float::class.javaPrimitiveType)
                        encodeMethod.invoke(mmkvObj, key, value)
                    }
                    is Double -> {
                        val encodeMethod = mmkvObj.javaClass.getMethod("encode", String::class.java, Double::class.javaPrimitiveType)
                        encodeMethod.invoke(mmkvObj, key, value)
                    }
                    else -> {
                        val encodeMethod = mmkvObj.javaClass.getMethod("encode", String::class.java, String::class.java)
                        encodeMethod.invoke(mmkvObj, key, value?.toString() ?: "")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "updateMmkvValue reflect error", e)
            }
            return
        }

        val realSpName = fileName.removePrefix("[SP] ").trim()
        SPUtils.saveValue(context ?: return, realSpName, key, value)
    }
}
