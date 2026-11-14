# 迭代记录

## 2026-10-28 · 第二轮：从「个人行程本」做到「团队协作」

上一轮完成的是单用户闭环（登录、行程、地点、链接、示意图）。这一轮把它补成产品定义里真正的「一起规划」，
并修掉了上一轮遗留的体验问题。

### 补齐的核心能力（上一轮完全缺失）

| 能力 | 后端 | App |
|------|------|-----|
| 邀请码加入行程 | `POST /api/trips/join`、`Trips.kt` 的 `inviteCode` 生成/轮换 | 「加入行程」弹窗，输入 8 位码 |
| 成员与角色 | `TripMember` + `TripAccessor`（OWNER/EDITOR/VIEWER） | 成员底部面板：改角色、移除、看加入时间 |
| 一起编辑 | 地点/链接/评论/清单/决策全部改成成员写权限 | 按角色隐藏增删改按钮，只读成员有明确提示 |
| 讨论 | `TripComment` + `/comments` | 「讨论」页签，可挂到具体地点 |
| 群决策投票 | `Decision` / `DecisionOption`（投票存为用户 id 集合） | 「投票」页签，点选/取消/改票/定案/重开 |
| 行前清单 | `ChecklistItem` + `/checklist` | 「清单」页签，勾选、负责人、进度 |
| 预算 | 行程增加 `budgetTarget` | 「预算」页签：合计、人均、按天、明细、超支提示 |
| 编辑历史 | `TripActivity` + `ActivityLogger` | 成员面板里的「最近动态」 |
| 导出 | `TripExportService` → `GET /export`（Markdown） | 「更多 → 导出 Markdown」预览、复制、分享 |
| 数据删除 | 删除行程按外键顺序级联清理 | 「更多 → 删除行程」二次确认 |

### 修掉的上一轮问题

- **token 不持久化**：改用 DataStore 保存，冷启动自动恢复；`/api/auth/me` 校验，401 才清空
- **分享只支持冷启动**：`MainActivity.onNewIntent` + eventId，App 在前台时分享链接也会弹保存框
- **删旅程会把库删崩**：原先 `tripRepository.delete` 会被外键挡住，现在先清子表
- **所有接口只认创建者**：`findOwnedTrip` 被 `TripAccessor` 替换，成员可读可写、只读成员被拦
- **加了地点全排 0**：`sortOrder` 由后端按 current max+1 分配，排序稳定
- **UI 没有编辑/删除地点**：补齐编辑器与删除确认
- **地图页无法修改顺序**：排序收敛到「行程」页签，地图页只负责看

### 工程与验证

- `scripts/api-smoke.ps1`：66 项后端断言，覆盖注册/邀请/权限/协作/投票/清单/动态/导出/级联删除，当前全绿
- `scripts/seed-demo.ps1`：一键造演示数据（两个账号 + 一趟杭州行程）
- 后端 `gradle compileKotlin`、App `gradle assembleDebug` 均通过
- 用 AVD 实机走查：登录 → 行程列表 → 7 个页签 → 成员面板 → 导出 → 加入行程（错误码/正确码）→
  只读角色渲染 → 杀进程重启保持登录 → 运行中接收分享并保存链接

### 遗留

参见 `README.md` 的「已知限制」。下一轮建议先做拖拽排序 + 下拉刷新 + 邀请深链/二维码。

## 2026-10-28 · 第三轮：协作实时性与并发安全

上一轮补齐了"多人能一起改"，但两个人同时用的时候仍会觉得"不同步、会互相覆盖、退不出去"。这一轮把这几个洞补上。

### 自动同步（不再需要手动刷新）

- 后端新增 `GET /api/trips/{id}/revision`：返回该行程最新一条动态 id，作为轻量变更计数
- 已有写操作原本都记动态；这轮补记了**排序、链接编辑、清单改名**，保证任何改动都会让计数变大
- App 在 `TripDetailViewModel` 里每 12 秒拉一次 revision，只有变大才重新拉数据；
  页面 `ON_RESUME` 时立即补拉一次，`ON_PAUSE` 停止轮询（`LifecycleEventEffect`）

### 并发保护（不再静默覆盖）

- `Trip` / `Place` / `LinkItem` / `ChecklistItem` / `Decision` / `DecisionOption` 加 `@Version`
- 响应带 `version`；`PUT` 请求可带 `version`，服务端比对不一致直接 `409` + 中文提示
- 关键坑：JPA 的版本号在事务提交时才自增，所以更新接口里先 `entityManager.flush()` 再组装响应，
  否则返回给客户端的还是旧版本号（这一轮实测发现并修掉）
- 客户端遇到 409：提示 + 自动 `refreshAll`，用户看到的是最新内容
- 投票是并发高发点：`DecisionOption` 也加了版本号，客户端撞车时**刷新并自动重试一次**

