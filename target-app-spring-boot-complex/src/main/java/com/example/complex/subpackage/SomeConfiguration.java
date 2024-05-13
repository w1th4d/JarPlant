package com.example.complex.subpackage;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SomeConfiguration {
    @Bean
    public SomeController someController() {
        return new SomeController();
    }
}
