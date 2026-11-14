# PlanTogether 团队旅行规划 App

把团队旅行里散落在各处的链接、地点、想法，汇聚成一个大家一起看、一起决定、一起用的行程计划。

> 当前状态：MVP 闭环已跑通 —— 建行程 → 发邀请码拉人 → 一起加地点/链接 → 讨论 → 投票定案 → 行前清单 → 预算 → 导出。

## 一、目录结构

```
D:\PlanTogether\
├── README.md                  ← 本文件（查询入口）
├── docs\
│   ├── PlanTogether_Business_Plan.md   ← 商业计划书（原始需求）
│   └── Change_Log.md                   ← 迭代记录（每次改了什么）
├── scripts\
│   ├── api-smoke.ps1          ← 后端接口冒烟测试（88 项断言）
│   ├── seed-demo.ps1          ← 生成一份可用的演示数据
│   └── package-for-team.ps1   ← 打包成「源码 zip + APK」发给组员
├── dist\                      ← 打包产物（源码 zip / APK），不参与开发
├── backend\                   ← 后端（Spring Boot + Kotlin）
│   ├── build.gradle.kts
│   └── src\main\kotlin\com\plantogether\
│       ├── config\SecurityConfig.kt            ← 安全配置
│       ├── common\                             ← 统一异常处理（400/401/403/404）
│       ├── security\                           ← JWT 认证
│       ├── user\                               ← 注册 / 登录 / me
│       └── trip\                               ← 行程域（下面单独展开）
│           ├── TripAccessor.kt                 ← 统一权限入口（读/写/仅创建者）
│           ├── TripService.kt                  ← 行程 + 地点 + 成员
│           ├── LinkService.kt                  ← 链接收藏箱
│           ├── CommentService.kt               ← 讨论区
│           ├── ChecklistService.kt             ← 行前清单
│           ├── DecisionService.kt              ← 群决策投票
│           ├── ActivityLogger.kt               ← 轻量编辑历史（同时触发实时推送）
│           ├── TripEventBroker.kt              ← SSE 连接管理 + 心跳
│           ├── TripEvents.kt                   ← 事务提交后再推，避免读到旧数据
│           ├── TripExportService.kt            ← 导出 Markdown
│           ├── TripCleanup.kt                  ← 删除行程时按外键顺序清理
│           └── *Controller / *Dtos / *Repository
├── android\                   ← Android App（Kotlin + Compose）
│   └── app\src\main\
│       ├── AndroidManifest.xml                 ← 权限、分享接收、邀请深链
│       ├── kotlin\com\plantogether\app\
│       │   ├── MainActivity.kt                 ← 入口：分享 + 深链（运行中亦可）
│       │   ├── data\                           ← ApiModels / ApiService / ApiClient / TokenStore
│       │   │   ├── TripRealtimeClient.kt       ← SSE 客户端（自动重连）
│       │   │   ├── ReadStateStore.kt           ← 每趟行程「读到哪」的本地记录（未读红点）
│       │   │   ├── SettingsStore.kt            ← 服务器地址等本机设置（DataStore）
│       │   │   ├── ChatTranscriptParser.kt     ← 解压微信导出的 zip，读出聊天记录
│       │   │   └── ChatTripExtractor.kt        ← 从聊天文字里认出地点/待定/待办/预算
│       │   └── ui\                             ← 界面层
│       │       ├── PlanTogetherApp.kt          ← 路由 + 启动态恢复 + 分享/深链处理
│       │       ├── MainViewModel.kt            ← 登录态 / 行程列表 / 加入行程 / 未读 / 服务器地址
│       │       ├── LoginScreen.kt              ← 登录注册 + 服务器设置入口
│       │       ├── ServerSettingsDialog.kt     ← 改后端地址并试连
│       │       ├── FormInputs.kt               ← 日期/时间选择器、输入校验、数字键盘
│       │       ├── TripListScreen.kt           ← 行程列表（下拉刷新 + 未读圆点）
│       │       ├── CreateTripScreen.kt         ← 新建行程
│       │       ├── TripDetailScreen.kt         ← 详情骨架（7 个页签 + 成员面板 + 未读点）
│       │       ├── TripDetailViewModel.kt      ← 单趟行程的读写 + SSE + 兜底轮询
│       │       ├── ItineraryTab.kt             ← 按天行程 + 地点编辑器
│       │       ├── ReorderablePlaceList.kt     ← 长按拖动排序
│       │       ├── MapTab.kt / SchematicMap.kt ← 示意图地图
│       │       ├── LinksTab.kt                 ← 链接收藏箱
│       │       ├── DiscussionTab.kt            ← 讨论
│       │       ├── DecisionsTab.kt             ← 投票决策
│       │       ├── ChecklistTab.kt             ← 清单
│       │       ├── BudgetTab.kt                ← 预算
│       │       ├── QrCode.kt / InviteQrDialog.kt ← 邀请二维码（ZXing）
│       │       ├── Formatting.kt               ← 金额/日期/分组格式化
│       │       ├── ExternalLookup.kt           ← 「看点评」跳点评/美团/高德/小红书
│       │       ├── PoiPicker.kt                ← 搜店名自动补全（高德 POI）
│       │       ├── ChatImportScreen.kt         ← 聊天记录导入预览页（逐条勾选）
│       │       ├── ChatImportViewModel.kt      ← 导入流程：解析 → 勾选 → 写入行程
│       │       ├── FormInputs.kt               ← 日期/时间选择器、输入校验
│       │       ├── ServerSettingsDialog.kt     ← 服务器地址设置
│       │       ├── SaveLinkDialog.kt           ← 分享进来的链接选行程保存
│       │       └── theme\Theme.kt
│       └── res\                                ← 图标/字符串/主题资源
│   └── src\test\kotlin\...\ChatTranscriptParserTest.kt  ← 聊天解析/抽取的单元测试（18 例）
└── .tools\                    ← 开发工具（Gradle、AEHD 驱动、截图）
```

