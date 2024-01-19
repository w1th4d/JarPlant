package org.example.injector;

import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ClassFile;
import javassist.bytecode.annotation.Annotation;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class SpringInjector {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.exit(1);
        }

        SpringInjector injector = new SpringInjector();
        try {
            Path targetPath = Path.of(args[0]);
            System.out.println("[+] Infecting " + targetPath + ".");
            injector.infect(targetPath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    SpringInjector() {
    }

    public boolean infect(final Path targetJarFilePath) throws IOException {
        try (JarFile targetJar = new JarFile(targetJarFilePath.toFile())) {
            return infect(targetJar);
        }
    }

    public boolean infect(final JarFile targetJar) throws IOException {
        // TODO Consider that some JARs may be multi-versioned
        boolean foundSignedClasses = false;
        List<JarEntry> classEntries = new ArrayList<>();
        Enumeration<JarEntry> entries = targetJar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (!entry.getName().endsWith(".class")) {
                continue;
            }
            if (entry.getCodeSigners() != null) {
                //System.out.println("[-] File '" + entry.getName() + "' is signed. Not touching this!");
                foundSignedClasses = true;
                continue;
            }
            //System.out.println("[+] Found viable class: " + entry.getName());
            classEntries.add(entry);
        }

        if (foundSignedClasses) {
            System.out.println("[-] Found signed classes. These will not be considered.");
        }
        if (classEntries.isEmpty()) {
            System.out.println("[-] Did not find any classes in JAR.");
            return false;
        }
        System.out.println("[+] Found " + classEntries.size() + " classes. Looking for a Spring configuration.");

        Map<JarEntry, ClassFile> selectedTargets = new HashMap<>();
        for (JarEntry entry : classEntries) {
            ClassFile classFile;
            try (DataInputStream in = new DataInputStream(targetJar.getInputStream(entry))) {
                classFile = new ClassFile(in);
                if (!isSpringContext(classFile)) {
                    continue;
                }
                System.out.println("[+] Found Spring configuration: " + entry.getName());
                selectedTargets.put(entry, classFile);
            }
        }

        for (JarEntry jarEntry : selectedTargets.keySet()) {
            ClassFile springContext = selectedTargets.get(jarEntry);

            if (!hasComponentScanEnabled(springContext)) {
                // TODO Explicitly add a @Bean to the configuration
                // Maybe it doesn't hurt to add it despite @ContentScan being enabled?
                System.out.println("[-] Spring configuration is not set to scan for components (@ComponentScan). Infection not (yet) supported!");
                return false;
            }

            // TODO Just add implant class to the JAR (also set the package name)
        }

        return true;
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
