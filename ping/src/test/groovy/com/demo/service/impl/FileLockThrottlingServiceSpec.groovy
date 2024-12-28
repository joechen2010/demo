package com.demo.service.impl


import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import spock.lang.Specification

@SpringBootTest
class FileLockThrottlingServiceSpec extends Specification {

    @Autowired
    FileLockThrottlingService fileLockThrottlingService

    def setup() {
        new File(fileLockThrottlingService.counterFilePath).delete()
        new File(fileLockThrottlingService.lockFilePath).delete()
    }

    def "should acquire lock when request count is below permits per second"() {
        given:
        fileLockThrottlingService.permitsPerSecond = 2

        when:
        boolean acquired = fileLockThrottlingService.tryAcquire()

        then:
        acquired == true
        fileLockThrottlingService.getRequestCount() == 1
    }

    def "should not acquire lock when request count is equal to permits per second"() {
        given:
        fileLockThrottlingService.permitsPerSecond = 1
        fileLockThrottlingService.tryAcquire()

        when:
        boolean acquired = fileLockThrottlingService.tryAcquire()

        then:
        acquired == false
        fileLockThrottlingService.getRequestCount() == 1
    }

    def "should reset request count every second"() {
        given:
        fileLockThrottlingService.permitsPerSecond = 1
        fileLockThrottlingService.tryAcquire()
        sleep(1100)

        when:
        boolean acquired = fileLockThrottlingService.tryAcquire()

        then:
        acquired == true
        fileLockThrottlingService.getRequestCount() == 1
    }

    def "should release lock successfully"() {
        given:
        fileLockThrottlingService.tryAcquire()

        when:
        fileLockThrottlingService.release()

        then:
        fileLockThrottlingService.fileLock == null
    }
}
