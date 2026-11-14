package com.plantogether.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.plantogether.app.ui.PlanTogetherApp
import com.plantogether.app.ui.theme.PlanTogetherTheme

class MainActivity : ComponentActivity() {

    /** 系统分享进来的链接（App 已在前台时也能收到）。 */
    private val shareState = mutableStateOf<SharedPayload?>(null)

    /** 深链邀请 plantogether://join/CODE。 */
    private val inviteState = mutableStateOf<InvitePayload?>(null)

    /** 微信「转发到其他应用」过来的聊天记录压缩包。 */
    private val chatZipState = mutableStateOf<ChatZipPayload?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        shareState.value = parseShare(intent)
        inviteState.value = parseInvite(intent)
        chatZipState.value = parseChatZip(intent)

        setContent {
            PlanTogetherTheme {
                val share = shareState.value
                val invite = inviteState.value
                val chatZip = chatZipState.value
                PlanTogetherApp(
                    sharedUrl = share?.url,
                    sharedTitle = share?.title,
                    shareEventId = share?.eventId ?: 0L,
                    inviteCode = invite?.code,
                    inviteEventId = invite?.eventId ?: 0L,
                    chatZipBytes = chatZip?.bytes,
                    chatZipName = chatZip?.name,
                    chatZipEventId = chatZip?.eventId ?: 0L,
                )
            }
        }
    }

    /** App 已在运行时又被分享/被深链打开：统一走 onNewIntent。 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        shareState.value = parseShare(intent)
        inviteState.value = parseInvite(intent)
        chatZipState.value = parseChatZip(intent)
    }

    private fun parseShare(intent: Intent?): SharedPayload? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim() ?: return null
        val url = Regex("https?://\\S+").find(text)?.value
            ?: text.ifBlank { null }
            ?: return null
        val title = intent.getStringExtra(Intent.EXTRA_SUBJECT)
            ?: intent.getStringExtra(Intent.EXTRA_TITLE)
        return SharedPayload(url = url, title = title, eventId = System.nanoTime())
    }

    private fun parseInvite(intent: Intent?): InvitePayload? {
        val uri = intent?.data ?: return null
        if (!"plantogether".equals(uri.scheme, ignoreCase = true)) return null
        if (!"join".equals(uri.host, ignoreCase = true)) return null
        val code = uri.lastPathSegment?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return InvitePayload(code = code.uppercase(), eventId = System.nanoTime())
    }

    /**
     * 接住微信「转发到其他应用」送来的聊天记录压缩包。
     *
     * 微信 8.0.78 起会把选中的消息打成一个标准 ZIP（里面有 聊天记录.txt），
     * 通过 ACTION_SEND + EXTRA_STREAM 交给下一个 App。这里只做两件事：
     * 确认它真的是 ZIP、把字节读进来。解压和解析都在 ChatImportScreen 里做。
     */
    private fun parseChatZip(intent: Intent?): ChatZipPayload? {
        if (intent == null) return null
        val action = intent.action
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_VIEW) return null

        val uri = if (action == Intent.ACTION_VIEW) {
            intent.data
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        } ?: return null

        val name = displayName(uri) ?: uri.lastPathSegment ?: "聊天记录.zip"
        val mime = intent.type.orEmpty().lowercase()
        val looksLikeZip = mime.contains("zip") ||
            mime == "application/octet-stream" ||
            name.lowercase().endsWith(".zip")
        if (!looksLikeZip) return null

        val bytes = runCatching {
            contentResolver.openInputStream(uri)?.use { stream ->
                stream.readNBytes(MAX_ZIP_BYTES + 1)
            }
        }.getOrNull() ?: return null
        if (bytes.size < 4 || bytes.size > MAX_ZIP_BYTES) return null
        // PK\x03\x04 —— 不是 ZIP 就不接，免得把别的附件当聊天记录解析
        if (bytes[0] != 'P'.code.toByte() || bytes[1] != 'K'.code.toByte()) return null

        return ChatZipPayload(bytes = bytes, name = name, eventId = System.nanoTime())
    }

    private fun displayName(uri: Uri): String? = runCatching {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex("_display_name")
                if (index >= 0) cursor.getString(index) else null
            } else null
        }
    }.getOrNull()

    private data class SharedPayload(val url: String, val title: String?, val eventId: Long)

    private data class InvitePayload(val code: String, val eventId: Long)

    private data class ChatZipPayload(val bytes: ByteArray, val name: String, val eventId: Long) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ChatZipPayload) return false
            return eventId == other.eventId && name == other.name && bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int =
            (eventId.hashCode() * 31 + name.hashCode()) * 31 + bytes.contentHashCode()
    }

    private companion object {
        /** 微信一次最多导出 100 条消息，32MB 已经很宽裕了。 */
        const val MAX_ZIP_BYTES = 32 * 1024 * 1024
    }
}
