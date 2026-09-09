package com.fbscraper;

import com.fbscraper.config.AppConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Spring Boot entry point for the Facebook monitoring dashboard application.
 */
@SpringBootApplication
@EnableConfigurationProperties(AppConfig.class)
public class App {

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }
}
