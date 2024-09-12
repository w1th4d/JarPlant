package io.github.w1th4d.jarplant.test.simple;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// The @SpringBootApplication annotation is a shorthand for @Configuration, @EnableAutoConfiguration and @ComponentScan
// If used, this will show up as its own annotation in the bytecode instead of these three
@SpringBootApplication
public class SimpleApplication {
    public static void main(String[] args) {
        SpringApplication.run(SimpleApplication.class, args);
    }
}
