# MonitorX 业务问题监控使用指南

本文面向接入业务和维护规则的开发者，说明业务规则放置位置、上下文管理、业务链路监控、历史问题查看和规则扩展方式。

## 1. 代码边界与设计原则

MonitorX / InspectorX 是场景中立的诊断与规则引擎，核心职责包括：

- 将 HTTP 请求和响应自动转换为标准流事件；
- 执行 `POINT`、`RATE`、`FUNNEL`、`DEPENDENCY`、`FORBIDDEN_BEFORE` 等通用时序与约束规则；
- 保存并展示命中记录，提供应用内通知、Android 历史页与 PC Web 控制台；
- **场景中立**：SDK 源码中不包含任何具体业务语义（如特定业务接口、支付订单、商品字段等），所有业务差异均由配置文件驱动，业务代码按需插桩。

具体业务规则必须放在业务宿主工程的 assets 中，例如：

```text
app/src/main/assets/monitorx/business-rules.json
```

应用启动时，MonitorX 会扫描合并后的 `assets/monitorx/*.json`，按文件名稳定排序并自动加载。多模块架构下，各业务子模块可在各自的 `src/<variant>/assets/monitorx/` 下提供模块级规则包。

业务监控默认采用自动模式：存在至少一条启用规则时开启，没有规则文件时关闭。引用端可以通过
代码动态控制：

```kotlin
MonitorX.setBusinessMonitoringEnabled(false)
```

也可以在宿主的 `src/<variant>/assets/monitor.properties` 中静态配置：

```properties
monitor.businessMonitoringEnabled=false
```

通过网页“启用监控/暂停监控”切换时，手机通知、Android 历史记录和网页记录使用同一个状态，
不会再出现网页已暂停但手机仍产生告警的情况。规则文件应交给自动扫描加载；不要再额外调用
`InspectorX.installRulePack` 重复安装，否则网页无法可靠展示规则包来源信息。

默认规则来源是 `assets/monitorx/*.json`。宿主需要指定其他规则时，可调用
`MonitorX.loadBusinessRulePacks(context, assetPaths)` 或
`MonitorX.loadBusinessRuleDirectory(context, assetDirectory)`；手动指定会替换默认扫描结果，优先级更高。
静态配置也支持：

```properties
# 明确文件列表优先于目录；多个文件使用逗号分隔
monitor.businessRuleAssets=business/login.json,business/payment.json
# 未配置文件列表时可指定目录
monitor.businessRuleDirectory=business/rules
```

## 2. 查看监控结果

### Android 端

打开 Monitor 应用，在底部选择“业务问题”。页面提供：

- 问题总数、严重/警告数量和已启用规则数量；
- 按规则名、规则 ID、接口、字段或页面搜索；
- 发生时间、所在 Activity、请求路径、现场值和修复建议；
- 下拉刷新。

InspectorX 检测到问题后只发送系统通知，不会向宿主 Activity 注入告警 View，因此不会遮挡或改变业务测试页面。点击系统通知会直接打开 Monitor 的“业务问题”页。Monitor 首页的“清空”会同时清理网络、IM 和业务问题历史。

### PC 端

打开 Monitor 网页后，点击顶部“业务监控”进入独立页面。页面提供运行状态、全部已加载规则包、每条规则的触发条件与处理建议、规则搜索筛选，以及历史问题查询和清理。网络数据首页仍会实时弹出新问题提醒。

必须通过 App Monitor 页面展示的 HTTP 地址打开控制台，例如 `http://192.168.1.20:9527/business_monitor_index`，并确保手机和电脑处于同一局域网。直接通过 `file://` 打开 HTML 只能预览静态页面，无法访问手机上的规则与异常记录接口；页面会明确提示当前处于离线预览状态。

## 3. 上下文状态与标记检测

### 3.1 检查单次接口请求/响应字段（无需代码插桩）

HTTP source 会将请求参数展平为 `http.param.<key>`，响应 JSON 递归展平为 `http.response.<path>`。

例如，检测“新用户签到响应中必须下发设备绑定标识”：

```json
{
  "id": "new_user_signin_device_id_missing",
  "name": "新用户签到未返回设备标识",
  "type": "POINT",
  "severity": "WARNING",
  "matcher": {
    "eventType": "HTTP_RESPONSE",
    "pathRegex": ".*/user/getSignInRecord.*"
  },
  "conditionMode": "ALL",
  "conditions": [
    { "attribute": "http.response.info.isNewUser", "operator": "EQUALS", "value": "1" },
    { "attribute": "http.response.info.deviceId", "operator": "BLANK" }
  ],
  "message": "新用户签到响应缺少关键 deviceId 字段。",
  "solution": "请服务端排查新用户签到下发分支是否遗漏设备标识。"
}
```

这种方式完全由配置文件驱动，无需在宿主业务代码中添加任何监控插桩。

### 3.2 跨接口链路的会话上下文（Session Context）

当某个状态（如用户身份、是否为新用户、AB 测试分组等）需要在后续多个接口的监控规则中持续生效时，可在业务登录态确立后写入上下文：

```kotlin
// 推荐通过轻量级的 MonitorX API 写入，Release 环境下自动 safe no-op
MonitorX.setContext("user.isNew", isNewUser.toString())
MonitorX.setContext("user.id", userId.toString())
```

在 JSON 规则中通过 `contextConditions` 约束：

