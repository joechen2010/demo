package com.demo.controller;

import com.demo.kafka.KafkaProducerService;
import com.demo.model.Message;
import com.demo.service.ThrottlingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@RestController
public class PongController {

    private static final Logger logger = LoggerFactory.getLogger(PongController.class);
    public static final String RESPONSE = "World";

    @Autowired
    private ThrottlingService throttlingService;

    @Autowired
    private KafkaProducerService kafkaProducerService;

    @PostMapping(value = "/pong", consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public Mono<String> handle(@RequestBody String message) {

        if (!throttlingService.tryAcquire()) {
            logger.warn("Throttling request as too many requests in the same second");
            return Mono.error(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many requests in this second"));
        }
        logger.info("Received ping message: {}", message);
        kafkaProducerService.sendMessage(new Message(message, System.currentTimeMillis()));
        return Mono.just(RESPONSE);
    }
}