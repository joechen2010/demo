package com.demo;

import com.demo.service.PingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.UUID;

@SpringBootApplication
@ComponentScan(basePackages = {"com.demo"})
@EnableScheduling
public class PingApplication {

    @Autowired
    private PingService pingService;

    public static void main(String[] args) {
        //SET INSTANCE_ID for logback then each instance can separate log by instanceId
        if (System.getProperty("INSTANCE_ID") == null){
            String instanceId = UUID.randomUUID().toString();
            System.setProperty("INSTANCE_ID", instanceId);
        }
        ApplicationContext ctx = SpringApplication.run(PingApplication.class, args);

        // in multiple instance, there always have the time gap between start up, so that the requests are not concurrent
        //PingService pingService = ctx.getBean(PingService.class);
        //pingService.start().subscribe();
    }

    @Scheduled(cron = "0 09 2 * * ?")
    public void startPing() {
        pingService.start().subscribe();
    }
}