### 成员主动退出

- `DELETE /api/trips/{id}/members/me`；创建者调用返回 403（提示删除行程）
- App「更多」菜单对非创建者显示「退出行程」，二次确认后回到列表并刷新

### 验证

- `scripts/api-smoke.ps1` 扩到 **75 项断言**，新增：revision 可读/会增长、正确版本可保存、
  过期版本 409、按新版本重试成功、创建者不能退出、成员退出 204、退出后 403、重复退出 404
- 实机验证：数据库重置后旧 token 自动失效回登录页；队友用 API 加地点后，App 12 秒内自己出现（D1 小计 ¥555 → ¥655）；
  队友「退出行程」后回到空列表，接口确认其行程数为 0

### 数据说明

- 加 `@Version` 后旧库需要重建：删除 `backend/data/` 重启即可（本轮已重置并重新 seed 演示数据）

### 遗留

- 仍是轮询而非推送，后台/锁屏不同步；冲突是"提示刷新"而非自动合并
- 下一轮建议：SSE/WebSocket + 未读红点、拖拽排序、邀请深链/二维码

## 2026-10-28 · 第四轮：实时推送、未读、拖拽、邀请二维码

把上一轮遗留的三件事一次做完，另外修了「首次打开行程把历史动态全算未读」的体验 bug。

### 1. SSE 实时推送（替代 12 秒轮询）

- 后端 `TripEventBroker`：按 tripId 管理 `SseEmitter`，20 秒心跳；`TripEvents` 用
  `TransactionSynchronization.afterCommit` 推送，避免客户端收到通知时事务还没提交、拉到旧数据
- 触发点收敛在 `ActivityLogger.log`：任何写操作都会记一条动态，从而自然带出一次推送
- 端点 `GET /api/trips/{id}/stream`（`text/event-stream`），复用 JWT 过滤器做鉴权
- App 用 OkHttp SSE 长连接，`ON_RESUME` 建立、`ON_PAUSE` 断开；失败后 5 秒重连、最多 5 次，
  同时保留 30 秒兜底轮询（SSE 被网络掐掉时不至于完全看不到更新）
- 实测：队友通过 API 写一条地点，App 在 2 秒内自动出现（D1 小计 ¥555 → ¥643），无需任何操作

### 2. 未读红点

- `ReadStateStore`（DataStore）记录每趟行程读到的最新 revision
- 列表页：`trip.revision > 已读` 时标题前显示红点
- 详情页：页签按动态类型标红点（地点→行程、链接→链接、评论→讨论、决策→投票、清单→清单），
  头部显示「有 N 条新动态」
- 首次打开某趟从未看过的行程时，把当前 revision 作为基线（否则会把历史动态全算成未读，
  这一轮就踩到了：显示「24 条新动态」）
- 离开详情页（`DisposableEffect.onDispose`）时回写已读

### 3. 长按拖拽排序

- 新增 `ReorderablePlaceList`：`detectDragGesturesAfterLongPress` + `graphicsLayer` 位移，
  拖动中实时交换并高亮，松手才提交
- 仅在同一天内排序：提交时把该天的新顺序合并回全局 `sortOrder`（其他天位置不变）
- 行程页从 `LazyColumn` 改为滚动 `Column`（便于测量与拖拽），地点数量级别下没有性能问题
- 实测：把「西湖断桥」拖到「楼外楼」之后，服务端顺序从 5,6,7… 变为 6,5,7…，动态里也多了一条「调整了行程的先后顺序」

### 4. 邀请深链 + 二维码

- `plantogether://join/CODE` + Manifest intent-filter，`launchMode=singleTop`，
  运行中也能通过 `onNewIntent` 收到
- 未登录时先记下邀请码，登录后自动加入；加入失败用 Toast 提示，成功直接进这趟行程
- 成员面板新增「二维码」：ZXing core 生成位图，扫码即打开深链，另附「复制链接 / 分享」
- 实测：把队友移出后，队友用深链打开 App，自动重新加入并进入行程（成员数回到 2）

### 验证

- `scripts/api-smoke.ps1` 扩到 **76 项断言**（新增「行程自带 revision 与 /revision 一致」），全绿
- SSE 用 `curl -N` 验证：连上收到 `event:connected`，写入后收到 `event:revision`
- 真机走查：列表红点 → 打开行程基线 → 队友改动 → 头部「有 1 条新动态」→ 页签红点 →
  拖拽改序 → 二维码 → 深链自动加入

### 依赖变化

- App 新增 `com.squareup.okhttp3:okhttp-sse:4.12.0`、`com.google.zxing:core:3.5.3`

### 遗留

- 后台/锁屏没有系统级推送；红点不区分具体人和数量；拖拽跨天会改日期语义，因此限制在同一天内
- 下一轮建议：系统推送（FCM）、下拉刷新、模板、上线前的 PostgreSQL/Flyway 迁移

