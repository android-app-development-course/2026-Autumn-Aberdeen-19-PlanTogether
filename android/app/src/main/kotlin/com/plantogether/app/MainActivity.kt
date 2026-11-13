package com.plantogether.app

import android.content.Intent
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        shareState.value = parseShare(intent)
        inviteState.value = parseInvite(intent)

        setContent {
            PlanTogetherTheme {
                val share = shareState.value
                val invite = inviteState.value
                PlanTogetherApp(
                    sharedUrl = share?.url,
                    sharedTitle = share?.title,
                    shareEventId = share?.eventId ?: 0L,
                    inviteCode = invite?.code,
                    inviteEventId = invite?.eventId ?: 0L,
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

    private data class SharedPayload(val url: String, val title: String?, val eventId: Long)

    private data class InvitePayload(val code: String, val eventId: Long)
}
