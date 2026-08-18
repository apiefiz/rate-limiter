# Lightweight API Gateway & Distributed Rate Limiter

A lightweight API Gateway built in Java that implements distributed rate limiting, circuit breaking, and reverse proxying capabilities. It is designed to be highly concurrent and demonstrates core distributed system primitives.

## Features

1. **Reverse Proxying**: Built on top of [Javalin](https://javalin.io/) (for high-performance routing) and `OkHttp` (for efficient upstream proxying).
2. **Distributed Rate Limiting**: Uses a **Token Bucket** algorithm backed by Redis and executed via Lua scripts for strict atomicity in a distributed environment.
3. **Circuit Breaking**: Implements a custom state machine (`CLOSED`, `OPEN`, `HALF_OPEN`) that protects upstream services from cascading failures by failing fast when error thresholds are exceeded.
4. **Declarative Configuration**: Driven entirely by `gateway-config.yml`.

## Prerequisites

- Java 17+
- Maven 3.8+
- Redis (running locally or remotely)

## Quick Start

1. **Start Redis**:
   ```bash
   docker run -p 6379:6379 -d redis
   ```

2. **Configure Routes**:
   Edit `src/main/resources/gateway-config.yml` to set up your downstream routes, rate limits, and circuit breaker thresholds.

3. **Run the Gateway**:
   ```bash
   mvn clean compile exec:java
   ```
   The gateway will start on the port specified in the config (default `8080`).

## Architecture & Code Structure

- `com.gateway.core.ProxyHandler`: Forwards incoming requests to the configured upstream targets.
- `com.gateway.middleware.RateLimitInterceptor`: A Javalin before-handler that intercepts requests and evaluates the token bucket Lua script against Redis.
- `com.gateway.core.CircuitBreaker`: The state machine tracking successes/failures and managing the `OPEN`/`HALF_OPEN`/`CLOSED` states per route.
- `src/main/resources/token_bucket.lua`: The atomic Token Bucket algorithm executed in Redis.
