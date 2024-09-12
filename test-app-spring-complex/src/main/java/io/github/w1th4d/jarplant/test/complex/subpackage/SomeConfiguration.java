package io.github.w1th4d.jarplant.test.complex.subpackage;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@SuppressWarnings("unused")
@Configuration
public class SomeConfiguration {
    @Bean
    public SomeController someController() {
        return new SomeController();
    }
}