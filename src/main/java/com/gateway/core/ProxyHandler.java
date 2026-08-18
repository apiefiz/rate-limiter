package com.gateway.core;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import okhttp3.*;
import java.io.IOException;
import java.io.InputStream;
import com.gateway.core.CircuitBreaker;

public class ProxyHandler implements Handler {
    private final OkHttpClient httpClient;
    private final String upstreamBaseUrl;
    private final String routePath;
    private final CircuitBreaker circuitBreaker;

    public ProxyHandler(OkHttpClient httpClient, String upstreamBaseUrl, String routePath, CircuitBreaker cb) {
        this.httpClient = httpClient;
        this.upstreamBaseUrl = upstreamBaseUrl.endsWith("/") ? upstreamBaseUrl.substring(0, upstreamBaseUrl.length() - 1) : upstreamBaseUrl;
        this.routePath = routePath;
        this.circuitBreaker = cb;
    }

    @Override
    public void handle(Context ctx) throws Exception {
        String requestPath = ctx.path();
        String queryString = ctx.queryString();
        
        String prefix = routePath.replace("/*", "");
        String subPath = "";
        if (requestPath.startsWith(prefix)) {
            subPath = requestPath.substring(prefix.length());
        }
        
        String targetUrl = upstreamBaseUrl + subPath + (queryString != null ? "?" + queryString : "");

        RequestBody body = null;
        if (ctx.method().name().equals("POST") || ctx.method().name().equals("PUT") || ctx.method().name().equals("PATCH")) {
            byte[] bytes = ctx.bodyAsBytes();
            if (bytes.length > 0) {
                body = RequestBody.create(bytes, MediaType.parse(ctx.contentType() != null ? ctx.contentType() : "application/octet-stream"));
            } else {
                body = RequestBody.create(new byte[0], null);
            }
        }

        Request.Builder requestBuilder = new Request.Builder()
                .url(targetUrl)
                .method(ctx.method().name(), body);

        ctx.headerMap().forEach((k, v) -> {
            if (!k.equalsIgnoreCase("host") && !k.equalsIgnoreCase("connection") && !k.equalsIgnoreCase("content-length")) {
                requestBuilder.addHeader(k, v);
            }
        });

        if (!circuitBreaker.allowRequest()) {
            ctx.status(503).result("Service Unavailable (Circuit Breaker OPEN)");
            return;
        }

        try (Response upstreamResponse = httpClient.newCall(requestBuilder.build()).execute()) {
            int code = upstreamResponse.code();
            if (code >= 500) {
                circuitBreaker.recordFailure();
            } else {
                circuitBreaker.recordSuccess();
            }
            
            ctx.status(code);
            upstreamResponse.headers().forEach(pair -> ctx.header(pair.getFirst(), pair.getSecond()));
            
            ResponseBody responseBody = upstreamResponse.body();
            if (responseBody != null) {
                InputStream inputStream = responseBody.byteStream();
                ctx.result(inputStream);
            }
        } catch (IOException e) {
            circuitBreaker.recordFailure();
            ctx.status(502).result("Bad Gateway: Upstream connection failed");
        }
    }
}
