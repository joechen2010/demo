package com.demo.service.impl

import com.demo.BaseSpec
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FileLockThrottlingServiceSpec extends BaseSpec {

    @Autowired
    FileLockThrottlingService fileLockThrottlingService

    RandomAccessFile lockFile

    def setup() {
        // reset request count
        fileLockThrottlingService.resetCounter();
        lockFile = new RandomAccessFile(fileLockThrottlingService.lockFilePath, "rw");
    }


    def "should acquire lock when request count is below permits per second"() {
        given:
        fileLockThrottlingService.permitsPerSecond = 2

        when:
        boolean acquired = fileLockThrottlingService.tryAcquire()

        then:
        acquired == true
        fileLockThrottlingService.getRequestCount(lockFile) == 1
    }

    def "should not acquire lock when request count is equal to permits per second"() {
        given:
        fileLockThrottlingService.permitsPerSecond = 1
        fileLockThrottlingService.tryAcquire()

        when:
        boolean acquired = fileLockThrottlingService.tryAcquire()

        then:
        acquired == false
        fileLockThrottlingService.getRequestCount(lockFile) == 1
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
        fileLockThrottlingService.getRequestCount(lockFile) == 1
    }

}
