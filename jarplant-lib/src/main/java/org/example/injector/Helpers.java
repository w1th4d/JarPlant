package org.example.injector;

import javassist.CtClass;
import javassist.bytecode.*;

import java.io.*;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
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

    public static boolean isVolatileFlagSet(FieldInfo field) {
        int accessFlags = field.getAccessFlags();
        return (accessFlags & AccessFlag.VOLATILE) != 0;
    }

    public static void setStaticFlagForMethod(MethodInfo method) {
        int accessFlags = method.getAccessFlags();
        accessFlags |= AccessFlag.STATIC;   // Yes, bit-flipping!
        method.setAccessFlags(accessFlags);
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

    public static MethodInfo createAndAddClassInitializerStub(ClassFile instance) throws DuplicateMemberException {
        MethodInfo clinit = new MethodInfo(instance.getConstPool(), MethodInfo.nameClinit, "()V");
        setStaticFlagForMethod(clinit);
        Bytecode stubCode = new Bytecode(instance.getConstPool(), 0, 0);
        stubCode.addReturn(CtClass.voidType);
        clinit.setCodeAttribute(stubCode.toCodeAttribute());

        instance.addMethod(clinit);
        return clinit;
    }

    public static Optional<Integer> searchForEndOfMethodIndex(CodeAttribute codeAttribute, CodeIterator codeIterator) throws IOException {
        int index = 0;
        while (codeIterator.hasNext()) {
            try {
                index = codeIterator.next();
            } catch (BadBytecode e) {
                throw new RuntimeException(e);
            }
        }

        DataInput converter = new DataInputStream(new ByteArrayInputStream(codeAttribute.getCode()));
        converter.skipBytes(index);
        int opcode = converter.readUnsignedByte();
        if (opcode != Opcode.RETURN) {
            return Optional.empty();
        }

        return Optional.of(index);
    }
}
