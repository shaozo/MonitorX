package com.hkjustar.plugin.okhttp


import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.InstrumentationParameters
import org.objectweb.asm.ClassVisitor

abstract class OkHttpTransform : AsmClassVisitorFactory<InstrumentationParameters.None> {
    override fun createClassVisitor(
        classContext: ClassContext,
        nextClassVisitor: ClassVisitor
    ): ClassVisitor {
        //指定真正的ASM转换器
        return OkHttpClassVisitor(nextClassVisitor)
    }

    //通过classData中的当前类的信息，用来过滤哪些类需要执行字节码转换，这里支持通过类名，包名，注解，接口，父类等属性来组合判断
    override fun isInstrumentable(classData: ClassData): Boolean {
        val extension = PluginHelper.extension

        return when {
            shouldIncludeClass(classData.className, extension?.includePackages) -> true
            shouldExcludeClass(classData.className, extension?.excludePackages) -> false
            else -> true
        }
    }

    private fun shouldIncludeClass(className: String, includePackages: Array<String>?): Boolean {
        return includePackages?.isNotEmpty() == true && includePackages.any { className.contains(it) }
    }

    private fun shouldExcludeClass(className: String, excludePackages: Array<String>?): Boolean {
        return excludePackages?.isNotEmpty() == true && excludePackages.any {
            val regex = it.replace(".", "\\.").replace("*", ".*")
            Regex(regex).matches(className)
        }
    }
}
