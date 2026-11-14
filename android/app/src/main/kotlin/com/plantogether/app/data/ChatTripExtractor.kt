package com.plantogether.app.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

/** 从聊天里抽出来的一条建议。分四类，对应行程里四种东西。 */
enum class SuggestionKind(val label: String) {
    PLACE("地点"),
    DECISION("待定"),
    TODO("待办"),
    BUDGET("预算"),
}

data class ChatSuggestion(
    val id: Long,
    val kind: SuggestionKind,
    val title: String,
    /** 补充说明，写进备注/清单项。 */
    val detail: String? = null,
    /** 原话，让用户知道这条建议是从哪句来的。 */
    val quote: String,
    val sender: String,
    val timeLabel: String? = null,
    /** 地点日期 yyyy-MM-dd（从聊天里提到的日子推出来）。 */
    val date: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val budget: Double? = null,
    val options: List<String> = emptyList(),
    var selected: Boolean = true,
)

data class ChatExtraction(
    val suggestions: List<ChatSuggestion>,
    val messageCount: Int,
    val senderCount: Int,
    val attachmentCount: Int,
    /** 认出来的目的地，没有就是 null。 */
    val destination: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val budgetTarget: Double? = null,
    val groupSize: Int? = null,
) {
    val isEmpty: Boolean get() = suggestions.isEmpty()
}

/**
 * 从聊天记录里认出「这趟行程该有哪些东西」。
 *
 * 原则：**只建议，不替用户决定**。每条都带上原话，让人一眼看出为什么它被挑出来；
 * 规则宁可漏也不要乱猜 —— 漏了用户自己加，猜错了用户得一条条删。
 *
 * 全部本地跑，不联网。旅行群聊的词汇是封闭的（去哪、几点、多少钱、谁带东西），
 * 够用就不引入模型。
 */
object ChatTripExtractor {

    private val placeVerbs = listOf(
        "去看看", "去", "逛逛", "逛", "吃", "喝", "住", "玩", "打卡", "推荐", "订了", "订",
        "集合", "出发", "到达", "住在", "去了", "想吃", "想去", "要去", "准备去",
    )

    /** 地名/店名常见的结尾字，用来从散句里捞出专有名词。 */
    private val placeSuffix = listOf(
        "古镇", "小镇", "公园", "广场", "车站", "机场", "火车站", "博物馆", "美术馆",
        "酒店", "民宿", "客栈", "餐厅", "饭店", "小吃", "咖啡", "茶馆", "酒吧",
        "寺", "庙", "塔", "桥", "湖", "山", "岛", "街", "路", "巷", "弄", "坊",
        "店", "馆", "园", "宫", "城", "湾", "滩", "温泉", "滑雪场", "动物园",
    )

    private val stopWords = setOf(
        "我们", "你们", "他们", "大家", "一起", "明天", "今天", "后天", "昨天", "早上", "上午",
        "中午", "下午", "晚上", "周末", "下周", "这周", "那边", "这边", "这里", "那里", "什么",
        "怎么", "多少", "哪里", "几点", "一下", "地方", "东西", "时间", "号", "点", "吧", "吗",
        "呢", "啊", "的话", "附近", "门口", "里面", "外面", "上面", "下面", "饭店", "吃", "玩",
    )

    private val todoCues = listOf(
        "记得", "别忘了", "别忘", "要带", "带好", "记得带", "负责", "谁去", "谁买", "谁订",
        "预约", "订票", "订房", "买票", "买好", "报名", "准备", "安排一下",
    )

    private val decisionCues = listOf(
        "还是", "要不", "或者", "你们觉得", "大家觉得", "定一下", "要哪个", "哪个好",
        "投票", "二选一", "选哪个", "决定一下",
    )

    private val budgetCues = listOf("预算", "人均", "大概", "差不多", "花了", "总共", "一共")

    /** 「订票」「买水」这类是动作，不是地方。落在动词后面时按前缀挡掉。 */
    private val actionPrefixes = listOf(
        "订", "买", "带", "准备", "安排", "预约", "付", "交", "查", "问", "找", "算", "看",
    )

