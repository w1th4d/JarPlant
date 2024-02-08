package org.example.injector;

import javassist.bytecode.ClassFile;
import javassist.bytecode.FieldInfo;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ImplantHandler {
    private final ByteBuffer classData;
    private final String implantClassName;

    public ImplantHandler(ByteBuffer classData, String implantClassName) {
        this.classData = classData;
        this.implantClassName = implantClassName;
    }

    // Finds the class file using some weird Java quirks
    public static ImplantHandler findAndCreateFor(final Class<?> clazz) throws ClassNotFoundException, IOException {
        CodeSource codeSource = clazz.getProtectionDomain().getCodeSource();
        if (codeSource == null) {
            // Can't find oneself
            throw new ClassNotFoundException("Can't determine the path to the class file");
        }
        Path sourcePath = Path.of(codeSource.getLocation().getPath());

        return findAndCreateFor(clazz.getName(), sourcePath);
    }

    // This method may be used when extracting the implant from another place than self
    public static ImplantHandler findAndCreateFor(final String className, final Path path) throws ClassNotFoundException, IOException {
        if (Files.isDirectory(path)) {
            return findAndReadFromDirectory(className, path);
        } else {
            return findAndReadFromJar(className, path);
        }
    }

    private static ImplantHandler findAndReadFromDirectory(final String className, final Path directory) throws ClassNotFoundException, IOException {
        if (!Files.isDirectory(directory)) {
            throw new UnsupportedOperationException("Not a directory");
        }

        // Convert "org.example.injector.Inject" to "org/example/injector/Inject.class"
        String[] packageHierarchy = className.split("\\.");
        packageHierarchy[packageHierarchy.length - 1] += ".class";
        Path sourcePath = Path.of(directory.toString(), packageHierarchy);

        if (!Files.exists(sourcePath)) {
            throw new ClassNotFoundException(sourcePath.toString());
        }

        ByteBuffer bytes = bufferFrom(sourcePath);
        return new ImplantHandler(bytes, className);
    }

    private static ImplantHandler findAndReadFromJar(final String className, final Path jarFilePath) throws ClassNotFoundException, IOException {
        try (JarFile jarFile = new JarFile(jarFilePath.toFile())) {
            String lookingForFileName = className.replace(".", File.separator) + ".class";

            JarEntry classFileInJar = (JarEntry) jarFile.getEntry(lookingForFileName);
            if (classFileInJar == null) {
                throw new ClassNotFoundException(lookingForFileName);
            }

            ByteBuffer bytes;
            try (DataInputStream inputStream = new DataInputStream(jarFile.getInputStream(classFileInJar))) {
                bytes = bufferFrom(inputStream);
            }

            return new ImplantHandler(bytes, className);
        }
    }

    private static ByteBuffer bufferFrom(Path classFilePath) throws IOException {
        byte[] bytes = Files.readAllBytes(classFilePath);
        return ByteBuffer.wrap(bytes);
    }

    private static ByteBuffer bufferFrom(DataInputStream in) throws IOException {
        ByteArrayOutputStream allocator = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int bytesRead;
        do {
            bytesRead = in.read(chunk);
            allocator.write(chunk, 0, bytesRead);
        } while (bytesRead != -1);

        return ByteBuffer.wrap(allocator.toByteArray());
    }

    public String getImplantClassName() {
        return implantClassName;
    }

    // Unfortunately, ClassFile is not Cloneable so a fresh instance needs to be read for every injection
    public ClassFile loadFreshSpecimen() throws IOException {
        ClassFile instance;
        try (DataInputStream classDataInput = new DataInputStream(new ByteArrayInputStream(classData.array()))) {
            instance = new ClassFile(classDataInput);
        }
        return instance;
    }

    public Map<String, Object> readImplantConfig() throws IOException {
        ClassFile implantInstance = loadFreshSpecimen();
        Map<String, Object> configFields = new HashMap<>();

        for (FieldInfo field : implantInstance.getFields()) {
            if (!Helpers.isStaticFlagSet(field)) {
                continue;
            }
            if (!Helpers.isFinalFlagSet(field)) {
                continue;
            }
            String fieldName = field.getName();
            if (!fieldName.startsWith("CONF_")) {
                continue;
            }

            int valueConstPoolIndex = field.getConstantValue();
            if (valueConstPoolIndex == 0) {
                throw new RuntimeException("The static final field '" + fieldName + "' has no value.");
            }

            Object fieldValue = switch (field.getDescriptor()) {
                case "Ljava/lang/String;" -> field.getConstPool().getStringInfo(valueConstPoolIndex);
                case "Z" -> field.getConstPool().getIntegerInfo(valueConstPoolIndex);   // Booleans are Integers
                case "I" -> field.getConstPool().getIntegerInfo(valueConstPoolIndex);   // Actual Integer
                default -> null;
            };

            configFields.put(fieldName, fieldValue);
        }

        return configFields;
    }
}
