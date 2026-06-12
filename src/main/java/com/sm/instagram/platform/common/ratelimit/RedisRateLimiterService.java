package com.sm.instagram.platform.common.ratelimit;

import com.sm.instagram.platform.common.exceptions.NetworkTranslatableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Redis-based rate limiter with circuit breaker pattern and automatic fallback.
 * This is the ONLY Redis rate limiter implementation - merges all Redis functionality.
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "storage.mode", havingValue = "redis")
public class RedisRateLimiterService extends RateLimiterService {

    private final RedisTemplate<String, String> redisTemplate;
    private CircuitBreaker circuitBreaker;
    private final ConcurrentHashMap<String, InMemoryWindow> fallbackWindows = new ConcurrentHashMap<>();
    private final AtomicLong fallbackModeActivations = new AtomicLong(0);
    private Thread cleanupThread;
    
    @Value("${rate-limit.redis.circuit-breaker.enabled:true}")
    private boolean circuitBreakerEnabled;
    
    @Value("${rate-limit.redis.circuit-breaker.failure-threshold:5}")
    private int failureThreshold;
    
    @Value("${rate-limit.redis.circuit-breaker.timeout:30s}")
    private Duration circuitBreakerTimeout;
    
    @Value("${rate-limit.redis.circuit-breaker.fallback-to-memory:true}")
    private boolean fallbackToMemory;
    
    @Value("${rate-limit.redis.sliding-window.enabled:true}")
    private boolean slidingWindowEnabled;
    
    @Value("${rate-limit.redis.sliding-window.precision:10}")
    private int slidingWindowPrecision;

    @Value("${rate-limit.redis.key-prefix:rate_limit:}")
    private String keyPrefix;

    // Sliding window Lua script
    // NOTE: All time values use EPOCH SECONDS for consistency
    // - ZADD score: epoch seconds (when request occurred)
    // - ZREMRANGEBYSCORE: epoch seconds (remove entries older than window)
    // - ZCOUNT: epoch seconds (count entries within window)
    private static final String SLIDING_WINDOW_LUA_SCRIPT = """
        local key = KEYS[1]
        local limit = tonumber(ARGV[1])
        local window_size = tonumber(ARGV[2])
        local current_time = tonumber(ARGV[3])
        -- precision arg preserved for API compatibility but not used in cleanup

        -- Calculate oldest valid timestamp (epoch seconds, same unit as ZADD scores)
        local oldest_valid_time = current_time - window_size

        -- Clean up old entries using epoch seconds (matches ZADD score unit)
        redis.call('ZREMRANGEBYSCORE', key, '-inf', oldest_valid_time)

        -- Get current window count
        local current_count = redis.call('ZCOUNT', key, oldest_valid_time, current_time)

        if current_count < limit then
            -- Add current request (score = epoch seconds)
            redis.call('ZADD', key, current_time, current_time .. ':' .. math.random())
            redis.call('EXPIRE', key, window_size + 60)

            return {1, limit - current_count - 1, current_time + window_size}
        else
            -- Get oldest entry to calculate reset time
            local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
            local reset_time = current_time + window_size
            if #oldest > 0 then
                reset_time = tonumber(oldest[2]) + window_size
            end

            return {0, 0, reset_time}
        end
    """;

    // Fixed window Lua script - TOCTOU race condition fixed
    // INCREMENT FIRST, then check - prevents multiple requests from passing simultaneously
    private static final String FIXED_WINDOW_LUA_SCRIPT = """
        local key = KEYS[1]
        local limit = tonumber(ARGV[1])
        local duration = tonumber(ARGV[2])
        local current_time = tonumber(ARGV[3])

        -- ATOMIC: Increment first, then check - prevents race condition
        local new_count = redis.call('INCR', key)

        -- Set expiration if this is the first request in the window
        if new_count == 1 then
            redis.call('EXPIRE', key, duration)
        end

        local ttl = redis.call('TTL', key)
        if ttl == -1 then
            -- Key has no expiration (shouldn't happen but be defensive)
            redis.call('EXPIRE', key, duration)
            ttl = duration
        end

        -- Check AFTER increment to prevent TOCTOU race
        if new_count <= limit then
            return {1, limit - new_count, current_time + ttl}
        else
            -- Over limit - but we already incremented, which is fine for fixed window
            return {0, 0, current_time + ttl}
        end
    """;

    @SuppressWarnings("unchecked")
    private final RedisScript<List<Long>> slidingWindowScript = 
            RedisScript.of(SLIDING_WINDOW_LUA_SCRIPT, (Class<List<Long>>) (Class<?>) List.class);
    
    @SuppressWarnings("unchecked")
    private final RedisScript<List<Long>> fixedWindowScript = 
            RedisScript.of(FIXED_WINDOW_LUA_SCRIPT, (Class<List<Long>>) (Class<?>) List.class);

    public RedisRateLimiterService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void init() {
        log.info("Redis Rate Limiter Configuration:");
        log.info("  - Sliding window: {}", slidingWindowEnabled);
        log.info("  - Circuit breaker: {}", circuitBreakerEnabled);
        log.info("  - Fallback to memory: {}", fallbackToMemory);
        log.info("  - Key prefix: {}", keyPrefix);
        
        if (circuitBreakerEnabled) {
            CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                    .failureRateThreshold(50)
                    .waitDurationInOpenState(circuitBreakerTimeout)
                    .slidingWindowSize(10)
                    .minimumNumberOfCalls(failureThreshold)
                    .build();
            
            this.circuitBreaker = CircuitBreakerRegistry.of(config)
                    .circuitBreaker("redis-rate-limiter");
            
            circuitBreaker.getEventPublisher()
                    .onStateTransition(event -> {
                        log.warn("Redis circuit breaker state transition: {} -> {}", 
                                event.getStateTransition().getFromState(),
                                event.getStateTransition().getToState());
                        if (event.getStateTransition().getToState() == CircuitBreaker.State.OPEN) {
                            fallbackModeActivations.incrementAndGet();
                        }
                    });
        }
        
        if (fallbackToMemory) {
            startFallbackCleanupThread();
        }
    }