## 二、如何运行

### 环境（已配置好）
- Android Studio：`D:\Android`（自带 JDK）
- Android SDK：`C:\Users\alex\AppData\Local\Android\Sdk`
- 本地 Gradle：`D:\PlanTogether\.tools\gradle-9.6.0`
- 依赖走阿里云镜像（Maven Central / Google 直连被墙）

### 启动步骤
1. **启动后端**（二选一）：
   - 用 IntelliJ IDEA / Android Studio 打开 `backend\`，运行 `PlanTogetherApplication`
   - 或命令行：
     ```bash
     JAVA_HOME="D:\\Android\\jbr" /d/PlanTogether/.tools/gradle-9.6.0/bin/gradle.bat -p D:/PlanTogether/backend bootRun
     ```
     > 项目已带 Gradle Wrapper：在 `backend\` 里执行 `gradlew.bat bootRun` 也可以（首次会自动下载 Gradle 9.6，走腾讯镜像）。
2. **启动模拟器**（AVD `Medium_Phone_API_37.0`）
3. **转发端口**（用模拟器 + 默认地址时才需要）：
   ```bash
   adb reverse tcp:8080 tcp:8080
   ```
4. **运行 App**：Android Studio 打开 `android\` 目录，点 Run

> ℹ️ 默认地址是 `http://127.0.0.1:8080/`，配合 `adb reverse` 用。
> 换成真机、或后端跑在另一台电脑上时，**不用改代码**：登录页底部点「服务器设置」，
> 填电脑的局域网地址（如 `http://192.168.1.8:8080`），点「保存并测试」，
> 看到绿色「✓ 已连接」即可。地址会存本地，下次启动自动生效。

### 想要一份现成数据？

```powershell
# 后端已启动的前提下
powershell -ExecutionPolicy Bypass -File scripts\seed-demo.ps1
```

会创建两个账号和一趟带地点、链接、投票、清单、评论的行程：

| 账号 | 密码 | 角色 |
|------|------|------|
| `demo@plantogether.app` | `password123` | 创建者 |
| `mate@plantogether.app` | `password123` | 可编辑成员 |

### 想验证后端改动？

