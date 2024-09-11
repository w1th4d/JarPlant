package io.github.w1th4d.jarplant.test.complex.multipleconfigs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SecondConfiguration {
    @SuppressWarnings("unused")
    @Bean
    public SecondController secondController() {
        return new SecondController();
    }
}