    /** 拆出来的「选项」如果只是语气词，不算一个真的选项。 */
    private val optionNoise = listOf("你们觉得", "大家觉得", "你们说", "觉得", "怎么样", "如何")

    private val destinationPatterns = listOf(
        Regex("去([\\u4e00-\\u9fa5]{2,6})(?:玩|旅游|旅行|耍|自驾|玩几天)"),
        Regex("(?:周末|假期|下个月|国庆|元旦|春节)(?:去|到)([\\u4e00-\\u9fa5]{2,6})"),
        Regex("([\\u4e00-\\u9fa5]{2,6})(?:两日游|三日游|一日游|两天一夜|三天两夜)"),
    )

    private val datePattern = Regex("(\\d{4})?[年\\-/]?(\\d{1,2})[月\\-/](\\d{1,2})[日号]?")

    private val timePattern = Regex("(\\d{1,2})[:：](\\d{2})\\s*(?:-|到|~|—|至)\\s*(\\d{1,2})[:：](\\d{2})")
    private val hourPattern = Regex("(凌晨|早上|上午|中午|下午|傍晚|晚上)?\\s*(\\d{1,2})\\s*(?:点|时)(半|\\d{1,2}分?)?")

    private val groupSizePattern = Regex("(\\d{1,2})\\s*(?:个人|人|位)")

    /** 主入口。 */
    fun extract(transcript: ChatTranscript, today: LocalDate = LocalDate.now()): ChatExtraction {
        val messages = transcript.messages
        val out = ArrayList<ChatSuggestion>()
        var nextId = 1L
        val seen = HashSet<String>()

        fun add(s: ChatSuggestion) {
            val key = "${s.kind}|${s.title}"
            if (!seen.add(key)) return
            if (out.size >= 60) return
            out += s
        }

        val destination = detectDestination(messages)
        val (startDate, endDate) = detectDateRange(messages, today)
        val budgetTarget = detectBudgetTarget(messages)
        val groupSize = messages.firstNotNullOfOrNull { m ->
            groupSizePattern.find(m.text)?.groupValues?.get(1)?.toIntOrNull()
        }?.takeIf { it in 2..99 }

        for (message in messages) {
            if (ChatTranscriptParser.isAttachmentOnly(message.text)) continue
            val text = message.text.trim()
            if (text.length < 2) continue
            val when_ = message.time
            val timeLabel = when_?.let { "%02d-%02d %02d:%02d".format(it.monthValue, it.dayOfMonth, it.hour, it.minute) }
            val day = when_?.toLocalDate()

            // ---- 待办 ----
            val todoHit = todoCues.firstOrNull { text.contains(it) }
            if (todoHit != null) {
                val body = cleanupText(text, todoHit)
                    .removePrefix("谁去").removePrefix("谁买").removePrefix("谁订")
                    .trimStart('？', '?', '，', ',', ' ')
                if (body.length in 2..60) {
                    add(
                        ChatSuggestion(
                            id = nextId++, kind = SuggestionKind.TODO,
                            title = body.take(40),
                            detail = text.take(200),
                            quote = text.take(200), sender = message.sender, timeLabel = timeLabel,
                        )
                    )
                }
            }

            // ---- 待定（选项型讨论）----
            val decisionHit = decisionCues.firstOrNull { text.contains(it) }
            if (decisionHit != null) {
                val options = splitOptions(text)
                    .filterNot { option -> optionNoise.any { option.contains(it) } }
                    .distinct()
                val title = if (options.size >= 2) {
                    options.joinToString(" / ")
                } else {
                    cleanupText(text, decisionHit)
                }.take(40)
                if (title.length >= 2) {
                    add(
                        ChatSuggestion(
                            id = nextId++, kind = SuggestionKind.DECISION,
                            title = title,
                            detail = if (options.isEmpty()) text.take(200) else null,
                            quote = text.take(200), sender = message.sender, timeLabel = timeLabel,
                            options = options,
                        )
                    )
                }
            }

            // ---- 地点 ----
            for (name in detectPlaces(text)) {
                // 目的地本身不是「要去的一站」，它已经填在行程上了
                if (name == destination) continue
                val (start, end) = detectTimes(text)
                add(
                    ChatSuggestion(
                        id = nextId++, kind = SuggestionKind.PLACE,
                        title = name,
                        detail = text.take(160).takeIf { it != name },
                        quote = text.take(200), sender = message.sender, timeLabel = timeLabel,
                        date = day?.toString(),
                        startTime = start, endTime = end,
                        budget = detectAmount(text),
                    )
                )
            }

            // ---- 预算 ----
            if (text.contains("预算")) {
                val amount = detectAmount(text)
                if (amount != null && amount >= 10) {
                    add(
                        ChatSuggestion(
                            id = nextId++, kind = SuggestionKind.BUDGET,
                            title = "预算 ¥${formatAmount(amount)}",
                            detail = text.take(160),
                            quote = text.take(200), sender = message.sender, timeLabel = timeLabel,
                            budget = amount,
                        )
                    )
                }
            }
        }

        // 地点排在前面：用户最先想看的是「去哪」
        val ordered = out.sortedWith(
            compareBy(
                { it.kind != SuggestionKind.PLACE },
                { it.date == null },
                { it.date ?: "" },
                { it.timeLabel ?: "99" },
            )
        )
        return ChatExtraction(
            suggestions = ordered,
            messageCount = messages.size,
            senderCount = transcript.senderCount,
            attachmentCount = transcript.attachmentCount,
            destination = destination,
            startDate = startDate,
            endDate = endDate,
            budgetTarget = budgetTarget,
            groupSize = groupSize,
        )
    }

