package com.demo


import okhttp3.mockwebserver.MockWebServer
import org.springframework.web.reactive.function.client.WebClient
import redis.embedded.RedisServer
import spock.lang.Shared
import spock.lang.Specification

class BaseSpec extends Specification {

    MockWebServer mockWebServer

    @Shared
    WebClient webClient

    @Shared
    RedisServer redisServer

    def setupSpec() {
        redisServer = new RedisServer(6379)
        redisServer.start()
        Thread.sleep(2000)
        System.setProperty("ping.intervalInMillis", "1000")
        System.setProperty("throttle.type", "filelock")
        System.setProperty("spring.redis.host", "")
        System.setProperty("spring.redis.port", String.valueOf(redisServer.ports().get(0)))
    }

    def setup(){
        mockWebServer = new MockWebServer()
        mockWebServer.start()
        webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .build()
    }

    def cleanup() {
        mockWebServer.shutdown()
        redisServer.stop();
    }
}
