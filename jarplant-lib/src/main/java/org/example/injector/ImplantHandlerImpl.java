package org.example.injector;

import javassist.bytecode.*;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import static org.example.injector.Helpers.*;

public class ImplantHandlerImpl implements ImplantHandler {
    private final byte[] classData;
    private final String implantClassName;
    private final Map<String, ConfDataType> availableConfig;
    private final Map<String, Object> configModifications;
    private final Map<String, byte[]> dependencies;

    ImplantHandlerImpl(byte[] classData, String implantClassName, Map<String, ConfDataType> availableConfig, Map<String, byte[]> dependencies) {
        this.classData = classData;
        this.implantClassName = implantClassName;
        this.availableConfig = Collections.unmodifiableMap(availableConfig);
        this.dependencies = dependencies;
        this.configModifications = new HashMap<>();
    }

    public static ImplantHandler createFor(Path classFilePath) throws IOException {
        byte[] bytes = bufferFrom(classFilePath);
        ClassFile classFile = readClassFile(bytes);
        String className = classFile.getName();
        Map<String, ConfDataType> availableConfig = readImplantConfig(classFile);
        return new ImplantHandlerImpl(bytes, className, availableConfig, Collections.emptyMap());
    }

    // Finds the class file using some weird Java quirks
    public static ImplantHandler findAndCreateFor(final Class<?> clazz, String... dependencyClasses) throws ClassNotFoundException, IOException {
        CodeSource codeSource = clazz.getProtectionDomain().getCodeSource();
        if (codeSource == null) {
            // Can't find oneself
            throw new ClassNotFoundException("Can't determine the path to the class file");
        }
        Path sourcePath = Path.of(codeSource.getLocation().getPath());

        return findAndCreateFor(sourcePath, clazz.getName(), dependencyClasses);
    }

    // This method may be used when extracting the implant from another place than self
    public static ImplantHandler findAndCreateFor(final Path path, final String className, String... dependencyClasses) throws ClassNotFoundException, IOException {
        if (Files.isDirectory(path)) {
            return findAndReadFromDirectory(path, className, dependencyClasses);
        } else {
            return findAndReadFromJar(path, className, dependencyClasses);
        }
    }

    private static ImplantHandler findAndReadFromDirectory(final Path directory, final String className, String... dependencyClasses) throws ClassNotFoundException, IOException {
        if (!Files.isDirectory(directory)) {
            throw new UnsupportedOperationException("Not a directory");
        }

        byte[] implantRawClassBytes = bufferFrom(calcSourcePath(directory, className));
        Map<String, ConfDataType> availableConfig = readImplantConfig(readClassFile(implantRawClassBytes));

        Map<String, byte[]> readDependencies = new HashMap<>(dependencyClasses.length);
        for (String dependency : dependencyClasses) {
            byte[] dependencyRawClassBytes = bufferFrom(calcSourcePath(directory, dependency));
            readDependencies.put(dependency, dependencyRawClassBytes);
        }

        return new ImplantHandlerImpl(implantRawClassBytes, className, availableConfig, readDependencies);
    }

    private static Path calcSourcePath(Path directory, String className) throws ClassNotFoundException {
        // Convert "org.example.injector.Inject" to "org/example/injector/Inject.class"
        // TODO Make sense of all the helper methods at this point
        String[] packageHierarchy = className.split("\\.");
        packageHierarchy[packageHierarchy.length - 1] += ".class";
        Path sourcePath = Path.of(directory.toString(), packageHierarchy);

        if (!Files.exists(sourcePath)) {
            throw new ClassNotFoundException(sourcePath.toString());
        }

        return sourcePath;
    }

    private static ImplantHandler findAndReadFromJar(final Path jarFilePath, final String className, String... dependencyClasses) throws ClassNotFoundException, IOException {
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

            Map<String, byte[]> readDependencies = new HashMap<>(dependencyClasses.length);
            for (String dependencyClass : dependencyClasses) {
                ZipEntry entry = jarFile.getEntry(convertToJarEntryPathName(dependencyClass));
                if (entry == null) {
                    // Just go on anyway?
                    continue;
                }

                byte[] dependencyRawClassBytes = jarFile.getInputStream(entry).readAllBytes();
                readDependencies.put(dependencyClass, dependencyRawClassBytes);
            }

            Map<String, ConfDataType> availableConfig = readImplantConfig(readClassFile(bytes));
            return new ImplantHandlerImpl(bytes, className, availableConfig, readDependencies);
        }
    }

    @Override
    public String getImplantClassName() {
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
                System.out.println("[ ] Wrote config override: " + confKey + "=" + strValue + " (String)");
            } else if (confValue instanceof Boolean boolValue) {
                if (boolValue) {
                    bytecode.addIconst(1);
                } else {
                    bytecode.addIconst(0);
                }
                bytecode.addPutstatic(forClass.getName(), entry.getKey(), ConfDataType.BOOLEAN.descriptor);
                System.out.println("[ ] Wrote config override: " + confKey + "=" + boolValue + " (Boolean)");
            } else if (confValue instanceof Integer intValue) {
                int constPoolIndex = bytecode.getConstPool().addIntegerInfo(intValue);
                bytecode.addLdc(constPoolIndex);
                bytecode.addPutstatic(forClass.getName(), confKey, ConfDataType.INT.descriptor);
                System.out.println("[ ] Wrote config override: " + confKey + "=" + intValue + " (Integer)");
            }
        }

        return bytecode;
    }

    public Map<String, byte[]> getDependencies() {
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
}
