package org.example.implants;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SpringImplantController {
    @GetMapping("/implant")
    public String implant() {
        return "This is the implant!\n";
    }
}