## 2026-10-28 · 第五轮：外部跳转 + 给组员的打包

### 1. 地点卡片「看点评」

- 每张地点卡片新增「看点评」，弹出选择：大众点评 / 美团 / 高德地图 / 复制店名
- 跳转策略：
  - 点评、美团用 **https 搜索链接**（装了 App 且有 App Links 就直接进 App，没装进浏览器，不会失败）
  - 高德用官方 `androidamap://poi` scheme，未安装或打不开时退回 `uri.amap.com` 网页
  - 面板里会显示"已安装 / 未安装，用浏览器打开"，让用户有预期
- 边界：**只跳转，不抓取、不缓存对方数据**。这是这些平台唯一允许的集成方式，也规避了爬虫合规风险
- 实测：点「看点评」→ 大众点评 → Chrome 打开 `dianping.com/search/keyword/...`（模拟器无外网所以页面没加载，但链接已正确带到浏览器）

### 2. 让组员能跑起来

- 两个模块都补上了 **Gradle Wrapper**（之前只有 properties、没有 jar），
  `gradlew.bat` 可直接用，发行包指向腾讯镜像；对方不需要有本地 Gradle
- 新增 `scripts/package-for-team.ps1`：产出 `dist\PlanTogether-src.zip`（约 0.2 MB，121 个文件）
  和 `dist\app-debug.apk`（约 20 MB），自动排除 `build/.gradle/.kotlin/.tools/backend\data/local.properties`
- README 新增「十、怎么把项目发给组员」：源码包、APK、Git 仓库三条路，以及 APK 的后端地址限制
  （`BASE_URL` 写死 127.0.0.1，装到别人手机上要改成局域网 IP 或做成可配置项）

### 遗留

- 组员直接装 APK 时仍需能访问到后端；「服务器地址可配置」还没做（改动很小，需要时再加）
- 系统推送、下拉刷新、截图 OCR 导入仍未做



## 2026-11-14 · 第六轮：把「能用」磨成「好用」

前五轮做的是功能有没有，这一轮只做一件事：**把每天都会碰到的粗糙处磨平**。
不改数据模型、不加新接口（只加了一个 `/api/health` 复用），全部是体验与容错。

### 1. 服务器地址可配置（去掉「只能自己电脑上用」的限制）

之前 `BASE_URL` 写死在 `ApiClient.kt`，装到别人手机上必然连不上，改地址还得改代码重编译。

- `ApiClient` 从 `object` 常量改成**运行时可重建**：`baseUrl` + `api` 都是 `@Volatile var`，
  `setBaseUrl()` 校验通过后整条 Retrofit 链重建；地址非法时**保持原地址不变**，不会把自己弄失联
- 地址规范化：不写 `http://` 自动补、缺尾部 `/` 自动补，用 OkHttp 的 `toHttpUrlOrNull()` 做真实校验
- 新增 `SettingsStore`（DataStore）持久化地址；`restoreSession()` 启动时先加载地址再校验登录态
- 新增「服务器设置」弹窗（登录页底部入口）：改地址 →「保存并测试」→ 调免登录的 `/api/health`
  探活，分别报「格式不对 / 找不到主机 / 连不上 / 超时」，成功显示绿色「✓ 已连接到 …」
- 网络错误文案按异常类型细分（`UnknownHostException` / `ConnectException` / `SocketTimeoutException`），
  并直接指路「检查服务器设置」

**意义**：同一个 APK，填一次局域网 IP 就能给别人手机用。第五轮遗留的「APK 只能自己用」问题关闭。

### 2. 日期/时间从手输改成选择器

原先是 `OutlinedTextField` 手打 `yyyy-MM-dd`，格式错就 400，共 5 处。

- 新增 `FormInputs.kt`：`DateField`（系统日历）、`TimeField`（24 小时表盘）
- 实现方式：输入框 `readOnly` + 透明 `Box(matchParentSize)` 遮罩接点击，避免和 TextField 的
  focus/软键盘打架；有值时右侧给「清除」
- 替换范围：新建行程、编辑行程信息、地点编辑器的日期与起止时间，**全 App 手输日期清零**
  （`grep "yyyy-MM-dd\|toDoubleOrNull\|toIntOrNull"` 在 `FormInputs.kt` 之外已无匹配）

### 3. 非法输入不再静默丢值

原先 `toDoubleOrNull()` / `toIntOrNull()` 失败就变 `null`，等于默默把用户填的东西扔了。

- 新增 `NumberResult<T>`（`Empty` / `Invalid(message)` / `Ok(value)`），配 `orNull()` / `errorOf()`
- 新增 `ValidatedField`：输入框 + 错误提示 + `isError` 红边；**只在用户点过保存后（`tried`）才报错**，
  不打扰正在输入的人
