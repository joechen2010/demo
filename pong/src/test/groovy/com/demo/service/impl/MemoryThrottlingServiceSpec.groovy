package com.demo.service.impl

import spock.lang.Specification

import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MemoryThrottlingServiceSpec extends Specification{

    MemoryThrottlingService throttlingService = new MemoryThrottlingService()

    def setup() {
        throttlingService.afterPropertiesSet()
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
        responses.count { it } == 1
        responses.count {! it } == 2


        cleanup:
        executorService.shutdown()
    }
}
