package com.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.InputStream;
import java.util.List;

public class GatewayConfig {
    public ServerConfig server;
    public List<RouteConfig> routes;

    public static class ServerConfig {
        public int port = 8080;
    }

    public static class RouteConfig {
        public String path;
        public String upstream;
        public RateLimitConfig rateLimit;
        public CircuitBreakerConfig circuitBreaker;
    }

    public static class RateLimitConfig {
        public boolean enabled = false;
        public int capacity = 100;
        public int refillTokens = 10;
        public int refillPeriodSeconds = 1;
    }

    public static class CircuitBreakerConfig {
        public boolean enabled = false;
        public double failureThreshold = 0.5;
        public int windowSizeSeconds = 10;
        public int halfOpenTestRequests = 2;
        public int recoveryTimeoutSeconds = 30;
    }

    public static GatewayConfig load(String resourcePath) throws Exception {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try (InputStream in = GatewayConfig.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalArgumentException("Config file not found: " + resourcePath);
            }
            return mapper.readValue(in, GatewayConfig.class);
        }
    }
}