- 覆盖：预计人数、总预算、地点预算、纬度、经度、停留时长
- 所有数字栏位配 `numberKeyboard()`，自动弹数字/小数键盘

### 4. 下拉刷新

- 行程列表、行程详情 7 个页签都支持（material3 1.4.0 的 `PullToRefreshBox`）
- 列表页去掉了顶栏「刷新」按钮，改由下拉承担，顶栏更宽松
- **下拉失败不清空已有内容**：只有当前不是 Success 才提示错误，避免网络抖动把列表刷成白屏
- 详情页抽出 `loadEverything(reportErrors)`，`refreshAll` 与 `pullRefresh` 共用，避免两套加载逻辑

### 5. 后端错误兜底

`ApiExceptionHandler` 原来只覆盖业务异常，格式错/缺参/未捕获异常会漏成 500 + 英文技术细节甚至堆栈。

新增 4 个 handler：

| 异常 | 原来 | 现在 |
|------|------|------|
| `HttpMessageNotReadableException` | 500 + Jackson 英文细节 | 400 +「日期格式不对，请用 2026-11-07 这种写法」/「数字格式不对，请只填阿拉伯数字」 |
| `MethodArgumentTypeMismatchException` | 500 | 400 +「参数「x」的格式不对」 |
| `MissingServletRequestParameterException` | 400 但文案泛 | 400 +「缺少必要参数「x」」 |
| 其他未捕获 `Exception` | 500 + 堆栈外泄 | 500 +「服务器出了点问题，请稍后重试」，堆栈只进服务端日志 |

### 验证

- 后端 `compileKotlin` BUILD SUCCESSFUL
- `scripts/api-smoke.ps1`：**76 项断言全部通过**（0 失败）
- App `assembleDebug` BUILD SUCCESSFUL（20,628,341 字节，与上一版同尺寸）
- 模拟器实机走查 12 张截图（`.tools/shots/v1`–`v12`）：
  - v1–v4：服务器设置入口、弹窗、试连成功（绿）、填错地址超时（红）且原地址未被破坏
  - v5：行程列表，顶栏「刷新」已移除
  - v7：下拉时截到转圈指示器，确认刷新真的触发
  - v8–v9：编辑行程，日期为只读框 + 系统日历
  - v10：预计人数填 `abc` → 红边 +「请填整数」，弹窗不关、不发请求
  - v11–v12：地点编辑器日期/时间只读，点「开始」弹 24 小时表盘，选完回填 `21:10`

### 依赖变化

- 无新增第三方依赖（`PullToRefreshBox`、`DatePicker`、`TimePicker` 都在已有的 material3 里）

### 遗留

- 日期选择器在英文系统上显示英文（跟随系统语言，非 App 问题）
- 下拉刷新没有"上次更新时间"提示
- 系统推送、截图 OCR 导入仍未做

---

## 2026-11-14 · 第七轮：把「点评」接进来

### 先说结论

用户提的需求是「像接入高德一样接入美团/大众点评做餐饮点评」。查完三家的开放程度后，
做的是一个**分工**方案，而不是三个都硬接：

| 平台 | 有 C 端搜索 API | 能给什么 | 结论 |
|------|----------------|---------|------|
| 高德 | 有（v5 POI，`show_fields=business`） | 评分、人均、营业时间、特色菜、电话、商圈 | 结构化数据从这拿 |
| 大众点评 | 没有 | 食客评价正文 | 只能跳转 |
| 美团 | 没有 | 团购/优惠 | 只能跳转 |

美团/大众点评的开放平台面向商家和服务商（团购核销），没有公开的「按店名搜并拿评分」接口；
网页有登录墙和反爬，抓取违规且不稳。所以两类信息分开呈现：**值不值得去**看高德那份数据，
**别人吃过怎么评价**点进去看。

### 后端：高德 POI 代理

新增 `poi/` 包：

- **`PoiDtos.kt`**：`PoiSuggestion`（全字段可空）、`PoiSearchResponse(configured, items, message?)`。
  `configured=false` 表示服务端没配 Key——这是正常状态，不是错误，App 据此显示「还没接地图服务」。
- **`AmapPoiService.kt`**：调 `https://restapi.amap.com/v5/place/text`，`show_fields=business` 一次拿到
  评分/人均/营业时间（v3 要额外再打一次详情接口，翻倍消耗配额）。
  - 缓存：同城市+关键词 5 分钟内复用，上限 200 条，省高德配额。
  - 错误翻译：`infocode` 10001/10002/10003/10004 →「Key 无效或已过期」，
    10020/10021/10029 →「今日配额已用完」，其他→「地图服务返回异常」。
  - `parseLocation` 拆 v5 的 `"120.1489,30.2587"` 单字符串经纬度。
  - 过滤 `"[]"`：高德对缺失字段返回空数组占位，不是缺 key。
