package org.example.injector;

import javassist.CtClass;
import javassist.Modifier;
import javassist.bytecode.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.rmi.AlreadyBoundException;
import java.util.*;

public class Injector {
    private static final String PAYLOAD_METHOD_NAME = "initz";

    public static void main(String[] args) {
        System.out.println("\t     |-----[==Class=Injector=v0.1==]-----");
        if (args.length == 0) {
            System.out.println("[-] Usage: java -jar classinjector.jar org.example.injector.Injector <target-class-file>");
            System.exit(1);
        }

        Path target = Path.of(args[0]);
        if (!Files.exists(target) || !Files.isWritable(target)) {
            System.out.println("[!] Target class file does not exist or is not writable!");
            System.exit(2);
        }

//        final IntBuffer payload1;
//        try {
//            final Path selfPath = getSelf();
//            payload1 = Injector.readPayload(selfPath, "implant");
//            System.out.println("[+] Read and serialized payload: " + payload1.limit() + " bytes.");
//        } catch (IOException e) {
//            System.out.println("[!] Failed to serialize payload!");
//            System.out.println("[!] " + e.getMessage());
//            System.exit(3);
//            throw new RuntimeException("Unreachable", e);
//        }

        //System.out.println("[+] Dumping opcodes:");
        //Injector.dumpOpcodes(payload1, System.out, "    ");

        MethodInfo implant;
        try {
            final Path selfPath = getSelf();
            implant = Injector.readPayload3(selfPath, "implant");
            System.out.println("[+] Read and serialized payload: " + implant);
        } catch (IOException e) {
            System.out.println("[!] Failed to serialize payload!");
            System.out.println("[!] " + e.getMessage());
            System.exit(3);
            throw new RuntimeException("Unreachable", e);
        }


        try {
            boolean didInfect = Injector.infectTarget(target, implant);
            if (!didInfect) {
                System.out.println("[-] Class already infected. Skipping.");
            } else {
                System.out.println("[+] Attached to target class.");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        //implant();
    }

    public static void implant() {
        System.out.println("This is the implant!");
    }

    private static Path getSelf() {
        // TODO Do this properly
        return Path.of("injector/target/classes/org/example/injector/Injector.class");
    }

    private static IntBuffer readPayload(final Path classFilePath, final String sourceMethodName) throws IOException {
        BufferedInputStream in = new BufferedInputStream(new FileInputStream(classFilePath.toFile()));
        ClassFile cf = new ClassFile(new DataInputStream(in));
        MethodInfo implantMethod = cf.getMethod(sourceMethodName);

        if (implantMethod == null) {
            throw new IOException("Cannot find method '" + sourceMethodName + "' in " + classFilePath);
        }

        CodeIterator codeIterator = implantMethod.getCodeAttribute().iterator();
        byte[] code = implantMethod.getCodeAttribute().getCode();
        IntBuffer serializedCode = IntBuffer.allocate(codeIterator.getCodeLength());
        while (codeIterator.hasNext()) {
            try {
                int nextOpcodeIndex = codeIterator.next();
                int opcode = codeIterator.byteAt(nextOpcodeIndex);
                assertThat(opcode >= 0 && opcode < 256);
                serializedCode.put(opcode);
            } catch (BadBytecode e) {
                throw new IOException("Failed to parse source class", e);
            }
        }

        return serializedCode.flip();
    }

    private static byte[] readPayload2(final Path classFilePath, final String sourceMethodName) throws IOException {
        BufferedInputStream in = new BufferedInputStream(new FileInputStream(classFilePath.toFile()));
        ClassFile cf = new ClassFile(new DataInputStream(in));
        MethodInfo implantMethod = cf.getMethod(sourceMethodName);

        if (implantMethod == null) {
            throw new IOException("Cannot find method '" + sourceMethodName + "' in " + classFilePath);
        }

        return implantMethod.getCodeAttribute().getCode();
    }

    private static MethodInfo readPayload3(final Path classFilePath, final String sourceMethodName) throws IOException {
        BufferedInputStream in = new BufferedInputStream(new FileInputStream(classFilePath.toFile()));
        ClassFile cf = new ClassFile(new DataInputStream(in));
        MethodInfo implantMethod = cf.getMethod(sourceMethodName);

        if (implantMethod == null) {
            throw new IOException("Cannot find method '" + sourceMethodName + "' in " + classFilePath);
        }

        return implantMethod;
    }

    public static void dumpOpcodes(final IntBuffer opcodes, final PrintStream out, final String prePad) {
        opcodes.mark();

        while (opcodes.hasRemaining()) {
            int opcode = opcodes.get();
            out.println(prePad + Mnemonic.OPCODE[opcode]);
        }

        opcodes.rewind();
    }

    public static boolean infectTarget(final Path classFilePath, final MethodInfo implantMethod) throws IOException {
        BufferedInputStream in = new BufferedInputStream(new FileInputStream(classFilePath.toFile()));
        ClassFile cf = new ClassFile(new DataInputStream(in));
        in.close();

        MethodInfo main = cf.getMethod("main");
        if (main == null) {
            // Only infect classes with a main function.
            return false;
        }

        MethodInfo targetImplantMethod;
        try {
//            ConstPool sourcePool = implantMethod.getConstPool();
//            ConstPool destPool = cf.getConstPool();
//            for (int n = 0; n < sourcePool.getSize(); n++) {
//                sourcePool.copy(n, destPool, new HashMap<>());
//            }
            ConstPool destPool = cf.getConstPool();

            targetImplantMethod = new MethodInfo(destPool, implantMethod.getName(), implantMethod.getDescriptor());
            targetImplantMethod.setExceptionsAttribute(implantMethod.getExceptionsAttribute());
            //targetImplantMethod.setCodeAttribute((CodeAttribute) implantMethod.getCodeAttribute().copy(cf.getConstPool(), new HashMap<>()));
            HashMap<String, String> classTranslation = new HashMap<>();
            //classTranslation.put("org.example.injector.Main", "org.example.target.Main");
            CodeAttribute copy = (CodeAttribute) implantMethod.getCodeAttribute().copy(destPool, classTranslation);
            copy.setMaxLocals(10);  // Don't know why this is necessary, but it throws an error otherwise.

            // Make it static
            int accessFlags = implantMethod.getAccessFlags();
            accessFlags |= AccessFlag.STATIC;   // Yes, bit-flipping!
            targetImplantMethod.setAccessFlags(accessFlags);

            //targetImplantMethod.getAttributes().clear();    // Cringe workaround due to internal bug in Javassist.
            targetImplantMethod.getAttributes().removeIf(Objects::isNull);
            targetImplantMethod.setCodeAttribute(copy);

            // TODO Now what?

            cf.addMethod(targetImplantMethod);
        } catch (DuplicateMemberException e) {
            // Class already infected
            return false;
        }

        Bytecode newCode = new Bytecode(cf.getConstPool());
        newCode.addInvokestatic("org.example.target.Main", targetImplantMethod.getName(), targetImplantMethod.getDescriptor());
        CodeAttribute newCodeAttr = newCode.toCodeAttribute();

        CodeAttribute mainCode = main.getCodeAttribute();
        ByteBuffer buff = ByteBuffer.allocate(newCodeAttr.getCodeLength() + main.getCodeAttribute().getCodeLength());
        buff.put(newCodeAttr.getCode());
        buff.put(mainCode.getCode());

        CodeAttribute newCodeAttribute = new CodeAttribute(
                cf.getConstPool(),
                mainCode.getMaxStack(),
                mainCode.getMaxLocals(),
                buff.array(),
                mainCode.getExceptionTable());
        main.setCodeAttribute(newCodeAttribute);

        cf.write(new DataOutputStream(new FileOutputStream(classFilePath.toFile())));
        System.out.println("[+] Overwrote class: " + classFilePath);

        return true;
    }

    /**
     * Always assert a certain condition.
     * Java assertions must explicitly be enabled with the -ea flag. This method always asserts a given condition.
     * @param condition Expression
     * @throws RuntimeException If condition is false
     */
    private static void assertThat(boolean condition) {
        if (!condition) {
            throw new RuntimeException("[!] Assertion failed! This is an indication of an internal error.");
        }
    }
}