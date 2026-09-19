package com.sentinel.aml;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties
public class SentinelAmlApplication {

    public static void main(String[] args) {
        SpringApplication.run(SentinelAmlApplication.class, args);
    }
}
