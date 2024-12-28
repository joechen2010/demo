package com.demo.service.impl;

import com.demo.service.ThrottlingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ThrottlingServiceFactory {

    @Value("${throttle.type:filelock}")
    private String throttleType;

    @Autowired
    @Qualifier("filelockThrottlingService")
    private ThrottlingService filelockThrottlingService;

    @Autowired
    @Qualifier("redisThrottlingService")
    private ThrottlingService redisThrottlingService;

    @Autowired
    @Qualifier("redissonThrottlingService")
    private ThrottlingService redissonThrottlingService;

    public ThrottlingService getThrottlingService() {
        if (throttleType.equals("filelock")) {
            return filelockThrottlingService;
        } else if (throttleType.equals("redis")) {
            return redisThrottlingService;
        } else if (throttleType.equals("redisson")) {
            return redissonThrottlingService;
        } else {
            throw new IllegalArgumentException("Invalid throttle type: " + throttleType);
        }
    }
}
