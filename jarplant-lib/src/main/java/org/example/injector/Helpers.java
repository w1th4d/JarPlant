package org.example.injector;

import javassist.CtClass;
import javassist.bytecode.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Helpers {
    private static final Set<Integer> returnOpcodes = Set.of(Opcode.RETURN, Opcode.DRETURN, Opcode.ARETURN, Opcode.FRETURN, Opcode.IRETURN, Opcode.LRETURN);

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
        if (parts[parts.length - 1].equals("class")) {
            throw new RuntimeException("Not a fully qualified class name (hint: don't include .class extension): " + fqcn);

        }
        return parts[parts.length - 1];
    }

    public static String convertToClassFormatFqcn(final String dotFormatClassName) {
        if (!dotFormatClassName.contains(".")) {
            throw new RuntimeException("Not a fully qualified class name: " + dotFormatClassName);
        }
        return dotFormatClassName.replace(".", "/");
    }

    public static String convertToBinaryClassNameFromPath(final String classFilePath) {
        Pattern pattern = Pattern.compile("\\/?((\\w+\\/)*)?(\\w+)\\.class");
        Matcher matcher = pattern.matcher(classFilePath);
        if (!matcher.matches() || matcher.groupCount() < 3) {
            throw new RuntimeException("Not a path to a class file: " + classFilePath);
        }
        String packageName = matcher.group(1).replace("/", ".");
        String className = matcher.group(3);
        return packageName + className;
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
        if (!returnOpcodes.contains(opcode)) {
            return Optional.empty();
        }

        return Optional.of(index);
    }

    public static byte[] bufferFrom(Path classFilePath) throws IOException {
        return Files.readAllBytes(classFilePath);
    }

    public static byte[] bufferFrom(DataInputStream in) throws IOException {
        ByteArrayOutputStream allocator = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int bytesRead;
        while ((bytesRead = in.read(chunk)) != -1) {
            allocator.write(chunk, 0, bytesRead);
        }

        return allocator.toByteArray();
    }

    public static boolean jarLooksSigned(Path jarFilePath) throws IOException {
        Pattern regex = Pattern.compile("META-INF/.+\\.SF|DSA|RSA");

        try (StreamedJarFiddler jar = StreamedJarFiddler.open(jarFilePath)) {
            for (StreamedJarFiddler.StreamedJarEntry entry : jar) {
                Matcher matcher = regex.matcher(entry.getName());
                if (matcher.matches()) {
                    return true;
                }
            }
        }

        return false;
    }
}
