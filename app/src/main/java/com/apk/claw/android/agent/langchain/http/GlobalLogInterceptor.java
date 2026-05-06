package com.apk.claw.android.agent.langchain.http;

import com.apk.claw.android.utils.LogRecorder;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import java.util.concurrent.TimeUnit;

/**
 * 全局 OkHttp 拦截器 — 当 LogRecorder 录制中时，记录所有 HTTP 请求/响应
 */
public class GlobalLogInterceptor implements Interceptor {

    @Override
    public Response intercept(Chain chain) throws java.io.IOException {
        Request request = chain.request();
        long startMs = System.nanoTime();

        // 读取请求体
        String requestBodyStr = "";
        if (request.body() != null) {
            Buffer buffer = new Buffer();
            request.body().writeTo(buffer);
            requestBodyStr = buffer.readUtf8();
        }

        // 执行请求
        Response response;
        try {
            response = chain.proceed(request);
        } catch (java.io.IOException e) {
            if (LogRecorder.INSTANCE.isRecording()) {
                LogRecorder.INSTANCE.record(
                    request.url().toString(),
                    request.method(),
                    -1, 0,
                    requestBodyStr, "",
                    "", e.getMessage()
                );
            }
            throw e;
        }

        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startMs);

        // 读取响应体
        ResponseBody responseBody = response.body();
        if (responseBody != null) {
            MediaType contentType = responseBody.contentType();
            String responseBodyStr = responseBody.string();
            response = response.newBuilder()
                    .body(ResponseBody.create(contentType, responseBodyStr))
                    .build();

            if (LogRecorder.INSTANCE.isRecording()) {
                LogRecorder.INSTANCE.record(
                    request.url().toString(),
                    request.method(),
                    response.code(),
                    durationMs,
                    requestBodyStr,
                    responseBodyStr,
                    "", null
                );
            }
            return response;
        }

        if (LogRecorder.INSTANCE.isRecording()) {
            LogRecorder.INSTANCE.record(
                request.url().toString(),
                request.method(),
                response.code(),
                durationMs,
                requestBodyStr, "",
                "", null
            );
        }
        return response;
    }
}
