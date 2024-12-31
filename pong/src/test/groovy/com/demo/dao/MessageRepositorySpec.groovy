package com.demo.dao

import com.demo.model.Message
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import spock.lang.Specification

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MessageRepositorySpec extends Specification {

    @Autowired
    MessageRepository messageRepository



    def "find all should return all messages"() {
        given:
        def message1 = new Message( "Message 1", System.currentTimeMillis())
        def message2 = new Message("Message 2", System.currentTimeMillis())
        messageRepository.save(message1)
        messageRepository.save(message2)

        when:
        def foundMessage = messageRepository.findById(message1.id)

        then:
        foundMessage.isPresent()
        foundMessage.get().getContent().equals("Message 1")

        when:
        def allMessages = messageRepository.findAll()

        then:
        allMessages.size() == 2
    }
}
