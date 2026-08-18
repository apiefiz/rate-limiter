package com.gateway.core;

import com.gateway.config.GatewayConfig.CircuitBreakerConfig;
import java.util.concurrent.atomic.AtomicInteger;

public class CircuitBreaker {
    public enum State { CLOSED, OPEN, HALF_OPEN }
    
    private volatile State state = State.CLOSED;
    private final CircuitBreakerConfig config;
    
    private AtomicInteger failureCount = new AtomicInteger(0);
    private AtomicInteger requestCount = new AtomicInteger(0);
    
    private volatile long windowStartTime = System.currentTimeMillis();
    private volatile long lastOpenTime = 0;
    private AtomicInteger halfOpenTestCount = new AtomicInteger(0);

    public CircuitBreaker(CircuitBreakerConfig config) {
        this.config = config != null ? config : new CircuitBreakerConfig();
    }

    public synchronized boolean allowRequest() {
        if (!config.enabled) return true;

        long now = System.currentTimeMillis();
        
        if (state == State.OPEN) {
            if (now - lastOpenTime > config.recoveryTimeoutSeconds * 1000L) {
                state = State.HALF_OPEN;
                halfOpenTestCount.set(0);
                System.out.println("Circuit Breaker transitioned to HALF_OPEN");
                return true;
            }
            return false; // still OPEN
        }
        
        if (state == State.HALF_OPEN) {
            if (halfOpenTestCount.incrementAndGet() <= config.halfOpenTestRequests) {
                return true;
            }
            return false;
        }

        // CLOSED
        if (now - windowStartTime > config.windowSizeSeconds * 1000L) {
            resetMetrics(now);
        }
        return true;
    }

    public synchronized void recordSuccess() {
        if (!config.enabled) return;

        if (state == State.HALF_OPEN) {
            state = State.CLOSED;
            resetMetrics(System.currentTimeMillis());
            System.out.println("Circuit Breaker transitioned to CLOSED");
        } else if (state == State.CLOSED) {
            requestCount.incrementAndGet();
        }
    }

    public synchronized void recordFailure() {
        if (!config.enabled) return;

        if (state == State.HALF_OPEN) {
            state = State.OPEN;
            lastOpenTime = System.currentTimeMillis();
            System.out.println("Circuit Breaker transitioned to OPEN");
        } else if (state == State.CLOSED) {
            int total = requestCount.incrementAndGet();
            int failures = failureCount.incrementAndGet();
            
            if (total >= 3 && (double) failures / total >= config.failureThreshold) {
                state = State.OPEN;
                lastOpenTime = System.currentTimeMillis();
                System.out.println("Circuit Breaker transitioned to OPEN");
            }
        }
    }

    private void resetMetrics(long now) {
        failureCount.set(0);
        requestCount.set(0);
        windowStartTime = now;
    }
}