- **`PoiController.kt`**：`GET /api/poi/search?q=&city=&limit=`，走现有 JWT 鉴权。

Key 放后端不放 APK（APK 能被 unzip，Key 打进去等于送人），环境变量 `PLANTOGETHER_AMAP_KEY` 传入；
另加 `PLANTOGETHER_AMAP_ENDPOINT` 可改端点，本地联调时指向假响应，不消耗真实配额。

**踩坑**：Kotlin 注释里写 `/api/auth/**` 会触发 `Syntax error: Missing '}'`
—— `/*` 被当成嵌套注释开头。注释里不要出现 `/*` 序列。

### 数据模型：评分快照

`Place` 加三个字段（都是字符串，因为高德返回的就是字符串）：

```kotlin
var rating: String? = null
var avgCost: String? = null
@Column(length = 200) var openTime: String? = null
```

存**快照**而不是每次实时查：评分人均天天在变，行程卡不该跟着抖动。
`PlaceRequest` / `PlaceResponse` 同步加这三个字段，`ddl-auto=update` 自动加列。

### App：搜店名自动补全

- **`PoiPicker.kt`**：`PoiSearchSection` 五态（Idle/Searching/NotConfigured/Results/Empty），
  结果行显示店名 + 类型标签 + `★ 4.6 · 人均 ¥218.00` + 地址/商圈 + 营业时间。
- **`PlaceEditorDialog`**：表单顶部插入搜索区。选中后**只覆盖能从高德确认的字段**
  （名称/地址/经纬度/评分/人均/营业时间），用户已经写好的备注、预算、时间一律不动
  —— 这是「补全」不是「覆盖」。
- **`RatingSummary`**：编辑弹窗里和地点卡片上都显示 `★ 4.6 · 人均 ¥218.00 · 营业 10:30-20:30`，
  用中性色（`onSurfaceVariant`）因为这是参考信息，不该抢「几点到、花多少钱」的注意力。

### 「看点评」面板升级

原来只有三个跳转按钮，现在：

1. 顶部先把已有的高德数据摊开（评分/人均/营业时间 +「来自高德地图」），
   不用跳出去就能判断值不值得去；没数据时整块隐藏，不留空框。
2. 下面小标题「看真实食客怎么说」，再列大众点评 / 美团 / 高德 / **小红书·微博**
   （复制「XX 好吃吗」并提示去搜，补上了高德没有的「素人口碑」）。

### 后端异常处理修正

冒烟测试发现日期格式错返回的是通用提示而不是日期提示。原因：`mostSpecificCause`
是最内层的 `DateTimeParseException`（`Text '2026/11/07' could not be parsed`），
里面**没有** `LocalDate` 字样，原来的字符串匹配落到了 else 分支。

改为顺着整条 cause 链拼起来判断，并从 `InvalidFormatException.targetType` 取目标类型
（比消息文本可靠）。`LocalDateTime` 的判断必须排在 `LocalDate` 前面——前者字符串含后者。

### 验证

- 后端 `compileKotlin` ✅、App `assembleDebug` ✅
- **假高德服务联调**（`.tools/fake-amap.py`）：用真实 v5 响应形状造了 4 条数据，
  覆盖「字段齐全 / `[]` 占位 / `business` 缺失 / `location` 缺失」四种情况，
  解析全部正确，不崩
- 缓存验证：同词连打 3 次，假高德只收到 1 次请求
- 真机走查（`Medium_Phone_API_37.0`）：
  - `w3-place-editor.png`：添加地点弹窗，顶部搜索区就位，「优先搜『杭州』附近」
  - `w4-poi-results.png`：4 条结果，评分/人均/营业时间/类型标签/商圈都正确渲染，
    第 3、4 条（故意缺字段）各自少几行，符合预期
  - `w5-poi-filled.png`：点第一条 → 名称「楼外楼（孤山路店）」、地址、纬度 `30.253893`、
    经度 `120.142715` 全部回填，评分摘要行出现
  - `w6-card-rating.png`：保存后地点卡片下方出现 `★ 4.6 · 人均 ¥218.00 · 营业 10:30-20:30`
  - `w7-lookup-sheet.png`：有评分时面板顶部有数据块 +「来自高德地图」，
    下面「看真实食客怎么说」
  - `w8-lookup-norating.png`：无评分地点（河坊街）整块隐藏，不留空框
  - `w9-not-configured.png`：不配 Key 时显示「这个服务器还没接地图服务，先手动填吧。」，
    表单完全可用
- **冒烟测试从 76 项加到 88 项**，全新通过：日期格式错→400+中文提示（不是 500）、
  错误响应不含英文堆栈、未登录搜 POI→401、`configured` 标志、空关键词不报错、
  评分快照存进去能原样读回

### 依赖变化

