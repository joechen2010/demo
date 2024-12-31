package com.demo.kafka;

import com.demo.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class KafkaProducerService {

    private static final Logger logger = LoggerFactory.getLogger(KafkaProducerService.class);

    @Autowired
    private KafkaTemplate<String, Message> kafkaTemplate;

    @Autowired
    private KafkaConfig kafkaConfig;

    public void sendMessage(Message message) {
        kafkaTemplate.send(kafkaConfig.getTopic(), message);
        logger.debug("Sent message: {}", message.getContent());
    }
}
