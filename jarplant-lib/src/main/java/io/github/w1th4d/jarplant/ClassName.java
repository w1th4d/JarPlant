package io.github.w1th4d.jarplant;

import javassist.bytecode.ClassFile;

import java.util.jar.JarEntry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents the name of a class in all it's various forms.
 * <p>This class can be used to convert between the different formats.
 * For example, class names are usually written in the form <code>com.example.MyClass</code>,
 * where <code>com.example</code> is the package name and <code>MyClass</code> is the actual class name.</p>
 * <p>When dealing paths to class files, package and name may be inferred from the relative path, like
 * <code>com/example/MyClass.class</code>. This is typically the case when dealing with a CLASSPATH directory or
 * JAR files.</p>
 * <p>This class only deals with names. The named classes or paths does not have to exist.</p>
 * <p>Example usage:
 * <pre>{@code
 * ClassName cn = ClassName.of(MyClass.class);
 * JarEntry newEntry = new JarEntry(cn.getClassFilePath());
 * System.out.println("Created entry " + cn);
 * }</pre>
 * The JarEntry will have the name <code>com/example/MyClass.class</code></p>.
 * <p>
 */
public class ClassName implements Comparable<ClassName>, Cloneable {
    private final static Pattern FULL_CLASS_NAME_PATTERN = Pattern.compile("^(?:([\\w-]+(?:\\.[\\w-]+)*)\\.)?([\\w\\$]+)$");

    private final String fullClassName;
    private final String packageName;
    private final String className;
    private final String classFormatInternalName;
    private final String classFilePath;
    private final String springJarEntryPath;

    ClassName(String fullClassName, String packageName, String className, String classFormatInternalName, String classFilePath, String springJarEntryPath) {
        this.fullClassName = fullClassName;
        this.packageName = packageName;
        this.className = className;
        this.classFormatInternalName = classFormatInternalName;
        this.classFilePath = classFilePath;
        this.springJarEntryPath = springJarEntryPath;
    }

    /**
     * Get an instance by parsing the full class name.
     * <p>The "full class name" means both the package and class name in dot-notation.
     * Example: <code>com.example.MyClass</code>.</p>
     * <p>The input may optionally contain a ".class" suffix that will be ignored.
     * Example: <code>com.example.MyClass.class</code> will be parsed as being of the package <code>com.example</code>
     * with the name <code>MyClass</code>.</p>
     * <p>The package can also be empty.</p>
     *
     * @param fullClassName Full dot-notation of a class including its package name
     * @return Instance
     * @throws ClassNameException If the format is not right
     */
    public static ClassName fromFullClassName(String fullClassName) throws ClassNameException {
        // Explicitly strip away .class file suffix (if present) because regex is too greedy to do so properly
        if (fullClassName.endsWith(".class")) {
            int suffixIndex = fullClassName.lastIndexOf(".class");
            fullClassName = fullClassName.substring(0, suffixIndex);
        }

        Matcher matcher = FULL_CLASS_NAME_PATTERN.matcher(fullClassName);
        if (!matcher.matches()) {
            throw new ClassNameException(fullClassName);
        }
        String packageName = matcher.group(1);
        String className = matcher.group(2);
        if (packageName == null) {
            packageName = "";
        }
        if (className == null) {
            throw new ClassNameException(fullClassName);
        }

        String sanitizedFullClassName;
        if (packageName.isEmpty()) {
            sanitizedFullClassName = className;
        } else {
            sanitizedFullClassName = packageName + "." + className;
        }

        return new ClassName(sanitizedFullClassName, packageName, className,
                toClassFormatInternalName(sanitizedFullClassName),
                toClassFilePath(sanitizedFullClassName),
                toSpringJarEntryPath(sanitizedFullClassName)
        );
    }

    /**
     * Get an instance by parsing the format that's used internally in the class format.
     * <p>Example: <code>com/example/MyClass</code>.</p>
     *
     * @param classFormatInternalName Name formatted as when coming from a class file
     * @return Instance
     * @throws ClassNameException If the format is not right
     */
    public static ClassName fromClassFormatInternalName(String classFormatInternalName) throws ClassNameException {
        if (classFormatInternalName.endsWith(".class")) {
            throw new ClassNameException(classFormatInternalName);
        }

        String replaced = classFormatInternalName.replace("/", ".");
        return fromFullClassName(replaced);
    }

