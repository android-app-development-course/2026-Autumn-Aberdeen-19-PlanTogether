package com.plantogether.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.plantogether.app.data.ActivityResponse
import com.plantogether.app.data.MemberResponse
import com.plantogether.app.data.TripRequest
import com.plantogether.app.data.TripResponse

private val tripTabs = listOf("行程", "地图", "链接", "讨论", "投票", "清单", "预算")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    viewModel: MainViewModel,
    tripId: Long,
    onBack: () -> Unit,
) {
    val detailVm: TripDetailViewModel = viewModel(
        key = "trip-$tripId",
        factory = viewModelFactory { initializer { TripDetailViewModel(tripId) } },
    )

    val trip by detailVm.trip.collectAsStateWithLifecycle()
    val places by detailVm.places.collectAsStateWithLifecycle()
    val links by detailVm.links.collectAsStateWithLifecycle()
    val comments by detailVm.comments.collectAsStateWithLifecycle()
    val checklist by detailVm.checklist.collectAsStateWithLifecycle()
    val decisions by detailVm.decisions.collectAsStateWithLifecycle()
    val members by detailVm.members.collectAsStateWithLifecycle()
    val activity by detailVm.activity.collectAsStateWithLifecycle()
    val loading by detailVm.loading.collectAsStateWithLifecycle()
    val refreshing by detailVm.refreshing.collectAsStateWithLifecycle()
    val message by detailVm.message.collectAsStateWithLifecycle()

    val seenRevisions by viewModel.seenRevisions.collectAsStateWithLifecycle()
    val seenRevision = seenRevisions?.get(tripId) ?: 0L
    val unseen = remember(activity, seenRevision, seenRevisions) {
        if (seenRevisions == null) emptyList() else activity.filter { it.id > seenRevision }
    }
    val unseenTabs = remember(unseen) { unseen.mapNotNull { tabIndexForKind(it.kind) }.toSet() }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showMembers by remember { mutableStateOf(false) }
    var showEditTrip by remember { mutableStateOf(false) }
    var confirmDeleteTrip by remember { mutableStateOf(false) }
    var confirmLeaveTrip by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var exportText by remember { mutableStateOf<String?>(null) }

    // 页面可见时开 SSE + 兜底轮询，切到后台就停
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { detailVm.startAutoRefresh() }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { detailVm.stopAutoRefresh() }

    // 离开详情页时把「读到哪」记下来，列表上的红点随之消失
    DisposableEffect(tripId) {
        onDispose { viewModel.markTripSeen(tripId, detailVm.revision.value) }
    }

    // 第一次打开这趟行程：把当前进度当作基线，不要把历史动态都算成未读
    LaunchedEffect(trip?.id, seenRevisions) {
        val map = seenRevisions ?: return@LaunchedEffect
        val current = trip ?: return@LaunchedEffect
        if (!map.containsKey(tripId)) {
            viewModel.markTripSeen(tripId, current.revision)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            detailVm.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = {
                    Text(
                        trip?.title ?: "行程详情",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("‹ 返回", style = MaterialTheme.typography.bodyLarge)
                    }
                },
                actions = {
                    TextButton(onClick = { showMembers = true }) { Text("成员") }
                    Box {
                        TextButton(onClick = { menuOpen = true }) { Text("更多") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            if (trip?.isOwner == true) {
                                DropdownMenuItem(
                                    text = { Text("编辑行程信息") },
                                    onClick = {
                                        menuOpen = false
                                        showEditTrip = true
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("导出 Markdown") },
                                onClick = {
                                    menuOpen = false
                                    detailVm.exportMarkdown { exportText = it }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("刷新") },
                                onClick = {
                                    menuOpen = false
                                    detailVm.refreshAll()
                                },
                            )
                            if (trip?.isOwner == true) {
                                DropdownMenuItem(
                                    text = { Text("删除行程", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        menuOpen = false
                                        confirmDeleteTrip = true
                                    },
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("退出行程", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        menuOpen = false
                                        confirmLeaveTrip = true
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            TripHeader(
                trip = trip,
                newActivityCount = unseen.size,
                onOpenInvite = { showMembers = true },
                onShowActivity = { showMembers = true },
            )

            PrimaryScrollableTabRow(
                selectedTabIndex = tab,
                edgePadding = 12.dp,
                containerColor = MaterialTheme.colorScheme.background,
                divider = {},
            ) {
                tripTabs.forEachIndexed { index, label ->
                    Tab(
                        selected = tab == index,
                        onClick = { tab = index },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(label)
                                if (index in unseenTabs) {
                                    Spacer(Modifier.width(4.dp))
                                    Box(
                                        Modifier
                                            .size(6.dp)
                                            .background(MaterialTheme.colorScheme.error, CircleShape),
                                    )
                                }
                            }
                        },
                    )
                }
            }

            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { detailVm.pullRefresh() },
                modifier = Modifier.weight(1f),
            ) {
                if (loading && trip == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    when (tab) {
                        0 -> ItineraryTab(detailVm, trip, places)
                        1 -> MapTab(trip, places)
                        2 -> LinksTab(detailVm, trip, links)
                        3 -> DiscussionTab(detailVm, trip, comments)
                        4 -> DecisionsTab(detailVm, trip, decisions)
                        5 -> ChecklistTab(detailVm, trip, checklist)
                        else -> BudgetTab(detailVm, trip, places)
                    }
                }
            }
        }
    }

    if (showMembers) {
        MembersSheet(
            viewModel = detailVm,
            trip = trip,
            members = members,
            activity = activity,
            onDismiss = { showMembers = false },
            onCopy = { code -> copyToClipboard(context, "PlanTogether 邀请码", code) },
            onShare = { code -> shareInvite(context, trip, code) },
        )
    }

    if (showEditTrip) {
        TripEditDialog(
            trip = trip,
            onDismiss = { showEditTrip = false },
            onSave = { request ->
                detailVm.updateTrip(request) { showEditTrip = false }
            },
        )
    }

    exportText?.let { text ->
        MarkdownDialog(
            text = text,
            onDismiss = { exportText = null },
            onCopy = { copyToClipboard(context, "行程计划", text) },
            onShare = { shareText(context, text) },
        )
    }

    if (confirmDeleteTrip) {
        AlertDialog(
            onDismissRequest = { confirmDeleteTrip = false },
            title = { Text("删除行程") },
            text = {
                Text(
                    "将永久删除这个行程以及其中的地点、链接、讨论、投票和清单，且无法恢复。" +
                        "如果只是想停止协作，可以改用「移除成员」。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteTrip = false
                    detailVm.deleteTrip {
                        viewModel.loadTrips()
                        onBack()
                    }
                }) { Text("永久删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteTrip = false }) { Text("取消") }
            },
        )
    }

    if (confirmLeaveTrip) {
        AlertDialog(
            onDismissRequest = { confirmLeaveTrip = false },
            title = { Text("退出行程") },
            text = { Text("退出后就看不到这趟行程了，需要再拿邀请码加入。你的行程记录仍留在组长的计划里。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeaveTrip = false
                    detailVm.leaveTrip {
                        viewModel.loadTrips()
                        onBack()
                    }
                }) { Text("退出行程", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeaveTrip = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun TripHeader(
    trip: TripResponse?,
    newActivityCount: Int,
    onOpenInvite: () -> Unit,
    onShowActivity: () -> Unit,
) {
    // 顶部信息块：目的地色块 + 日期 + 人数，邀请码是其中唯一可点的胶囊
    AppCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                DestinationBadge(trip?.destination, size = 56)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        trip?.destination?.takeIf { it.isNotBlank() } ?: "还没定去哪",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        tripDateRange(trip),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val people = trip?.let {
                            buildString {
                                append("${it.memberCount} 人已加入")
                                it.groupSize?.let { size -> append("／预计 $size") }
                            }
                        }
                        if (!people.isNullOrBlank()) {
                            Pill(people, container = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        trip?.let { Pill(roleLabel(it.role)) }
                    }
                }
            }

            val code = trip?.inviteCode
            if (trip?.isOwner == true && !code.isNullOrBlank()) {
                Spacer(Modifier.height(14.dp))
                SoftDivider()
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "邀请码",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            code,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onOpenInvite) { Text("邀请队友") }
                }
            }

            if (newActivityCount > 0) {
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.clickable(onClick = onShowActivity),
                ) {
                    Text(
                        "有 $newActivityCount 条新动态 ›",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

// ---------------- 成员 / 动态 ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MembersSheet(
    viewModel: TripDetailViewModel,
    trip: TripResponse?,
    members: List<MemberResponse>,
    activity: List<ActivityResponse>,
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
) {
    LaunchedEffect(Unit) { viewModel.refreshMembersAndActivity() }
    var showQr by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Text("成员", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))

            val code = trip?.inviteCode
            if (trip?.isOwner == true && !code.isNullOrBlank()) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("邀请码 $code", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "队友扫码，或在「加入行程」里输入邀请码，都能加入。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row {
                            TextButton(onClick = { showQr = true }) { Text("二维码") }
                            TextButton(onClick = { onCopy(code) }) { Text("复制") }
                            TextButton(onClick = { onShare(code) }) { Text("分享") }
                            TextButton(onClick = { viewModel.rotateInviteCode() }) { Text("重置") }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (showQr) {
                    InviteQrDialog(
                        code = code,
                        tripTitle = trip?.title,
                        onDismiss = { showQr = false },
                        onCopy = { onCopy(inviteLink(code)) },
                        onShare = { onShare(code) },
                    )
                }
            }

            members.forEach { member ->
                MemberRow(
                    member = member,
                    canManage = member.canManage,
                    onSetRole = { role -> viewModel.setMemberRole(member.id, role) },
                    onRemove = { viewModel.removeMember(member.id) },
                )
                HorizontalDivider()
            }

            Spacer(Modifier.height(16.dp))
            Text("最近动态", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            if (activity.isEmpty()) {
                Text("还没有动态", style = MaterialTheme.typography.bodySmall)
            } else {
                activity.take(15).forEach { item ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(
                            item.userName ?: "系统",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(72.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            item.summary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            formatTimestamp(item.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun MemberRow(
    member: MemberResponse,
    canManage: Boolean,
    onSetRole: (String) -> Unit,
    onRemove: () -> Unit,
) {
    var confirmRemove by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(member.displayName, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${roleLabel(member.role)} · ${member.email}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (canManage) {
                TextButton(onClick = {
                    onSetRole(if (member.role == "VIEWER") "EDITOR" else "VIEWER")
                }) {
                    Text(if (member.role == "VIEWER") "设为可编辑" else "设为只读")
                }
                TextButton(onClick = { confirmRemove = true }) {
                    Text("移除", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        if (confirmRemove) {
            AlertDialog(
                onDismissRequest = { confirmRemove = false },
                title = { Text("移除成员") },
                text = { Text("确定把 ${member.displayName} 移出这个行程吗？TA 将无法再查看这个行程。") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmRemove = false
                        onRemove()
                    }) { Text("移除") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmRemove = false }) { Text("取消") }
                },
            )
        }
    }
}

// ---------------- 编辑行程 ----------------

@Composable
private fun TripEditDialog(
    trip: TripResponse?,
    onDismiss: () -> Unit,
    onSave: (TripRequest) -> Unit,
) {
    var title by remember(trip) { mutableStateOf(trip?.title.orEmpty()) }
    var destination by remember(trip) { mutableStateOf(trip?.destination.orEmpty()) }
    var startDate by remember(trip) { mutableStateOf(trip?.startDate.orEmpty()) }
    var endDate by remember(trip) { mutableStateOf(trip?.endDate.orEmpty()) }
    var groupSize by remember(trip) { mutableStateOf(trip?.groupSize?.toString().orEmpty()) }
    var budgetTarget by remember(trip) {
        mutableStateOf(trip?.budgetTarget?.let { formatPlainNumber(it) }.orEmpty())
    }
    var tried by remember { mutableStateOf(false) }

    val dateRangeError = checkDateRange(startDate, endDate)
    val groupSizeResult = parseOptionalInt(groupSize)
    val budgetResult = parseOptionalNumber(budgetTarget)

    DialogShell(onDismiss = onDismiss) {
        Text("编辑行程信息", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            OutlinedTextField(title, { title = it }, label = { Text("行程标题 *") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(destination, { destination = it }, label = { Text("目的地") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            DateField(startDate, { startDate = it }, "开始日期")
            Spacer(Modifier.height(8.dp))
            DateField(endDate, { endDate = it }, "结束日期")
            if (dateRangeError != null) {
                Text(dateRangeError, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.height(8.dp))
            ValidatedField(
                value = groupSize,
                onValueChange = { groupSize = it },
                label = "预计人数",
                error = if (tried) groupSizeResult.errorOf() else null,
                keyboardOptions = numberKeyboard(integerOnly = true),
            )
            Spacer(Modifier.height(8.dp))
            ValidatedField(
                value = budgetTarget,
                onValueChange = { budgetTarget = it },
                label = "总预算 ¥",
                error = if (tried) budgetResult.errorOf() else null,
                keyboardOptions = numberKeyboard(),
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("取消") }
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = {
                    tried = true
                    val ok = title.isNotBlank() && dateRangeError == null &&
                        groupSizeResult.errorOf() == null && budgetResult.errorOf() == null
                    if (!ok) return@TextButton
                    onSave(
                        TripRequest(
                            title = title.trim(),
                            destination = destination.trim().ifBlank { null },
                            startDate = startDate.ifBlank { null },
                            endDate = endDate.ifBlank { null },
                            groupSize = groupSizeResult.orNull()?.toInt(),
                            budgetTarget = budgetResult.orNull(),
                            visibility = trip?.visibility ?: "PRIVATE",
                            version = trip?.version,
                        ),
                    )
                },
            ) { Text("保存") }
        }
    }
}

// ---------------- 导出预览 ----------------

@Composable
private fun MarkdownDialog(
    text: String,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    DialogShell(onDismiss = onDismiss) {
        Text("导出为 Markdown", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "只包含成员手动填写的内容，可直接粘进笔记或群里。",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                .verticalScroll(rememberScrollState())
                .padding(10.dp),
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                ),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("关闭") }
            TextButton(onClick = onCopy) { Text("复制") }
            TextButton(onClick = onShare) { Text("分享") }
        }
    }
}

@Composable
private fun DialogShell(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
            Column(Modifier.padding(20.dp), content = content)
        }
    }
}

// ---------------- 小工具 ----------------

/** 动态类型 → 对应页签下标；没有对应页签的返回 null（只影响头部提示）。 */
private fun tabIndexForKind(kind: String): Int? = when {
    kind.startsWith("PLACE_") -> 0
    kind.startsWith("LINK_") -> 2
    kind.startsWith("COMMENT_") -> 3
    kind.startsWith("DECISION_") || kind == "VOTE" -> 4
    kind.startsWith("CHECKLIST_") -> 5
    else -> null
}

private fun formatPlainNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "分享行程计划"))
}

private fun shareInvite(context: Context, trip: TripResponse?, code: String) {
    val title = trip?.title ?: "我的行程"
    val text = "来一起规划「$title」！\n邀请码：$code\n点开直接加入：${inviteLink(code)}\n（需先安装 PlanTogether）"
    shareText(context, text)
}
