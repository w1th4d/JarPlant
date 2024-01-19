package org.example.injector;

import javassist.bytecode.ClassFile;
import javassist.bytecode.MethodInfo;
import org.example.implants.Stub;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.example.injector.Helpers.readClassFile;

public class ImplantReader {
    public static ClassFile getStubImplant() throws IOException {
        try {
            return findAndReadClassFile(Stub.class);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Cannot find a class file for oneself! This program may be running through a very exotic ClassLoader.");
        }
    }

    // Finds the class file using some weird Java quirks
    public static ClassFile findAndReadClassFile(final Class<?> clazz) throws ClassNotFoundException, IOException {
        CodeSource codeSource = clazz.getProtectionDomain().getCodeSource();
        if (codeSource == null) {
            // Can't find oneself
            throw new ClassNotFoundException("Can't determine the path to the class file");
        }
        Path sourcePath = Path.of(codeSource.getLocation().getPath());

        return findAndReadClassFile(clazz, sourcePath);
    }

    // This method may be used when extracting the implant from another place than self
    public static ClassFile findAndReadClassFile(final Class<?> clazz, final Path path) throws ClassNotFoundException, IOException {
        if (Files.isDirectory(path)) {
            return findAndReadClassFileFromDirectory(clazz, path);
        } else {
            return findAndReadClassFileFromJar(clazz, path);
        }
    }

    private static ClassFile findAndReadClassFileFromDirectory(final Class<?> clazz, final Path directory) throws ClassNotFoundException, IOException {
        if (!Files.isDirectory(directory)) {
            throw new UnsupportedOperationException("Not a directory");
        }

        // Convert "org.example.injector.Inject" to "org/example/injector/Inject.class"
        String[] packageHierarchy = clazz.getName().split("\\.");
        packageHierarchy[packageHierarchy.length - 1] += ".class";
        Path sourcePath = Path.of(directory.toString(), packageHierarchy);

        if (!Files.exists(sourcePath)) {
            throw new ClassNotFoundException(sourcePath.toString());
        }

        return readClassFile(sourcePath);
    }

    private static ClassFile findAndReadClassFileFromJar(final Class<?> clazz, final Path jarFilePath) throws ClassNotFoundException, IOException {
        DataInputStream inputStream;
        try (JarFile jarFile = new JarFile(jarFilePath.toFile())) {
            String lookingForFileName = clazz.getName().replace(".", File.separator) + ".class";

            JarEntry classFileInJar = (JarEntry) jarFile.getEntry(lookingForFileName);
            if (classFileInJar == null) {
                throw new ClassNotFoundException(lookingForFileName);
            }

            inputStream = new DataInputStream(jarFile.getInputStream(classFileInJar));
            return new ClassFile(inputStream);
        }
    }

    public static MethodInfo readImplant(final Path classFilePath, final String sourceMethodName) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(classFilePath.toFile())))) {
            return readImplant(in, sourceMethodName);
        }
    }

    public static MethodInfo readImplant(final DataInputStream classFileInputStream, final String sourceMethodName) throws IOException {
        return readImplant(new ClassFile(classFileInputStream), sourceMethodName);
    }

    public static MethodInfo readImplant(final ClassFile sourceClass, final String sourceMethodName) throws IOException {
        MethodInfo implantMethod = sourceClass.getMethod(sourceMethodName);

        if (implantMethod == null) {
            throw new IOException("Cannot find method '" + sourceMethodName + "' in " + sourceClass.getName());
        }

        return implantMethod;
    }
}