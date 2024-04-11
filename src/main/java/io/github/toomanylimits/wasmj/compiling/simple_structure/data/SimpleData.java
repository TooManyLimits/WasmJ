package io.github.toomanylimits.wasmj.compiling.simple_structure.data;

import io.github.toomanylimits.wasmj.compiling.helpers.Names;
import io.github.toomanylimits.wasmj.compiling.compiler.Compiler;
import io.github.toomanylimits.wasmj.compiling.compiler.CompilingSimpleInstructionVisitor;
import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleInstruction;
import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleModule;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.ClassGenCallback;
import io.github.toomanylimits.wasmj.compiling.helpers.BytecodeHelper;
import io.github.toomanylimits.wasmj.runtime.sandbox.InstanceLimiter;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.List;
import java.util.Set;

/**
 * InitializeInstructions is null iff this SimpleData is passive.
 */
public record SimpleData(int declaredIndex, byte[] bytes, List<SimpleInstruction> initializeInstructions) {

    /**
     * Push the byte[] on the stack
     */
    public void getBytes(SimpleModule callingModule, MethodVisitor visitor) {
        // Just read the field
        visitor.visitFieldInsn(Opcodes.GETSTATIC, Names.className(callingModule.moduleName), Names.dataFieldName(declaredIndex), "[B");
    }

    /**
     * Set the byte[] field to the byte[] on top of the stack.
     */
    public void setBytes(SimpleModule callingModule, MethodVisitor visitor) {
        visitor.visitFieldInsn(Opcodes.PUTSTATIC, Names.className(callingModule.moduleName), Names.dataFieldName(declaredIndex), "[B");
    }

    /**
     * Emit the data to the class writer.
     */
    public void emitData(SimpleModule declaringModule, ClassVisitor classWriter, MethodVisitor initFunction, Set<ClassGenCallback> classGenCallbacks) {
        // Store each data byte[] in a field.
        // Increment memory usage by the byte[]'s size:
        if (declaringModule.instance.limiter.countsMemory) {
            // Increment memory use by the byte array size
            initFunction.visitFieldInsn(Opcodes.GETSTATIC, Names.className(declaringModule.moduleName), Names.limiterFieldName(), Type.getDescriptor(InstanceLimiter.class));
            BytecodeHelper.constLong(initFunction, bytes.length); // [limiter, size]
            initFunction.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(InstanceLimiter.class), "incHeapMemoryUsed", "(J)V", false); // []
        }

        // Create the field:
        classWriter.visitField(Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC, Names.dataFieldName(declaredIndex), "[B", null, null);
        // Fetch the byte[] instance from the module, which is the third parameter to the init method
        initFunction.visitVarInsn(Opcodes.ALOAD, Compiler.INIT_FUNCTION_SIMPLE_MODULE_LOCAL); // [module]
        initFunction.visitFieldInsn(Opcodes.GETFIELD, Type.getInternalName(SimpleModule.class), "datas", Type.getDescriptor(SimpleData[].class)); // [module.datas]
        BytecodeHelper.constInt(initFunction, declaredIndex); // [module.datas, dataIndex]
        initFunction.visitInsn(Opcodes.AALOAD); // [module.datas[dataIndex]]
        initFunction.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(SimpleData.class), "bytes", "()[B", false); // [module.datas[dataIndex].bytes]
        // Store it in the newly created field:
        initFunction.visitFieldInsn(Opcodes.PUTSTATIC, Names.className(declaringModule.moduleName), Names.dataFieldName(declaredIndex), "[B"); // []

        // Visit the initialization code, if we have any (meaning it's an Active data):
        if (initializeInstructions != null) {
            CompilingSimpleInstructionVisitor compilingVisitor = new CompilingSimpleInstructionVisitor(declaringModule, initFunction, Compiler.INIT_FUNCTION_NEXT_LOCAL, classGenCallbacks);
            compilingVisitor.emitMultipleInstructions(initializeInstructions);
        }
    }


}
