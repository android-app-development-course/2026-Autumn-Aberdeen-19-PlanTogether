package com.plantogether.app.data

import com.google.gson.Gson
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException

object ApiClient {

    /**
     * 默认后端地址：真机通过 `adb reverse tcp:8080 tcp:8080` 转发到开发机。
     * 换成别的服务器（局域网 IP、线上域名）在登录页「服务器设置」里改，会存到本机。
     */
    const val DEFAULT_BASE_URL = "http://127.0.0.1:8080/"

    @Volatile
    var baseUrl: String = DEFAULT_BASE_URL
        private set

    @Volatile
    var authToken: String? = null

    /** 给 Retrofit 和 SSE 共用；拦截器统一带上 Authorization。 */
    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val token = authToken
            val request = if (token != null) {
                chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            } else {
                chain.request()
            }
            chain.proceed(request)
        }
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        // SSE 是长连接，读写超时要放宽（心跳 20 秒一次）
        .readTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
        .build()

    @Volatile
    var api: ApiService = buildApi(DEFAULT_BASE_URL)
        private set

    /**
     * 保存新地址。返回 true 表示生效（地址没变也算生效），false 表示填的地址不合法——
     * 不合法时保持原地址不动，避免把 App 弄成连不上的状态。
     */
    fun setBaseUrl(raw: String): Boolean {
        val normalized = normalizeBaseUrl(raw) ?: return false
        // 地址没变就不用重建，避免打断正在进行的 SSE
        if (normalized == baseUrl) return true
        baseUrl = normalized
        api = buildApi(normalized)
        return true
    }

    /**
     * 用户可能只输入 `192.168.1.8:8080`、`192.168.1.8`、末尾带斜杠等各种写法，
     * 这里统一补成 `http://host:port/`。补不出主机的返回 null。
     */
    fun normalizeBaseUrl(raw: String): String? {
        var text = raw.trim()
        if (text.isEmpty()) return null
        if (!text.startsWith("http://", ignoreCase = true) && !text.startsWith("https://", ignoreCase = true)) {
            text = "http://$text"
        }
        if (!text.endsWith("/")) text = "$text/"
        val url = text.toHttpUrlOrNull() ?: return null
        if (url.host.isBlank()) return null
        return url.toString()
    }

    /** 用来在设置里给出即时反馈：能不能连由运行时决定，这里只校验形态。 */
    fun isValidBaseUrl(raw: String): Boolean = normalizeBaseUrl(raw) != null

    private fun buildApi(url: String): ApiService = Retrofit.Builder()
        .baseUrl(if (url.endsWith("/")) url else "$url/")
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ApiService::class.java)
}

/** 把异常转成用户可读的错误信息。 */
fun errorMessage(e: Throwable): String = when (e) {
    is HttpException -> {
        val body = try {
            e.response()?.errorBody()?.string()
        } catch (_: Exception) {
            null
        }
        val msg = body?.let {
            runCatching { Gson().fromJson(it, ErrorResponse::class.java).error }.getOrNull()
        }
        msg ?: "请求失败 (${e.code()})"
    }

    is java.net.UnknownHostException -> "找不到这个服务器地址，请检查「服务器设置」里填的对不对"

    is java.net.ConnectException -> "连不上服务器，请确认后端已启动、地址和端口正确"

    is java.net.SocketTimeoutException -> "服务器响应超时，请检查网络后重试"

    is IOException -> "无法连接服务器，请确认后端已启动"

    else -> e.message ?: "未知错误"
}
