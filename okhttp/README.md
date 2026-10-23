# monitor-okhttp 配置说明

`monitorX/okhttp` 提供无 UI 的抓包运行时，`monitorX/gradle-plugin` 把监控拦截器自动插入到 `OkHttpClient.Builder`。

## 接入方式

在业务模块里应用插件并引入监控库：

```kotlin
plugins {
    id("com.shaovi.monitorx")
}

dependencies {
    "demoImplementation"(projects.monitorX.ui)
}
```

然后在模块的 `build.gradle.kts` 中配置 `OkHttpPlugin`：

```kotlin
OkHttpPlugin {
    excludePackages = listOf("com.google.*", "com.facebook.*").toTypedArray()
    injectionPoint = "build"
    insertMode = "before"
}
```

## 参数说明

### `excludePackages`

- 类型：`Array<String>`
- 默认值：`emptyArray()`
- 作用：排除不需要做 ASM 处理的类
- 示例：`arrayOf("com.google.*", "com.facebook.*")`

### `includePackages`

- 类型：`Array<String>`
- 默认值：`emptyArray()`
- 作用：只处理命中的类
- 说明：当前插件里 `includePackages` 优先级高于 `excludePackages`

### `includeMethods`

- 类型：`Array<String>`
- 默认值：`emptyArray()`
- 作用：预留字段，当前版本未参与实际插桩判断

### `excludeMethods`

- 类型：`Array<String>`
- 默认值：`emptyArray()`
- 作用：预留字段，当前版本未参与实际插桩判断

### `limitTime`

- 类型：`Long`
- 默认值：`500`
- 作用：预留字段，当前版本未参与实际插桩判断

### `injectionPoint`

- 类型：`String`
- 默认值：`"build"`
- 可选值：`"build"`、`"init"`
- 作用：决定在 `OkHttpClient.Builder` 的哪个阶段注入 monitor 拦截器

取值说明：

- `build`：在 `OkHttpClient.Builder.build()` 前注入
- `init`：在 `OkHttpClient.Builder` 构造时注入

推荐使用 `build`，因为业务代码通常会在 `Builder` 创建后继续 `addInterceptor(...)`，放到 `build` 阶段更容易控制最终顺序。

### `insertMode`

- 类型：`String`
- 默认值：
  `injectionPoint = "build"` 时默认 `before`
  `injectionPoint = "init"` 时默认 `first`
- 可选值：`"before"`、`"after"`、`"append"`、`"prepend"`
- 作用：决定 monitor 拦截器相对锚点拦截器的位置

取值说明：

- `before`：插到锚点前
- `after`：插到锚点后
- `append`：直接追加到最后
- `prepend`：直接插到最前面

### `anchorInterceptorClassName`

- 类型：`String`
- 默认值：`""`
- 作用：指定顺序锚点，用于配合 `insertMode`

如果指定的类没有出现在当前 `Builder.interceptors` 中：

- `before`：退化为追加到末尾
- `after`：退化为追加到末尾
- `append`：直接追加到末尾
- `prepend`：直接插到最前面

## 参数组合关系

`injectionPoint` 和 `insertMode` 会一起决定最终效果，但不是所有组合都会严格按字面值生效。

- `injectionPoint`：决定什么时候把 monitor 拦截器注入到 `Builder`
- `insertMode`：决定 monitor 拦截器在 `Builder.interceptors` 列表中的相对位置

例如：

- `build + before EncryptInterceptor`
  含义是：在 `build()` 前执行插入，并把 monitor 拦截器放到 `EncryptInterceptor` 前面

- `build + after EncryptInterceptor`
  含义是：在 `build()` 前执行插入，并把 monitor 拦截器放到 `EncryptInterceptor` 后面

所以 `build + after` 是合法配置，不是“build 之后再插入”。

## 兜底规则

不合法或不适用的配置不会报错，也不会打印 warning，而是静默按默认规则处理。

当前规则如下：

- `injectionPoint` 不是 `build` 或 `init`
  会退回到 `build`

- 未显式配置 `insertMode`
  `build` 会默认使用 `before`
  `init` 会默认使用 `first`

- `insertMode` 不是 `before`、`after`、`append`、`prepend`
  会退回到 `before`

- `injectionPoint = "init"`
  `insertMode` 会被直接忽略，并统一按 `first` 处理

- `insertMode = "before"` 或 `insertMode = "after"`，但未设置 `anchorInterceptorClassName`
  会按“没有锚点”处理

- `insertMode = "append"` 或 `insertMode = "prepend"`
  `anchorInterceptorClassName` 会被忽略

## 推荐配置

### 1. 同时拿到公参和解密后的响应

如果你的链路类似：

`DomainInterceptor -> CommonInterceptor -> EncryptInterceptor -> TimeoutInterceptor`

推荐：

```kotlin
OkHttpPlugin {
    injectionPoint = "build"
    insertMode = "before"
    anchorInterceptorClassName = "com.hkjustar.modulebase.net.EncryptInterceptor"
}
```

最终顺序会接近：

`DomainInterceptor -> CommonInterceptor -> MonitorInterceptors -> EncryptInterceptor -> TimeoutInterceptor`

这样有两个好处：

- 请求方向上，monitor 能看到 `CommonInterceptor` 补完的公参
- 响应方向上，`EncryptInterceptor` 会先解密，再回到 monitor，所以 monitor 能拿到解密后的响应体

### 2. 只关心请求最终参数，不关心响应是否解密

```kotlin
OkHttpPlugin {
    injectionPoint = "build"
    insertMode = "append"
}
```

这种方式更简单，但如果有响应解密拦截器，monitor 看到的通常会是解密前的内容。

### 3. 希望 monitor 最早执行

```kotlin
OkHttpPlugin {
    injectionPoint = "build"
    insertMode = "prepend"
}
```

这种方式适合调试“原始请求”，但通常看不到后续拦截器补充后的参数。

### 4. 合法但不推荐的组合示例

```kotlin
OkHttpPlugin {
    injectionPoint = "build"
    insertMode = "after"
    anchorInterceptorClassName = "com.hkjustar.modulebase.net.EncryptInterceptor"
}
```

这个配置是合法的，但一般不适合需要“公参 + 解密响应”同时成立的场景。

常见结果是：

- 请求方向上仍然能看到公参
- 响应方向上 monitor 会先拿到 response，导致抓到的是解密前内容

## 运行时行为

插件注入后，运行时会调用 `MonitorHelper.installHookInterceptors(...)` 对拦截器再次整理，主要做两件事：

1. 移除已存在的 monitor 拦截器，避免重复插入
2. 按 `insertMode + anchorInterceptorClassName` 重新插入

所以即使同一个 `Builder` 被多次处理，也不会把 monitor 拦截器重复加很多份。

## 常见问题

### 为什么能拿到公参，但看不到解密后的响应？

说明 monitor 被放到了 `EncryptInterceptor` 后面。

建议改成：

```kotlin
OkHttpPlugin {
    injectionPoint = "build"
    insertMode = "before"
}
```

### 为什么看得到解密响应，但请求里没有公参？

说明 monitor 太靠前，先于 `CommonInterceptor` 执行了。

这种情况通常是：

- `injectionPoint = "init"`
- 或者 `insertMode = "prepend"`
- 或者锚点选择不合适

### 修改配置后为什么效果没变？

因为这是编译期插件，改完配置后需要重新打包安装，旧 APK 中的插桩结果不会自动更新。
