package io.github.w1th4d.jarplant;

import javassist.bytecode.*;
import javassist.bytecode.annotation.Annotation;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.jar.*;
import java.util.logging.Formatter;
import java.util.logging.*;

/**
 * Contains methods that tests may have in common.
 * The purpose of having these methods in their own class is to reduce code duplication and add clarity to tests
 * where it makes sense.
 * Feel free to add arbitrary static methods to this class as the test suite evolves.
 */
public class TestHelpers {
    /**
     * Configure JUL (java.util.logger) to write nicely to stdout.
     * Invoke this <code>@Before</code> all tests.
     */
    public static void configureLogger() {
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(Level.ALL);
        for (Handler handler : rootLogger.getHandlers()) {
            rootLogger.removeHandler(handler);
        }
        StreamHandler consoleHandler = new StreamHandler(System.out, new Formatter() {
            @Override
            public String format(LogRecord logRecord) {
                return logRecord.getLoggerName() + ": " + logRecord.getMessage() + "\n";
            }
        });
        rootLogger.addHandler(consoleHandler);
    }

    /**
     * Finds the directory where the compiled test classes are.
     * This perhaps naively exploits the fact that the root resource path will be the <code>target/test-classes</code>
     * directory.
     *
     * @param testClass a class to use as basis
     * @return path to a directory with compiled classes (and sub dirs) for the project
     * @throws RuntimeException if anything doesn't make sense with the resource folder
     */
    public static Path findTestEnvironmentDir(Class<?> testClass) {
        URL testClassesDir = testClass.getClassLoader().getResource("");
        if (testClassesDir == null) {
            throw new RuntimeException("Failed to find resource directory for testing environment.");
        }

        return Path.of(testClassesDir.getPath());
    }

    /**
     * Add some files into a JAR.
     * Used for adding test classes into a temporary file. The JAR will typically be an empty file.
     *
     * @param existingJar an existing JAR or empty file
     * @param baseDir     base directory where <code>files</code> are expected to be
     * @param files       all files inside <code>baseDir</code> that should be added
     * @throws IOException f anything went wrong
     */
    public static void populateJarEntriesIntoEmptyFile(Path existingJar, Path baseDir, Path... files) throws IOException {
        JarOutputStream jarWriter = new JarOutputStream(new FileOutputStream(existingJar.toFile()));

        JarEntry manifestEntry = new JarEntry("META-INF/MANIFEST.MF");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        jarWriter.putNextEntry(manifestEntry);
        manifest.write(jarWriter);

        for (Path fileRelativePath : files) {
            Path fileFullPath = baseDir.resolve(fileRelativePath);
            JarEntry classEntry = new JarEntry(fileRelativePath.toString());
            jarWriter.putNextEntry(classEntry);
            jarWriter.write(Files.readAllBytes(fileFullPath));
            jarWriter.closeEntry();
        }

        jarWriter.close();
    }

    /**
     * Find a JAR file located in the resource folder of this project.
     * This can be used to find compiled JAR artifacts used for testing (such as target apps and test implants).
     * See pom files for the `target-*` and `test-*` submodules.
     *
     * @param jarFileName filename, like <code>test-app-pojo.jar</code>
     * @return oath to existing JAR file
     * @throws IOException if anything went wrong or doesn't make sense
     */
    public static Path getJarFileFromResourceFolder(String jarFileName) throws IOException {
        URL resource = ClassInjectorTests.class.getClassLoader().getResource(jarFileName);
        if (resource == null) {
            throw new FileNotFoundException("Cannot find '" + jarFileName + "' in resource folder. Have you run `mvn package` in the project root yet?");
        }

        Path jarFilePath;
        try {
            jarFilePath = Path.of(resource.toURI());
        } catch (URISyntaxException e) {
            throw new IOException("Cannot make sense of resource path: Invalid URI: " + resource, e);
        }

        // This should not be necessary but let's make sure
        if (!Files.exists(jarFilePath)) {
            throw new IOException("Cannot make sense of resource path: File does not exist: " + jarFilePath);
        }

        return jarFilePath;
    }

