package com.eaglepoint.venue;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.eaglepoint.venue.mapper")
@EnableScheduling
public class VenuePlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(VenuePlatformApplication.class, args);
    }
}
