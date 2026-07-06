package com.nplohs.market;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NplohsMarketApplication {
    public static void main(String[] args) {
        SpringApplication.run(NplohsMarketApplication.class, args);
    }
}
