package org.example.injector;

import javassist.bytecode.ClassFile;

import java.io.IOException;
import java.nio.file.Path;

import static org.example.injector.Helpers.readClassFile;

public class TargetAnalyzer {
    private final ClassFile target;

    TargetAnalyzer(final ClassFile target) {
        this.target = target;
    }

    public static TargetAnalyzer of(ClassFile loadedClass) {
        return new TargetAnalyzer(loadedClass);
    }

    public static TargetAnalyzer loadClassFile(final Path classFilePath) throws IOException {
        return new TargetAnalyzer(readClassFile(classFilePath));
    }

    public boolean isInfected() throws IOException {
        // TODO Figure out a more dynamic way of determining infection status (other than just this method name)
        return target.getMethod("implant") != null;
    }
}
