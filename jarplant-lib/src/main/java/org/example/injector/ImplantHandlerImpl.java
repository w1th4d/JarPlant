package org.example.injector;

import javassist.bytecode.*;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;

import static org.example.injector.Helpers.bufferFrom;
import static org.example.injector.Helpers.searchForEndOfMethodIndex;

public class ImplantHandlerImpl implements ImplantHandler {
    private final static Logger log = Logger.getLogger("ImplantHandler");
    private final byte[] classData;
    private final ClassName implantClassName;
    private final Map<String, ConfDataType> availableConfig;
    private final Map<String, Object> configModifications;
    private final Map<ClassName, byte[]> dependencies;

    ImplantHandlerImpl(byte[] classData, ClassName implantClassName, Map<String, ConfDataType> availableConfig, Map<ClassName, byte[]> dependencies) {
        this.classData = classData;
        this.implantClassName = implantClassName;
        this.availableConfig = Collections.unmodifiableMap(availableConfig);
        this.dependencies = dependencies;
        this.configModifications = new HashMap<>();
    }

    public static ImplantHandler createFor(Path classFilePath) throws IOException {
        byte[] bytes = bufferFrom(classFilePath);
        ClassFile classFile = readClassFile(bytes);
        ClassName className = ClassName.of(classFile);
        Map<String, ConfDataType> availableConfig = readImplantConfig(classFile);
        return new ImplantHandlerImpl(bytes, className, availableConfig, Collections.emptyMap());
    }

    // Finds the class file using some weird Java quirks
    public static ImplantHandler findAndCreateFor(final Class<?> clazz) throws ClassNotFoundException, IOException {
        CodeSource codeSource = clazz.getProtectionDomain().getCodeSource();
        if (codeSource == null) {
            // Can't find oneself
            throw new ClassNotFoundException("Can't determine the path to the class file");
        }
        Path sourcePath = Path.of(codeSource.getLocation().getPath());

        return findAndCreateFor(sourcePath, ClassName.of(clazz));
    }

    // This method may be used when extracting the implant from another place than self
    public static ImplantHandler findAndCreateFor(final Path path, final ClassName className) throws ClassNotFoundException, IOException {
        if (Files.isDirectory(path)) {
            return findAndReadFromDirectory(path, className);
        } else {
            return findAndReadFromJar(path, className);
        }
    }

    private static ImplantHandler findAndReadFromDirectory(final Path directory, ClassName className) throws ClassNotFoundException, IOException {
        if (!Files.isDirectory(directory)) {
            throw new UnsupportedOperationException("Not a directory");
        }

        byte[] implantRawClassBytes = bufferFrom(calcSourcePath(directory, className));
        Map<String, ConfDataType> availableConfig = readImplantConfig(readClassFile(implantRawClassBytes));

        // Dig through the implant class and snatch its dependencies, too
        ThrowingFunction<ClassName, Optional<byte[]>, IOException> readEntryFromDirectory = (name) -> {
            Optional<byte[]> result = Optional.empty();
            try {
                byte[] dependencyRawClassBytes = bufferFrom(calcSourcePath(directory, name));
                result = Optional.of(dependencyRawClassBytes);
            } catch (ClassNotFoundException ignored) {
                // It's likely a provided dependency (like the standard library)
            }
            return result;
        };
        Map<ClassName, byte[]> readDependencies = readAllDependencies(className, readEntryFromDirectory);

        return new ImplantHandlerImpl(implantRawClassBytes, className, availableConfig, readDependencies);
    }

    private static Path calcSourcePath(Path directory, ClassName className) throws ClassNotFoundException {
        Path sourcePath = Path.of(directory.toString(), className.getClassFilePath());

        if (!Files.exists(sourcePath)) {
            throw new ClassNotFoundException(sourcePath.toString());
        }

        return sourcePath;
    }

