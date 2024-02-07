package org.example.implants;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spring component template for the SpringInjector.
 * <p>This is the main class that will be injected into a Spring target. The entire class will be renamed and implanted
 * into the target JAR. The SpringInjector will find a suitable place for it and - if necessary - an accompanying
 * Spring configuration template may be used. This is specified to the injector when used.</p>
 * <p>This class in specific demonstrates a REST controller and an HTTP(S) REST endpoint. Other Spring components
 * could be used, too.</p>
 */
@RestController
public class SpringImplantController {
    /**
     * Example of an HTTP(S) endpoint.
     * <p>Make sure that the mapping for this handler (the URL) is unique for the app (it will fail at startup
     * otherwise).</p>
     * <p>As of now, returning an object of any custom class is not supported. Return a String.</p>
     *
     * @return The response body
     */
    @SuppressWarnings("unused")
    @GetMapping("/implant")
    public String implant() {
        return "This is the implant!\n";
    }
}