```json
{
  "id": "new_user_reward_missing",
  "name": "新用户奖励字段缺失",
  "type": "POINT",
  "severity": "CRITICAL",
  "matcher": {
    "eventType": "HTTP_RESPONSE",
    "pathRegex": ".*/reward/claim.*"
  },
  "contextConditions": [
    { "attribute": "user.isNew", "operator": "EQUALS", "value": "true" }
  ],
  "conditions": [
    { "attribute": "http.response.rewardId", "operator": "BLANK" }
  ],
  "message": "新用户领取奖励响应未包含奖励标识。"
}
```

**账号切换与清理**：
账号切换或退出登录时，必须重置上下文以避免污染后续用户状态：

```kotlin
// 登录成功时建立新会话并绑定上下文
MonitorX.resetSession(userId.toString())
MonitorX.setContext("user.isNew", "true")

// 退出登录时重置
MonitorX.resetSession()
```

## 4. 业务链路与时序漏斗监控（以支付/验单链路为例）

针对跨事件的时序漏斗（如“客户端支付成功后必须在限定时间内通知服务端验单”），InspectorX 采用**通用事件插桩 + 规则配置关联**的模式，SDK 不绑定任何特定业务 API。

### 4.1 业务端插桩

在业务生命周期关键节点，只需调用通用 `emit` 打点：

```kotlin
// 在 Google Play / 微信 / 支付宝等支付成功回调处插桩
MonitorX.emit(
    name = "PAYMENT_SUCCEEDED",
    attributes = mapOf(
        "order_id" to purchase.orderId,
        "product_id" to productId
    )
)
```

> **提示**：若在 Release 构建中只引入了 `monitor-api`，上述 `MonitorX.emit` 为安全空实现，零性能损耗、零依赖。

旧版 `InspectorXAndroid.watchPaymentServerNotification(...)` 仍保留兼容，但已标记废弃。
新接入应使用 `MonitorX.emit(...)` 配合规则包显式描述成功、服务端通知和超时节点；需要继续
沿用旧 watchdog 行为的宿主可在迁移完成前继续调用旧方法。

### 4.2 规则配置定义（JSON 驱动）

在 assets 的 JSON 规则包中定义 `FUNNEL` 漏斗规则：

```json
{
  "id": "payment_server_notice_missing",
  "name": "支付后缺少服务端验单",
  "type": "FUNNEL",
  "severity": "CRITICAL",
  "start": { "eventType": "CUSTOM", "name": "PAYMENT_SUCCEEDED" },
  "completion": { "eventType": "HTTP_REQUEST", "pathRegex": ".*/pay/noticeResult.*" },
  "windowMs": 15000,
  "correlationAttribute": "order_id",
  "message": "支付成功后 15 秒内未向服务端发起验单请求。",
  "solution": "请检查客户端支付回调中向后端发起 noticeResult 接口的时机与网络状态。"
}
```

- **关联字段跟随配置**：通过 `correlationAttribute` 将自定义事件中的 `order_id` 与接口请求中的 `order_id` 自动关联，多个并发订单不会产生串扰。
- **完成点推荐使用 `HTTP_REQUEST`**：关注客户端“是否发起请求”，避免服务端响应慢或偶发 500 导致误报。服务端响应异常可由单独的 `HTTP_RESPONSE` 规则检测。

## 5. 规则类型选择

| 类型 | 适用场景 | 示例 |
|---|---|---|
| `POINT` | 单次事件的字段非法或缺失 | 经纬度异常（99999）、设备指纹为空、状态码不匹配 |
| `RATE` | 短时间内高频重复调用 | 重复提现、重复送礼、Token 刷新风暴 |
| `FUNNEL` | A 事件发生后限定时间内必须发生 B 事件 | 支付完成后必须发起服务端验单、点击提现后必须发起网络调用 |
| `DEPENDENCY` | B 事件发生前必须先发生 A 事件 | 进入核心业务前必须完成 SDK 初始化 |
| `FORBIDDEN_BEFORE` | A 事件发生前禁止出现 B 事件 | 登录完成前禁止执行用户态网络请求 |

HTTP 参数统一使用 `http.param.<字段名>`；响应 JSON 会递归展平为 `http.response.<路径>`。常用事件类型为 `HTTP_REQUEST`、`HTTP_RESPONSE` 和 `CUSTOM`。

## 6. 新增或修改规则

1. 在业务模块的 `assets/monitorx/*.json` 中新增或修改，保持规则 ID 全局唯一且稳定。
2. 为规则提供明确的 `message` 和具有可操作性的 `solution`。
3. URL 正则尽量限定到具体业务路径，避免使用宽泛的 `.*` 引发误报。
4. 涉及订单、会话或用户维度时，优先设置 `correlationAttribute`。
5. 本地验证与构建：

```bash
./gradlew test
```

规则包在加载时会执行严格校验：未知字段、重复 ID、非法正则或缺少必填项均会触发明确报错，杜绝格式错误上线。

## 7. 典型场景覆盖参考

在实际项目中，通过组合 JSON 规则与少量业务通用打点，可以轻松覆盖：

- **基础质量**：网络请求公共参数校验、设备指纹非空、基础配置合法性；
- **时序与状态**：未登录态禁止发起业务请求、SDK 初始化依赖关系；
- **频控与风控**：按钮防重复点击/提交、Token 刷新风暴、短时间内重复发起敏感接口；
- **关键链路闭环**：支付充值验单漏斗、关键功能点击到网络发起的闭环追踪。