```powershell
powershell -ExecutionPolicy Bypass -File scripts\api-smoke.ps1
```

覆盖注册、邀请码加入、角色权限、协作编辑、评论、投票改票、定案、清单、动态、导出、级联删除、变更计数、乐观锁冲突、成员主动退出、错误提示是否说人话、高德 POI 代理与评分快照，共 88 项断言。

## 三、技术栈

| 端 | 技术 |
|----|------|
| 后端 | Spring Boot 3.5.16 · Kotlin 2.4.21 · Spring Security + JWT · JPA + H2（PostgreSQL 兼容模式）· SSE（SseEmitter） |
| App | Kotlin · Jetpack Compose · AGP 9.4.1（内置 Kotlin 2.2.10）· Retrofit + Gson · Navigation · DataStore · OkHttp SSE · ZXing |
| 数据库 | H2 文件库（`backend/data/`，上线前切 PostgreSQL） |

## 四、已实现功能

### 账号
- ✅ 注册 / 登录（JWT）
- ✅ **token 本地持久化**：重启 App 不用重新登录（DataStore，只存 token 和昵称，不存密码）
- ✅ 启动时用 `/api/auth/me` 校验登录态；401 自动清 token 回登录页，断网时不会莫名被登出

### 行程与协作（核心）
- ✅ 行程增删改查；删除时按外键顺序级联清理地点/链接/评论/清单/决策/动态
- ✅ **邀请码加入**：创建者拿到 8 位邀请码，队友在「加入行程」里输入即可加入（大小写不敏感、重复加入幂等）
- ✅ **三种角色**：`OWNER` 创建者 / `EDITOR` 可编辑 / `VIEWER` 只读；后端每个接口都校验，App 按权限隐藏按钮
- ✅ **成员管理**：查看成员列表与加入时间、改角色、移除成员（仅创建者）
- ✅ **成员主动退出**：成员可自己退出行程；创建者不允许退出（需删除行程）
- ✅ **邀请码轮换**：旧码立即失效，用于收回没用掉的邀请
- ✅ **自动同步（SSE 实时推送）**：页面打开时建立长连接，任何人改动都会在 1–2 秒内出现在你屏幕上；
  断开时自动重连（最多 5 次），并保留 30 秒兜底轮询
- ✅ **未读红点**：行程列表上有「有新动态」圆点，页签上标出哪一页有新内容；离开详情页即记为已读
- ✅ **并发保护**：行程/地点/链接/清单/决策带版本号，用旧版本提交返回 409 并自动刷新提示；投票撞车自动重试一次
- ✅ **长按拖动排序**：行程页长按地点即可拖动调整当天顺序，松手保存（也保留 ↑↓ 按钮）
- ✅ **我的行程** = 自己创建的 + 被邀请加入的，卡片上标注「队友的行程」和我的角色
- ✅ 邀请码复制 / 分享到微信等（系统分享面板）
- ✅ **邀请深链 + 二维码**：`plantogether://join/CODE`，扫二维码或点链接可直接打开 App 并自动加入

### 收集与整理
- ✅ 地点增删改：名称、地址、经纬度、日期、起止时间、预算、停留时长、备注、备选方案、地图链接
- ✅ **按天分组**的行程视图（D1/D2…，每天显示预算小计），未定日期的地点单独成组
- ✅ 手动排序（↑↓），顺序就是地图上的连线顺序
- ✅ 地点卡片显示「由谁添加」，方便追责/感谢
- ✅ 链接收藏箱：URL + 自己的标题/来源/标签/说明，可编辑可删除，显示提交人
- ✅ 地点卡片一键「看点评」：先看已有信息（评分/人均/营业时间），再选大众点评 / 美团 / 高德 /
  小红书打开该店搜索页（装了 App 就进 App，没装走浏览器）；也可以只复制店名。
  只做跳转，不抓取对方的评价正文
- ✅ **搜店名自动补全**：添加地点时输入店名，从高德带出地址、经纬度、评分、人均、营业时间，
  只覆盖能确认的字段，你写好的备注/预算/时间不会被冲掉
