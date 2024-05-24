package com.example.complex.multipleconfigs;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.atomic.AtomicLong;

@RestController
public class FirstController {
    private static final String template = "Hello, %s!";
    private final AtomicLong counter = new AtomicLong();

    @GetMapping("/first")
    public FirstMessage first(@RequestParam(value = "name", defaultValue = "World") String name) {
        return new FirstMessage(counter.incrementAndGet(), String.format(template, name));
    }
}
