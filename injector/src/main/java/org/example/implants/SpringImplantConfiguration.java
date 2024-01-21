package org.example.implants;

import org.springframework.context.annotation.Bean;

public class SpringImplantConfiguration {
    @Bean
    public SpringImplantController getImplantController() {
        return new SpringImplantController();
    }
}