    /**
     * Get the name from a Class.
     *
     * @param clazz Class to get the name from
     * @return Instance
     */
    public static ClassName of(Class<?> clazz) {
        try {
            return fromFullClassName(clazz.getName());
        } catch (ClassNameException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Get the name from a Javassist ClassFile instance.
     *
     * @param classFile ClassFile instance
     * @return Instance
     */
    public static ClassName of(ClassFile classFile) {
        try {
            return fromFullClassName(classFile.getName());
        } catch (ClassNameException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Get the name from a JarEntry.
     * <p>The file in the entry myst end with the suffix ".class" to be considered valid.
     * Assumptions will be made based on how classes are expected to be organized inside a JAR.
     * This includes the special way the Spring Framework organizes JARs.</p>
     * <p>The actual entry will not be read. Only the metadata in the entry will be parsed.</p>
     *
     * @param jarEntry Class file entry in a ZIP or JAR file
     * @return Instance
     * @throws ClassNameException If the format is not right, including the expected file name suffix
     */
    public static ClassName of(JarEntry jarEntry) throws ClassNameException {
        String name = jarEntry.getName();
        if (!name.endsWith(".class")) {
            throw new ClassNameException("Does not end with '.class': " + name);
        }

        String path = name;
        if (path.startsWith("BOOT-INF/classes")) {
            // This looks like a JAR using the Spring Framework
            path = path.substring(0, path.indexOf("BOOT-INF/classes"));
        }

        return fromFullClassName(path.replace("/", "."));
    }

    private static String toClassFormatInternalName(String sanitizedFullClassName) {
        return sanitizedFullClassName.replace(".", "/");
    }

    private static String toClassFilePath(String sanitizedFullClassName) {
        return sanitizedFullClassName.replace(".", "/") + ".class";
    }

    private static String toSpringJarEntryPath(String sanitizedFullClassName) {
        return "BOOT-INF/classes/" + toClassFilePath(sanitizedFullClassName);
    }

    /**
     * Use another package name.
     * This instance will not be modified.
     *
     * @param newPackageName Other instance of ClassName to take the package name from
     * @return A new instance with a changed package name
     */
    public ClassName renamePackage(ClassName newPackageName) {
        try {
            return ClassName.fromFullClassName(newPackageName.getPackageName() + "." + getClassName());
        } catch (ClassNameException e) {
            throw new RuntimeException("Internal error", e);
        }
    }

    /**
     * Use another class name.
     * This instance will not be modified.
     *
     * @param newClassName Other instance of ClassName to take the class name from
     * @return A new instance with a changed class name
     */
    public ClassName renameClassName(ClassName newClassName) {
        try {
            return ClassName.fromFullClassName(getPackageName() + "." + newClassName.getClassName());
        } catch (ClassNameException e) {
            throw new RuntimeException("Internal error", e);
        }
    }

    /**
     * Get the full dot-notated class name, including the package name.
     *
     * @return Full class name, like "com.example.MyClass"
     */
    public String getFullClassName() {
        return fullClassName;
    }

    /**
     * Get the package name.
     * This may be an empty string.
     *
     * @return Package name, like "com.example", or just "" if none
     */
    public String getPackageName() {
        return packageName;
    }

    /**
     * Get the class name.
     * The wording may be confusing. This returns the actual name part of the class name, like "MyClass".
     *
     * @return Class name, like "MyClass"
     */
    public String getClassName() {
        return className;
    }

    /**
     * Get the expected relative path that this class would populate in a class path.
     * This can be used to find a class in a CLASSPATH or inside a JAR file.
     *
     * @return Relative path, like "com/example/MyClass.class"
     */
    public String getClassFilePath() {
        return classFilePath;
    }

    /**
     * Get the expected relative path that this class would have inside a JAR for the Spring Framework.
     *
     * @return Relative path inside a Spring JAR, like "BOOT-INF/classes/com/example/MyClass.class"
     */
    public String getSpringJarEntryPath() {
        return springJarEntryPath;
    }

    /**
     * Get the way class names tend to be represented inside the class format itself.
     *
     * @return Class name formatted suitable for class file internals, like "com/example/MyClass"
     */
    public String getClassFormatInternalName() {
        return classFormatInternalName;
    }

    @Override
    public String toString() {
        return fullClassName;
    }

    @Override
    public int compareTo(ClassName other) {
        return fullClassName.compareTo(other.getFullClassName());
    }

    @Override
    public final boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ClassName otherClassName)) return false;
        return fullClassName.equals(otherClassName.fullClassName);
    }

    @Override
    public int hashCode() {
        return fullClassName.hashCode();
    }

    @Override
    @SuppressWarnings("all")
    public ClassName clone() {
        return new ClassName(fullClassName, packageName, className, classFormatInternalName, classFilePath, springJarEntryPath);
    }
}
