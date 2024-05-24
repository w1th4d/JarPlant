package org.example.implants;

import org.springframework.context.annotation.Bean;

public class TestSpringConfigImplant {
    @SuppressWarnings("unused")
    @Bean
    public TestSpringBeanImplant getBeanImplant() {
        return new TestSpringBeanImplant();
    }
}
