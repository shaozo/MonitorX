# MonitorX / InspectorX

[English](#english) · [中文](#中文)

MonitorX is an Android diagnostics toolkit for HTTP inspection, runtime performance, developer
tools, and rule-based business-flow diagnostics. InspectorX is its platform-independent rule
engine.

---

## English

### Modules

MonitorX is published as separate Maven artifacts. Selecting a high-level artifact pulls its
required lower-level modules through normal Maven dependencies; the modules are not merged into a
single AAR.

| Artifact | Purpose | Android |
|---|---|---|
| `monitor-api` | Stable API with an automatic no-op fallback | Yes, lightweight |
| `monitor-core` | InspectorX events, rules, sessions, and JSON rule packs | No |
| `monitor-source-okhttp` | Converts HTTP snapshots into InspectorX events | No |
| `monitor-okhttp` | OkHttp capture, Room storage, mocking, and anomaly bridge | Yes |
| `monitor-runtime-android` | Lifecycle, foreground Activity, and performance collectors | Yes |
| `monitor-tools` | PC console, API debugger, IM inspector, and local service | Yes |
| `monitor-ui` | Optional activities, fragments, adapters, and resources | Yes |
| `monitorx-gradle-plugin` | OkHttp builder bytecode instrumentation | Build time |

```text
monitor-ui -> monitor-tools -> monitor-runtime-android
           -> monitor-okhttp -> monitor-source-okhttp -> monitor-core
                             -> monitor-api
```

### What does no-op mean?

No-op means “no operation.” The call is valid but intentionally performs no work. In a release
build that contains only `monitor-api`:

- `MonitorX.initialize()` does not create a database or start a thread/service;
- `MonitorX.setEnabled()` safely does nothing;
- `MonitorX.isEnabled()` and `MonitorX.open()` return `false`;
- no MonitorX Activity, AndroidX Startup initializer, OkHttp interceptor, or bytecode
  instrumentation is packaged.

This lets shared `main` source call the same API in debug and release builds without reflection,
flavor checks, or release-only stubs in the application.

### Recommended integration

Use the lightweight API in every variant and package the real implementation only in debug builds:

```kotlin
plugins {
    id("io.github.shaozo.monitorx") version "0.1.1"
}

dependencies {
    implementation("io.github.shaozo.monitorx:monitor-api:0.1.1")
    debugImplementation("io.github.shaozo.monitorx:monitor-ui:0.1.1")
}

MonitorX {
    enabledVariants = arrayOf(".*Debug")
    disabledVariants = arrayOf(".*Release")
}
```

Calls from shared application code are safe:

```kotlin
import com.shaovi.monitorx.api.MonitorX

MonitorX.initialize(applicationContext)
MonitorX.setEnabled(true)

if (MonitorX.isRuntimeAvailable()) {
    MonitorX.open(this)
}
```

For the strongest production guarantee, do not use `implementation` or
`releaseImplementation` with `monitor-ui`, `monitor-tools`, `monitor-runtime-android`, or
`monitor-okhttp`. Variant filtering disables instrumentation, but dependency scoping is what keeps
the runtime code, manifest entries, database, and services out of the release APK.

Choose a smaller debug artifact when UI or tooling is unnecessary:

```kotlin
debugImplementation("io.github.shaozo.monitorx:monitor-okhttp:0.1.1")
```

### InspectorX

InspectorX can run in a JVM process or test without Android:

```kotlin
InspectorX.installRulePack(json)
InspectorX.emit("LOGIN_COMPLETED")
val violations = InspectorX.emit("CHECKOUT_STARTED", mapOf("orderId" to orderId))
InspectorX.resetSession(userId)
```

Place portable rule packs in `src/main/assets/monitorx/*.json`. Supported rule types are `POINT`,
`DEPENDENCY`, `FORBIDDEN_BEFORE`, `RATE`, and `FUNNEL`. See
[`samples/rules/checkout-rules.json`](samples/rules/checkout-rules.json) and
[`BUSINESS_MONITORING.md`](BUSINESS_MONITORING.md).

### Build and publish

```bash
./gradlew test assemble publishToMavenLocal
./gradlew -p gradle-plugin build publishToMavenLocal
```

Maven Central publishing uses a Central Portal token and an in-memory OpenPGP key supplied outside
the repository:

```bash
./gradlew publishAndReleaseToMavenCentral
./gradlew -p gradle-plugin publishAndReleaseToMavenCentral
```

Never commit AAR files, Central credentials, or signing keys. Maven coordinates use
`io.github.shaozo.monitorx`; existing Kotlin/Java packages remain `com.shaovi.monitorx` for source
and binary compatibility.

---

## 中文

### 模块说明

MonitorX 会以多个独立 Maven 产物发布。引用上层模块时，Gradle 会通过正常的 Maven 传递依赖
拉取所需的下层模块，并不是把所有代码合并进一个 AAR。

| 产物 | 用途 | 是否依赖 Android |
|---|---|---|
| `monitor-api` | 稳定公共 API；缺少真实实现时自动 no-op | 是，但非常轻量 |
| `monitor-core` | InspectorX 事件、规则、会话和 JSON 规则包 | 否 |
| `monitor-source-okhttp` | 将 HTTP 快照转换为 InspectorX 事件 | 否 |
| `monitor-okhttp` | OkHttp 抓包、Room 存储、Mock 和异常桥接 | 是 |
| `monitor-runtime-android` | 页面生命周期、前台 Activity 和性能采集 | 是 |
| `monitor-tools` | PC 控制台、接口调试、IM 检查和本地服务 | 是 |
| `monitor-ui` | 可选 Activity、Fragment、Adapter 和资源 | 是 |
| `monitorx-gradle-plugin` | OkHttp Builder 字节码插桩 | 仅编译期 |

```text
monitor-ui -> monitor-tools -> monitor-runtime-android
           -> monitor-okhttp -> monitor-source-okhttp -> monitor-core
                             -> monitor-api
```

### no-op 是什么意思？

no-op 是 “no operation（不执行操作）” 的缩写：调用合法，但实现有意不做任何工作。
Release 只包含 `monitor-api` 时：

- `MonitorX.initialize()` 不创建数据库，也不启动线程或服务；
- `MonitorX.setEnabled()` 安全地直接返回；
- `MonitorX.isEnabled()` 和 `MonitorX.open()` 返回 `false`；
- APK 中不会包含 MonitorX 页面、AndroidX Startup 初始化器、OkHttp 拦截器或字节码插桩。

因此，`main` 公共源码可以在 Debug 和 Release 中调用同一套 API，不需要在业务项目中写反射、
BuildConfig 判断或 Release 专用假实现。

### 推荐接入方式

所有构建变体依赖轻量 API，只有 Debug 打包真实实现：

```kotlin
plugins {
    id("io.github.shaozo.monitorx") version "0.1.1"
}

dependencies {
    implementation("io.github.shaozo.monitorx:monitor-api:0.1.1")
    debugImplementation("io.github.shaozo.monitorx:monitor-ui:0.1.1")
}

MonitorX {
    enabledVariants = arrayOf(".*Debug")
    disabledVariants = arrayOf(".*Release")
}
```

公共业务代码可以安全调用：

```kotlin
import com.shaovi.monitorx.api.MonitorX

MonitorX.initialize(applicationContext)
MonitorX.setEnabled(true)

if (MonitorX.isRuntimeAvailable()) {
    MonitorX.open(this)
}
```

为了获得最可靠的线上隔离，请不要用 `implementation` 或 `releaseImplementation` 引入
`monitor-ui`、`monitor-tools`、`monitor-runtime-android` 或 `monitor-okhttp`。插件的变体过滤只负责
关闭插桩；真正保证线上 APK 不包含监控代码、Manifest 节点、数据库和服务的是
`debugImplementation` 依赖隔离。

如果不需要 UI 或工具层，可以选择更小的 Debug 依赖：

```kotlin
debugImplementation("io.github.shaozo.monitorx:monitor-okhttp:0.1.1")
```

### InspectorX

InspectorX 可以脱离 Android，在 JVM 服务或单元测试中运行：

```kotlin
InspectorX.installRulePack(json)
InspectorX.emit("LOGIN_COMPLETED")
val violations = InspectorX.emit("CHECKOUT_STARTED", mapOf("orderId" to orderId))
InspectorX.resetSession(userId)
```

可移植规则包放在 `src/main/assets/monitorx/*.json`。支持 `POINT`、`DEPENDENCY`、
`FORBIDDEN_BEFORE`、`RATE` 和 `FUNNEL`。示例见
[`samples/rules/checkout-rules.json`](samples/rules/checkout-rules.json)，完整业务监控说明见
[`BUSINESS_MONITORING.md`](BUSINESS_MONITORING.md)。

### 本地构建与发布

```bash
./gradlew test assemble publishToMavenLocal
./gradlew -p gradle-plugin build publishToMavenLocal
```

发布 Maven Central 时，从仓库外提供 Central Portal Token 和 OpenPGP 私钥：

```bash
./gradlew publishAndReleaseToMavenCentral
./gradlew -p gradle-plugin publishAndReleaseToMavenCentral
```

不要提交 AAR、Central 凭据或签名私钥。Maven 坐标使用 `io.github.shaozo.monitorx`；为了保持
源码和二进制兼容，现有 Kotlin/Java 包名继续使用 `com.shaovi.monitorx`。
