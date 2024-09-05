package org.example.injector;

import javassist.bytecode.ClassFile;
import org.example.TestHelpers;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

/**
 * Mock for the ImplantHandler.
 * This mock can be used to hold a ClassFile instance that corresponds to a test implant. It greatly reduces the
 * functionality of a proper ImplantHandler (like handling of config) and can thus only be used for forwarding a
 * specified implant specimen as-is.
 */
public class ImplantHandlerMock implements ImplantHandler {
    private final ClassFile specimen;

    ImplantHandlerMock(ClassFile specimen) {
        this.specimen = specimen;
    }

    public static ImplantHandler findAndCreateFor(Class<?> clazz) throws ClassNotFoundException, IOException {
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

    /**
     * Open a JAR and read a specified class within it.
     * Used to load a test implant from a properly Maven-baked JAR.
     *
     * @param pathToJar path to an existing JAR file
     * @param className name of the class to read
     * @return ImplantHandlerMock loaded with the specified implant class
     * @throws IOException if anything could not be read
     */
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
    public ClassName getImplantClassName() {
        return ClassName.of(specimen);
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
        return copy(specimen);
    }

    @Override
    public ClassFile loadFreshRawSpecimen() throws IOException {
        return copy(specimen);
    }

    @Override
    public Map<ClassName, byte[]> getDependencies() {
        return Map.of();
    }

    private static ClassFile copy(ClassFile original) throws IOException {
        byte[] bytecode = TestHelpers.asBytes(original);
        return new ClassFile(new DataInputStream(new ByteArrayInputStream(bytecode)));
    }
}
