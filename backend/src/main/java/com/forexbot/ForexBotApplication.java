package com.forexbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ForexBotApplication {
    public static void main(String[] args) {
        SpringApplication.run(ForexBotApplication.class, args);
    }
}
