package com.taskpulse.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI taskPulseOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("TaskPulse — Distributed Task Scheduling Engine")
                        .description("REST API for distributed cron and delayed task scheduling, worker pool coordination, and fault-tolerant execution.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Shubham Raghawant")
                                .url("https://github.com/shubhamraghawant/taskpulse"))
                        .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")));
    }
}
