package org.example.implants;

import org.springframework.context.annotation.Bean;

@SuppressWarnings("unused")
public class TestSpringConfigImplant {
    @Bean
    public TestSpringBeanImplant getBeanImplant() {
        return new TestSpringBeanImplant();
    }
}
