package com.taskpulse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TaskPulseApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaskPulseApplication.class, args);
    }
}
