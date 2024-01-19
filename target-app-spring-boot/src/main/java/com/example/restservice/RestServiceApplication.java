package com.example.restservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

// The @SpringBootApplication annotation is a shorthand for @Configuration, @EnableAutoConfiguration and @ComponentScan
// If used, this will show up as its own annotation in the bytecode instead of these three
@Configuration
@EnableAutoConfiguration
@ComponentScan      // This annotation is responsible for searching the package for any @RestController annotations
public class RestServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(RestServiceApplication.class, args);
    }
}
