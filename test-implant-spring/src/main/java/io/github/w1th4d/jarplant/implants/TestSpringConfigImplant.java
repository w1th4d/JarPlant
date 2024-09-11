package io.github.w1th4d.jarplant.implants;

import org.springframework.context.annotation.Bean;

@SuppressWarnings("unused")
public class TestSpringConfigImplant {
    @Bean
    public TestSpringBeanImplant getBeanImplant() {
        return new TestSpringBeanImplant();
    }
}
