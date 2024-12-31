package com.demo.controller

import com.demo.KafkaTestSupportSpec
import com.demo.dao.MessageRepository
import com.demo.model.Message
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.reactive.server.WebTestClient
import spock.lang.Unroll

import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

import static java.util.concurrent.TimeUnit.SECONDS
import static org.awaitility.Awaitility.await
import static org.springframework.http.MediaType.TEXT_PLAIN
import static org.springframework.web.reactive.function.BodyInserters.fromValue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(partitions = 1, topics = ["pong-message-topic"])
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PongControllerSpec extends KafkaTestSupportSpec {

    @LocalServerPort
    private int port

    @Autowired
    MessageRepository messageRepository

    WebTestClient webTestClient

    def setup() {
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:${port}")
                .build()
        messageRepository.deleteAll()
    }

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
        await().atMost(5, SECONDS).until {
            List<Message> messages = messageRepository.findAll()
            messages.size() == 1
            messages.first().content == input
        }

        where:
        input      | expected | status
        "Hello"    | "World"  | 200
    }

    @Unroll
    def "should throttled correctly"() {
        when:
        // we wait 1 second to make sure previous requests are processed
        Thread.sleep(1000)
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
        responses.count { it == 200 } == 1
        responses.count { it == 429 } == 2

        cleanup:
        executorService.shutdown()
    }
}