无新增第三方依赖。高德调用用 JDK 自带 `java.net.http.HttpClient` + 已有 Jackson。

### 遗留

- **没有真实高德 Key**，真实数据链路没端到端跑过（解析逻辑用假响应验证过了）
- 美团/大众点评的 deeplink scheme（如 `imeituan://`）未核实，目前仍走 https 链接靠 App Links
- 点评只给跳转，没有店名自动匹配（用户贴一条点评链接进来，还得手打店名）

---

## 2026-11-14 · 第八轮：把微信群聊变成行程

### 起因

需求原话：「微信聊天可以合并转发，我想在软件中加入一个解压功能并且读取聊天内容生成规划」。

一开始的直觉是「微信不给接口，做不了」。查证后推翻了这个直觉：**微信 8.0.78 之后真的会打包**。
多选消息 →「转发到其他应用」→ 选择 App，微信自己把选中的消息打成一个标准 zip
（内含按时间排好的 `聊天记录.txt` 和附件），交给收到 Intent 的 App。电脑端微信 4.1.13 起也有类似入口。

所以「解压 + 读文字」这件事既真实、又不需要任何逆向或破解——只需要接住微信主动交出来的文件。

**顺手排除掉的错路**：`AccessibilityService` 读聊天界面。微信 8 的聊天界面
`uiautomator dump` 出来是 `<node text="" class="" package="com.tencent.mm" bounds="[0,0][0,0]" />`，
读不到任何文本节点。这条路不通，且违反平台条款，明确不做。

### 做了什么

**1. 解析：`data/ChatTranscriptParser.kt`**

微信导出的 txt 是固定三行结构，消息之间靠行首的 `·`（U+00B7）切分：

```
·发送人昵称
2026年11月7日 09:12
正文（可以多行）
```

- `parseZip(bytes, name)`：`java.util.zip` 遍历，累计附件数，在所有 `.txt` 里优先取名字就是
  `聊天记录.txt` 的那份，没有就取消息最多的一份
- `parseText(raw)`：去掉 BOM、`\r\n` 归一、按正则 `^·(.+)\n(\d{4}年\d{1,2}月\d{1,2}日 \d{2}:\d{2})\n`
  切分。第一条必须从位置 0 开始（微信的格式如此，开头有别的字说明这不是聊天记录）
- 空值占位、纯附件消息（`[图片]`）、多批次合并的 `【第 1 批】` 这类元信息行都单独处理

**2. 抽取：`data/ChatTripExtractor.kt`**

纯规则，不联网、不用 LLM，认出四类：

| 类型 | 判据 | 落地成 |
|------|------|-------|
| `PLACE` | 动词（去/逛/吃/住…）+ 地名后缀（寺/湖/街/店…） | 地点（带日期、时间段、人均） |
| `DECISION` | 决策词（还是/要不/你们觉得…）+ 能拆出 ≥2 个选项 | 带选项的投票 |
| `TODO` | 待办词（记得/别忘了/要带/负责/订票…） | 清单项 |
| `BUDGET` | 明确出现「预算」+ 金额 ≥50 | 行程预算 |

调优过程中被真实样例打回来 7 次，每次的教训都留在代码注释里：

- 目的地会带语气词（「杭州吧」）→ 剥尾字
- 起止日期识不出来 → **优先用消息自带的时间戳**，比正则可靠得多
- 「人均 150」被当成全程预算 → 先找明确含「预算」的那句，阈值更高
- 「订票」被当成地点 → 动作前缀表拦掉
- 「住民宿还是住酒店」既当地点又当决策 → 决策/待办/疑问句一律不算地点
- 待办标题带「谁去」→ 前缀剥离
- **目的地本身也被当成地点**（走查时才发现的）→ 和 `destination` 同名的跳过

**3. 预览页：`ui/ChatImportScreen.kt` + `ui/ChatImportViewModel.kt`**

「只建议不自动写」是硬原则，所以中间必须有一页让人逐条过一遍：
Checkbox + 类型标签 + 标题 + 元信息 + **原话引用**（能看见这句是从哪条消息来的）+「改」「不要」。

- 按天分组（`11月7日 周六` / `未定日期`），用户一眼能对上自己的行程结构
- 底部选一趟行程再点「导入 N 条」
- 导入串行执行，每条之前更新 `Importing(i, n)`，进度可见
- 结束时显示 `已写入 X 条` / `Y 条行程里已经有了，没重复写` / `Z 条没写成功`

**4. 判重**

走查时发现同一份聊天导两次会把内容翻倍。加了 `Existing` 快照：导入前拉一遍
places/checklist/decisions，比较归一化后的名字（去空格），命中就跳过并计数。
这不是「幂等」的严格实现（改名后会重导），但对「手滑点两次」这个真实场景够了。

**5. 接线**

