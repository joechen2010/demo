package com.demo.service.impl

import com.demo.BaseSpec
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class RedissonThrottlingServiceSpec extends BaseSpec {

    @Autowired
    RedissonThrottlingService throttlingService

    def setupSpec() {
        System.setProperty("spring.redis.host", "localhost")
    }


    def "should throttled correctly"() {
        //TODO: Not work since embedded redis do not support lua script
    }

}
