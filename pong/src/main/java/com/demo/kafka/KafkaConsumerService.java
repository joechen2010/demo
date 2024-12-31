package com.demo.kafka;

import com.demo.dao.MessageRepository;
import com.demo.model.Message;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Service
public class KafkaConsumerService {

    private static final Logger logger = LoggerFactory.getLogger(KafkaConsumerService.class);

    @Autowired
    private MessageRepository messageRepository;
    @KafkaListener(topics = "pong-message-topic", groupId = "pong-service")
    public void listen(ConsumerRecord<String, Message> record, Acknowledgment acknowledgment) {
        try {
            Message message = record.value();
            logger.debug("Kafak Consumer Received message: {}", message.getContent());
            messageRepository.save(message);
            acknowledgment.acknowledge();
        } catch (Exception e) {
            logger.error("Error processing message: " + e.getMessage());
        }
    }
}
