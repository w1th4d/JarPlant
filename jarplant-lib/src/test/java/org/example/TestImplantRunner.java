package org.example;

import javassist.bytecode.ClassFile;
import org.example.injector.ClassName;
import org.example.injector.ClassNameException;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Dynamically define/load classes.
 * Used for triggering implants during tests.
 * The approach used in this class is a bit experimental. It's still a bit unclear if this properly covers the desired
 * test cases or if another approach is required.
 */
public class TestImplantRunner extends ClassLoader {
    private final Map<String, Class<?>> loadedClasses = new HashMap<>();

    public TestImplantRunner() {
        super();
    }

    /**
     * Loads all classes found inside a specified JAR file.
     *
     * @param jarPath path to JAR
     * @return classes found and loaded
     * @throws IOException if anything went wrong
     */
    public Set<Class<?>> loadAllClassesFromJar(Path jarPath) throws IOException {
        Set<Class<?>> loadedClasses = new HashSet<>();

        try (JarFile jar = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) {
                    continue;
                }

                ClassName className;
                try {
                    className = ClassName.of(entry);
                } catch (ClassNameException e) {
                    throw new RuntimeException(e);
                }
                byte[] classData = jar.getInputStream(entry).readAllBytes();
                Class<?> loadedClass = load(className.getClassFormatInternalName(), classData);
                loadedClasses.add(loadedClass);
            }
        }

        return loadedClasses;
    }

    /**
     * Load a class by its raw bytecode.
     *
     * @param binaryName   binary name of the class, like <code>com.example.TestImplant</code>
     * @param rawClassData raw JVM bytecode
     * @return newly loaded proper Java class
     */
    public Class<?> load(String binaryName, byte[] rawClassData) {
        Class<?> loadedClass;

        if (loadedClasses.containsKey(binaryName)) {
            try {
                loadedClass = super.loadClass(binaryName);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Could not load class that should already be defined.", e);
            }
        } else {
            loadedClass = super.defineClass(binaryName, rawClassData, 0, rawClassData.length);
            loadedClasses.put(binaryName, loadedClass);
        }

        return loadedClass;
    }

    /**
     * Load a class by its representation as a Javassist ClassFile.
     *
     * @param classFile class definition
     * @return newly loaded proper Java class
     */
    public Class<?> load(ClassFile classFile) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            classFile.write(new DataOutputStream(buffer));
        } catch (IOException e) {
            throw new RuntimeException("Could not write ClassFile to internal buffer.", e);
        }

        return load(classFile.getName(), buffer.toByteArray());
    }

    /**
     * Invoke a method based on the class name.
     * The name of the class must correspond to a class that has been loaded by this TestImplantRunner.
     *
     * @param className  binary class name, like <code>com.example.TestImplant</code>
     * @param methodName method name, like <code>init</code>
     * @param returnType expected return type
     * @param <T>        any type
     * @return value returned by the method invocation
     * @throws ClassNotFoundException if the class has not been loaded by this TestImplantRunner
     */
    public <T> T runMethod(String className, String methodName, Class<T> returnType) throws ClassNotFoundException {
        Class<?> loadedClass = loadedClasses.get(className);
        if (loadedClass == null) {
            throw new ClassNotFoundException();
        }

        return runMethod(loadedClass, methodName, returnType);
    }

    /**
     * Invoke a method.
     *
     * @param loadedClass any Java class
     * @param methodName  method name, like <code>init</code>
     * @param returnType  expected return type
     * @param <T>         any type
     * @return value returned by the method invocation
     */
    public <T> T runMethod(Class<?> loadedClass, String methodName, Class<T> returnType) {
        Method method;
        try {
            method = loadedClass.getMethod(methodName);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Could not find method.", e);
        }

        Object returnValue;
        try {
            returnValue = method.invoke(loadedClass);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Could not run method.", e);
        }

        T castedReturnValue;
        try {
            castedReturnValue = returnType.cast(returnValue);
        } catch (ClassCastException e) {
            throw new RuntimeException("Unexpected return value data type.");
        }

        return castedReturnValue;
    }
}