    // ---------------- 各字段的识别 ----------------

    private fun detectDestination(messages: List<ChatMessage>): String? {
        for (message in messages) {
            for (pattern in destinationPatterns) {
                val raw = pattern.find(message.text)?.groupValues?.getOrNull(1)?.trim()
                val value = raw?.trimEnd('吧', '呢', '啊', '哦', '呀', '吗', '了', '的', '哈', '～', '~')
                if (!value.isNullOrBlank() && value !in stopWords && value.length in 2..6) return value
            }
        }
        return null
    }

    /** 从聊天里提到的最早/最晚日期作为行程起止。认不出就都返回 null。 */
    private fun detectDateRange(messages: List<ChatMessage>, today: LocalDate): Pair<String?, String?> {
        // 聊天记录每条都自带发送时间，这是最可靠的来源。
        // 但如果这段记录横跨太久（比如群里闲聊了两个月），就不能当作行程范围。
        val stamps = messages.mapNotNull { it.time?.toLocalDate() }.distinct().sorted()
        if (stamps.size >= 2) {
            val start = stamps.first()
            val end = stamps.last()
            if (!end.isAfter(start.plusDays(60))) {
                return start.toString() to end.toString()
            }
        }

        val dates = ArrayList<LocalDate>()
        for (message in messages) {
            for (match in datePattern.findAll(message.text)) {
                val g = match.groupValues
                val month = g[2].toIntOrNull() ?: continue
                val day = g[3].toIntOrNull() ?: continue
                if (month !in 1..12 || day !in 1..31) continue
                val year = g[1].toIntOrNull() ?: today.year
                val date = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: continue
                if (!dates.contains(date)) dates += date
            }
        }
        if (dates.isEmpty()) return null to null
        val sorted = dates.sorted()
        // 只取一个人单天说的日期可能不准，但聊到多个日期时首尾基本就是行程范围
        val start = sorted.first()
        val end = sorted.last().takeIf { it != start }
        return start.toString() to end?.toString()
    }

    private fun detectBudgetTarget(messages: List<ChatMessage>): Double? {
        // 先找明确说了「预算」的那句 —— 「人均 150」大多是单顿饭，不是全程预算
        for (message in messages) {
            if (!message.text.contains("预算")) continue
            val amount = detectAmount(message.text)
            if (amount != null && amount >= 50) return amount
        }
        for (message in messages) {
            if (!budgetCues.any { message.text.contains(it) }) continue
            if (groupSizePattern.containsMatchIn(message.text)) continue
            val amount = detectAmount(message.text)
            if (amount != null && amount >= 100) return amount
        }
        return null
    }

