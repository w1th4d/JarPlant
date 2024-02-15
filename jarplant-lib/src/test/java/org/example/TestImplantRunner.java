package org.example;

import javassist.bytecode.ClassFile;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class TestImplantRunner extends ClassLoader {
    public TestImplantRunner() {
        super();
    }

    public Class<?> load(String name, byte[] rawClassData) {
        return super.defineClass(name, rawClassData, 0, rawClassData.length);
    }

    public String exec(ClassFile classFile) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            classFile.write(new DataOutputStream(buffer));
        } catch (IOException e) {
            throw new RuntimeException("Could not write ClassFile to internal buffer.", e);
        }

        return exec(classFile.getName(), buffer.toByteArray());
    }

    public String exec(String name, byte[] rawClassData) {
        Class<?> clazz = load(name, rawClassData);

        Method init;
        try {
            init = clazz.getMethod("init");
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Could not find init() method.", e);
        }

        Object returnValue;
        try {
            returnValue = init.invoke(clazz);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Could not run init() method.", e);
        }

        if (returnValue instanceof String strReturnValue) {
            return strReturnValue;
        } else {
            throw new RuntimeException("Test implant did not return a String.");
        }
    }
}
