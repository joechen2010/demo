package com.demo.controller


import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Import
import org.springframework.test.web.reactive.server.WebTestClient
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.function.Consumer

import static org.springframework.http.MediaType.TEXT_PLAIN
import static org.springframework.web.reactive.function.BodyInserters.fromValue

@WebFluxTest(controllers = [PongController])
class PongControllerSpec extends Specification {

    @Autowired
    WebTestClient webTestClient

    @Unroll
    def "should handle correctly (#input -> #expected, #status)"(String input, String expected, int status) {
        when:
        def response = webTestClient.post()
                .uri("/pong")
                .contentType(TEXT_PLAIN)
                .body(fromValue(input))
                .exchange()

        then:
        response.expectStatus().isEqualTo(status)
        response.expectBody(String.class).isEqualTo(expected)

        where:
        input      | expected | status
        "Hello"    | "World"  | 200
    }

    @Unroll
    def "should throttled correctly"() {
        when:
        def numberOfThreads = 3
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads)
        CountDownLatch latch = new CountDownLatch(numberOfThreads)
        List<Integer> responses = Collections.synchronizedList(new ArrayList<>())
        (0..<numberOfThreads).parallelStream().forEach({ i ->
            executorService.submit({
                try {
                    def response = webTestClient.post()
                            .uri("/pong")
                            .contentType(TEXT_PLAIN)
                            .body(fromValue("Hello"))
                            .exchange()
                    response.expectStatus().value({ integer -> responses.add(integer) })
                } finally {
                    latch.countDown();
                }
            })
        })
        latch.await()

        then:
        responses.count { it == 200 } >= 1
        responses.count { it == 429 } >= 1

        cleanup:
        executorService.shutdown()
    }

}
