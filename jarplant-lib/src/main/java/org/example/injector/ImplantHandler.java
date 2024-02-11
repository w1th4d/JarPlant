package org.example.injector;

import javassist.bytecode.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ImplantHandler {
    private final byte[] classData;
    private final String implantClassName;
    private final Map<String, ConfDataType> availableConfig;
    private final Map<String, Object> configModifications;

    public ImplantHandler(byte[] classData, String implantClassName, Map<String, ConfDataType> availableConfig) {
        this.classData = classData;
        this.implantClassName = implantClassName;
        this.availableConfig = Collections.unmodifiableMap(availableConfig);
        this.configModifications = new HashMap<>();
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

        byte[] bytes = bufferFrom(sourcePath);
        Map<String, ConfDataType> availableConfig = readImplantConfig(readClassFile(bytes));
        return new ImplantHandler(bytes, className, availableConfig);
    }

    private static ImplantHandler findAndReadFromJar(final String className, final Path jarFilePath) throws ClassNotFoundException, IOException {
        try (JarFile jarFile = new JarFile(jarFilePath.toFile())) {
            String lookingForFileName = className.replace(".", File.separator) + ".class";

            JarEntry classFileInJar = (JarEntry) jarFile.getEntry(lookingForFileName);
            if (classFileInJar == null) {
                throw new ClassNotFoundException(lookingForFileName);
            }

            byte[] bytes;
            try (DataInputStream inputStream = new DataInputStream(jarFile.getInputStream(classFileInJar))) {
                bytes = bufferFrom(inputStream);
            }

            Map<String, ConfDataType> availableConfig = readImplantConfig(readClassFile(bytes));
            return new ImplantHandler(bytes, className, availableConfig);
        }
    }

    private static byte[] bufferFrom(Path classFilePath) throws IOException {
        return Files.readAllBytes(classFilePath);
    }

    private static byte[] bufferFrom(DataInputStream in) throws IOException {
        ByteArrayOutputStream allocator = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int bytesRead;
        do {
            bytesRead = in.read(chunk);
            allocator.write(chunk, 0, bytesRead);
        } while (bytesRead != -1);

        return allocator.toByteArray();
    }

    public String getImplantClassName() {
        return implantClassName;
    }

    public Map<String, ConfDataType> getAvailableConfig() {
        return availableConfig;     // Unmodifiable map
    }

    public Map<String, Object> getConfigModifications() {
        return configModifications; // Modifiable map
    }

    // Unfortunately, ClassFile is not Cloneable so a fresh instance needs to be read for every injection
    public ClassFile loadFreshConfiguredSpecimen() throws IOException {
        ClassFile instance = readClassFile(classData);
        overrideImplantConfig(instance, configModifications);
        return instance;
    }

    public ClassFile loadFreshRawSpecimen() throws IOException {
        return readClassFile(classData);
    }

    private static ClassFile readClassFile(byte[] classData) throws IOException {
        ClassFile instance;
        try (DataInputStream classDataInput = new DataInputStream(new ByteArrayInputStream(classData))) {
            instance = new ClassFile(classDataInput);
        }
        return instance;
    }

    private static Map<String, ConfDataType> readImplantConfig(ClassFile implantInstance) {
        Map<String, ConfDataType> configFields = new HashMap<>();

        for (FieldInfo field : implantInstance.getFields()) {
            if (!Helpers.isStaticFlagSet(field)) {
                continue;
            }
            if (!Helpers.isVolatileFlagSet(field)) {
                continue;
            }
            String fieldName = field.getName();
            if (!fieldName.startsWith("CONF_")) {
                continue;
            }

            String typeDescriptor = field.getDescriptor();
            configFields.put(fieldName, ConfDataType.valueOfDescriptor(typeDescriptor));
        }

        return configFields;
    }

    private static void overrideImplantConfig(ClassFile instance, Map<String, Object> newConfig) throws IOException {
        if (newConfig.isEmpty()) {
            return;
        }

        MethodInfo clinit = instance.getMethod(MethodInfo.nameClinit);
        if (clinit == null) {
            throw new RuntimeException("Expected there to be a <clinit>.");
        }

        byte[] code = clinit.getCodeAttribute().getCode();
        CodeIterator codeIterator = clinit.getCodeAttribute().iterator();
        int index = -1;
        while (codeIterator.hasNext()) {
            try {
                index = codeIterator.next();
            } catch (BadBytecode e) {
                throw new RuntimeException(e);
            }
        }

        if (index == -1) {
            throw new RuntimeException("No code in <clinit>.");
        }
        DataInput converter = new DataInputStream(new ByteArrayInputStream(code));
        converter.skipBytes(index);
        int opcode = converter.readUnsignedByte();
        if (opcode == Opcode.RETURN) {
            MethodInfo overrideConfigMethod = generateOverrideConfigMethod(instance, newConfig);

            Bytecode bytecode = new Bytecode(instance.getConstPool());
            bytecode.addInvokestatic(instance.getName(), overrideConfigMethod.getName(), overrideConfigMethod.getDescriptor());
            try {
                codeIterator.insertAt(index, bytecode.get());
            } catch (BadBytecode e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static MethodInfo generateOverrideConfigMethod(ClassFile instance, Map<String, Object> newConfig) {
        Bytecode bytecode = new Bytecode(instance.getConstPool());

        for (Map.Entry<String, Object> entry : newConfig.entrySet()) {
            if (entry.getValue() instanceof String strValue) {
                int constPoolIndex = instance.getConstPool().addStringInfo(strValue);
                bytecode.addLdc(constPoolIndex);
                bytecode.addPutstatic(instance.getName(), entry.getKey(), ConfDataType.STRING.descriptor);
            } else if (entry.getValue() instanceof Boolean) {
                boolean boolValue = (boolean) entry.getValue();
                if (boolValue) {
                    bytecode.addIconst(1);
                } else {
                    bytecode.addIconst(0);
                }
                bytecode.addPutstatic(instance.getName(), entry.getKey(), ConfDataType.BOOLEAN.descriptor);
            } else if (entry.getValue() instanceof Integer) {
                int intValue = (int) entry.getValue();
                int constPoolIndex = instance.getConstPool().addIntegerInfo(intValue);
                bytecode.addLdc(constPoolIndex);
                bytecode.addPutstatic(instance.getName(), entry.getKey(), ConfDataType.INT.descriptor);
            }
        }

        bytecode.addReturn(null);
        bytecode.setMaxLocals(newConfig.size());

        MethodInfo overrideConfigMethod = new MethodInfo(instance.getConstPool(), "overrideConfigValues", "()V");
        overrideConfigMethod.setCodeAttribute(bytecode.toCodeAttribute());
        Helpers.setStaticFlagForMethod(overrideConfigMethod);

        try {
            instance.addMethod(overrideConfigMethod);
        } catch (DuplicateMemberException e) {
            throw new RuntimeException("Class already infected?", e);
        }

        return overrideConfigMethod;
    }

    public enum ConfDataType {
        STRING("Ljava/lang/String;"),
        BOOLEAN("Z"),
        INT("I"),
        UNSUPPORTED("");

        public final String descriptor;

        ConfDataType(String descriptor) {
            this.descriptor = descriptor;
        }

        public static ConfDataType valueOfDescriptor(String descriptor) {
            for (ConfDataType type : values()) {
                if (type.descriptor.equals(descriptor)) {
                    return type;
                }
            }
            return UNSUPPORTED;
        }
    }
}
