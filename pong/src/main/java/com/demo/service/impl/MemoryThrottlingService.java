package com.demo.service.impl;

import com.demo.service.ThrottlingService;
import com.google.common.util.concurrent.RateLimiter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class MemoryThrottlingService implements ThrottlingService, InitializingBean {

    @Value("${throttle.permitsPerSecond:1}")
    private int permitsPerSecond = 1;

    private RateLimiter rateLimiter;

    @Override
    public void afterPropertiesSet() throws Exception {
        rateLimiter = RateLimiter.create(permitsPerSecond);
    }

    @Override
    public boolean tryAcquire() {
        return rateLimiter.tryAcquire();
    }


}
