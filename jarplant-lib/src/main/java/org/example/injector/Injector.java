package org.example.injector;

import java.io.IOException;

public interface Injector {
    boolean inject(JarFiddler jar) throws IOException;
}
