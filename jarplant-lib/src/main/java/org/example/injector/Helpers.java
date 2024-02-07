package org.example.injector;

import javassist.bytecode.AccessFlag;
import javassist.bytecode.ClassFile;
import javassist.bytecode.FieldInfo;
import javassist.bytecode.MethodInfo;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;

public class Helpers {
    public static ClassFile readClassFile(final Path classFilePath) throws IOException {
        final ClassFile cf;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(classFilePath.toFile())))) {
            cf = new ClassFile(in);
        }
        return cf;
    }

    public static boolean isStaticFlagSet(FieldInfo field) {
        int accessFlags = field.getAccessFlags();
        return (accessFlags & AccessFlag.STATIC) != 0;
    }

    public static boolean isFinalFlagSet(FieldInfo field) {
        int accessFlags = field.getAccessFlags();
        return (accessFlags & AccessFlag.FINAL) != 0;
    }

    public static void setStaticFlagForMethod(MethodInfo clinit) {
        int accessFlags = clinit.getAccessFlags();
        accessFlags |= AccessFlag.STATIC;   // Yes, bit-flipping!
        clinit.setAccessFlags(accessFlags);
    }

    public static String parsePackageNameFromFqcn(final String fqcn) {
        String[] parts = fqcn.split("\\.");
        if (parts.length < 2) {
            throw new RuntimeException("Not a fully qualified class name: " + fqcn);
        }
        String[] packageParts = Arrays.copyOfRange(parts, 0, parts.length - 1);
        return String.join(".", packageParts);
    }

    public static String parseClassNameFromFqcn(final String fqcn) {
        String[] parts = fqcn.split("\\.");
        if (parts.length < 2) {
            throw new RuntimeException("Not a fully qualified class name: " + fqcn);
        }
        return parts[parts.length - 1];
    }

    public static String convertToClassFormatFqcn(final String dotFormatClassName) {
        return dotFormatClassName.replace(".", "/");
    }

    public static JarEntry convertToJarEntry(final ClassFile classFile) {
        String fullPathInsideJar = classFile.getName().replace(".", "/") + ".class";
        return new JarEntry(fullPathInsideJar);
    }

    public static JarEntry convertToSpringJarEntry(final ClassFile classFile) {
        // TODO Maybe this "BOOT-INF" etc is not a good thing to hardcode? Versioned JARs? Discrepancies in Spring JAR structure?
        String fullPathInsideJar = "BOOT-INF/classes/" + classFile.getName().replace(".", "/") + ".class";
        return new JarEntry(fullPathInsideJar);
    }

    public static Map<String, Object> readImplantConfig(ClassFile implant) {
        Map<String, Object> configFields = new HashMap<>();

        for (FieldInfo field : implant.getFields()) {
            if (!isStaticFlagSet(field)) {
                continue;
            }
            if (!isFinalFlagSet(field)) {
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
