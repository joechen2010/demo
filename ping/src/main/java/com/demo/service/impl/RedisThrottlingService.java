package com.demo.service.impl;

import com.demo.service.ThrottlingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component("redisThrottlingService")
public class RedisThrottlingService implements ThrottlingService {

    private static final Logger logger = LoggerFactory.getLogger(RedisThrottlingService.class);
    public static final String KEY = "PING-REDIS-THROTTLING";

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Value("${throttle.permitsPerSecond:2}")
    private int permitsPerSecond = 2;

    @Override
    public boolean tryAcquire() {
        if (redisTemplate == null) {
            logger.warn("Redis is not available, throttling is disabled");
            return true;
        }
        Long currentCount = redisTemplate.opsForValue().increment(KEY);
        if (currentCount == 1) {
            redisTemplate.expire(KEY, Duration.ofSeconds(1));
        }
        logger.debug("Rate limiter key: {}, current count: {}", KEY, currentCount);
        return currentCount <= permitsPerSecond;
    }
}