    /** 从一句话里认地点名。按「动词 + 名词」和「名词以地名后缀结尾」两条路。 */
    private fun detectPlaces(text: String): List<String> {
        val found = LinkedHashSet<String>()

        var verbHits = false
        for (verb in placeVerbs.sortedByDescending { it.length }) {
            val index = text.indexOf(verb)
            if (index < 0) continue
            verbHits = true
            var tail = text.substring(index + verb.length)
                .trimStart('了', '个', '家', '间', '趟', ' ', '　')
            // 取到标点或空格为止
            tail = tail.split('，', ',', '。', '.', '！', '!', '？', '?', '；', ';', '：', ':', '\n', ' ')
                .firstOrNull().orEmpty()
            val candidate = cleanCandidate(tail)
            if (candidate != null) found += candidate
            if (found.size >= 3) break
        }
        if (!verbHits) {
            // 没有动词时，退回「以地名后缀结尾」的片段
            val tokens = text.split('，', ',', '。', '.', '！', '!', '？', '?', '；', ';', ' ', '\n')
            for (token in tokens) {
                val trimmed = token.trim()
                if (trimmed.length in 2..12 && placeSuffix.any { trimmed.endsWith(it) }) {
                    val candidate = cleanCandidate(trimmed)
                    if (candidate != null) found += candidate
                }
                if (found.size >= 3) break
            }
        }
        return found.toList()
    }

    private fun cleanCandidate(raw: String): String? {
        var value = raw.trim()
        value = value.trimStart('在', '到', '往', '于', '了', '的', '把', '和', '跟', '与')
        value = value.trimEnd('吧', '呢', '啊', '哦', '呀', '吗', '了', '的', '呢', '哈', '～', '~')
        value = stripPrefixes(value, listOf("我们", "你们", "他们", "大家"))
        value = stripSuffixes(value, listOf("附近", "那边", "这边", "门口", "里面", "外面"))
        if (value.length < 2 || value.length > 12) return null
        if (value in stopWords) return null
        if (value.all { it.isDigit() || it in ":：-~—、" }) return null
        if (datePattern.matches(value)) return null
        if (hourPattern.matches(value)) return null
        if (value.all { it in "，,。.!！?？…~～-— " }) return null
        // 动作不是地方：「订票」「买水」
        if (actionPrefixes.any { value.startsWith(it) }) return null
        // 讨论句不是地方：「住民宿还是住酒店」
        if (decisionCues.any { value.contains(it) }) return null
        if (todoCues.any { value.contains(it) }) return null
        if (optionNoise.any { value.contains(it) }) return null
        if (value.contains('？') || value.contains('?')) return null
        // 「9点」「10人」这类不是地名
        if (Regex("^\\d").containsMatchIn(value) && placeSuffix.none { value.endsWith(it) }) return null
        return value
    }

    /** 反复剥掉开头的人称词（「我们大家去」→「去」）。 */
    private fun stripPrefixes(value: String, prefixes: List<String>): String {
        var result = value
        var changed = true
        while (changed) {
            changed = false
            for (prefix in prefixes) {
                if (result.startsWith(prefix) && result.length > prefix.length) {
                    result = result.removePrefix(prefix).trim()
                    changed = true
                }
            }
        }
        return result
    }

    /** 反复剥掉结尾的方位词（「西湖附近」→「西湖」）。 */
    private fun stripSuffixes(value: String, suffixes: List<String>): String {
        var result = value
        var changed = true
        while (changed) {
            changed = false
            for (suffix in suffixes) {
                if (result.endsWith(suffix) && result.length > suffix.length) {
                    result = result.removeSuffix(suffix).trim()
                    changed = true
                }
            }
        }
        return result
    }

    /** 一句话里的时间：「9:30-11:00」优先，其次「9点」「下午3点」。 */
    private fun detectTimes(text: String): Pair<String?, String?> {
        val range = timePattern.find(text)
        if (range != null) {
            val g = range.groupValues
            return pad(g[1], g[2]) to pad(g[3], g[4])
        }
        val single = hourPattern.find(text) ?: return null to null
        val period = single.groupValues[1]
        val hour = single.groupValues[2].toIntOrNull() ?: return null to null
        val minuteRaw = single.groupValues[3].trim()
        val minute = when {
            minuteRaw == "半" -> 30
            minuteRaw.isBlank() -> 0
            else -> minuteRaw.trimEnd('分').toIntOrNull() ?: 0
        }
        val adjusted = when (period) {
            "下午", "傍晚", "晚上" -> if (hour in 1..11) hour + 12 else hour
            else -> hour
        }
        if (adjusted !in 0..23) return null to null
        return "%02d:%02d".format(adjusted, minute) to null
    }

