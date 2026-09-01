package com.vesit.openattend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OpenAttendApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpenAttendApplication.class, args);
    }
}
