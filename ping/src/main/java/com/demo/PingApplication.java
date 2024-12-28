package com.demo;

import com.demo.service.PingService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;

import java.util.UUID;

@SpringBootApplication
@ComponentScan(basePackages = {"com.demo"})
public class PingApplication {

    public static void main(String[] args) {
        String instanceId = UUID.randomUUID().toString();
        System.setProperty("INSTANCE_ID", instanceId);
        ApplicationContext ctx = SpringApplication.run(PingApplication.class, args);
        PingService pingService = ctx.getBean(PingService.class);
        pingService.start().subscribe();
    }
}