    private fun pad(hourText: String, minuteText: String): String? {
        val hour = hourText.toIntOrNull() ?: return null
        val minute = minuteText.toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return "%02d:%02d".format(hour, minute)
    }

    /** 金额：¥100 / 100元 / 100块 / 人均100 / 1.5万。 */
    private fun detectAmount(text: String): Double? {
        val pattern = Regex("(?:¥|￥)?\\s*(\\d+(?:\\.\\d{1,2})?)\\s*(万|千|元|块|rmb|RMB)?")
        var best: Double? = null
        for (match in pattern.findAll(text)) {
            val number = match.groupValues[1].toDoubleOrNull() ?: continue
            val unit = match.groupValues[2]
            val scaled = when (unit) {
                "万" -> number * 10000
                "千" -> number * 1000
                else -> number
            }
            // 带「元/块」的优先，其次是 1000 以上的裸数字
            val confidence = when {
                unit == "万" || unit == "千" -> 2
                unit == "元" || unit == "块" -> 2
                match.value.contains('¥') || match.value.contains('￥') -> 2
                scaled >= 100 -> 1
                else -> 0
            }
            if (confidence > 0 && (best == null || confidence > 1)) best = scaled
        }
        return best
    }

    private fun splitOptions(text: String): List<String> {
        val pieces = text
            .split("还是", "或者", " / ", "/", "、", "，", ",", " vs ", "VS")
            .map { cleanupText(it, "") }
            .filter { it.length in 1..24 }
            .distinct()
        return if (pieces.size in 2..5) pieces else emptyList()
    }

    private fun cleanupText(text: String, cue: String): String {
        var value = text.trim()
        if (cue.isNotEmpty()) value = value.replace(cue, "").trim()
        value = value.trimStart('，', ',', '。', '.', '！', '!', '？', '?', '：', ':', ' ', '的', '了')
        value = value.trimEnd('吧', '呢', '啊', '？', '?', '！', '!', '。', '.', '～', '~')
        return value.trim()
    }

    private fun formatAmount(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString()
        else String.format(Locale.CHINA, "%.2f", value)

    /** 把建议按天分组，预览页照着天排版。 */
    fun groupByDay(suggestions: List<ChatSuggestion>): List<Pair<String?, List<ChatSuggestion>>> {
        val groups = LinkedHashMap<String?, MutableList<ChatSuggestion>>()
        for (item in suggestions) {
            val key = if (item.kind == SuggestionKind.PLACE) item.date else null
            groups.getOrPut(key) { ArrayList() } += item
        }
        return groups.entries
            .sortedWith(compareBy({ it.key == null }, { it.key ?: "" }))
            .map { it.key to it.value.toList() }
    }

    /** 主界面上那句「认出了 N 条」。 */
    fun summary(extraction: ChatExtraction): String {
        val places = extraction.suggestions.count { it.kind == SuggestionKind.PLACE }
        val decisions = extraction.suggestions.count { it.kind == SuggestionKind.DECISION }
        val todos = extraction.suggestions.count { it.kind == SuggestionKind.TODO }
        val parts = buildList {
            if (places > 0) add("$places 个地点")
            if (decisions > 0) add("$decisions 个待定")
            if (todos > 0) add("$todos 个待办")
        }
        return if (parts.isEmpty()) "没认出可直接用的内容" else "认出 " + parts.joinToString("、")
    }

    /** 把聊天里的 LocalDateTime 转成给人看的「11-07 09:12」。 */
    fun timeLabelOf(time: LocalDateTime?): String? =
        time?.let { "%02d-%02d %02d:%02d".format(it.monthValue, it.dayOfMonth, it.hour, it.minute) }
}
