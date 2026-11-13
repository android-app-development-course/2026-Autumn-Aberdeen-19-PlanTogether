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
