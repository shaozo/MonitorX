package com.shaovi.monitorx.gradle

import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.variant.AndroidComponentsExtension
import com.shaovi.monitorx.gradle.okhttp.ConfigExtension
import com.shaovi.monitorx.gradle.okhttp.OkHttpTransform
import com.shaovi.monitorx.gradle.okhttp.PluginHelper
import org.gradle.api.Plugin
import org.gradle.api.Project

class MonitorOkHttpPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        //这里appExtension获取方式与原transform api不同，可自行对比
        val appExtension = project.extensions.getByType(
            AndroidComponentsExtension::class.java
        )
        //读取配置文件
        val extension = project.extensions.create("OkHttpPlugin", ConfigExtension::class.java)
        project.extensions.add("MonitorX", extension)
        PluginHelper.extension = extension

        //这里通过transformClassesWith替换了原registerTransform来注册字节码转换操作
        appExtension.onVariants { variant ->
            project.logger.info("monitor-okhttp variant: ${variant.name}")
            val isEnabled = extension.enabledVariants.any { pattern ->
                Regex(pattern, RegexOption.IGNORE_CASE).matches(variant.name)
            }
            val isDisabled = extension.disabledVariants.any { pattern ->
                Regex(pattern, RegexOption.IGNORE_CASE).matches(variant.name)
            }
            if (isEnabled && !isDisabled) {
                //可以通过variant来获取当前编译环境的一些信息，最重要的是可以 variant.name 来区分是debug模式还是release模式编译
                variant.instrumentation.transformClassesWith(
                    OkHttpTransform::class.java,
                    InstrumentationScope.ALL
                ) {}

                //InstrumentationScope.ALL 配合 FramesComputationMode.COPY_FRAMES可以指定该字节码转换器在全局生效，包括第三方lib
                variant.instrumentation.setAsmFramesComputationMode(FramesComputationMode.COPY_FRAMES)
            }
        }
    }
}
