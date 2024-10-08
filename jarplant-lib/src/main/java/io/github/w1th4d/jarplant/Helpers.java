package io.github.w1th4d.jarplant;

import javassist.CtClass;
import javassist.bytecode.*;

import java.io.*;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Helpers {
    private static final Set<Integer> returnOpcodes = Set.of(Opcode.RETURN, Opcode.DRETURN, Opcode.ARETURN, Opcode.FRETURN, Opcode.IRETURN, Opcode.LRETURN);

    public static ClassFile readClassFile(Path classFilePath) throws IOException {
        final ClassFile cf;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(classFilePath.toFile())))) {
            cf = new ClassFile(in);
        }
        return cf;
    }

    public static ClassFile readClassFile(byte[] rawClassData) {
        ClassFile cf;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(rawClassData))) {
            cf = new ClassFile(in);
        } catch (IOException e) {
            throw new RuntimeException("Cannot read ClassFile", e);
        }
        return cf;
    }

    public static ClassFile cloneClassFile(ClassFile original) {
        ClassFile clone;
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            original.write(new DataOutputStream(buffer));
            buffer.flush();
            clone = new ClassFile(new DataInputStream(new ByteArrayInputStream(buffer.toByteArray())));
        } catch (IOException e) {
            throw new RuntimeException("Cannot clone ClassFile", e);
        }

        return clone;
    }

    public static byte[] asByteArray(ClassFile classFile) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            classFile.write(new DataOutputStream(buffer));
            buffer.close();
        } catch (IOException e) {
            throw new RuntimeException("Cannot serialize ClassFile", e);
        }
        return buffer.toByteArray();
    }

    public static boolean isStaticFlagSet(FieldInfo field) {
        int accessFlags = field.getAccessFlags();
        return (accessFlags & AccessFlag.STATIC) != 0;
    }

    public static boolean isVolatileFlagSet(FieldInfo field) {
        int accessFlags = field.getAccessFlags();
        return (accessFlags & AccessFlag.VOLATILE) != 0;
    }

    public static void setStaticFlagForMethod(MethodInfo method) {
        int accessFlags = method.getAccessFlags();
        accessFlags |= AccessFlag.STATIC;   // Yes, bit-flipping!
        method.setAccessFlags(accessFlags);
    }

    public static MethodInfo createAndAddClassInitializerStub(ClassFile instance) throws DuplicateMemberException {
        MethodInfo clinit = new MethodInfo(instance.getConstPool(), MethodInfo.nameClinit, "()V");
        setStaticFlagForMethod(clinit);
        Bytecode stubCode = new Bytecode(instance.getConstPool(), 0, 0);
        stubCode.addReturn(CtClass.voidType);
        clinit.setCodeAttribute(stubCode.toCodeAttribute());

        instance.addMethod(clinit);
        return clinit;
    }

    public static Optional<Integer> searchForEndOfMethodIndex(CodeAttribute codeAttribute, CodeIterator codeIterator) {
        int index = 0;
        while (codeIterator.hasNext()) {
            try {
                index = codeIterator.next();
            } catch (BadBytecode e) {
                throw new RuntimeException(e);
            }
        }

        try {
            DataInput converter = new DataInputStream(new ByteArrayInputStream(codeAttribute.getCode()));
            converter.skipBytes(index);
            int opcode = converter.readUnsignedByte();
            if (!returnOpcodes.contains(opcode)) {
                return Optional.empty();
            }
        } catch (IOException e) {
            throw new RuntimeException("Cannot interpret code attribute", e);
        }

        return Optional.of(index);
    }

    public static byte[] bufferFrom(DataInputStream in) throws IOException {
        ByteArrayOutputStream allocator = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int bytesRead;
        while ((bytesRead = in.read(chunk)) != -1) {
            allocator.write(chunk, 0, bytesRead);
        }

        return allocator.toByteArray();
    }

    public static boolean jarLooksSigned(JarFiddler jar) {
        Pattern regex = Pattern.compile("META-INF/.+\\.SF|DSA|RSA");

        for (JarFiddler.Entry entry : jar) {
            Matcher matcher = regex.matcher(entry.getName());
            if (matcher.matches()) {
                return true;
            }
        }

        return false;
    }
}
