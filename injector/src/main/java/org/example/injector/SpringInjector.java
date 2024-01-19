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
            infect(springContext);
            // TODO Somehow edit the entry inside the actual JAR file
            // Alternatively, create a new JAR file and put it on the side
        }

        return true;
    }

    public void infect(ClassFile springContextClass) {
        // TODO Do the infection thing!

        // Merge strategy:
        // Take the implant, move over all methods, fields and consts.
        // Make sure all attributes tag along (especially the Annotation for the REST handler method).
        // This is a more risky approach but should be slightly stealthier (more difficult to find when troubleshooting the app).

        // Add strategy:
        // Just add a new class file into the JAR file (copy the implant class as-is). Just make sure the package name match the target.
        // This is only the case where @ComponentScan (or @SpringBootApplication) annotations are used in the target app.

        // Maybe injecting another @Configuration into the target JAR could work? How does Spring handle several Configurations?
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
}
