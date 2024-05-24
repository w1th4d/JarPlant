package com.example.complex.multipleconfigs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FirstConfiguration {
    @Bean
    public FirstController firstController() {
        return new FirstController();
    }
}
