package com.aicontractmanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AIContractManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AIContractManagerApplication.class, args);
    }
}
