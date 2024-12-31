package com.demo.kafka

import com.demo.KafkaTestSupportSpec
import com.demo.dao.MessageRepository
import com.demo.model.Message
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext

import static java.util.concurrent.TimeUnit.SECONDS
import static org.awaitility.Awaitility.await

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class KafkaSpec extends KafkaTestSupportSpec {

    @Autowired
    KafkaProducerService kafkaProducerService

    @Autowired
    MessageRepository messageRepository

    def setup() {
        messageRepository.deleteAll()
    }

    def "should send and receive message"() {
        given:
        def content = "Hello World-" + System.currentTimeMillis()
        def message = new Message(content, System.currentTimeMillis())

        when:
        kafkaProducerService.sendMessage( message)

        then:
        await().atMost(5, SECONDS).until {
            List<Message> messages = messageRepository.findAll()
            messages.stream().anyMatch { it.content == content }
        }
    }
}