- ✅ **系统分享面板接入（冷启动和 App 已运行时都支持）**：从点评/B站/地图分享链接进来，选行程、补标题说明后保存

### 一起决定
- ✅ **讨论区**：全组发言，可挂到某个地点下（「关于楼外楼」），作者或创建者可删
- ✅ **群决策投票**：发起问题 + 多个候选选项，每人一票、点一下投票、再点取消、改票自动覆盖
- ✅ **定案**：选定某个选项标为「已定案」，可重新开放或取消决策；发起人或创建者可删
- ✅ **行前清单**：勾选完成、负责人、进度条、编辑、删除
- ✅ **预算页**：已计划支出、人均、按天拆分、费用明细、预算目标与超支提示
- ✅ **动态（轻量编辑历史）**：谁在什么时候加了地点/发了言/投了票/改了角色，最近 50 条

### 收尾
- ✅ **导出 Markdown**：整趟行程导出为可读文本（行程/链接/决策/清单/预算/成员/讨论），可复制或直接分享
- ✅ 示意图画布地图：按坐标相对位置画点、按顺序连线、编号图例，点击跳外部地图 App

### 使用体验（第六轮补齐）
- ✅ **服务器地址可配置**：登录页底部「服务器设置」可改后端地址，保存前自动试连并给出中文提示
  （格式不对 / 找不到主机 / 连不上 / 超时分开报）；地址存本地，下次启动自动生效。
  这样同一个 APK 装到别人手机上，填一次局域网 IP 就能用
- ✅ **日期 / 时间用选择器**：日历和 24 小时表盘代替手输 `yyyy-MM-dd`，选错格式不可能再发生
- ✅ **输入立刻报错**：预计人数、预算、经纬度、停留时长填错时输入框变红并提示（如「请填整数」），
  不会静默把值丢掉；数字栏位自动弹数字键盘
- ✅ **下拉刷新**：行程列表、行程详情页的每个页签都支持下拉刷新（下拉失败不清空已有内容）
- ✅ **后端错误说中文**：日期/数字格式错、缺参数、服务器内部异常都有可读提示，不再返回英文技术细节或堆栈

## 四之一、从微信群聊记录生成行程

微信 8.0.78 之后，**多选消息 → 转发到其他应用 → 选择 PlanTogether**，微信会自己把这段聊天
打成一个 zip（里面有 `聊天记录.txt` 和附件）交给 App。App 在本机解开、读出文字，
认出「地点 / 待定 / 待办 / 预算」四类东西，**逐条给你确认后**才写进行程。

```
行程列表 → 聊天导入 → 选 zip（或粘贴文字）→ 预览勾选 → 选一趟行程 → 导入 N 条
```

| 聊天里长这样 | 认成什么 |
|-------------|---------|
| 「想去西湖断桥」「中午吃楼外楼，人均 150」 | 地点（带日期/时间/预算） |
| 「住民宿还是住酒店，你们觉得呢」 | 待定（生成一个带选项的投票） |
| 「记得带充电宝」「谁去订票？」 | 待办（清单项） |
| 「预算 人均 500」 | 行程预算 |

几条设计上的硬规矩：

- **只建议，不自动写。** 预览页逐条可勾选、可就地改名、可整条丢掉；没勾的不会进数据库
- **聊天不出本机。** 解析全在手机上做，原文不上传；只有你勾选后的结构化条目才发给后端
- **不覆盖已有内容。** 目标行程的目的地/日期/预算缺了才补，已有的绝不改
- **重复导入不会翻倍。** 第二次导同一份聊天，已在行程里的条目会被跳过并告诉你跳了几条
- **目的地不当地点。** 「去杭州」填进行程的目的地，不会多出一张叫「杭州」的地点卡

解析器不用 LLM、不联网、零新依赖（`java.util.zip` + 正则）。
规则在 `ChatTripExtractor.kt` 里，`ChatTranscriptParserTest.kt` 用 18 个用例钉住了这些行为。

