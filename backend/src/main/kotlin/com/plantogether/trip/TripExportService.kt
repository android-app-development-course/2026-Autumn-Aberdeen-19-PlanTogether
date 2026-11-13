package com.plantogether.trip

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 把行程导出成 Markdown：只包含成员手动录入的内容，不抓取任何外部页面。
 * 用户可以把导出的文本存进笔记、粘贴到群里，或作为离线备份。
 */
@Service
class TripExportService(
    private val placeRepository: PlaceRepository,
    private val linkRepository: LinkRepository,
    private val checklistRepository: ChecklistItemRepository,
    private val decisionRepository: DecisionRepository,
    private val optionRepository: DecisionOptionRepository,
    private val memberRepository: TripMemberRepository,
    private val commentRepository: TripCommentRepository,
    private val accessor: TripAccessor,
) {

    @Transactional(readOnly = true)
    fun exportMarkdown(tripId: Long): String {
        val trip = accessor.read(tripId)
        val places = placeRepository.findAllByTripIdOrderBySortOrderAsc(tripId)
        val links = linkRepository.findAllByTripIdOrderByCreatedAtDesc(tripId)
        val checklist = checklistRepository.findAllByTripIdOrderBySortOrderAscIdAsc(tripId)
        val decisions = decisionRepository.findAllByTripIdOrderByCreatedAtDesc(tripId)
        val members = memberRepository.findAllByTripIdOrderByJoinedAtAsc(tripId)
        val comments = commentRepository.findAllByTripIdOrderByCreatedAtDesc(tripId).take(20)

        val sb = StringBuilder()
        sb.appendLine("# ${trip.title}")
        sb.appendLine()

        val meta = buildList {
            trip.destination?.takeIf { it.isNotBlank() }?.let { add(it) }
            add(dateRange(trip))
            trip.groupSize?.let { add("预计 $it 人") }
            add("${1 + members.size} 人已加入")
        }
        sb.appendLine(meta.joinToString(" · "))
        sb.appendLine()
        sb.appendLine(
            "> 由 PlanTogether 导出于 " +
                Instant.now().atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) +
                "。内容均为成员手动填写，不包含任何第三方平台抓取的数据。",
        )
        sb.appendLine()

        // ---- 行程安排 ----
        sb.appendLine("## 一、行程安排")
        sb.appendLine()
        if (places.isEmpty()) {
            sb.appendLine("_还没有添加地点。_")
        } else {
            val grouped = places.groupBy { it.date }
            val undated = grouped[null].orEmpty()
            grouped.keys.filterNotNull().sorted().forEachIndexed { index, date ->
                sb.appendLine("### ${dayLabel(trip.startDate, date, index + 1)}")
                sb.appendLine()
                grouped[date].orEmpty().forEachIndexed { i, place ->
                    sb.appendLine(placeLine(i + 1, place))
                }
                sb.appendLine()
            }
            if (undated.isNotEmpty()) {
                sb.appendLine("### 未安排日期")
                sb.appendLine()
                undated.forEachIndexed { i, place -> sb.appendLine(placeLine(i + 1, place)) }
                sb.appendLine()
            }
        }

        // ---- 链接收藏 ----
        sb.appendLine("## 二、链接收藏")
        sb.appendLine()
        if (links.isEmpty()) {
            sb.appendLine("_还没有收藏链接。_")
        } else {
            links.forEach { link ->
                val title = link.title?.takeIf { it.isNotBlank() } ?: link.url
                val suffix = buildList {
                    link.notes?.takeIf { it.isNotBlank() }?.let { add(it) }
                    link.tags?.takeIf { it.isNotBlank() }?.let { add("标签：$it") }
                    link.createdBy?.displayName?.let { add("提交：$it") }
                }.joinToString(" — ")
                sb.appendLine("- [$title](${link.url})" + if (suffix.isBlank()) "" else " — $suffix")
            }
        }
        sb.appendLine()

        // ---- 群决策 ----
        sb.appendLine("## 三、群决策")
        sb.appendLine()
        if (decisions.isEmpty()) {
            sb.appendLine("_还没有需要投票的决策。_")
        } else {
            decisions.forEach { decision ->
                val statusLabel = when (decision.status) {
                    DecisionStatus.DECIDED.name -> "已定案"
                    DecisionStatus.CANCELLED.name -> "已取消"
                    else -> "进行中"
                }
                sb.appendLine("### ${decision.title}（$statusLabel）")
                decision.notes?.takeIf { it.isNotBlank() }?.let { sb.appendLine(it) }
                val options = optionRepository.findAllByDecisionIdOrderBySortOrderAscIdAsc(decision.id)
                if (options.isEmpty()) {
                    sb.appendLine("- _还没有选项_")
                } else {
                    options.forEach { option ->
                        val marker = if (decision.decidedOptionId == option.id) "x" else " "
                        val votes = "${option.voterIds.size} 票"
                        sb.appendLine("- [$marker] ${option.title} — $votes")
                    }
                }
                sb.appendLine()
            }
        }

        // ---- 清单 ----
        sb.appendLine("## 四、行前清单")
        sb.appendLine()
        if (checklist.isEmpty()) {
            sb.appendLine("_清单还是空的。_")
        } else {
            checklist.forEach { item ->
                val mark = if (item.done) "x" else " "
                val assignee = item.assignee?.takeIf { it.isNotBlank() }?.let { "（$it）" } ?: ""
                sb.appendLine("- [$mark] ${item.title}$assignee")
            }
        }
        sb.appendLine()

        // ---- 预算 ----
        val total = places.mapNotNull { it.budget }.sum()
        val headcount = trip.groupSize ?: (1 + members.size)
        sb.appendLine("## 五、预算（成员手动填写）")
        sb.appendLine()
        sb.appendLine("- 地点预算合计：${money(total)}")
        if (headcount > 0) sb.appendLine("- 人均（按 $headcount 人）：${money(total / headcount)}")
        trip.budgetTarget?.let { target ->
            val diff = target - total
            sb.appendLine(
                "- 预算目标：${money(target)}，${if (diff >= 0) "剩余 ${money(diff)}" else "超出 ${money(-diff)}"}",
            )
        }
        sb.appendLine()

        // ---- 成员 ----
        sb.appendLine("## 六、成员")
        sb.appendLine()
        sb.appendLine("- ${trip.owner.displayName}（创建者）")
        members.forEach { member ->
            val label = if (member.role == TripRole.VIEWER.name) "只读" else "可编辑"
            sb.appendLine("- ${member.user.displayName}（$label）")
        }
        sb.appendLine()

        // ---- 讨论 ----
        if (comments.isNotEmpty()) {
            sb.appendLine("## 七、讨论摘录（最近 ${comments.size} 条）")
            sb.appendLine()
            comments.asReversed().forEach { comment ->
                val where = comment.place?.let { "（关于「${it.name}」）" } ?: ""
                sb.appendLine("- **${comment.user.displayName}**$where：${comment.body}")
            }
            sb.appendLine()
        }

        return sb.toString()
    }

    private fun placeLine(index: Int, place: Place): String {
        val time = when {
            place.startTime != null && place.endTime != null -> "${place.startTime}–${place.endTime} "
            place.startTime != null -> "${place.startTime} "
            else -> ""
        }
        val sb = StringBuilder("$index. **$time${place.name}**")
        place.stayLength?.takeIf { it.isNotBlank() }?.let { sb.append("（停留 $it）") }
        sb.appendLine()
        place.address?.takeIf { it.isNotBlank() }?.let { sb.appendLine("   - 地址：$it") }
        place.budget?.let { sb.appendLine("   - 预算：${money(it)}") }
        place.notes?.takeIf { it.isNotBlank() }?.let { sb.appendLine("   - 备注：$it") }
        place.backupOption?.takeIf { it.isNotBlank() }?.let { sb.appendLine("   - 备选：$it") }
        place.mapLink?.takeIf { it.isNotBlank() }?.let { sb.appendLine("   - 地图：$it") }
        return sb.toString().trimEnd()
    }

    private fun dayLabel(startDate: LocalDate?, date: LocalDate, fallbackIndex: Int): String {
        val dayNumber = if (startDate != null) {
            startDate.until(date).days.toInt() + 1
        } else {
            fallbackIndex
        }
        val weekday = date.format(DateTimeFormatter.ofPattern("EEEE", Locale.SIMPLIFIED_CHINESE))
        return "D$dayNumber · $date（$weekday）"
    }

    private fun dateRange(trip: Trip): String = when {
        trip.startDate != null && trip.endDate != null -> "${trip.startDate} ~ ${trip.endDate}"
        trip.startDate != null -> "${trip.startDate} 出发"
        trip.endDate != null -> "至 ${trip.endDate}"
        else -> "日期待定"
    }

    private fun money(value: Double): String =
        if (value == value.toLong().toDouble()) "¥${value.toLong()}" else "¥%.2f".format(value)
}
