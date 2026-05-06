package com.apk.claw.android.agent.langchain.http

import com.apk.claw.android.utils.LogRecorder
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import java.util.concurrent.TimeUnit

/**
 * 全局 OkHttp 拦截器 — 当 LogRecorder 录制中时，记录所有 HTTP 请求/响应
 */
class GlobalLogInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startMs = System.nanoTime()

        // 读取请求体
        val requestBodyStr = if (request.body() != null) {
            val buffer = Buffer()
            request.body()!!.writeTo(buffer)
            buffer.readUtf8()
        } else ""

        // 执行请求
        val response: Response
        try {
            response = chain.proceed(request)
        } catch (e: Exception) {
            if (LogRecorder.isRecording()) {
                LogRecorder.record(
                    request.url().toString(),
                    request.method(),
                    -1, 0,
                    requestBodyStr, "",
                    "", e.message
                )
            }
            throw e
        }

        val durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startMs)

        // 读取响应体
        val responseBodyStr: String
        val responseBody = response.body()
        if (responseBody != null) {
            val contentType = responseBody.contentType()
            responseBodyStr = responseBody.string()
            // 重新包装
            val newResponse = response.newBuilder()
                .body(ResponseBody.create(contentType, responseBodyStr))
                .build()

            if (LogRecorder.isRecording()) {
                LogRecorder.record(
                    request.url().toString(),
                    request.method(),
                    response.code(),
                    durationMs,
                    requestBodyStr,
                    responseBodyStr,
                    "", null
                )
            }
            return newResponse
        }

        if (LogRecorder.isRecording()) {
            LogRecorder.record(
                request.url().toString(),
                request.method(),
                response.code(),
                durationMs,
                requestBodyStr, "",
                "", null
            )
        }
        return response
    }
}
