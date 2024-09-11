package io.github.w1th4d.jarplant.test.complex.subpackage;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.atomic.AtomicLong;

@SuppressWarnings("unused")
@RestController
public class SomeController {
    private static final String template = "Hello, %s!";
    private final AtomicLong counter = new AtomicLong();

    @GetMapping("/some")
    public SomeMessage some(@RequestParam(value = "name", defaultValue = "World") String name) {
        return new SomeMessage(counter.incrementAndGet(), String.format(template, name));
    }
}
