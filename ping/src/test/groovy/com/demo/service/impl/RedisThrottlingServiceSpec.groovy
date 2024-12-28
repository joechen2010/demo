package com.demo.service.impl

import com.demo.BaseSpec
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import redis.embedded.RedisServer
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@SpringBootTest
class RedisThrottlingServiceSpec extends BaseSpec {

    @Autowired
    RedisThrottlingService throttlingService

    def setupSpec() {
        System.setProperty("ping.intervalInMillis", "1000")
        System.setProperty("throttle.type", "redis")
        System.setProperty("spring.redis.host", "localhost")

    }

    def "should throttled correctly"() {
        when:
        def numberOfThreads = 3
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads)
        CountDownLatch latch = new CountDownLatch(numberOfThreads)
        List<Boolean> responses = Collections.synchronizedList(new ArrayList<>())
        (0..<numberOfThreads).parallelStream().forEach({ i ->
            executorService.submit({
                boolean result = throttlingService.tryAcquire()
                latch.countDown()
                responses.add(result)
            })
        })
        latch.await()

        then:
        responses.count { it } == 2
        responses.count {! it } == 1


        cleanup:
        executorService.shutdown()
    }

}
