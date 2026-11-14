package com.plantogether.app.data

import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.util.zip.ZipInputStream

/** 一条聊天消息。时间是微信自己写进 TXT 的，不是我们猜的。 */
data class ChatMessage(
    val sender: String,
    val time: LocalDateTime?,
    val text: String,
)

/** 从一份聊天记录里读出来的东西。 */
data class ChatTranscript(
    val messages: List<ChatMessage>,
    val sourceName: String,
    val attachmentCount: Int,
) {
    val senderCount: Int get() = messages.map { it.sender }.distinct().size
}

/**
 * 微信 8.0.78 起，「多选消息 → 转发 → 选择手机中的应用」会导出一个标准 ZIP：
 * 根目录一份 `聊天记录.txt`，附件在 `聊天记录内的图片、视频和文件/` 里。
 *
 * TXT 里每条消息是固定三行：
 * ```
 * ·张三
 * 2026年11月7日 09:12
 * 正文，可以有很多行
 * ```
 * 行首那个点是 U+00B7（间隔号），日期是 `yyyy年M月d日 HH:mm`、月日不补零。
 *
 * 解析全部在本地完成，不上传、不联网 —— 聊天记录不出这台手机。
 */
object ChatTranscriptParser {

    /** 单份 TXT 解压上限，防止压缩炸弹。 */
    private const val MAX_TEXT_BYTES = 16 * 1024 * 1024
    private const val MAX_ENTRIES = 1000

    /** 行首间隔号，兼容几种长得一样的点。 */
    private const val DOT = "[\\u00B7\\u2022\\u30FB]"

    private val header = Regex(
        "(?m)^$DOT([^\\n]+)\\n(\\d{4})年(\\d{1,2})月(\\d{1,2})日 (\\d{1,2}):(\\d{2})\\n"
    )

    /** 微信/渡口合并多批次时会插进来的说明行，不该算进正文。 */
    private val metaLine = Regex(
        "^(本批文件：|本批文字中的文件路径相对于：|原始记录：|聊天：|说明：|【第\\s*\\d+\\s*批】|batches/)"
    )

    private val attachmentMarkers = listOf(
        "[图片]", "[圖片]", "[photo]", "[image]",
        "[视频]", "[小视频]", "[影片]", "[微影片]", "[video]",
        "[文件]", "[檔案]", "[file]", "[语音]", "[語音]", "[录音]", "[錄音]", "[音频]", "[音訊]",
        "[voice]", "[audio]", "[recording]", "[表情]", "[动画表情]", "[動態貼圖]", "[sticker]",
    )

    /**
     * 解一份 ZIP。返回 null 表示这看起来不是微信导出的聊天记录。
     * 找 txt 的规则和渡口一致：优先叫「聊天记录.txt」的，否则取消息最多的一份。
     */
    fun parseZip(bytes: ByteArray, fallbackName: String): ChatTranscript? {
        var best: ChatTranscript? = null
        var attachmentCount = 0
        var entries = 0

        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (++entries > MAX_ENTRIES) break
                if (entry.isDirectory) continue

                val name = entry.name
                val isAttachment = name.contains("图片") || name.contains("视频") ||
                    Regex("\\.(jpg|jpeg|png|gif|mp4|mov|amr|silk|webp)$", RegexOption.IGNORE_CASE)
                        .containsMatchIn(name)
                if (isAttachment) {
                    attachmentCount++
                    continue
                }

                if (!name.endsWith(".txt", ignoreCase = true)) continue

                val text = readLimited(zip) ?: continue
                val parsed = parseText(text, sourceName = name.substringAfterLast('/'))
                    ?: continue
                // 优先「聊天记录.txt」，否则要消息更多的
                val preferNamed = name.substringAfterLast('/') == "聊天记录.txt"
                val bestNamed = best?.sourceName == "聊天记录.txt"
                if (best == null ||
                    (preferNamed && !bestNamed) ||
                    (preferNamed == bestNamed && parsed.messages.size > best.messages.size)
                ) {
                    best = parsed
                }
            }
        }

        return best?.copy(attachmentCount = attachmentCount)
    }

    /** 直接解一段 TXT（也用于 ZIP 里的每一份 txt）。 */
    fun parseText(raw: String, sourceName: String = "聊天记录"): ChatTranscript? {
        val body = raw
            .removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
        val matches = header.findAll(body).toList()
        if (matches.isEmpty()) return null

        val messages = ArrayList<ChatMessage>(matches.size)
        for ((index, match) in matches.withIndex()) {
            val g = match.groupValues
            val time = runCatching {
                LocalDateTime.of(
                    g[2].toInt(), g[3].toInt(), g[4].toInt(), g[5].toInt(), g[6].toInt(),
                )
            }.getOrNull()

            val start = match.range.last + 1
            val end = if (index + 1 < matches.size) matches[index + 1].range.first else body.length
            val text = body.substring(start, end)
                .lines()
                .filterNot { metaLine.containsMatchIn(it.trim()) }
                .joinToString("\n")
                .trim()

            messages += ChatMessage(sender = g[1].trim(), time = time, text = text)
        }
        if (messages.isEmpty()) return null
        return ChatTranscript(messages = messages, sourceName = sourceName, attachmentCount = 0)
    }

    /** 消息正文里只剩一个附件标记时，这条消息没有可读文字。 */
    fun isAttachmentOnly(text: String): Boolean {
        var rest = text.trim()
        if (rest.isEmpty()) return true
        for (marker in attachmentMarkers) rest = rest.replace(marker, "")
        return rest.isBlank()
    }

    private fun readLimited(zip: ZipInputStream): String? {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(8 * 1024)
        while (true) {
            val read = zip.read(chunk)
            if (read <= 0) break
            if (buffer.size() + read > MAX_TEXT_BYTES) return null
            buffer.write(chunk, 0, read)
        }
        return runCatching { buffer.toString(Charsets.UTF_8.name()) }.getOrNull()
    }
}
