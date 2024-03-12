package org.example;

import javassist.bytecode.ClassFile;
import org.example.injector.Helpers;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class TestImplantRunner extends ClassLoader {
    private static TestImplantRunner instance;
    private final Map<String, Class<?>> loadedClasses = new HashMap<>();

    TestImplantRunner() {
        super();
    }

    public static TestImplantRunner getInstance() {
        if (instance == null) {
            instance = new TestImplantRunner();
        }

        return instance;
    }

    public Set<Class<?>> loadAllClassesFromJar(Path jarPath) throws IOException {
        Set<Class<?>> loadedClasses = new HashSet<>();

        try (JarFile jar = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) {
                    continue;
                }

                String binaryClassName = Helpers.convertToBinaryClassNameFromPath(entry.getRealName());
                byte[] classData = jar.getInputStream(entry).readAllBytes();
                Class<?> loadedClass = load(binaryClassName, classData);
                loadedClasses.add(loadedClass);
            }
        }

        return loadedClasses;
    }

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

    public Class<?> load(ClassFile classFile) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            classFile.write(new DataOutputStream(buffer));
        } catch (IOException e) {
            throw new RuntimeException("Could not write ClassFile to internal buffer.", e);
        }

        return load(classFile.getName(), buffer.toByteArray());
    }

    public <T> T runMethod(String className, String methodName, Class<T> returnType) throws ClassNotFoundException {
        Class<?> loadedClass = loadedClasses.get(className);
        if (loadedClass == null) {
            throw new ClassNotFoundException();
        }

        return runMethod(loadedClass, methodName, returnType);
    }

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
