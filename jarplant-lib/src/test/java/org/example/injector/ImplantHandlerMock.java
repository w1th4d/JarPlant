package org.example.injector;

import javassist.bytecode.ClassFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

public class ImplantHandlerMock implements ImplantHandler {
    private final ClassFile specimen;

    ImplantHandlerMock(ClassFile specimen) {
        this.specimen = specimen;
    }

    public static ImplantHandler findAndCreateFor(final Class<?> clazz) throws ClassNotFoundException, IOException {
        CodeSource codeSource = clazz.getProtectionDomain().getCodeSource();
        if (codeSource == null) {
            // Can't find oneself
            throw new ClassNotFoundException("Can't determine the path to the class file");
        }
        Path sourcePath = Path.of(codeSource.getLocation().getPath());

        if (!Files.isDirectory(sourcePath)) {
            throw new RuntimeException("Expected a directory.");
        }

        // Convert "org.example.injector.Inject" to "org/example/injector/Inject.class"
        String[] packageHierarchy = clazz.getName().split("\\.");
        packageHierarchy[packageHierarchy.length - 1] += ".class";
        Path sourceFullPath = Path.of(sourcePath.toString(), packageHierarchy);

        if (!Files.exists(sourcePath)) {
            throw new ClassNotFoundException(sourcePath.toString());
        }

        ClassFile cf = new ClassFile(new DataInputStream(new FileInputStream(sourceFullPath.toFile())));
        return new ImplantHandlerMock(cf);
    }

    public static ImplantHandler findInJar(Path pathToJar, String className) throws IOException {
        Map<String, byte[]> foundMatchingEntries = new HashMap<>();

        JarInputStream in = new JarInputStream(new FileInputStream(pathToJar.toFile()));
        for (JarEntry entry = in.getNextJarEntry(); entry != null; entry = in.getNextJarEntry()) {
            if (entry.getName().endsWith(className) || entry.getName().endsWith(className + ".class")) {
                foundMatchingEntries.put(entry.getName(), in.readAllBytes());
            }
        }

        if (foundMatchingEntries.isEmpty()) {
            throw new FileNotFoundException("Did not find class '" + className + "' in JAR " + pathToJar);
        } else if (foundMatchingEntries.size() > 1) {
            throw new RuntimeException("Found more than one class matching '" + className + "' in JAR " + pathToJar);
        }

        byte[] classData = foundMatchingEntries.values().stream().findAny().orElseThrow();

        return new ImplantHandlerMock(new ClassFile(new DataInputStream(new ByteArrayInputStream(classData))));
    }

    @Override
    public String getImplantClassName() {
        return specimen.getName();
    }

    @Override
    public Map<String, ImplantHandlerImpl.ConfDataType> getAvailableConfig() {
        return Map.of();
    }

    @Override
    public void setConfig(Map<String, Object> bulkConfigs) throws ImplantConfigException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setConfig(String key, Object value) throws ImplantConfigException {
        throw new UnsupportedOperationException();
    }

    @Override
    public ClassFile loadFreshConfiguredSpecimen() throws IOException {
        // WARNING: Returning mutable reference
        return specimen;
    }

    @Override
    public ClassFile loadFreshRawSpecimen() throws IOException {
        // WARNING: Returning mutable reference
        return specimen;
    }
}
