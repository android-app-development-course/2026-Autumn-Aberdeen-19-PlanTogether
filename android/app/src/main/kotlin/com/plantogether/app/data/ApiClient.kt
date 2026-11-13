package com.plantogether.app.data

import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException

object ApiClient {

    // 通过 adb reverse tcp:8080 tcp:8080 将设备 127.0.0.1:8080 转发到宿主机后端
    const val BASE_URL = "http://127.0.0.1:8080/"

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

    val api: ApiService = Retrofit.Builder()
        .baseUrl(BASE_URL)
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

    is IOException -> "无法连接服务器，请确认后端已启动"

    else -> e.message ?: "未知错误"
}
