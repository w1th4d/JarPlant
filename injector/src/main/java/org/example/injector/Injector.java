package org.example.injector;

import javassist.CtClass;
import javassist.bytecode.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.HashMap;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class Injector {
    public static void main(String[] args) {
        System.out.println("    |-----[==Class=Injector=v0.1==]-----");
        if (args.length == 0) {
            System.out.println("[-] Usage: java -jar classinjector.jar org.example.injector.Injector <target-class-file>");
            System.exit(1);
        }

        final ClassFile selfClassFile;
        try {
            selfClassFile = getSelf();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("[i] Source class: " + selfClassFile.getName());

        Path target = Path.of(args[0]);
        System.out.println("[i] Target class file: " + target);
        if (!Files.exists(target) || !Files.isWritable(target)) {
            System.out.println("[!] Target class file does not exist or is not writable!");
            System.exit(2);
        }

        MethodInfo implant;
        try {
            implant = Injector.readImplant(selfClassFile, "implant");
            System.out.println("[+] Read and serialized payload: " + implant);
        } catch (IOException e) {
            System.out.println("[!] Failed to read payload! Error message: " + e.getMessage());
            System.exit(3);
            throw new RuntimeException("Unreachable", e);
        }

        final boolean isAlreadyInfected;
        try {
            isAlreadyInfected = Injector.isInfected(target, implant);
        } catch (IOException e) {
            System.out.println(("[!] Cannot read class! Error message: " + e.getMessage()));
            System.exit(4);
            throw new RuntimeException("Unreachable");
        }

        if (isAlreadyInfected) {
            System.out.println("[-] Target class already infected. Skipping.");
            System.exit(5);
        }

        final boolean didInfect;
        try {
            didInfect = Injector.infectTarget(target, implant);
        } catch (IOException e) {
            System.out.println("[!] Cannot infect target class file! Error message: " + e.getMessage());
            System.exit(4);
            throw new RuntimeException("Unreachable");
        }

        if (!didInfect) {
            System.out.println("[-] Target class not suitable for infection. Skipping.");
        } else {
            System.out.println("[+] Infected target class: " + target);
        }
    }

    // This is the method that will be transplanted into the target
    public static void implant() {
        // "java.class.init" is a made up property used to determine if an implant is already running in this JVM
        // (as could the case be if more than one class is infected)
        if (System.getProperty("java.class.init") == null) {
            if (System.setProperty("java.class.init", "true") == null) {
                System.out.println("This is the implant running (once per JVM)!");
            }
        }
    }

    private static ClassFile getSelf() throws IOException {
        try {
            return findAndReadClassFile(Injector.class);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Cannot find a class file for oneself! This program may be running through a very exotic ClassLoader.");
        }
    }

    // Finds the class file using some weird Java quirks
    public static ClassFile findAndReadClassFile(final Class<?> clazz) throws ClassNotFoundException, IOException {
        CodeSource codeSource = clazz.getProtectionDomain().getCodeSource();
        if (codeSource == null) {
            // Can't find oneself
            throw new ClassNotFoundException("Can't determine the path to the class file");
        }
        Path sourcePath = Path.of(codeSource.getLocation().getPath());

        return findAndReadClassFile(clazz, sourcePath);
    }

    // This method may be used when extracting the implant from another place than self
    public static ClassFile findAndReadClassFile(final Class<?> clazz, final Path path) throws ClassNotFoundException, IOException {
        if (Files.isDirectory(path)) {
            return findAndReadClassFileFromDirectory(clazz, path);
        } else {
            return findAndReadClassFileFromJar(clazz, path);
        }
    }

    private static ClassFile findAndReadClassFileFromDirectory(final Class<?> clazz, final Path directory) throws ClassNotFoundException, IOException {
        if (!Files.isDirectory(directory)) {
            throw new UnsupportedOperationException("Not a directory");
        }

        // Convert "org.example.injector.Inject" to "org/example/injector/Inject.class"
        String[] packageHierarchy = clazz.getName().split("\\.");
        packageHierarchy[packageHierarchy.length - 1] += ".class";
        Path sourcePath = Path.of(directory.toString(), packageHierarchy);

        if (!Files.exists(sourcePath)) {
            throw new ClassNotFoundException(sourcePath.toString());
        }

        return readClassFile(sourcePath);
    }

    private static ClassFile findAndReadClassFileFromJar(final Class<?> clazz, final Path jarFilePath) throws ClassNotFoundException, IOException {
        DataInputStream inputStream;
        try (JarFile jarFile = new JarFile(jarFilePath.toFile())) {
            String lookingForFileName = clazz.getName().replace(".", File.separator) + ".class";

            JarEntry classFileInJar = (JarEntry) jarFile.getEntry(lookingForFileName);
            if (classFileInJar == null) {
                throw new ClassNotFoundException(lookingForFileName);
            }

            inputStream = new DataInputStream(jarFile.getInputStream(classFileInJar));
            return new ClassFile(inputStream);
        }
    }

    private static MethodInfo readImplant(final Path classFilePath, final String sourceMethodName) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(classFilePath.toFile())))) {
            return readImplant(in, sourceMethodName);
        }
    }

    private static MethodInfo readImplant(final DataInputStream classFileInputStream, final String sourceMethodName) throws IOException {
        return readImplant(new ClassFile(classFileInputStream), sourceMethodName);
    }

    private static MethodInfo readImplant(final ClassFile sourceClass, final String sourceMethodName) throws IOException {
        MethodInfo implantMethod = sourceClass.getMethod(sourceMethodName);

        if (implantMethod == null) {
            throw new IOException("Cannot find method '" + sourceMethodName + "' in " + sourceClass.getName());
        }

        return implantMethod;
    }

    public static boolean isInfected(final Path classFilePath, final MethodInfo implant) throws IOException {
        final ClassFile classFile = readClassFile(classFilePath);

        return classFile.getMethod(implant.getName()) != null;
    }

    public static boolean infectTarget(final Path targetClassFilePath, final MethodInfo implantMethod) throws IOException {
        final ClassFile targetClass = readClassFile(targetClassFilePath);
        final ConstPool constPool = targetClass.getConstPool();

        // Add the implant method to target class
        MethodInfo targetImplantMethod;
        try {
            // Construct a target method from the source (implant) method
            targetImplantMethod = new MethodInfo(constPool, implantMethod.getName(), implantMethod.getDescriptor());
            targetImplantMethod.setExceptionsAttribute(implantMethod.getExceptionsAttribute());
            HashMap<String, String> classTranslation = new HashMap<>();
            CodeAttribute copy = (CodeAttribute) implantMethod.getCodeAttribute().copy(constPool, classTranslation);
            copy.setMaxLocals(1);  // Don't know why this is necessary, but it throws an error otherwise
            setStaticFlagForMethod(targetImplantMethod);

            targetImplantMethod.getAttributes().removeIf(Objects::isNull);  // Cringe workaround due to internal bug in Javassist
            targetImplantMethod.setCodeAttribute(copy);

            targetClass.addMethod(targetImplantMethod);
        } catch (DuplicateMemberException e) {
            // Class already infected
            return false;
        }

        MethodInfo currentClinit = targetClass.getMethod(MethodInfo.nameClinit);
        if (currentClinit == null) {
            // There are no static blocks in this class, create an empty one
            currentClinit = new MethodInfo(constPool, MethodInfo.nameClinit, "()V");
            setStaticFlagForMethod(currentClinit);
            Bytecode stubCode = new Bytecode(constPool, 0, 0);
            stubCode.addReturn(CtClass.voidType);
            currentClinit.setCodeAttribute(stubCode.toCodeAttribute());

            try {
                targetClass.addMethod(currentClinit);
            } catch (DuplicateMemberException e) {
                throw new RuntimeException("Internal error: clinit already exist despite not existing", e);
            }
        }

        // Modify the clinit method of the target class to run the implant method (before its own code)
        Bytecode additionalClinitCode = new Bytecode(constPool);
        additionalClinitCode.addInvokestatic(targetClass.getName(), targetImplantMethod.getName(), targetImplantMethod.getDescriptor());
        CodeAttribute additionalClinitCodeAttr = additionalClinitCode.toCodeAttribute();
        CodeAttribute currentClinitCodeAttr = currentClinit.getCodeAttribute();
        ByteBuffer concatenatedCode = ByteBuffer.allocate(additionalClinitCodeAttr.getCodeLength() + currentClinit.getCodeAttribute().getCodeLength());
        concatenatedCode.put(additionalClinitCodeAttr.getCode());
        concatenatedCode.put(currentClinitCodeAttr.getCode());
        CodeAttribute newCodeAttribute = new CodeAttribute(constPool, currentClinitCodeAttr.getMaxStack(), currentClinitCodeAttr.getMaxLocals(), concatenatedCode.array(), currentClinitCodeAttr.getExceptionTable());
        currentClinit.setCodeAttribute(newCodeAttribute);

        targetClass.write(new DataOutputStream(new FileOutputStream(targetClassFilePath.toFile())));

        return true;
    }

    private static void setStaticFlagForMethod(MethodInfo clinit) {
        int accessFlags = clinit.getAccessFlags();
        accessFlags |= AccessFlag.STATIC;   // Yes, bit-flipping!
        clinit.setAccessFlags(accessFlags);
    }

    private static ClassFile readClassFile(Path classFilePath) throws IOException {
        final ClassFile cf;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(classFilePath.toFile())))) {
            cf = new ClassFile(in);
        }
        return cf;
    }
}