    private static ImplantHandler findAndReadFromJar(final Path jarFilePath, ClassName className) throws ClassNotFoundException, IOException {
        try (JarFile jarFile = new JarFile(jarFilePath.toFile())) {
            String lookingForFileName = className.getClassFilePath();

            JarEntry classFileInJar = (JarEntry) jarFile.getEntry(lookingForFileName);
            if (classFileInJar == null) {
                throw new ClassNotFoundException(lookingForFileName);
            }

            byte[] bytes;
            try (DataInputStream inputStream = new DataInputStream(jarFile.getInputStream(classFileInJar))) {
                bytes = bufferFrom(inputStream);
            }

            // Dig through the implant class and snatch its dependencies, too
            ThrowingFunction<ClassName, Optional<byte[]>, IOException> readEntryFromJar = (name) -> {
                ZipEntry entry = jarFile.getEntry(name.getClassFilePath());
                if (entry == null) {
                    // This is probably a provided dependency (like the standard library)
                    return Optional.empty();
                }

                byte[] dependencyRawClassBytes = jarFile.getInputStream(entry).readAllBytes();
                return Optional.of(dependencyRawClassBytes);
            };
            Map<ClassName, byte[]> readDependencies = readAllDependencies(className, readEntryFromJar);

            Map<String, ConfDataType> availableConfig = readImplantConfig(readClassFile(bytes));
            return new ImplantHandlerImpl(bytes, className, availableConfig, readDependencies);
        }
    }

    @Override
    public ClassName getImplantClassName() {
        return implantClassName;
    }

    @Override
    public Map<String, ConfDataType> getAvailableConfig() {
        return availableConfig;     // Unmodifiable map
    }

    @Override
    public void setConfig(Map<String, Object> bulkConfigs) throws ImplantConfigException {
        for (Map.Entry<String, Object> entry : bulkConfigs.entrySet()) {
            setConfig(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void setConfig(String key, Object value) throws ImplantConfigException {
        if (!availableConfig.containsKey(key)) {
            throw new ImplantConfigException("Config property " + key + " is not declared in implant class.");
        }

        Class<?> providedValueClass = value.getClass();
        Class<?> expectedValueClass = availableConfig.get(key).type;
        if (providedValueClass == String.class && expectedValueClass == String.class) {
            configModifications.put(key, value);
        } else if (providedValueClass == String.class) {
            Object convertedValue = attemptStringToValueParsing(key, (String) value, expectedValueClass);
            configModifications.put(key, convertedValue);
        } else if (providedValueClass != expectedValueClass) {
            throw new ImplantConfigException("Wrong data type '" + providedValueClass + "' for config property " + key + " (" + expectedValueClass + ").");
        } else {
            configModifications.put(key, value);
        }
    }

    private static Object attemptStringToValueParsing(String key, String value, Class<?> expectedValueClass) throws ImplantConfigException {
        if (expectedValueClass == String.class) {
            return value;
        } else if (expectedValueClass == Boolean.class) {
            if (value.equalsIgnoreCase("true")) {
                return true;
            } else if (value.equalsIgnoreCase("false")) {
                return false;
            } else {
                throw new ImplantConfigException("Expected boolean (true/false) value for config property " + key + ".");
            }
        } else if (expectedValueClass == Integer.class) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw new ImplantConfigException("Expected integer value for config property '" + key + "'.");
            }
        } else {
            throw new ImplantConfigException("Cannot parse String value '" + value + "' to data type suitable for config property " + key + " (" + expectedValueClass + ").");
        }
    }

    // Unfortunately, ClassFile is not Cloneable so a fresh instance needs to be read for every injection
    @Override
    public ClassFile loadFreshConfiguredSpecimen() throws IOException {
        ClassFile instance = readClassFile(classData);
        overrideImplantConfig(instance, configModifications);
        return instance;
    }

    @Override
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

        CodeIterator codeIterator = clinit.getCodeAttribute().iterator();
        Optional<Integer> endOfClinit = searchForEndOfMethodIndex(clinit.getCodeAttribute(), codeIterator);
        if (endOfClinit.isEmpty()) {
            throw new RuntimeException("No code in <clinit>.");
        }

        try {
            Bytecode bytecode = generateConfigOverrideBytecode(instance, newConfig);
            codeIterator.insertAt(endOfClinit.get(), bytecode.get());
        } catch (BadBytecode e) {
            throw new RuntimeException(e);
        }
    }