> 注：微信**没有**开放「读取聊天记录」的 API，也**不要**去写读屏（AccessibilityService）——
> 微信 8 的聊天界面读不到任何文本节点，那条路实测走不通，也违反平台条款。
> 能做的只有接住微信主动交出来的那份 zip。

## 四之二、餐饮点评数据怎么接的

结论先说：**结构化评分只从高德拿，大众点评和美团只能跳转。** 这不是偷懒，是三家开放程度不同：

| 平台 | 有 C 端搜索 API 吗 | 能给什么 | 我们怎么用 |
|------|------------------|---------|-----------|
| 高德地图 | ✅ 有（v5 POI 搜索，`show_fields=business`） | 评分、人均、营业时间、特色菜、电话、商圈 | 后端代理调用，落地到地点卡片 |
| 大众点评 | ❌ 没有 | 真实食客评价正文 | 跳过去看 |
| 美团 | ❌ 没有 | 团购/优惠 | 跳过去看 |

美团和大众点评的开放平台是给商家和服务商做团购核销的，没有公开的「按店名搜评分」接口；
它们的网页有登录墙和反爬，抓取既违规也不稳。所以：

- **想知道值不值得去** → 看高德那份数据（App 里已经显示在地点卡片上）
- **想看别人吃过怎么说** → 点「看点评」跳到点评/美团，那是它们的主场

高德 Key 放在**后端**，不放进 APK：

```
# PowerShell 里给后端传 Key
$env:PLANTOGETHER_AMAP_KEY="你的Key"; gradle bootRun
```

Key 申请：https://lbs.amap.com/api/webservice/create-project-and-key （选「Web服务」类型，个人可申请，有免费额度）。
**不配 Key 也能跑**——搜索框会显示「这个服务器还没接地图服务，先手动填吧」，其他功能不受影响。

为什么不在 App 里直接调高德：APK 是可以被 unzip 的，Key 打进去等于送人；放后端还能统一做缓存
（同一个城市 + 关键词 5 分钟内复用，省配额）。

## 五、权限模型

| 操作 | OWNER | EDITOR | VIEWER |
|------|:-----:|:------:|:------:|
| 查看行程及全部内容 | ✅ | ✅ | ✅ |
| 增删改地点 / 链接 | ✅ | ✅ | ❌ |
| 发表评论、投票、改清单 | ✅ | ✅ | ❌ |
| 导出 / 查看动态 | ✅ | ✅ | ✅ |
| 主动退出行程 | ❌（需删除行程） | ✅ | ✅ |
| 修改行程基本信息、轮换邀请码、管理成员、删除行程 | ✅ | ❌ | ❌ |

实现上所有子资源都走 `TripAccessor`（`read` / `write` / `ownerOnly`），不会出现某个接口忘记鉴权的情况。

## 六、后端接口一览