    @Override
    public RateLimitResult checkLimit(String key, int limit, int durationSeconds) {
        if (circuitBreakerEnabled && circuitBreaker != null) {
            try {
                return circuitBreaker.executeSupplier(() -> 
                        executeRedisCommand(key, limit, durationSeconds));
            } catch (Exception e) {
                log.warn("Redis rate limit check failed, using fallback", e);
                
                if (fallbackToMemory) {
                    return checkLimitInMemory(key, limit, durationSeconds);
                } else {
                    return new RateLimitResult(false, limit, 0, 
                            Instant.now().getEpochSecond() + durationSeconds);
                }
            }
        } else {
            return executeRedisCommand(key, limit, durationSeconds);
        }
    }

    @SuppressWarnings("unchecked")
    private RateLimitResult executeRedisCommand(String key, int limit, int durationSeconds) {
        try {
            long now = Instant.now().getEpochSecond();
            String redisKey = keyPrefix + key;
            
            List<Long> result;
            if (slidingWindowEnabled) {
                result = redisTemplate.execute(
                        slidingWindowScript,
                        Collections.singletonList(redisKey),
                        String.valueOf(limit),
                        String.valueOf(durationSeconds),
                        String.valueOf(now),
                        String.valueOf(slidingWindowPrecision)
                );
            } else {
                result = redisTemplate.execute(
                        fixedWindowScript,
                        Collections.singletonList(redisKey),
                        String.valueOf(limit),
                        String.valueOf(durationSeconds),
                        String.valueOf(now)
                );
            }

            if (result == null || result.size() != 3) {
                throw new NetworkTranslatableException("error.network.external_service", "Redis");
            }
            
            boolean allowed = result.get(0) == 1;
            int remaining = result.get(1).intValue();
            long resetTime = result.get(2);

            log.debug("Redis rate limit check - Allowed: {}, Remaining: {}", 
                    allowed, remaining);  // No PII in logs

            return new RateLimitResult(allowed, limit, remaining, resetTime);
            
        } catch (Exception e) {
            log.error("Redis command execution failed", e);  // No PII in logs
            throw new NetworkTranslatableException("error.network.external_service", "Redis");
        }
    }

    private RateLimitResult checkLimitInMemory(String key, int limit, int durationSeconds) {
        log.debug("Using in-memory fallback for rate limiting");  // No PII in logs
        
        long now = Instant.now().toEpochMilli();
        InMemoryWindow window = fallbackWindows.compute(key, (k, existing) -> {
            if (existing == null || existing.isExpired(now)) {
                return new InMemoryWindow(now, durationSeconds * 1000L);
            }
            return existing;
        });

        synchronized (window) {
            window.cleanup(now);
            boolean allowed = window.tryConsume(now, limit);
            int remaining = Math.max(0, limit - window.getCount());
            long resetTime = (window.windowStart + window.windowDuration) / 1000;

            return new RateLimitResult(allowed, limit, remaining, resetTime);
        }
    }

    @Override
    public int cleanupExpiredEntries() {
        // Redis handles expiration automatically
        // Clean up in-memory fallback entries
        int cleaned = 0;
        long now = Instant.now().toEpochMilli();
        
        var iterator = fallbackWindows.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().isExpired(now)) {
                iterator.remove();
                cleaned++;
            }
        }
        
        return cleaned;
    }

    @Override
    public int getTrackedEntriesCount() {
        try {
            if (circuitBreaker != null && circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
                return fallbackWindows.size();
            }
            
            Integer totalCount = redisTemplate.execute((RedisCallback<Integer>) connection -> {
                int entryCount = 0;
                String pattern = keyPrefix + "*";
                
                try (var cursor = connection.keyCommands().scan(
                        org.springframework.data.redis.core.ScanOptions.scanOptions()
                                .match(pattern)
                                .count(1000)
                                .build())) {
                    
                    while (cursor.hasNext()) {
                        cursor.next();
                        entryCount++;
                    }
                }
                
                return entryCount;
            });
            
            return totalCount != null ? totalCount : 0;
        } catch (Exception e) {
            log.error("Failed to get tracked entries count", e);
            return fallbackWindows.size();
        }
    }

    private void startFallbackCleanupThread() {
        cleanupThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(60000);
                    cleanupExpiredEntries();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "rate-limit-fallback-cleanup");
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }
    
    @PreDestroy
    public void shutdown() {
        if (cleanupThread != null && cleanupThread.isAlive()) {
            log.info("Shutting down rate limit cleanup thread");
            cleanupThread.interrupt();
            try {
                cleanupThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static class InMemoryWindow {
        private final long windowStart;
        private final long windowDuration;
        private final List<Long> requests = new java.util.ArrayList<>();

        InMemoryWindow(long windowStart, long windowDuration) {
            this.windowStart = windowStart;
            this.windowDuration = windowDuration;
        }

        boolean tryConsume(long now, int limit) {
            cleanup(now);
            if (requests.size() < limit) {
                requests.add(now);
                return true;
            }
            return false;
        }

        void cleanup(long now) {
            long windowStartTime = now - windowDuration;
            requests.removeIf(timestamp -> timestamp < windowStartTime);
        }

        int getCount() {
            return requests.size();
        }

        boolean isExpired(long now) {
            return requests.isEmpty() && 
                   (now - windowStart > windowDuration * 2);
        }
    }
}
