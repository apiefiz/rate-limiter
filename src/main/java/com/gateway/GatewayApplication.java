package com.gateway;

import com.gateway.config.GatewayConfig;
import com.gateway.core.CircuitBreaker;
import com.gateway.core.ProxyHandler;
import com.gateway.core.RedisManager;
import com.gateway.middleware.RateLimitInterceptor;
import io.javalin.Javalin;
import okhttp3.OkHttpClient;
import java.util.concurrent.TimeUnit;

public class GatewayApplication {
    public static void main(String[] args) throws Exception {
        GatewayConfig config = GatewayConfig.load("/gateway-config.yml");
        RedisManager.init();

        OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

        Javalin app = Javalin.create(javalinConfig -> {
            javalinConfig.showJavalinBanner = false;
            javalinConfig.router.ignoreTrailingSlashes = true;
        }).start(config.server.port);

        RateLimitInterceptor rateLimiter = new RateLimitInterceptor();

        for (GatewayConfig.RouteConfig route : config.routes) {
            CircuitBreaker cb = new CircuitBreaker(route.circuitBreaker);
            ProxyHandler proxyHandler = new ProxyHandler(httpClient, route.upstream, route.path, cb);
            
            // Register rate limiting middleware
            app.before(route.path, ctx -> {
                ctx.attribute("routeConfig", route);
                rateLimiter.handle(ctx);
            });

            // Register proxy handlers
            app.get(route.path, proxyHandler);
            app.post(route.path, proxyHandler);
            app.put(route.path, proxyHandler);
            app.patch(route.path, proxyHandler);
            app.delete(route.path, proxyHandler);
            
            System.out.println("Registered route: " + route.path + " -> " + route.upstream);
        }
        
        System.out.println("Gateway started on port " + config.server.port);
    }
}
