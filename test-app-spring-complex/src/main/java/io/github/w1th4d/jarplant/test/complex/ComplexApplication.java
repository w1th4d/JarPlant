package io.github.w1th4d.jarplant.test.complex;

import io.github.w1th4d.jarplant.test.complex.multipleconfigs.FirstConfiguration;
import io.github.w1th4d.jarplant.test.complex.multipleconfigs.SecondConfiguration;
import io.github.w1th4d.jarplant.test.complex.subpackage.SomeConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@EnableAutoConfiguration
// Skip @ComponentScan for this one. Import configs manually instead.
@Import({SomeConfiguration.class, FirstConfiguration.class, SecondConfiguration.class})
public class ComplexApplication {
    public static void main(String[] args) {
        SpringApplication.run(ComplexApplication.class, args);
    }
}