```
# 账号
POST   /api/auth/register
POST   /api/auth/login
GET    /api/auth/me

# 行程
GET    /api/trips
POST   /api/trips
POST   /api/trips/join                     { inviteCode }
GET    /api/trips/{id}
PUT    /api/trips/{id}                     (仅 OWNER)
DELETE /api/trips/{id}                     (仅 OWNER，级联清理)
POST   /api/trips/{id}/invite-code/rotate  (仅 OWNER)

# 成员 / 动态
GET    /api/trips/{id}/members
PUT    /api/trips/{id}/members/{memberId}  { role: EDITOR|VIEWER }
DELETE /api/trips/{id}/members/{memberId}  (仅 OWNER)
DELETE /api/trips/{id}/members/me          (成员主动退出)
GET    /api/trips/{id}/activity
GET    /api/trips/{id}/revision            → { revision }  轻量变更计数
GET    /api/trips/{id}/stream              → text/event-stream（SSE，推送 revision）

# 地点
GET    /api/trips/{id}/places
POST   /api/trips/{id}/places
PUT    /api/trips/{id}/places/{placeId}
DELETE /api/trips/{id}/places/{placeId}
PUT    /api/trips/{id}/places/reorder      [placeId, ...]

# 链接
GET    /api/trips/{id}/links
POST   /api/trips/{id}/links
PUT    /api/trips/{id}/links/{linkId}
DELETE /api/trips/{id}/links/{linkId}

# 讨论
GET    /api/trips/{id}/comments
POST   /api/trips/{id}/comments            { body, placeId? }
DELETE /api/trips/{id}/comments/{commentId}

# 清单
GET    /api/trips/{id}/checklist
POST   /api/trips/{id}/checklist
PUT    /api/trips/{id}/checklist/{itemId}
DELETE /api/trips/{id}/checklist/{itemId}

# 决策投票
GET    /api/trips/{id}/decisions
POST   /api/trips/{id}/decisions           { title, notes?, options: [...] }
PUT    /api/trips/{id}/decisions/{did}/status   { status: OPEN|DECIDED|CANCELLED, decidedOptionId? }
DELETE /api/trips/{id}/decisions/{did}
POST   /api/trips/{id}/decisions/{did}/options  { title }
DELETE /api/trips/{id}/decisions/{did}/options/{oid}
POST   /api/trips/{id}/decisions/{did}/options/{oid}/vote

# 导出
GET    /api/trips/{id}/export              → text/markdown

# 地图 / POI（代理高德，Key 不出后端）
GET    /api/poi/search?q=西湖&city=杭州&limit=10
       → { configured, items: [{ name, address, latitude, longitude, type,
            rating, avgCost, tag, openTimeToday, tel, businessArea }], message? }

# 健康检查（无需登录，用于「服务器设置」试连）
GET    /api/health                         → { status: "ok", service: "..." }
```

错误约定：`400` 参数/业务校验失败，`401` 未登录或 token 失效，`403` 无权限或不是成员，`404` 资源不存在，`409` 版本冲突（内容已被别人改过），`500` 服务端异常（只返回「服务器出了点问题，请稍后重试」，不暴露堆栈）。响应体统一为 `{"error": "中文提示"}`，App 直接展示。

写接口（`PUT`）可以带 `version` 字段：服务端发现版本不一致就返回 409，避免两个人同时编辑时后保存的静默覆盖。

## 七、日后加功能 / 改 UI

| 想改什么 | 文件 |
|---------|------|
| 主题色/配色 | `android\...\ui\theme\Theme.kt` |
| 某个页签的布局 | `android\...\ui\<Xxx>Tab.kt` |
| 行程详情的外壳/顶部菜单 | `android\...\ui\TripDetailScreen.kt` |
| 某个功能的读写逻辑 | `android\...\ui\TripDetailViewModel.kt` |
| 网络接口 | `android\...\data\ApiService.kt` + `ApiModels.kt` |
| 登录/行程列表 | `android\...\ui\LoginScreen.kt`、`TripListScreen.kt`、`MainViewModel.kt` |
| 后端某个域的接口 | `backend\...\trip\` 或 `user\` 下的 Controller/Service/Dtos/Repository |
| 权限规则 | `backend\...\trip\TripAccessor.kt` |
| 导出的 Markdown 结构 | `backend\...\trip\TripExportService.kt` |
| App 图标 | `android\...\res\drawable\ic_launcher_foreground.xml` |

## 八、已知限制

- 排序用「长按拖动」或 ↑↓ 按钮，但**只支持同一天内**调整（跨天顺序由日期决定）
- 地图是**示意图**：只画手填经纬度的地点，不做真实路线、距离、用时或导航
- 分享进来只取文本里的第一个 http(s) 链接；分享纯文本时会整段当作链接（可手动改）
- 不抓取任何第三方页面内容，标题/说明需用户自己写（这是产品边界，不是缺陷）
- **聊天记录导入是规则匹配，不是理解**：认出的是封闭词表里的地点/待办/决策，写得越含糊越容易漏。
  一次最多 60 条建议；跨多天、长句、方言俚语会漏
- 导入只处理**文字**。聊天里的图片/视频附件会被跳过（App 里会告诉你跳了几个）
- 微信导出一次最多 100 条消息，更长的一段聊天要分批导出、分几次导入
- 实时性靠 **SSE 长连接**：App 在后台/锁屏时连接会断开，重连后补拉一次；没有系统级推送通知
- 未读红点是「详情页的红点 + 列表圆点」，不区分具体谁改的，也没有角标数字
- 并发保护是"检测冲突 + 提示刷新"，不是自动合并；两人改同一字段时后提交的一方需要重新编辑
- 无图片/封面、无模板、无离线缓存、无分页
- 后端仍是 H2 文件库 + 开发用 JWT 密钥（`application.yml`），上线前必须换 PostgreSQL + 强随机密钥，并补 Flyway 迁移

## 九、下一步可以做什么

1. **体验细节**：空状态插画、列表虚拟化（当前行程页用普通 Column 以支持拖拽）、更细的离线提示
1.5 **聊天导入更进一步**：把规则换成「规则打底 + 本地小模型兜底」，处理写得更随意的群聊；
   导入后生成一段行程摘要，一键发回微信群（顺带带邀请码，是自然的拉人入口）
2. **点评数据加深**：高德只给评分/人均，不给评价正文。可以做一个「点评/美团链接自动识别店名」的粘贴框，
   用户贴进链接后自动跳到该店搜索页，少一次手动输入
3. **通知**：接入系统推送（FCM/厂商通道），别人投票/评论时能在通知栏提醒；@提醒与已读回执
4. **协作效率**：成员各自的「我负责的清单」视图、按人筛选动态
5. **行程模板**：把一趟行程存成模板，下次一键复制
6. **分享加强**：把 `plantogether://` 升级成带域名的 App Links，没装 App 时能落到网页介绍页
7. **上线准备**：PostgreSQL + Flyway、密钥外置、接口限流、日志脱敏、隐私政策页
8. **数据分析**：按商业计划书的关键指标打点（激活行程、邀请接受率、协作完成率、90 天复用）

