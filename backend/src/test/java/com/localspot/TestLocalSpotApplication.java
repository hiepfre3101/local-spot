package com.localspot;

import org.springframework.boot.SpringApplication;

public class TestLocalSpotApplication {

    public static void main(String[] args) {
        SpringApplication.from(LocalSpotApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
