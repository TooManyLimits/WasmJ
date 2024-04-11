package io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.memory;

import io.github.toomanylimits.wasmj.compiling.compiler.CompilingSimpleInstructionVisitor;
import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleInstruction;
import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleModule;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.ClassGenCallback;
import io.github.toomanylimits.wasmj.compiling.helpers.BytecodeHelper;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

/**
 * - The descriptor of the type to load from the array.
 * - Whether to upcast to a long.
 * - Whether to mask the bits.
 * - The offset to grab the object at.
 */
public record MemoryLoad(String loadDescriptor, boolean upcastToLong, boolean mask, int offset) implements SimpleInstruction.Intrinsic {

    // Wasm demands little endian
    public static final VarHandle SHORT_HANDLE = MethodHandles.byteArrayViewVarHandle(short[].class, ByteOrder.LITTLE_ENDIAN);
    public static final VarHandle INT_HANDLE = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);
    public static final VarHandle LONG_HANDLE = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
    public static final VarHandle FLOAT_HANDLE = MethodHandles.byteArrayViewVarHandle(float[].class, ByteOrder.LITTLE_ENDIAN);
    public static final VarHandle DOUBLE_HANDLE = MethodHandles.byteArrayViewVarHandle(double[].class, ByteOrder.LITTLE_ENDIAN);

    @Override
    public void atCallSite(SimpleModule module, MethodVisitor visitor, CompilingSimpleInstructionVisitor compilingVisitor) {
        // Stack = [defaultIndex]
        BytecodeHelper.constInt(visitor, offset); // [defaultIndex, offset]
        visitor.visitInsn(Opcodes.IADD); // [defaultIndex + offset]
        // If it's a byte[], special behavior:
        if (loadDescriptor.equals("B")) {
            module.memory.getMemory(module, visitor); // [defaultIndex + offset, byte array]
            visitor.visitInsn(Opcodes.SWAP); // [byte array, defaultIndex + offset]
            visitor.visitInsn(Opcodes.BALOAD); // [value]
        } else {
            // Fetch the var handle
            switch (loadDescriptor) {
                case "S" -> visitor.visitFieldInsn(Opcodes.GETSTATIC, Type.getInternalName(MemoryLoad.class), "SHORT_HANDLE", Type.getDescriptor(VarHandle.class));
                case "I" -> visitor.visitFieldInsn(Opcodes.GETSTATIC, Type.getInternalName(MemoryLoad.class), "INT_HANDLE", Type.getDescriptor(VarHandle.class));
                case "J" -> visitor.visitFieldInsn(Opcodes.GETSTATIC, Type.getInternalName(MemoryLoad.class), "LONG_HANDLE", Type.getDescriptor(VarHandle.class));
                case "F" -> visitor.visitFieldInsn(Opcodes.GETSTATIC, Type.getInternalName(MemoryLoad.class), "FLOAT_HANDLE", Type.getDescriptor(VarHandle.class));
                case "D" -> visitor.visitFieldInsn(Opcodes.GETSTATIC, Type.getInternalName(MemoryLoad.class), "DOUBLE_HANDLE", Type.getDescriptor(VarHandle.class));
                default -> throw new IllegalArgumentException();
            } // [defaultIndex + offset, varHandle]
            visitor.visitInsn(Opcodes.SWAP); // [varHandle, defaultIndex + offset]
            module.memory.getMemory(module, visitor); // [varHandle, defaultIndex + offset, byte array]
            visitor.visitInsn(Opcodes.SWAP); // [varHandle, byte array, defaultIndex + offset]
            visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(VarHandle.class), "get", "([BI)" + loadDescriptor, false); // [value]
        }
        // Stack = [fetched value].
        // Now convert the value according to what's needed:
        if (mask) {
            if (upcastToLong) {
                visitor.visitInsn(Opcodes.I2L);
                switch (loadDescriptor) {
                    case "B" -> BytecodeHelper.constLong(visitor, 0xFFL);
                    case "S" -> BytecodeHelper.constLong(visitor, 0xFFFFL);
                    case "I" -> BytecodeHelper.constLong(visitor, 0xFFFFFFFFL);
                    default -> throw new IllegalArgumentException();
                }
                visitor.visitInsn(Opcodes.LAND);
            } else {
                switch (loadDescriptor) {
                    case "B" -> BytecodeHelper.constInt(visitor, 0xFF);
                    case "S" -> BytecodeHelper.constInt(visitor, 0xFFFF);
                    default -> throw new IllegalArgumentException();
                }
                visitor.visitInsn(Opcodes.IAND);
            }
        } else if (upcastToLong) {
            visitor.visitInsn(Opcodes.I2L);
        }
        // Stack = [converted value]
    }

    @Override
    public ClassGenCallback classGenCallback() {
        return null;
    }
}
