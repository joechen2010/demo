package com.demo.ct

import com.demo.BaseSpec
import com.demo.service.PingService
import com.demo.service.ThrottlingService
import com.demo.service.impl.FileLockThrottlingService
import io.micrometer.core.instrument.MeterRegistry
import okhttp3.mockwebserver.MockResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.test.annotation.DirtiesContext
import reactor.core.Disposable
import spock.lang.Subject

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PingServiceCTSpec extends BaseSpec {

    @Autowired
    MeterRegistry meterRegistry;

    @Autowired
    @Subject
    PingService pingService

    @Autowired
    @Qualifier("filelockThrottlingService")
    FileLockThrottlingService throttlingService;

    def setupSpec() {
        //we make 5 request in 1 second, because the file lock will reset the counter in 1 second
        System.setProperty("ping.intervalInMillis", "200")
        System.setProperty("throttle.type", "filelock")
    }

    def setup() {
        pingService.setWebClient(webClient)
        throttlingService.setRequestCount(0)
    }

    def "should send requests and handle successful responses"() {
        given:
        mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.OK.value()))
        mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.TOO_MANY_REQUESTS.value()))

        when:
        Disposable disposable = pingService.start().subscribe()
        Thread.sleep(1000)
        disposable.dispose();

        then:
        meterRegistry.counter("pong.200").count() >= 1
        meterRegistry.counter("ping.429").count() >= 1
        meterRegistry.counter("pong.429").count() >= 1
    }
}