- `AndroidManifest.xml`：新增 `ACTION_SEND` + `application/zip` / `x-zip-compressed` / `octet-stream`
- `MainActivity`：`parseChatZip(intent)` 接 `EXTRA_STREAM`（分享）和 `data`（打开方式），
  **校验 PK 头**——微信会把普通附件也塞进来，不校验就会把一张图片当聊天记录解析
- `PlanTogetherApp`：新增 `chatImport` 路由；从微信直接转发进来时自动跳到这一页并开始解析
- `TripListScreen`：顶栏加「聊天导入」入口（按钮从 3 个变 4 个）

### 验证

**单元测试：新建 `android/app/src/test/kotlin/.../ChatTranscriptParserTest.kt`，18 例全绿。**

这是项目里第一份真正的自动化测试（之前只有 `api-smoke.ps1` 打后端 + 人工截图）。
选它最先写，是因为解析器和抽取器是纯文本处理、没有 UI 没有网络，最容易悄悄写坏。
覆盖：三行格式、多行正文、CRLF+BOM、无效文本拒绝、zip 内取对文件、附件跳过、
目的地/日期/预算识别、地点识别、动作与疑问句不当地点、时间段、决策选项、
待办、原话引用、空输入不崩、按天分组、**目的地不当地点**。

**真机走查（Medium_Phone_API_37.0）**：

- `x2-import-empty.png`：导入页初始态——操作说明（「长按一条消息 → 多选 → 转发到其他应用」）、
  「或者直接粘贴聊天文字」、四条「能认出什么」、「不确认就不会写进去」
- `x4-results.png`：选 zip 后 10 条消息 / 3 人 / 2 个附件（已跳过），
  认出 4 个地点 1 个待定 2 个待办，按 D1/D2 分组，每条带原话
- `x8-trip-picker.png`：底部选行程下拉，列出可写（OWNER/EDITOR）的行程
- `x9-finished.png`：导入到空白行程后——目的地/日期自动补上（`杭州 · 11月7日 – 11月8日`），
  D1 两个地点、D2 一个，**没有多出「杭州」地点卡**
- 重复导入同一份：数据没翻倍（3 地点 / 2 待办 / 1 待定 保持不变）

**编辑过程中修掉的 UI 问题**（都是走查才发现、单测抓不到的）：

- 说明文字用了 `Alignment.CenterHorizontally` 的父容器，四行长短不一，显示成阶梯状 → 改左对齐
- 待定条目的元信息行把选项又列了一遍，和标题重复 → 决策类型不再重复
- 预算条目的元信息行也有 `¥500` 和标题重复 → 同上

### 依赖变化

**无新增第三方依赖。** `java.util.zip` 是 JDK 自带；测试用 `junit:junit:4.13.2`
（`testImplementation`，已在本地 gradle 缓存里，不吃网络）。

### 遗留

- **规则引擎的边界**：词表是封闭的，写得含糊的群聊会漏。P2 可以接本地小模型兜底
- 附件（图片/视频）只跳过、不识别；图片里的店名要 OCR 才能用，暂不做
- 导入后不能生成「行程摘要」发回群里——这是自然的增长闭环，还没做
- 微信单次最多导出 100 条，长聊天要分批

## 2026-11-14 · 第九轮：把界面重做一遍

### 起因

第二轮功能齐了以后，界面一直是「能看」但不是「好看」：`Theme.kt` 里只有三个颜色，
全 App 12 处 `RoundedCornerShape` 用了 8dp / 10dp / 12dp / 16dp 四种不统一的圆角，
没有任何字号规范，一屏里主按钮、次按钮、导航、卡片标题全在抢注意力。
拿墨刀上的旅游类模板（`modao.cc` 的旅游 App 模板合集）对了一遍，差距很具体：

- 模板都是**大圆角 + 柔和着色底**（浅蓝/淡紫/暖白），不是纯白屏
- 一张卡片只有一个视觉落点，卡片顶部有图形区（我们没有图片资源，改用色块 + 目的地首字）
- 价格/关键数字用**唯一一个暖色**强调，其余全部压低
- 日期、状态、人数一律做成**胶囊徽章**
- 标题粗且大，留白足

墨刀是原型工具，导出的设计稿不能直接生成 Compose 代码，所以这轮的做法是
**把模板的视觉语言提炼成设计系统，在 Compose 里重建**，而不是照搬某一张图。

### 做了什么

**1. 设计系统重建**（`ui/theme/Theme.kt` 完全重写）

- 配色：`primary = #0F6E6E`（深海水青）、`secondary = #D2694C`（珊瑚，**只给金额**）、
  `background = #FFFBF3`（暖米色纸底，替代纯白）、`surfaceVariant = #F2EDE2`、
  `outlineVariant = #E5DED0`，深色主题配一套对应的
