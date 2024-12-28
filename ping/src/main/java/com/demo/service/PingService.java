package com.demo.service;

import com.demo.service.impl.ThrottlingServiceFactory;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
public class PingService implements InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(PingService.class);

    @Value("${ping.intervalInMillis:1000}")
    private int pingIntervalInMillis = 1000;

    @Value("${pong.endpoint:http://localhost:8080/pong}")
    private String pongEndpoint = "http://localhost:8080/pong";

    @Autowired
    private ThrottlingServiceFactory throttlingServiceFactory;

    @Autowired
    private MeterRegistry meterRegistry;

    private WebClient webClient;

    private Counter successCounter;
    private Counter pondThrottledCounter;
    private Counter pingThrottledCounter;

    public Flux<Integer> start() {
        return Flux.interval(Duration.ofMillis(pingIntervalInMillis))
                .filter(it -> {
                    boolean result = throttlingServiceFactory.getThrottlingService().tryAcquire();
                    logger.debug("Request {} throttled result {} by ping service.", it, result);
                    return result;
                })
                .doOnDiscard(Long.class, it -> {
                    pingThrottledCounter.increment();
                    logger.info("Request {} not sent as being 'ping rate limited'", it);
                })
                .flatMap(it -> {
                            logger.debug("Request {} is going to send to Pong", it);
                            return webClient.post()
                                    .contentType(MediaType.TEXT_PLAIN)
                                    .bodyValue("Hello")
                                    .retrieve()
                                    .toBodilessEntity()
                                    .map(response -> {
                                        int statusCode = response.getStatusCode().value();
                                        logger.info("Request {} sent & Pong Respond: {}", it, statusCode);
                                        successCounter.increment();
                                        return statusCode;
                                    })
                                    .onErrorResume(e -> {
                                        if (e instanceof WebClientResponseException) {
                                            WebClientResponseException webClientResponseException = (WebClientResponseException) e;
                                            int statusCode = webClientResponseException.getStatusCode().value();
                                            if (statusCode == HttpStatus.TOO_MANY_REQUESTS.value()) {
                                                pondThrottledCounter.increment();
                                                logger.info("Request {} send & Pong throttled it {}", it, statusCode);
                                            }
                                            return Mono.just(statusCode);
                                        }
                                        logger.error("Request {} failed with unexpected error: {}", it, e.getMessage());
                                        return Mono.just(HttpStatus.INTERNAL_SERVER_ERROR.value());
                                    });
                        }
                )
                .doFinally(signalType -> {
                    throttlingServiceFactory.getThrottlingService().release();
                });
    }


    @Override
    public void afterPropertiesSet() throws Exception {
        setWebClient(WebClient.create(pongEndpoint));
        successCounter = meterRegistry.counter("pong.200");
        pondThrottledCounter = meterRegistry.counter("pong.429");;
        pingThrottledCounter = meterRegistry.counter("ping.429");;
    }

    public void setWebClient(WebClient webClient) {
        this.webClient = webClient;
    }
}
