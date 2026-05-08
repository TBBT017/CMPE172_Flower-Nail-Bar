package com.flowernailbar.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

    /**
     * RestTemplate bean used by AppointmentService to call the mock
     * NotificationController (distribution boundary, M5).
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
