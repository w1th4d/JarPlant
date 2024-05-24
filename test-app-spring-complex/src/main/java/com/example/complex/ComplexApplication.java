package com.example.complex;

import com.example.complex.multipleconfigs.FirstConfiguration;
import com.example.complex.multipleconfigs.SecondConfiguration;
import com.example.complex.subpackage.SomeConfiguration;
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
