package com.demo.ct

import com.demo.BaseSpec
import com.demo.service.PingService
import io.micrometer.core.instrument.MeterRegistry
import okhttp3.mockwebserver.MockResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.test.annotation.DirtiesContext
import reactor.core.Disposable
import spock.lang.Subject

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PingServiceCTRedisSpec extends BaseSpec {

    @Autowired
    MeterRegistry meterRegistry

    @Autowired
    @Subject
    PingService pingService

    def setupSpec() {
        System.setProperty("ping.intervalInMillis", "300")
        System.setProperty("throttle.type", "redis")
        System.setProperty("spring.redis.host", "localhost")
    }

    def setup() {
        pingService.setWebClient(webClient)
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