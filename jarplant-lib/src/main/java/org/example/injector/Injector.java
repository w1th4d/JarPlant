package org.example.injector;

import java.io.IOException;
import java.nio.file.Path;

public interface Injector {
    boolean inject(Path targetJarFilePath, Path outputJarFilePath) throws IOException;
}