- `Shapes`：`extraSmall 8 / small 12 / medium 16 / large 22 / extraLarge 28`，取代散落的 `RoundedCornerShape`
- `Typography`：标题全 Bold/SemiBold + 负 letterSpacing，正文行高放宽（bodyLarge 24sp、bodyMedium 21sp）
- `PriceTextStyle`：金额统一珊瑚色 + 加粗

**2. 新建共用组件库 `ui/UIKit.kt`**

`AppCard`（白底 + 1dp 极细描边 + `shapes.large`，比 Material `Card` 轻）、
`Pill`（胶囊徽章）、`PriceTag`（金额）、`SectionLabel`（左侧 3dp 色条的小标题）、
`EmptyState`（空态统一版式）、`SoftDivider`、`DestinationBadge`（目的地首字色块，替代封面图）、
`NumberBadge`、`MetaLine`、`ScreenHeader`。

**3. 逐屏套用**

| 屏 | 关键改动 |
|---|---|
| 行程列表 | 顶栏「我的行程」+ 副标题 + 单个「更多」菜单（原来四个按钮平铺）；「＋ 新建行程」做成整宽主按钮；行程卡改色块 + 标题 + 日期 + 两个胶囊 |
| 行程详情头 | `DestinationBadge` + 日期 + 人数/角色胶囊 + 邀请码块；Tab 改下划线式、去掉分割线 |
| 行程页 | 天标题带小计胶囊；地点卡改 `AppCard`（圆形序号 + 名称 + 时间段胶囊 + 金额靠右 + 次要动作行 + 编辑删除） |
| 地图 | 示意图连线与标记改用主题色（原来硬编码 `#1E88E5`），列表项加序号圆徽章 |
| 链接 | 卡片化 + `SoftDivider`，动作按钮紧凑化 |
| 讨论 | 消息卡带头像色块、「关于某地点」变青色胶囊 |
| 投票 | 选项块色彩化（已投=青、定案=珊瑚）+ 圆头进度条 |
| 清单 | 进度头卡片化（`已完成 1/3` + 百分比胶囊 + 圆头进度条），条目改卡片 |
| 预算 | 金额 `PriceTag` + 人数胶囊 + 圆头进度条；「按天拆分」「费用明细」用 `SectionLabel` |
| 登录 | logo 色块 + 大标题 + 表单装进卡片 + 整宽按钮；服务器地址入口保留在底部 |
| 新建行程 | 六个输入框收进一张卡 + 大标题副标题 + 整宽按钮 |
| 聊天导入 | 选文件卡加青色描边 + 「点这里选文件」，结果页每条改卡片、「改/不要」改竖排紧凑按钮 |

**4. 顺手修掉的旧问题**

- `SchematicMap.kt` 硬编码 `Color(0xFF1E88E5)`（换主题就露馅）→ 改用 `MaterialTheme.colorScheme`
- `ItineraryTab.kt` 里 `fun EmptyHint(text: String) {    Box(` 缺换行，一行挤在一起
- `README.md` 目录树里 `FormInputs.kt` / `ServerSettingsDialog.kt` 各重复出现两次

### 验证

- `compileDebugKotlin` / `assembleDebug` 全部 BUILD SUCCESSFUL
- 模拟器（Medium_Phone_API_37.0）逐屏走查，12 张截图：
  `y1-login` `y2-detail` `y3-map` `y4-discussion` `y5-decisions` `y6-checklist`
  `y7-budget` `y8-links` `y9-create` `y10-menu` `y11-chatimport` `y12-results`
- 全 App 已无裸 `Card(`；裸 `RoundedCornerShape` 只剩二维码白底、logo 这类特例
- 后端与接口零改动，`scripts/api-smoke.ps1` 88 项仍全通过；18 个单测仍全绿

### 编译中踩的坑

- `LinksTab.kt` 用了 `Alignment` 忘记 import；`ChatImportScreen.kt` 用了 `PaddingValues` 忘记 import
- 批量替换时 `shape = MaterialTheme.shapes.medium,` 被重复插入两遍，
  报 `@Composable invocations can only happen from the context of a @Composable function`
  —— 批量改之前要确认 old_string 唯一

### 依赖变化

**无新增依赖。** 刻意没有引入图标库：现在界面上的箭头、加号都是文字符号（`‹`、`＋`、`↑↓`）
配色块，观感够用且 APK 不涨。真要更好看，接 `material-icons-extended` 或自绘 SVG 即可。

### 遗留

- **没有真图标**：`‹ 返回`、`＋ 添加` 这类还是文字符号，是这轮最明显的妥协
- **没有插画**：空态是文字符号 + 色块，不是插画
- **没有动效**：页面切换、卡片入场都是默认的，没有过渡动画
- **深色主题没走查**：配色定义了，但只在浅色下截图验证过
- 行程页仍是普通 `Column`（为了支持长按拖拽排序），行程很长时没有虚拟化
