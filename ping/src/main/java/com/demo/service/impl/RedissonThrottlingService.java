package com.demo.service.impl;

import com.demo.service.ThrottlingService;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component("redissonThrottlingService")
public class RedissonThrottlingService implements ThrottlingService, InitializingBean {

    public static final String KEY = "PING-REDISSON-THROTTLING";

    // By default, we don't have a redis server
    @Autowired(required = false)
    private RedissonClient redissonClient;

    @Value("${throttle.permitsPerSecond:2}")
    private int permitsPerSecond = 2;

    private RRateLimiter rateLimiter;

    @Override
    public void afterPropertiesSet() throws Exception {
        // if redis is available, create a rate limiter and set the rate
        if(redissonClient != null){
            rateLimiter = redissonClient.getRateLimiter(KEY);
            rateLimiter.trySetRate(RateType.OVERALL, permitsPerSecond, 1, RateIntervalUnit.SECONDS);
        }
    }

    @Override
    public boolean tryAcquire() {
        return rateLimiter != null
                && rateLimiter.tryAcquire(100, TimeUnit.MILLISECONDS);
    }
}
