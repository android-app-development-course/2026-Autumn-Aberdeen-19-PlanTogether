package com.plantogether.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 聊天记录解析 + 抽取的回归测试。
 *
 * 这些东西没有 UI、没有网络，是纯文本处理，最容易悄悄写坏，
 * 所以每条规则都钉一个断言在这儿。
 */
class ChatTranscriptParserTest {

    private val sample = """
        ·张三
        2026年11月7日 09:12
        我们周末去杭州吧，我看了下天气预报不错
        ·李四
        2026年11月7日 09:15
        好啊！想去西湖断桥，早上人少
        ·张三
        2026年11月7日 09:20
        中午吃楼外楼？人均大概 150 左右
        ·王五
        2026年11月7日 09:23
        住民宿还是住酒店，你们觉得呢
        ·李四
        2026年11月7日 09:30
        记得带充电宝，还有雨伞
        ·张三
        2026年11月8日 08:05
        第二天去灵隐寺，9:30-11:00
        ·王五
        2026年11月8日 08:10
        谁去订票？高铁票还没买
        ·李四
        2026年11月8日 08:20
        那就定 11 月 7 号到 8 号，预算 人均 500
    """.trimIndent()

    private fun zipOf(entries: Map<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, content) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    // ---------------- 解析 ----------------

    @Test
    fun `parses the three line message format`() {
        val transcript = ChatTranscriptParser.parseText(sample)
        assertNotNull(transcript)
        transcript!!
        assertEquals(8, transcript.messages.size)
        assertEquals(3, transcript.senderCount)

        val first = transcript.messages.first()
        assertEquals("张三", first.sender)
        assertEquals("我们周末去杭州吧，我看了下天气预报不错", first.text)
        assertEquals(9, first.time?.hour)
        assertEquals(12, first.time?.minute)
    }

    @Test
    fun `handles multi line message bodies`() {
        val text = """
            ·张三
            2026年11月7日 09:12
            第一行
            第二行
            ·李四
            2026年11月7日 09:13
            收到
        """.trimIndent()
        val transcript = ChatTranscriptParser.parseText(text)!!
        assertEquals(2, transcript.messages.size)
        assertEquals("第一行\n第二行", transcript.messages[0].text)
        assertEquals("收到", transcript.messages[1].text)
    }

    @Test
    fun `tolerates crlf and bom`() {
        val text = "\uFEFF" + sample.replace("\n", "\r\n")
        val transcript = ChatTranscriptParser.parseText(text)
        assertNotNull(transcript)
        assertEquals(8, transcript!!.messages.size)
    }

    @Test
    fun `rejects text without the wechat header`() {
        assertNull(ChatTranscriptParser.parseText("随便一句没有格式的话"))
        assertNull(ChatTranscriptParser.parseText(""))
    }

    @Test
    fun `reads the transcript out of a zip and skips attachments`() {
        val zip = zipOf(
            mapOf(
                "聊天记录.txt" to sample,
                "聊天记录内的图片、视频和文件/a.jpg" to "x",
                "聊天记录内的图片、视频和文件/b.mp4" to "y",
            ),
        )
        val transcript = ChatTranscriptParser.parseZip(zip, "聊天记录.zip")
        assertNotNull(transcript)
        assertEquals(8, transcript!!.messages.size)
        assertEquals(2, transcript.attachmentCount)
        assertEquals("聊天记录.txt", transcript.sourceName)
    }

    @Test
    fun `rejects a zip without any transcript`() {
        val zip = zipOf(mapOf("说明.md" to "hello"))
        assertNull(ChatTranscriptParser.parseZip(zip, "x.zip"))
        assertNull(ChatTranscriptParser.parseZip(ByteArray(0), "x.zip"))
    }

    @Test
    fun `prefers the file literally named transcript txt`() {
        val zip = zipOf(
            mapOf(
                "batches/0001/聊天记录.txt" to sample,
                // 名字更像正文但内容更少，不应该赢
                "别的.txt" to "·甲\n2026年11月7日 09:12\n就一句",
            ),
        )
        val transcript = ChatTranscriptParser.parseZip(zip, "x.zip")!!
        assertEquals("聊天记录.txt", transcript.sourceName)
        assertEquals(8, transcript.messages.size)
    }

