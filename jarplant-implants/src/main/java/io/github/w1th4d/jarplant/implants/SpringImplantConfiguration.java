package io.github.w1th4d.jarplant.implants;

import org.springframework.context.annotation.Bean;

/**
 * Template bean for the SpringInjector.
 * <p>This class may be used if the target Spring app requires a bean to be explicitly defined in the Spring
 * configuration. That is, component scanning is not enabled. If needed, all @Bean-annotated methods in this class
 * will be copied to the target Spring configuration. No fields, subclasses or anything else will be copied.</p>
 */
public class SpringImplantConfiguration {
    /**
     * This method should return a reference to the Spring component of your implant.
     *
     * @return An instance of the component
     */
    @SuppressWarnings("unused")
    @Bean
    public SpringImplantController getImplantController() {
        return new SpringImplantController();
    }
}
