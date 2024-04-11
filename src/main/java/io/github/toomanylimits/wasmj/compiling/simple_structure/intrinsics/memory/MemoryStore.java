package io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.memory;

import io.github.toomanylimits.wasmj.compiling.compiler.CompilingSimpleInstructionVisitor;
import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleInstruction;
import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleModule;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.ClassGenCallback;
import io.github.toomanylimits.wasmj.compiling.helpers.BytecodeHelper;
import io.github.toomanylimits.wasmj.parsing.types.ValType;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.invoke.VarHandle;

/**
 * - The type that's being converted then stored
 * - The descriptor with which to store this value
 * - The offset at which to store
 */
public record MemoryStore(ValType type, String storeDescriptor, int offset) implements SimpleInstruction.Intrinsic {

    @Override
    public void atCallSite(SimpleModule module, MethodVisitor visitor, CompilingSimpleInstructionVisitor compilingVisitor) {
        // Stack = [defaultIndex, value]
        int local = compilingVisitor.getNextLocalSlot();
        visitor.visitVarInsn(type.storeOpcode, local); // [defaultIndex]
        BytecodeHelper.constInt(visitor, offset); // [defaultIndex, offset]
        visitor.visitInsn(Opcodes.IADD); // [defaultIndex + offset]
        // If it's a byte[], special behavior:
        if (storeDescriptor.equals("B")) {
            module.memory.getMemory(module, visitor); // [defaultIndex + offset, byte array]
            visitor.visitInsn(Opcodes.SWAP); // [byte array, defaultIndex + offset]
            visitor.visitVarInsn(type.loadOpcode, local); // [byte array, defaultIndex + offset, value]
            visitor.visitInsn(Opcodes.BASTORE); // []
        } else {
            // Fetch the var handle:
            switch (storeDescriptor) {
                case "S" -> visitor.visitFieldInsn(Opcodes.GETSTATIC, Type.getInternalName(MemoryLoad.class), "SHORT_HANDLE", Type.getDescriptor(VarHandle.class));
                case "I" -> visitor.visitFieldInsn(Opcodes.GETSTATIC, Type.getInternalName(MemoryLoad.class), "INT_HANDLE", Type.getDescriptor(VarHandle.class));
                case "J" -> visitor.visitFieldInsn(Opcodes.GETSTATIC, Type.getInternalName(MemoryLoad.class), "LONG_HANDLE", Type.getDescriptor(VarHandle.class));
                case "F" -> visitor.visitFieldInsn(Opcodes.GETSTATIC, Type.getInternalName(MemoryLoad.class), "FLOAT_HANDLE", Type.getDescriptor(VarHandle.class));
                case "D" -> visitor.visitFieldInsn(Opcodes.GETSTATIC, Type.getInternalName(MemoryLoad.class), "DOUBLE_HANDLE", Type.getDescriptor(VarHandle.class));
                default -> throw new IllegalArgumentException();
            } // [defaultIndex + offset, varHandle]
            // Use it to set the value
            visitor.visitInsn(Opcodes.SWAP); // [varHandle, defaultIndex + offset]
            module.memory.getMemory(module, visitor); // [varHandle, defaultIndex + offset, byte array]
            visitor.visitInsn(Opcodes.SWAP); // [varHandle, byte array, defaultIndex + offset]
            visitor.visitVarInsn(type.loadOpcode, local); // [varHandle, byte array, defaultIndex + offset, value]
            // Downcast from a long if necessary:
            if (type == ValType.I64 && !storeDescriptor.equals("J"))
                visitor.visitInsn(Opcodes.L2I);
            // Execute the store function
            visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(VarHandle.class), "set", "([BI" + storeDescriptor + ")V", false); // []
        }
        // Done!
    }

    @Override
    public ClassGenCallback classGenCallback() {
        return null;
    }
}
