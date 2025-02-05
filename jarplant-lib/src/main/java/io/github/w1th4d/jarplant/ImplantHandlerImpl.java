package io.github.w1th4d.jarplant;

import javassist.bytecode.*;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.*;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;

import static io.github.w1th4d.jarplant.Helpers.searchForEndOfMethodIndex;

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
        byte[] bytes = Files.readAllBytes(classFilePath);
        ClassFile classFile = readClassFile(bytes);
        ClassName className = ClassName.of(classFile);
        Map<String, ConfDataType> availableConfig = readImplantConfig(classFile);
        return new ImplantHandlerImpl(bytes, className, availableConfig, Collections.emptyMap());
    }

    public static ImplantHandler createFromJar(Path jarFilePath, ClassName nameOfPayloadBearingClass) throws IOException, ClassNotFoundException, ImplantException {
        if (!looksLikeAJarFile(jarFilePath)) {
            throw new IOException("Does not looks like a JAR file: " + jarFilePath);
        }
        return findAndCreateFor(jarFilePath, nameOfPayloadBearingClass);
    }

    private static boolean looksLikeAJarFile(Path path) throws IOException {
        JarFile openAttempt = new JarFile(path.toFile());
        openAttempt.close();
        return true;
    }

    static Path getSourcePathFor(ClassName className) throws ClassNotFoundException, FileNotFoundException {
        String theFullName = className.getFullClassName();
        Class<?> classFromFullName = Class.forName(theFullName);
        return getSourcePathFor(classFromFullName);
    }

    static Path getSourcePathFor(Class<?> clazz) throws FileNotFoundException {
        CodeSource codeSource = clazz.getProtectionDomain().getCodeSource();
        if (codeSource == null) {
            // This class is likely a part of the SDK (or something else we can't find)
            throw new FileNotFoundException("Cannot find code source path for '" + clazz.getName() + "'.");
        }

        return Path.of(codeSource.getLocation().getPath());
    }

    private static ThrowingFunction<ClassName, Optional<byte[]>, IOException> getReaderFunctionFor(Path codePath) {
        ThrowingFunction<ClassName, Optional<byte[]>, IOException> ret;

        if (Files.isDirectory(codePath)) {
            // Lambda function for reading a class from this directory
            ret = (name) -> {
                Path sourcePath = Path.of(codePath.toString(), name.getClassFilePath());
                if (!Files.exists(sourcePath)) {
                    return Optional.empty();
                }

                return Optional.of(Files.readAllBytes(sourcePath));
            };
        } else {
            // Lambda function for reading a class from this JAR
            ret = (name) -> {
                byte[] entryBytes;

                try (JarFile jarFile = new JarFile(codePath.toFile())) {
                    ZipEntry entry = jarFile.getEntry(name.getClassFilePath());
                    if (entry == null) {
                        return Optional.empty();
                    }

                    entryBytes = jarFile.getInputStream(entry).readAllBytes();
                }

                return Optional.of(entryBytes);
            };
        }

        return ret;
    }

    public static ImplantHandler findAndCreateFor(Class<?> clazz) throws ClassNotFoundException, IOException, ImplantException {
        Path sourcePath = getSourcePathFor(clazz);

        return findAndCreateFor(sourcePath, ClassName.of(clazz));
    }

    public static ImplantHandler findAndCreateFor(Path path, ClassName className) throws ClassNotFoundException, IOException, ImplantException {
        ThrowingFunction<ClassName, Optional<byte[]>, IOException> classReaderFunction = getReaderFunctionFor(path);

        Optional<byte[]> rawClassDataMaybe = classReaderFunction.apply(className);
        if (rawClassDataMaybe.isEmpty()) {
            throw new ClassNotFoundException("Cannot figure out how to find class '" + className + "'.");
        }

        // Read its available config properties
        ClassFile sample = readClassFile(rawClassDataMaybe.get());
        Map<String, ConfDataType> availableConfig = readImplantConfig(sample);

        // Read its dependencies
        Map<ClassName, byte[]> dependencies = readAllDependencies(className, classReaderFunction);

        return new ImplantHandlerImpl(rawClassDataMaybe.get(), className, availableConfig, dependencies);
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
    public ClassFile loadFreshConfiguredSpecimen() {
        ClassFile instance = readClassFile(classData);
        overrideImplantConfig(instance, configModifications);
        return instance;
    }

    @Override
    public ClassFile loadFreshRawSpecimen() {
        return readClassFile(classData);
    }

    private static ClassFile readClassFile(byte[] classData) {
        ClassFile instance;
        try (DataInputStream classDataInput = new DataInputStream(new ByteArrayInputStream(classData))) {
            instance = new ClassFile(classDataInput);
        } catch (IOException e) {
            throw new RuntimeException("Cannot interpret class.", e);
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

    private static void overrideImplantConfig(ClassFile instance, Map<String, Object> newConfig) {
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
                // This dependency is already noted
                continue;
            }

            /*
             * First try to read the class from the code source path that's already given.
             * This works if the dependency is a class defined by the implant project itself.
             */
            Optional<byte[]> classRefDataMaybe = classDataReader.apply(classReferenceName);
            if (classRefDataMaybe.isPresent()) {
                // Found it! Add it and keep recursing down on any sub-dependencies it may have.
                accumulator.put(classReferenceName, classRefDataMaybe.get());
                readAllDependencies(classReferenceName, classDataReader, accumulator);
            } else {
                /*
                 * The referenced dependency class is not bundled with the implant.
                 * Go look for it on the classpath of this JVM.
                 * This may sound weird, but it's a common scenario when running in the context of an IDE, Maven or
                 * unit test, and it's using external dependencies (aka libraries).
                 */
                Path sourcePath;
                try {
                    sourcePath = getSourcePathFor(classReferenceName);
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException("Cannot get code source path for '" + classReferenceName + "'.", e);
                } catch (FileNotFoundException e) {
                    /*
                     * The code source for this dependency is still unknown.
                     * This is typically the case for classes in the SDK.
                     * Skip this and assume it's provided by the runtime environment when the implant runs.
                     */
                    log.fine("Assuming that '" + classReferenceName + "' is part of the SDK or otherwise provided during runtime. Skipping.");
                    continue;
                }

                // Get a new class reader function specific for this new code source path and use that instead.
                ThrowingFunction<ClassName, Optional<byte[]>, IOException> classRefSourcePathReader = getReaderFunctionFor(sourcePath);
                classRefDataMaybe = classRefSourcePathReader.apply(classReferenceName);
                if (classRefDataMaybe.isEmpty()) {
                    /*
                     * The referenced dependency class is not available somewhere on the current classpath either.
                     * This is an exotic case where we've loaded an implant in a JAR on disk and that's not a
                     * "fat JAR" (a JAR with all of its dependencies included). The implant JAR is probably
                     * assuming that dependencies are available (aka "provided") at runtime. This is a bold assumption
                     * for an implant JAR...
                     * Either way, there's nothing more we can do.
                     */
                    log.warning("Could not find referenced dependency class '" + classReferenceName + "'! Skipping. Expect trouble...");
                    continue;
                }

                log.fine("Found dependency '" + classReferenceName + "' on current classpath.");

                // Recurse down on the external dependency that's only available on the current classpath
                accumulator.put(classReferenceName, classRefDataMaybe.get());
                readAllDependencies(classReferenceName, classRefSourcePathReader, accumulator);
            }
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