    @Test
    fun `attachment only messages are recognised`() {
        assertTrue(ChatTranscriptParser.isAttachmentOnly("[图片]"))
        assertTrue(ChatTranscriptParser.isAttachmentOnly("[图片][图片]"))
        assertTrue(!ChatTranscriptParser.isAttachmentOnly("[图片]西湖真好看"))
    }

    // ---------------- 抽取 ----------------

    private fun extractSample(): ChatExtraction {
        val transcript = ChatTranscriptParser.parseText(sample)!!
        return ChatTripExtractor.extract(transcript, LocalDate.of(2026, 11, 1))
    }

    @Test
    fun `finds destination dates and budget`() {
        val ex = extractSample()
        assertEquals("杭州", ex.destination)
        assertEquals("2026-11-07", ex.startDate)
        assertEquals("2026-11-08", ex.endDate)
        // 「人均 150」是单顿饭；「预算 人均 500」才是全程预算
        assertEquals(500.0, ex.budgetTarget!!, 0.01)
    }

    @Test
    fun `finds the places that were actually mentioned`() {
        val places = extractSample().suggestions.filter { it.kind == SuggestionKind.PLACE }
        val names = places.map { it.title }
        assertTrue("应该有西湖断桥，实际 $names", names.contains("西湖断桥"))
        assertTrue("应该有楼外楼，实际 $names", names.contains("楼外楼"))
        assertTrue("应该有灵隐寺，实际 $names", names.contains("灵隐寺"))
    }

    @Test
    fun `the destination itself is not offered as a place`() {
        // 「去杭州」是行程目的地，不是要打卡的一站；否则导进去会多出一个叫「杭州」的地点卡
        val ex = extractSample()
        val names = ex.suggestions.filter { it.kind == SuggestionKind.PLACE }.map { it.title }
        assertEquals("杭州", ex.destination)
        assertTrue("目的地不该出现地点列表里，实际 $names", names.none { it == "杭州" })
    }

    @Test
    fun `does not turn actions or questions into places`() {
        val names = extractSample().suggestions
            .filter { it.kind == SuggestionKind.PLACE }
            .map { it.title }
        // 「订票」是动作
        assertTrue("不该出现订票，实际 $names", names.none { it.contains("订票") })
        // 「住民宿还是住酒店」是讨论
        assertTrue("不该出现讨论句，实际 $names", names.none { it.contains("还是") })
    }

    @Test
    fun `keeps the time range on the place`() {
        val lingyin = extractSample().suggestions
            .first { it.kind == SuggestionKind.PLACE && it.title == "灵隐寺" }
        assertEquals("09:30", lingyin.startTime)
        assertEquals("11:00", lingyin.endTime)
        assertEquals("2026-11-08", lingyin.date)
    }

    @Test
    fun `turns an either or question into a decision with options`() {
        val decision = extractSample().suggestions
            .first { it.kind == SuggestionKind.DECISION }
        assertEquals(listOf("住民宿", "住酒店"), decision.options)
        assertTrue(decision.title.contains("民宿"))
    }

    @Test
    fun `turns reminders into checklist items`() {
        val todos = extractSample().suggestions
            .filter { it.kind == SuggestionKind.TODO }
            .map { it.title }
        assertTrue("应该有带充电宝，实际 $todos", todos.any { it.contains("充电宝") })
    }

    @Test
    fun `every suggestion carries the quote it came from`() {
        for (suggestion in extractSample().suggestions) {
            assertTrue("「${suggestion.title}」没有原话", suggestion.quote.isNotBlank())
            assertTrue("「${suggestion.title}」没有发送人", suggestion.sender.isNotBlank())
        }
    }

    @Test
    fun `empty chat yields nothing instead of crashing`() {
        val ex = ChatTripExtractor.extract(
            ChatTranscript(emptyList(), "空", 0),
            LocalDate.of(2026, 11, 1),
        )
        assertTrue(ex.isEmpty)
        assertNull(ex.destination)
        assertNull(ex.startDate)
    }

    @Test
    fun `groups places by day and keeps the rest ungrouped`() {
        val groups = ChatTripExtractor.groupByDay(extractSample().suggestions)
        val days = groups.map { it.first }
        assertTrue(days.contains("2026-11-07"))
        assertTrue(days.contains("2026-11-08"))
        assertTrue(days.contains(null))
    }
}