## 十、怎么把项目发给组员

先想清楚对方要做什么，再选发送方式。

### 方式一：让他们自己跑起来（要一起开发时用）

1. 发 `dist\PlanTogether-src.zip`（只含源码，约 400 KB，不含 build/.tools/本地数据）
2. 对方解压后：
   - 装 Android Studio（自带 JDK 和 SDK 管理）
   - `backend\` 用 Android Studio / IDEA 打开，运行 `PlanTogetherApplication`
   - `android\` 用 Android Studio 打开，等 Gradle 同步（首次会下载 Gradle 9.6，走腾讯镜像）
   - 起模拟器 → 执行 `adb reverse tcp:8080 tcp:8080` → 点 Run
3. 说明：
   - `local.properties`（SDK 路径）和 `backend/data/`（本地数据库）**故意没打包**，对方打开时自动生成
   - 界面里的东西都在 README「四、已实现功能」里，演示数据可以用 `scripts\seed-demo.ps1` 生成

### 方式二：只想让他们"看看效果"（最快）

- 发 `dist\app-debug.apk` + 一段录屏（或本文里的截图）
- ✅ **同一个 APK 就能连不同的后端**：打开 App → 登录页底部「服务器设置」→ 填你电脑的局域网地址
  （如 `http://192.168.1.5:8080`）→「保存并测试」→ 出现绿色「✓ 已连接」即可用。
  前提是手机和电脑在同一个 Wi-Fi、电脑防火墙放行 8080
- 只在电脑上用模拟器时，什么都不用改：执行 `adb reverse tcp:8080 tcp:8080` 后直接用默认的 `127.0.0.1`
- 微信/QQ 传 APK 可能被拦截，建议用网盘、数据线或企业微信

### 方式三：长期协作（推荐）

- 建一个 Gitee / 公司内网 Git 仓库，把源码推上去，组员 `git clone`
- 好处：能看到每次改动（`docs\Change_Log.md`）、可以提 issue、各自开分支

### 发送清单

| 文件 | 大小 | 用途 |
|------|------|------|
| `dist\PlanTogether-src.zip` | 约 400 KB | 源码，别人能跑能改 |
| `dist\app-debug.apk` | 约 20 MB | 直接装手机看界面（需要能连到后端） |
| `docs\Change_Log.md` | 几 KB | 说明每轮做了什么，方便对方快速了解 |
