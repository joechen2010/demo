package com.demo.service.impl

import com.demo.service.ThrottlingService
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class ThrottlingServiceFactorySpec extends Specification {

    def throttlingServiceFactory = new ThrottlingServiceFactory()

    @Shared
    ThrottlingService mockFilelockThrottlingService = Mock(ThrottlingService)
    @Shared
    ThrottlingService mockRedisThrottlingService = Mock(ThrottlingService)
    @Shared
    ThrottlingService mockRedissonThrottlingService = Mock(ThrottlingService)

    def setup() {
        throttlingServiceFactory.filelockThrottlingService = mockFilelockThrottlingService
        throttlingServiceFactory.redisThrottlingService = mockRedisThrottlingService
        throttlingServiceFactory.redissonThrottlingService = mockRedissonThrottlingService
    }

    @Unroll
    def "should return #expectedService when throttle.type is #throttleType"() {
        given:
        throttlingServiceFactory.throttleType = throttleType

        when:
        def result = throttlingServiceFactory.getThrottlingService()

        then:
        result == expectedService

        where:
        throttleType | expectedService
        "filelock"   | mockFilelockThrottlingService
        "redis"      | mockRedisThrottlingService
        "redisson"   | mockRedissonThrottlingService
    }

    def "should throw IllegalArgumentException for invalid throttle type"() {
        given:
        throttlingServiceFactory.throttleType = "invalidType"

        when:
        throttlingServiceFactory.getThrottlingService()

        then:
        thrown(IllegalArgumentException)
    }
}