    /**
     * Open up a JAR, search for the specified file and open a stream towards that file.
     *
     * @param jarFilePath           path to JAR
     * @param entryFullInternalPath filename inside the JAR, like <code>com/example/Thing.class</code>
     * @return an open <code>InputStream</code> for reading the specified file in the JAR
     * @throws IOException if something could not be read
     */
    public static InputStream getRawClassStreamFromJar(Path jarFilePath, String entryFullInternalPath) throws IOException {
        JarFile jarFile = new JarFile(jarFilePath.toFile());
        JarEntry classFileInJar = (JarEntry) jarFile.getEntry(entryFullInternalPath);
        if (classFileInJar == null) {
            throw new FileNotFoundException(entryFullInternalPath);
        }

        return jarFile.getInputStream(classFileInJar);
    }

    /**
     * Serialize a ClassFile instance as Java bytecode.
     *
     * @param classFile Javassist ClassFile instance
     * @return bytes suitable for writing to a class file
     * @throws IOException if something went wrong
     */
    public static byte[] asBytes(ClassFile classFile) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        classFile.write(new DataOutputStream(buffer));
        return buffer.toByteArray();
    }

    /**
     * Read and hash all entries in a JAR file.
     *
     * @param jar JarFiddler instance loaded with stuff
     * @return a map keyed on internal filename with the message digest (aka hash) as the value
     */
    public static Map<String, String> hashAllJarContents(JarFiddler jar) {
        Map<String, String> hashes = new HashMap<>();
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Cannot find SHA-256 provider.", e);
        }

        for (JarFiddler.Entry entry : jar) {
            String filename = entry.getName();
            byte[] contentDigest = md.digest(entry.getContent());
            md.reset();
            String hashString = HexFormat.of().formatHex(contentDigest);

            hashes.put(filename, hashString);
        }

        return hashes;
    }

    /**
     * Compares two outputs from <code>hashAllJarContents</code>.
     * Note that entries that may be added or removed will not be accounted for. In other words: If <i>hashesAfter</i>
     * has a key that does not exist in <i>hashesBefore</i>, then this will <i>not</i> be considered a diff.
     *
     * @param hashesBefore   map of filenames and hashes before an operation
     * @param hashesAfter    map of filenames and hashes after an operation
     * @param keysOfInterest only look for these filenames in the JAR (ignore the rest)
     * @return filenames that have a different hash
     */
    public static Set<String> getDiffingEntries(Map<String, String> hashesBefore, Map<String, String> hashesAfter, Set<String> keysOfInterest) {
        Set<String> classesModified = new HashSet<>();

        for (Map.Entry<String, String> entry : hashesAfter.entrySet()) {
            String classFileInJar = entry.getKey();
            if (keysOfInterest.contains(classFileInJar)) {
                String beforeValue = hashesBefore.get(entry.getKey());
                String afterValue = entry.getValue();
                if (!afterValue.equals(beforeValue)) {
                    classesModified.add(entry.getKey());
                }
            }
        }

        return classesModified;
    }

    /**
     * Compares two outputs from <code>hashAllJarContents</code>.
     * Note that entries that may be added or removed will not be accounted for. In other words: If <i>hashesAfter</i>
     * has a key that does not exist in <i>hashesBefore</i>, then this will <i>not</i> be considered a diff.
     *
     * @param hashesBefore map of filenames and hashes before an operation
     * @param hashesAfter  map of filenames and hashes after an operation
     * @return filenames that has a different hash
     */
    public static Set<String> getDiffingEntries(Map<String, String> hashesBefore, Map<String, String> hashesAfter) {
        Set<String> classesModified = new HashSet<>();

        // Use hashesAfter as a source of truth
        for (Map.Entry<String, String> afterEntry : hashesAfter.entrySet()) {
            String beforeValue = hashesBefore.get(afterEntry.getKey());
            if (beforeValue == null) {
                // Entry has been added. Disregard this as a diff.
                continue;
            }
            String afterValue = afterEntry.getValue();
            if (!afterValue.equals(beforeValue)) {
                classesModified.add(afterEntry.getKey());
            }
        }

        // Note: Any entries added or removed will not be accounted for

        return classesModified;
    }

    /**
     * Get the entries that two sets have in common.
     *
     * @param a   first set
     * @param b   second set
     * @param <T> any type
     * @return entries that exist in both sets
     */
    public static <T> Set<T> setIntersection(Set<T> a, Set<T> b) {
        Set<T> copy = new HashSet<>(a);
        copy.retainAll(b);
        return copy;
    }

    /**
     * Figure out if a given array exists in a bigger array.
     * Example: <code>[b, c]</code> is a subset of <code>[a, b, c, d]</code>.
     *
     * @param bigArray   array to search within
     * @param smallArray array to search for
     * @return index where the match begins, if any
     */
    public static Optional<Integer> findSubArray(byte[] bigArray, byte[] smallArray) {
        for (int i = 0; i <= bigArray.length - smallArray.length; i++) {
            boolean found = true;
            for (int j = 0; j < smallArray.length; j++) {
                if (bigArray[i + j] != smallArray[j]) {
                    found = false;
                    break;
                }
            }
            if (found) {
                return Optional.of(i);
            }
        }

        return Optional.empty();
    }

    /**
     * Generate bytecode that looks like a Spring configuration class.
     * The generated configuration class will contain a <code>@Bean</code> annotated method denoting a Spring component.
     *
     * @param className what to name the Spring configuration class
     * @param beanName  what to name the Spring component
     * @return a Javassist ClassFile instance
     */
    public static ClassFile createSpringConfWithBean(String className, String beanName) {
        ClassFile configClass = new ClassFile(false, className, null);
        MethodInfo beanMethod = new MethodInfo(configClass.getConstPool(), beanName, "L" + beanName + "()");

        // Bake method annotations
        AnnotationsAttribute annotationsAttr = new AnnotationsAttribute(configClass.getConstPool(), "RuntimeVisibleAnnotations");
        annotationsAttr.addAnnotation(new Annotation("org/springframework/context/annotation/Bean", configClass.getConstPool()));
        beanMethod.addAttribute(annotationsAttr);

        // Bake method code
        byte[] dummyCode = {Opcode.NOP, Opcode.NOP, Opcode.NOP};
        ExceptionTable etable = new ExceptionTable(configClass.getConstPool());
        CodeAttribute componentCode = new CodeAttribute(configClass.getConstPool(), 0, 0, dummyCode, etable);
        beanMethod.addAttribute(componentCode);

        try {
            configClass.addMethod(beanMethod);
        } catch (DuplicateMemberException e) {
            throw new RuntimeException(e);
        }

        return configClass;
    }

    /**
     * Just generate some ClassFile instance with dummy stuff in it.
     *
     * @param fullName The full dot notated class name, like "com.example.Something"
     * @return ClassFile instance
     */
    public static ClassFile generateDummyClassFile(String fullName) {
        ClassFile cf = new ClassFile(false, fullName, null);

        try {
            cf.addField(new FieldInfo(cf.getConstPool(), "someField", "I"));
            cf.addField(new FieldInfo(cf.getConstPool(), "anotherField", "Ljava/lang/String;"));
            cf.addMethod(new MethodInfo(cf.getConstPool(), "aMethod", "V()"));
            cf.addMethod(new MethodInfo(cf.getConstPool(), "anotherMethod", "V()"));
        } catch (DuplicateMemberException e) {
            throw new RuntimeException(e);
        }

        return cf;
    }
}
