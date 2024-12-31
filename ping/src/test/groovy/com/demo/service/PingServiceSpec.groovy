package com.demo.service

import com.demo.BaseSpec
import io.micrometer.core.instrument.MeterRegistry
import okhttp3.mockwebserver.MockResponse
import org.spockframework.spring.SpringBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.test.annotation.DirtiesContext
import reactor.core.Disposable
import reactor.test.StepVerifier
import spock.lang.Subject

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PingServiceSpec extends BaseSpec {

    @SpringBean(name = "filelockThrottlingService")
    ThrottlingService throttlingService = Mock()

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    @Subject
    PingService pingService

    def setupSpec() {
        System.setProperty("ping.intervalInMillis", "1000")
        System.setProperty("throttle.type", "filelock")
    }

    def setup() {
        pingService.setWebClient(webClient)
    }


    def "should send requests and handle successful responses"() {
        given:
        mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.OK.value()))

        1 * throttlingService.tryAcquire() >> true

        when:
        def result = pingService.start()

        then:
        StepVerifier.create(result.take(1))
                .expectNext(HttpStatus.OK.value())
                .verifyComplete()

        and:
        meterRegistry.counter("pong.200").count() == 1
    }

    def "should handle throttled responses from Pong"() {
        given:
        mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.TOO_MANY_REQUESTS.value()))

        1 * throttlingService.tryAcquire() >> true

        when:
        def result = pingService.start()

        then:
        StepVerifier.create(result.take(1))
                .expectNext(HttpStatus.TOO_MANY_REQUESTS.value())
                .verifyComplete()

        and:
        meterRegistry.counter("pong.429").count() == 1
    }

    def "should handle throttled requests in Ping"() {
        given:
        mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.OK.value()))

        _ * throttlingService.tryAcquire() >> false

        when:
        Disposable disposable = pingService.start().subscribe()
        Thread.sleep(1000)
        disposable.dispose();

        then:
        meterRegistry.counter("ping.429").count() == 1
    }

    def "should handle other error response from Pone"() {
        given:
        meterRegistry.clear()
        mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR.value()))
        _ * throttlingService.tryAcquire() >> true

        when:
        Disposable disposable = pingService.start().subscribe()
        Thread.sleep(1000)
        disposable.dispose();

        then:
        meterRegistry.counter("ping.429").count() == 0
        meterRegistry.counter("pong.200").count() == 0
        meterRegistry.counter("pong.429").count() == 0
    }
}
