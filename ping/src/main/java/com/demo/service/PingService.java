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

import static com.demo.PingApplication.INSTANCE_ID;

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
                .filter(n -> {
                    boolean result = throttlingServiceFactory.getThrottlingService().tryAcquire();
                    logger.debug("Request {} throttled result {} by ping service.", n, result);
                    return result;
                })
                .doOnDiscard(Long.class, n -> {
                    pingThrottledCounter.increment();
                    logger.info("Request {} not sent as being 'ping rate limited'", n);
                })
                .flatMap(n -> {
                            logger.debug("Request {} is going to send to Pong", n);
                            return webClient.post()
                                    .uri(uriBuilder -> uriBuilder
                                            .queryParam("instance", System.getProperty(INSTANCE_ID))
                                            .build())
                                    .contentType(MediaType.TEXT_PLAIN)
                                    .bodyValue("Hello-" + System.currentTimeMillis())
                                    .retrieve()
                                    .bodyToMono(String.class)
                                    .map(responseBody -> {
                                        logger.info("Request {} sent & Pong Respond: [{}] with status code {}", n, responseBody, HttpStatus.OK.value());
                                        successCounter.increment();
                                        return HttpStatus.OK.value();
                                    })
                                    .onErrorResume(e -> {
                                        if (e instanceof WebClientResponseException) {
                                            WebClientResponseException webClientResponseException = (WebClientResponseException) e;
                                            int statusCode = webClientResponseException.getStatusCode().value();
                                            if (statusCode == HttpStatus.TOO_MANY_REQUESTS.value()) {
                                                pondThrottledCounter.increment();
                                                logger.info("Request {} send & Pong throttled it {}", n, statusCode);
                                            }
                                            return Mono.just(statusCode);
                                        }
                                        logger.error("Request {} failed with unexpected error: {}", n, e.getMessage());
                                        return Mono.just(HttpStatus.INTERNAL_SERVER_ERROR.value());
                                    });
                        }
                );
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
