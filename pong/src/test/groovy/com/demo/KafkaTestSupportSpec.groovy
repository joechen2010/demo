package com.demo

import org.springframework.kafka.test.EmbeddedKafkaBroker
import spock.lang.Shared
import spock.lang.Specification

class KafkaTestSupportSpec extends Specification {

    @Shared
    EmbeddedKafkaBroker embeddedKafkaBroker = new EmbeddedKafkaBroker(1, true, "pong-message-topic")

    def setupSpec() {
        embeddedKafkaBroker.afterPropertiesSet()
        System.setProperty("kafka.bootstrap-servers", embeddedKafkaBroker.getBrokersAsString())
        System.setProperty("throttle.permitsPerSecond", "1")
    }

    def cleanupSpec() {
        embeddedKafkaBroker.destroy()
    }
}
