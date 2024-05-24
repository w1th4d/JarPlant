package com.example.complex.multipleconfigs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@SuppressWarnings("unused")
@Configuration
public class FirstConfiguration {
    @Bean
    public FirstController firstController() {
        return new FirstController();
    }
}
