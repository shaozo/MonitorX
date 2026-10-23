# MonitorX 业务问题监控使用指南

本文面向接入业务和维护规则的开发者，说明业务规则放置位置、新用户判断、支付链路监控、历史问题查看和规则扩展方式。

## 1. 代码边界

MonitorX 是通用组件，只负责：

- 将 HTTP 请求和响应转换成标准事件；
- 执行 `POINT`、`RATE`、`FUNNEL` 等通用规则；
- 保存并展示命中记录；
- 提供应用内通知、Android 历史页和 PC 控制台。

具体业务规则必须放在业务工程的 assets 中，不能写入 MonitorX 组件源码。例如 Gemgala 的规则位于：

```text
app/src/demo/assets/monitorx/gemgala-rules.json
```

应用启动时，MonitorX 会扫描合并后的 `assets/monitorx/*.json`，按文件名稳定排序并自动加载。新增业务模块时，可以在该模块自己的 `src/<variant>/assets/monitorx/` 下增加规则包。

## 2. 查看监控结果

### Android 端

打开 Monitor 应用，在底部选择“业务问题”。页面提供：

- 问题总数、严重/警告数量和已启用规则数量；
- 按规则名、规则 ID、接口、字段或页面搜索；
- 发生时间、所在 Activity、请求路径、现场值和修复建议；
- 下拉刷新。

点击系统通知或页面顶部告警条，会直接打开 Monitor 的“业务问题”页。Monitor 首页的“清空”会同时清理网络、IM 和业务问题历史。

### PC 端

打开 Monitor 首页，点击右上角“业务/时序异常”。PC 页面支持历史记录、严重级别筛选、关键词搜索、规则包状态、运行开关和跳转原始请求。

## 3. 新用户判断

“新用户”应由可信的业务响应或登录态得出，不能用页面是否第一次打开、本地安装时间等 UI 状态猜测。

### 3.1 只检查当前接口响应

`getUserSignInRecord` 的 `info.isNewUser` 和 `info.yd_device_id` 会被 HTTP source 展平为：

```text
http.response.info.isNewUser
http.response.info.yd_device_id
```

当前 Gemgala 规则只在 `isNewUser == 1` 时检查响应中的 `yd_device_id`：

```json
{
  "id": "gemgala_new_user_signin_response_device_id_missing",
  "type": "POINT",
  "matcher": {
    "eventType": "HTTP_RESPONSE",
    "pathRegex": ".*/game/getUserSignInRecord.*"
  },
  "conditionMode": "ALL",
  "conditions": [
    { "attribute": "http.response.info.isNewUser", "operator": "EQUALS", "value": "1" },
    { "attribute": "http.response.info.yd_device_id", "operator": "BLANK" }
  ]
}
```

这种方式不需要写 Kotlin 接入代码，适合“判断字段和被检查字段在同一个响应中”的场景。

### 3.2 在后续业务链路中持续使用新用户状态

如果登录或签到响应确认了用户类型，而后续多个接口都要基于该状态检查，应写入 InspectorX 会话上下文：

```kotlin
InspectorX.setContext("user.isNew", isNewUser.toString())
InspectorX.setContext("user.id", userId.toString())
```

规则通过 `contextConditions` 限定只对新用户执行：

```json
{
  "id": "new_user_reward_missing",
  "name": "新用户奖励字段缺失",
  "type": "POINT",
  "message": "新用户奖励接口没有返回奖励标识。",
  "matcher": {
    "eventType": "HTTP_RESPONSE",
    "pathRegex": ".*/newUser/reward.*"
  },
  "contextConditions": [
    { "attribute": "user.isNew", "operator": "EQUALS", "value": "true" }
  ],
  "conditions": [
    { "attribute": "http.response.info.rewardId", "operator": "BLANK" }
  ]
}
```

账号切换、退出登录或重新建立登录会话时必须重置上下文，避免上一个用户的状态污染下一个用户：

```kotlin
InspectorX.resetSession(userId.toString())
```

推荐顺序是：登录成功 → `resetSession(userId)` → 写入 `user.isNew` 等上下文。退出登录时调用无参数的 `resetSession()`。

## 4. 支付链路监控

Gemgala 在 Google Play 返回 `PURCHASED` 后，以 `purchaseToken` 开始一次支付验单监控：

```kotlin
InspectorXAndroid.watchPaymentServerNotification(
    purchaseToken = purchase.purchaseToken,
    productId = productId
)
```

当前 Demo 已通过 `GooglePayRecoveryManager.PurchaseObservedListener` 自动接入，不需要各支付页面重复调用。

完整链路如下：

```text
Google Play PURCHASED
        ↓
PAYMENT_SUCCEEDED（以 purchaseToken 关联）
        ↓ 最长 15 秒
HTTP_REQUEST /google/pay/recharge/noticeResult
```

- 15 秒内完全没有发起 `noticeResult`：命中 `gemgala_payment_server_notice_missing`，级别为 `CRITICAL`。
- 同一 `purchaseToken` 在 30 秒内重复发起验单：命中 `gemgala_payment_notice_duplicate`。
- 完成点使用 `HTTP_REQUEST` 而不是 `HTTP_RESPONSE`，因此服务端响应慢或返回错误不会被误判为“客户端没有通知服务端”。响应错误由业务错误规则单独处理。
- 订阅订单当前由原订阅链路处理，不进入金币充值补单监控。

## 5. 规则类型选择

| 类型 | 适用场景 | 示例 |
|---|---|---|
| `POINT` | 单次事件的字段非法或缺失 | 经纬度为 `99999`、设备指纹为空 |
| `RATE` | 短时间重复调用 | 重复提现、重复送礼、Token 刷新风暴 |
| `FUNNEL` | A 发生后限定时间内必须发生 B | 支付成功后必须发起服务端验单 |
| `DEPENDENCY` | B 发生前必须先发生 A | 进入核心业务前必须完成初始化 |
| `FORBIDDEN_BEFORE` | A 发生前禁止出现 B | 登录完成前禁止执行用户态操作 |

HTTP 参数统一使用 `http.param.<字段名>`；响应 JSON 会递归展平为 `http.response.<路径>`。常用事件类型为 `HTTP_REQUEST`、`HTTP_RESPONSE` 和 `CUSTOM`。

## 6. 新增或修改规则

1. 在业务模块的 `assets/monitorx/*.json` 中修改，保持规则 ID 全局稳定。
2. 为规则提供明确的 `message` 和可执行的 `solution`。
3. URL 正则尽量限定到真实业务路径，避免使用过宽的 `.*` 产生误报。
4. 涉及订单或用户时，优先设置 `correlationAttribute`，避免并发链路串联。
5. 本地运行：

```bash
cd monitorX
ANDROID_HOME=/path/to/android-sdk ./gradlew test assemble
```

规则包采用严格校验：未知字段、重复 ID、非法正则、缺少必填值以及格式错误的禁用规则都会在加载时失败，防止问题配置静默上线。

## 7. 当前 Gemgala 覆盖范围

当前规则覆盖定位参数、易盾设备指纹、新用户签到、重复签到、提现、奖励领取、Token 登录风暴、支付验单缺失与重复验单、核心业务错误、重复送礼、重复下注、非法充值/提现金额、未登录房间操作和 IM 同步风暴。

这些规则均属于 Gemgala 业务配置；抽离 MonitorX 到其他项目时不会自动携带 Gemgala 规则。