    private static Bytecode generateConfigOverrideBytecode(ClassFile forClass, Map<String, Object> newConfig) {
        Bytecode bytecode = new Bytecode(forClass.getConstPool());
        bytecode.setMaxLocals(newConfig.size());

        // For each new config, generate bytecode that sets the value of the corresponding class field
        for (Map.Entry<String, Object> entry : newConfig.entrySet()) {
            String confKey = entry.getKey();
            Object confValue = entry.getValue();

            if (confValue instanceof String strValue) {
                int constPoolIndex = bytecode.getConstPool().addStringInfo(strValue);
                bytecode.addLdc(constPoolIndex);
                bytecode.addPutstatic(forClass.getName(), confKey, ConfDataType.STRING.descriptor);
                log.fine("Wrote config override: " + confKey + "=" + strValue + " (String)");
            } else if (confValue instanceof Boolean boolValue) {
                if (boolValue) {
                    bytecode.addIconst(1);
                } else {
                    bytecode.addIconst(0);
                }
                bytecode.addPutstatic(forClass.getName(), entry.getKey(), ConfDataType.BOOLEAN.descriptor);
                log.fine("Wrote config override: " + confKey + "=" + boolValue + " (Boolean)");
            } else if (confValue instanceof Integer intValue) {
                int constPoolIndex = bytecode.getConstPool().addIntegerInfo(intValue);
                bytecode.addLdc(constPoolIndex);
                bytecode.addPutstatic(forClass.getName(), confKey, ConfDataType.INT.descriptor);
                log.fine("Wrote config override: " + confKey + "=" + intValue + " (Integer)");
            }
        }

        return bytecode;
    }

    /**
     * Recursively search for all dependencies that the specified class uses.
     * The class itself will not be considered a dependency.
     * Provided classes (like the ones from the standard library) will not be considered.
     *
     * @param className       Full class name of the root class to search within, like "com.example.MyClass"
     * @param classDataReader Function that takes a full class name and returns the raw byte data for that class
     * @return A map of paths and class data, like <code>com/example/MyDep.class -> {1,2,3,4}</code>
     * @throws IOException If the class data could not be parsed
     */
    private static Map<ClassName, byte[]> readAllDependencies(
            ClassName className,
            ThrowingFunction<ClassName, Optional<byte[]>, IOException> classDataReader
    ) throws IOException {
        Map<ClassName, byte[]> dependencies = new HashMap<>();
        readAllDependencies(className, classDataReader, dependencies);
        return dependencies;
    }

    // This is not meant to be used directly
    private static void readAllDependencies(
            ClassName className,
            ThrowingFunction<ClassName, Optional<byte[]>, IOException> classDataReader,
            Map<ClassName, byte[]> accumulator
    ) throws IOException {
        byte[] thisClassData = classDataReader.apply(className).orElseThrow();

        ClassFile thisClass = readClassFile(thisClassData);
        ClassName thisClassName = ClassName.of(thisClass);
        Set<String> classReferences = thisClass.getConstPool().getClassNames();
        for (String classReference : classReferences) {
            ClassName classReferenceName;
            try {
                classReferenceName = ClassName.fromClassFormatInternalName(classReference);
            } catch (ClassNameException e) {
                continue;
            }
            if (classReferenceName.equals(thisClassName)) {
                // Don't go recursing on ourselves again
                continue;
            }

            if (accumulator.containsKey(classReferenceName)) {
                // This dependency is already noted (don't get lost in circular dependencies)
                continue;
            }

            Optional<byte[]> classRawData = classDataReader.apply(classReferenceName);
            if (classRawData.isEmpty()) {
                continue;
            }

            accumulator.put(classReferenceName, classRawData.get());

            // Recursively go through the whole dependency tree
            readAllDependencies(classReferenceName, classDataReader, accumulator);
        }
    }

    @Override
    public Map<ClassName, byte[]> getDependencies() {
        return Collections.unmodifiableMap(dependencies);
    }

    public enum ConfDataType {
        STRING(String.class, "Ljava/lang/String;"),
        BOOLEAN(Boolean.class, "Z"),
        INT(Integer.class, "I"),
        UNSUPPORTED(Object.class, "");

        public final Class<?> type;
        public final String descriptor;

        ConfDataType(Class<?> type, String descriptor) {
            this.type = type;
            this.descriptor = descriptor;
        }

        public static ConfDataType valueOfType(Class<?> type) {
            for (ConfDataType element : values()) {
                if (element.type.equals(type)) {
                    return element;
                }
            }
            return UNSUPPORTED;
        }

        public static ConfDataType valueOfDescriptor(String descriptor) {
            for (ConfDataType element : values()) {
                if (element.descriptor.equals(descriptor)) {
                    return element;
                }
            }
            return UNSUPPORTED;
        }
    }

    @FunctionalInterface
    private interface ThrowingFunction<T, R, E extends Throwable> {
        R apply(T t) throws E;
    }
}
