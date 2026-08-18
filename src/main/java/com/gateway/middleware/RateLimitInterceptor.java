package com.gateway.middleware;

import com.gateway.config.GatewayConfig.RouteConfig;
import com.gateway.core.RedisManager;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import redis.clients.jedis.Jedis;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class RateLimitInterceptor implements Handler {
    private final String luaScript;
    private String luaScriptSha;

    public RateLimitInterceptor() {
        try (InputStream in = getClass().getResourceAsStream("/token_bucket.lua")) {
            if (in == null) throw new RuntimeException("Lua script not found");
            this.luaScript = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            
            try (Jedis jedis = RedisManager.getPool().getResource()) {
                this.luaScriptSha = jedis.scriptLoad(luaScript);
            } catch (Exception e) {
                System.err.println("Could not connect to Redis to load Lua script: " + e.getMessage());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize RateLimitInterceptor", e);
        }
    }

    @Override
    public void handle(Context ctx) throws Exception {
        RouteConfig routeConfig = ctx.attribute("routeConfig");
        
        if (routeConfig != null && routeConfig.rateLimit != null && routeConfig.rateLimit.enabled) {
            String clientIp = ctx.ip();
            String redisKey = "rl:" + routeConfig.path + ":" + clientIp;
            
            long now = System.currentTimeMillis() / 1000;
            
            try (Jedis jedis = RedisManager.getPool().getResource()) {
                if (this.luaScriptSha == null || !jedis.scriptExists(this.luaScriptSha)) {
                     this.luaScriptSha = jedis.scriptLoad(luaScript);
                }

                Object result = jedis.evalsha(
                    this.luaScriptSha,
                    1,
                    redisKey,
                    String.valueOf(routeConfig.rateLimit.capacity),
                    String.valueOf(routeConfig.rateLimit.refillTokens),
                    String.valueOf(routeConfig.rateLimit.refillPeriodSeconds),
                    String.valueOf(now)
                );

                if ((Long) result == 0) {
                    ctx.status(429).result("Too Many Requests");
                    ctx.skipRemainingHandlers();
                }
            } catch (Exception e) {
                System.err.println("Redis error during rate limiting: " + e.getMessage());
                // Fail-open strategy
            }
        }
    }
}
