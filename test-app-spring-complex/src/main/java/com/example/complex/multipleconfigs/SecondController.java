package com.example.complex.multipleconfigs;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.atomic.AtomicLong;

@SuppressWarnings("unused")
@RestController
public class SecondController {
    private static final String template = "Hello, %s!";
    private final AtomicLong counter = new AtomicLong();

    @GetMapping("/second")
    public SecondMessage second(@RequestParam(value = "name", defaultValue = "World") String name) {
        return new SecondMessage(counter.incrementAndGet(), String.format(template, name));
    }
}
