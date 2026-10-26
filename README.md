# MonitorX / InspectorX

[English](#english) · [中文](#中文)

[![Maven Central](https://img.shields.io/maven-central/v/io.github.shaozo.monitorx/monitor-ui?label=Maven%20Central)](https://central.sonatype.com/namespace/io.github.shaozo.monitorx)
[![GitHub](https://img.shields.io/badge/GitHub-shaozo%2FMonitorX-181717?logo=github)](https://github.com/shaozo/MonitorX)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

MonitorX 是面向 Android 开发与测试环境的诊断工具箱：在应用内或电脑浏览器中查看
OkHttp 请求、运行时性能、SharedPreferences、IM 消息和业务规则异常，并提供接口重放、
Mock 与弱网调试。InspectorX 是其中可脱离 Android 运行的规则引擎。

> 当前版本：`0.1.2` · [Maven Central](https://central.sonatype.com/namespace/io.github.shaozo.monitorx) · [GitHub 仓库](https://github.com/shaozo/MonitorX) · 最低 Android 版本：API 23 · 许可证：Apache-2.0

## 中文

### 能做什么

- **网络诊断**：记录 OkHttp 请求/响应、耗时、Header 和 Body，支持搜索与历史查看。
- **调试工具**：请求重放、接口脚本、Mock 响应、弱网模拟和 SharedPreferences 查看/修改。
- **性能观察**：页面、CPU、内存、流量、FPS、卡顿堆栈与测试报告对比。
- **业务异常检测**：通过 JSON 规则描述前置依赖、禁止时序、频率和漏斗问题。
- **双端查看**：Android 内置页面与同一局域网中的 PC Web 控制台。
- **生产隔离**：公共源码只依赖轻量 `monitor-api`；Release 中没有真实运行时则自动 no-op。

MonitorX 会记录请求内容、响应内容和 SharedPreferences，并可在局域网启动调试服务。
它只应进入受控的开发/测试构建，不能直接作为生产环境的安全审计或 APM 产品使用。

### 快速接入

环境要求：JDK 17、Android SDK 36，以及能够访问 Google Maven 和 Maven Central 的 Gradle
环境。项目当前使用 AGP 9.3.2、Kotlin 2.2.10 和 OkHttp 4.12.0。

正式版本发布在 [Maven Central](https://central.sonatype.com/namespace/io.github.shaozo.monitorx)，
源码、Issue 和 Release 记录位于 [GitHub 仓库](https://github.com/shaozo/MonitorX)。Maven
group 为 `io.github.shaozo.monitorx`，当前版本为 `0.1.2`。

在应用模块的 `build.gradle.kts` 中应用插件。公共 API 进入所有变体，完整实现只进入 Debug：

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("io.github.shaozo.monitorx") version "0.1.2"
}

dependencies {
    implementation("io.github.shaozo.monitorx:monitor-api:0.1.2")
    debugImplementation("io.github.shaozo.monitorx:monitor-ui:0.1.2")
}

MonitorX {
    // 插件默认匹配 .*demo.*；标准 debug/release 项目应显式覆盖。
    enabledVariants = arrayOf(".*Debug")
    disabledVariants = arrayOf(".*Release")
    injectionPoint = "build"
    insertMode = "before"
}
```

在 `Application` 中初始化。Release 只有 `monitor-api` 时，下列调用会安全地退化为 no-op：

```kotlin
import com.shaovi.monitorx.api.MonitorX

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        MonitorX.initialize(this)
        MonitorX.setEnabled(true)
        // 可选：显式控制业务规则监控；不调用时，有规则文件才会自动开启。
        // MonitorX.setBusinessMonitoringEnabled(false)
    }
}
```

业务监控默认采用自动模式：合并后的 `assets/monitorx/*.json` 中存在至少一条启用规则时开启，
没有规则文件时保持关闭。宿主可以在运行时调用
`MonitorX.setBusinessMonitoringEnabled(true/false)`，也可以在 `assets/monitor.properties`
中配置 `monitor.businessMonitoringEnabled=true|false`；运行时显式调用优先用于动态开关。

规则来源优先级为“手动指定 > 默认 assets 扫描”。默认扫描 `assets/monitorx/*.json`；需要覆盖时可调用：

```kotlin
MonitorX.loadBusinessRulePacks(
    applicationContext,
    listOf("business/login-rules.json", "business/payment-rules.json")
)
// 或加载指定目录中的全部 JSON
MonitorX.loadBusinessRuleDirectory(applicationContext, "business/rules")
```

手动指定会替换已经自动发现的规则包，避免同一规则重复安装。也可在
`assets/monitor.properties` 中使用 `monitor.businessRuleAssets`（逗号分隔文件）或
`monitor.businessRuleDirectory`；其中明确的文件列表优先于目录配置。

需要时打开工具页：

```kotlin
if (MonitorX.isRuntimeAvailable()) {
    MonitorX.open(activity)
}
```

插件会在编译期修改 `OkHttpClient.Builder`，因此修改插件配置后需要重新构建并安装 APK。
若项目存在加密/解密拦截器，可用 `anchorInterceptorClassName` 控制顺序；完整参数、插入顺序与
兜底规则见 [`okhttp/README.md`](okhttp/README.md)。

插件还会保留首个构建完成的宿主 `OkHttpClient` 供 Android/PC 接口调试复用；后续若识别到
配置的锚点、加密拦截器或 `CommonInterceptor`，会自动切换到该主业务客户端。发起一次真实
业务请求后，调试工具会从流量中学习对应的 scheme、host 和 port。仍可通过
`ApiDebugBridge.executor` 显式配置执行器，显式配置始终优先。

### 验证 Release 隔离

插件的变体过滤只关闭字节码插桩，真正把页面、服务、数据库和运行时排除出线上包的是
`debugImplementation`。不要通过 `implementation` 或 `releaseImplementation` 引入
`monitor-ui`、`monitor-tools`、`monitor-runtime-android` 或 `monitor-okhttp`。

```bash
./gradlew :app:dependencies --configuration releaseRuntimeClasspath
./gradlew :app:assembleRelease
```

Release 依赖树应只包含 `monitor-api`，`MonitorX.isRuntimeAvailable()` 应返回 `false`。

### 选择合适的模块

各模块以独立 Maven 产物发布，上层模块通过 Maven 传递依赖拉取下层模块，并非合并成一个 AAR。

| Maven artifact | 用途 | Android |
|---|---|---|
| `monitor-api` | 稳定入口与自动 no-op 后端 | 是，轻量 |
| `monitor-core` | InspectorX 事件、规则、会话和 JSON 规则包 | 否 |
| `monitor-source-okhttp` | 将 HTTP 快照转换为 InspectorX 事件 | 否 |
| `monitor-okhttp` | OkHttp 抓包、Room 存储、Mock、弱网和异常桥接 | 是 |
| `monitor-runtime-android` | 页面生命周期和性能采集 | 是 |
| `monitor-tools` | PC 控制台、接口调试、IM 检查和本地服务 | 是 |
| `monitor-ui` | Android Activity、Fragment 和资源 | 是 |
| `monitorx-gradle-plugin` | OkHttp Builder 字节码插桩 | 编译期 |

```text
monitor-ui -> monitor-tools -> monitor-runtime-android
           -> monitor-okhttp -> monitor-source-okhttp -> monitor-core
                             -> monitor-api
```

不需要 UI 和 PC 工具时，可以只使用较小的抓包运行时：

```kotlin
debugImplementation("io.github.shaozo.monitorx:monitor-okhttp:0.1.2")
```

### InspectorX 业务规则

`monitor-core` 是纯 JVM 模块，可以在 Android 之外或单元测试中使用：

```kotlin
InspectorX.installRulePack(json)
InspectorX.resetSession(userId)
InspectorX.emit("LOGIN_COMPLETED")
val violations = InspectorX.emit("CHECKOUT_STARTED", mapOf("orderId" to orderId))
```

Android 应用可以把规则包放到 `src/<variant>/assets/monitorx/*.json`，运行时会按文件名加载。
目前支持 `POINT`、`DEPENDENCY`、`FORBIDDEN_BEFORE`、`RATE` 和 `FUNNEL`。参考：

- [`samples/rules/checkout-rules.json`](samples/rules/checkout-rules.json)：最小规则示例；
- [`BUSINESS_MONITORING.md`](BUSINESS_MONITORING.md)：规则字段、上下文、支付链路和查看方式。

### 运行示例与本地开发

克隆项目后，用 Android Studio 打开根目录，选择 `sample` 的 `debug` 变体运行。当前 `sample`
展示 UI、no-op API 和 InspectorX 规则；如果要验证 OkHttp **自动插桩**，还需要在
`sample/build.gradle.kts` 应用 `io.github.shaozo.monitorx` 插件并按上文启用 Debug 变体。

```bash
./gradlew test assemble
./gradlew -p gradle-plugin build
```

主要源码目录：

```text
api/               稳定 Android API
core/              平台无关规则引擎
source-okhttp/      OkHttp -> InspectorX 事件适配
okhttp/             抓包、存储、Mock 和弱网
runtime-android/    Android 页面与性能运行时
tools/              PC 控制台与调试服务
ui/                 Android UI
gradle-plugin/      自动插桩插件
sample/             示例应用
```

### 隐私与安全

- 网络 Body、Header、Token、Cookie 和 SharedPreferences 可能包含敏感信息。
- PC 控制台会通过局域网暴露设备内调试数据；只在可信网络和测试设备中使用。
- 规则包、Mock 与接口重放会改变调试行为，不应在生产构建开启。
- 提交 issue 前请删除日志、截图和导出文件中的账号、密钥与个人数据。
- 不要把 Central 凭据、签名私钥、keystore 或构建产物提交到仓库。

### 参与贡献

欢迎通过 Issue 报告可复现的问题，或通过 Pull Request 提交修复。提交前请：

1. 说明 Android/AGP/Kotlin/OkHttp 版本、构建变体和复现步骤；
2. 为规则引擎或插桩逻辑变更补充测试；
3. 运行 `./gradlew test assemble` 和 `./gradlew -p gradle-plugin build`；
4. 保持公共 API 与 Maven 坐标兼容，破坏性变更需在 PR 中明确说明。

当前仓库尚未提供正式的兼容性承诺、CI 门禁、`CONTRIBUTING.md` 和 `SECURITY.md`。在 1.0
之前，公共 API 与规则格式仍可能调整；生产团队应固定版本并自行完成升级验证。

### 发布维护者说明

项目通过 Central Portal 发布。凭据和签名信息应放在 `~/.gradle/gradle.properties`，或使用
带 `ORG_GRADLE_PROJECT_` 前缀的环境变量：

```properties
mavenCentralUsername=<token username>
mavenCentralPassword=<token password>
signing.keyId=<last 8 characters of key ID>
signing.password=<OpenPGP password>
signing.secretKeyRingFile=/absolute/path/to/secring.gpg
```

```bash
./gradlew publishToMavenLocal
./gradlew publishToMavenCentralPortal
./scripts/publish-all-to-maven-central.sh  # 库与 Gradle 插件
```

Maven group 为 `io.github.shaozo.monitorx`；Kotlin/Java package 保持
`com.shaovi.monitorx`，以兼容已有源码与二进制。

## English

MonitorX is an Android diagnostics toolkit for OkHttp inspection, request replay, mocking, weak
network simulation, runtime performance, SharedPreferences, IM messages, and rule-based business
flow diagnostics. InspectorX is its platform-independent rules engine.

### Quick start

Requirements: JDK 17, Android SDK 36, minSdk 23. Add the lightweight API to every variant and the
runtime only to debug builds:

Published artifacts are available from [Maven Central](https://central.sonatype.com/namespace/io.github.shaozo.monitorx).
Source code, issues, and releases are hosted in the [GitHub repository](https://github.com/shaozo/MonitorX).
The Maven group is `io.github.shaozo.monitorx`, and the current version is `0.1.2`.

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("io.github.shaozo.monitorx") version "0.1.2"
}

dependencies {
    implementation("io.github.shaozo.monitorx:monitor-api:0.1.2")
    debugImplementation("io.github.shaozo.monitorx:monitor-ui:0.1.2")
}

MonitorX {
    enabledVariants = arrayOf(".*Debug")
    disabledVariants = arrayOf(".*Release")
}
```

The plugin defaults to variants matching `.*demo.*`, so standard projects should explicitly enable
Debug as shown above. Initialize from your `Application`:

```kotlin
MonitorX.initialize(applicationContext)
MonitorX.setEnabled(true)

if (MonitorX.isRuntimeAvailable()) {
    MonitorX.open(activity)
}
```

With only `monitor-api` in a release build, calls safely become no-ops and no MonitorX UI, database,
service, initializer, interceptor, or bytecode instrumentation is packaged. Dependency scoping—not
variant filtering—is the production boundary.

### InspectorX

InspectorX works in a plain JVM process or test. Place Android rule packs in
`src/<variant>/assets/monitorx/*.json`. Supported types are `POINT`, `DEPENDENCY`,
`FORBIDDEN_BEFORE`, `RATE`, and `FUNNEL`. See
[`samples/rules/checkout-rules.json`](samples/rules/checkout-rules.json) and
[`BUSINESS_MONITORING.md`](BUSINESS_MONITORING.md).

### Build, security, and contributions

```bash
./gradlew test assemble
./gradlew -p gradle-plugin build
```

MonitorX may capture credentials, headers, bodies, cookies, preferences, and other private data,
and its PC console exposes debug data on the local network. Use it only in controlled development
or test builds and on trusted networks. Redact sensitive data from issues and exports.

Issues and pull requests are welcome. The project is pre-1.0 and does not yet provide a formal
compatibility policy, CI gate, contribution guide, security policy, or changelog; consumers should
pin versions and validate upgrades.

## License

Licensed under the [Apache License 2.0](LICENSE).
