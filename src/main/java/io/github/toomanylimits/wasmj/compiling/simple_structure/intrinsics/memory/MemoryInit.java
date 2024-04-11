package io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.memory;

import io.github.toomanylimits.wasmj.compiling.helpers.Names;
import io.github.toomanylimits.wasmj.compiling.compiler.CompilingSimpleInstructionVisitor;
import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleInstruction;
import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleModule;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.ClassGenCallback;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.sandbox.IncInstructions;
import io.github.toomanylimits.wasmj.compiling.helpers.BytecodeHelper;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.HashSet;
import java.util.Set;

public record MemoryInit(int dataIndex) implements SimpleInstruction.Intrinsic {

    @Override
    public void atCallSite(SimpleModule module, MethodVisitor visitor, CompilingSimpleInstructionVisitor compilingVisitor) {
        // Stack = [dest, src, count]
        // Push the arrays and call the helper.
        module.datas[dataIndex].getBytes(module, visitor); // [dest, src, count, data array]
        module.memory.getMemory(module, visitor); // [dest, src, count, data array, mem array]
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, Names.className(module.moduleName), "memoryInit", "(III[B[B)V", false); // []
    }

    @Override
    public ClassGenCallback classGenCallback() {
        return (module, classWriter) -> {

            // Locals:
            // 0 -> dest defaultIndex
            // 1 -> src defaultIndex
            // 2 -> count
            // 3 -> data array
            // 4 -> mem array

            // Create visitor
            MethodVisitor visitor = classWriter.visitMethod(Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC, "memoryInit", "(III[B[B)V", null, null);
            visitor.visitCode();

            // Sandboxing
            Set<ClassGenCallback> usedCallbacks = new HashSet<>();
            if (module.instance.limiter.countsInstructions) {
                // Penalize with count / 8 instructions, for the arraycopy.
                visitor.visitVarInsn(Opcodes.ILOAD, 2);
                BytecodeHelper.constInt(visitor, 8);
                visitor.visitInsn(Opcodes.IDIV);
                visitor.visitInsn(Opcodes.I2L);
                IncInstructions.INSTANCE.atCallSite(module, visitor, null);
                usedCallbacks.add(IncInstructions.INSTANCE.classGenCallback());
            }

            // Call System.arraycopy
            visitor.visitVarInsn(Opcodes.ALOAD, 3);
            visitor.visitVarInsn(Opcodes.ILOAD, 1);
            visitor.visitVarInsn(Opcodes.ALOAD, 4);
            visitor.visitVarInsn(Opcodes.ILOAD, 0);
            visitor.visitVarInsn(Opcodes.ILOAD, 2);
            BytecodeHelper.callNamedStaticMethod("arraycopy", visitor, System.class);

            // Return and end
            visitor.visitInsn(Opcodes.RETURN);
            visitor.visitMaxs(0, 0);
            visitor.visitEnd();

            return usedCallbacks;
        };
    }
}
