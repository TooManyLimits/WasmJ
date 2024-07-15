package io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.memory;

import io.github.toomanylimits.wasmj.compiling.compiler.CompilingSimpleInstructionVisitor;
import io.github.toomanylimits.wasmj.compiling.helpers.Names;
import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleInstruction;
import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleModule;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.ClassGenCallback;
import io.github.toomanylimits.wasmj.compiling.helpers.BytecodeHelper;
import io.github.toomanylimits.wasmj.parsing.types.ValType;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * - The descriptor of the type to load from the array.
 * - The type which is expected as output.
 * - Whether to read the type as unsigned
 * - The offset to grab with.
 */
public record MemoryLoad(String loadDescriptor, ValType type, boolean unsigned, int offset) implements SimpleInstruction.Intrinsic {

    // Wasm demands little endian
    public static final VarHandle SHORT_HANDLE = MethodHandles.byteArrayViewVarHandle(short[].class, ByteOrder.LITTLE_ENDIAN);
    public static final VarHandle INT_HANDLE = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);
    public static final VarHandle LONG_HANDLE = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
    public static final VarHandle FLOAT_HANDLE = MethodHandles.byteArrayViewVarHandle(float[].class, ByteOrder.LITTLE_ENDIAN);
    public static final VarHandle DOUBLE_HANDLE = MethodHandles.byteArrayViewVarHandle(double[].class, ByteOrder.LITTLE_ENDIAN);

    private static final Map<ValType, Map<String, Map<Boolean, ClassGenCallback>>> CLASS_GEN_CALLBACKS = new HashMap<>();

    @Override
    public void atCallSite(SimpleModule module, MethodVisitor visitor, CompilingSimpleInstructionVisitor compilingVisitor) {
        // Stack = [index]
        BytecodeHelper.constInt(visitor, offset); // [index, offset]
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, Names.className(module.moduleName), helperName(), helperDescriptor(), false); // [value]
        // Stack = [value]. Done!
    }

    private String helperName() {
        return "memoryLoadHelper_" + (unsigned ? "unsigned_" : "") + loadDescriptor + "_as_" + type.name();
    }
    private String helperDescriptor() {
        return "(II)" + type.descriptor;
    }

    @Override
    public ClassGenCallback classGenCallback() {
        return CLASS_GEN_CALLBACKS.computeIfAbsent(type, type -> new HashMap<>()).computeIfAbsent(loadDescriptor, loadDescriptor -> new HashMap<>()).computeIfAbsent(unsigned, unsigned -> (module, classWriter) -> {

            MethodVisitor visitor = classWriter.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, helperName(), helperDescriptor(), null, null);
            visitor.visitCode();

            if (BytecodeHelper.DEBUG_PRINTS_ENABLED) {
                 BytecodeHelper.debugPrint(visitor, "Loading " + loadDescriptor + " from memory. Offset = ");
                 visitor.visitVarInsn(Opcodes.ILOAD, 1);
                 BytecodeHelper.debugPrintInt(visitor);
                 visitor.visitInsn(Opcodes.POP);
            }

            // Params = [index, offset]
            // Stack = []

            // If it's not a byte, then need to use a VarHandle:
            if (!loadDescriptor.equals("B")) {
                switch (loadDescriptor) {
                    case "S" -> visitor.visitFieldInsn(Opcodes.GETSTATIC, Type.getInternalName(MemoryLoad.class), "SHORT_HANDLE", Type.getDescriptor(VarHandle.class));
                    case "I" -> visitor.visitFieldInsn(Opcodes.GETSTATIC, Type.getInternalName(MemoryLoad.class), "INT_HANDLE", Type.getDescriptor(VarHandle.class));
                    case "J" -> visitor.visitFieldInsn(Opcodes.GETSTATIC, Type.getInternalName(MemoryLoad.class), "LONG_HANDLE", Type.getDescriptor(VarHandle.class));
                    case "F" -> visitor.visitFieldInsn(Opcodes.GETSTATIC, Type.getInternalName(MemoryLoad.class), "FLOAT_HANDLE", Type.getDescriptor(VarHandle.class));
                    case "D" -> visitor.visitFieldInsn(Opcodes.GETSTATIC, Type.getInternalName(MemoryLoad.class), "DOUBLE_HANDLE", Type.getDescriptor(VarHandle.class));
                    default -> throw new IllegalArgumentException();
                } // [varHandle]
            }
            // Fetch the memory:
            module.memory.getMemory(module, visitor); // [varHandle?, byte array]
            // Compute the index:
            visitor.visitVarInsn(Opcodes.ILOAD, 0); // [varHandle?, byte array, index]
            visitor.visitVarInsn(Opcodes.ILOAD, 1); // [varHandle?, byte array, index, offset]
            visitor.visitInsn(Opcodes.IADD); // [varHandle?, byte array, index + offset]
            // Now fetch the value. Byte value means we can just BALOAD, but otherwise need the VarHandle method.
            if (loadDescriptor.equals("B"))
                visitor.visitInsn(Opcodes.BALOAD);
            else
                visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(VarHandle.class), "get", "([BI)" + loadDescriptor, false); // [value]

            // Stack = [fetched value]

            // If it's unsigned, do the masking operation
            if (unsigned) {
                if (type == ValType.I64) {
                    // Stack = [int]
                    visitor.visitInsn(Opcodes.I2L); // Stack = [long]
                    switch (loadDescriptor) {
                        case "B" -> BytecodeHelper.constLong(visitor, 0xFFL);
                        case "S" -> BytecodeHelper.constLong(visitor, 0xFFFFL);
                        case "I" -> BytecodeHelper.constLong(visitor, 0xFFFFFFFFL);
                        default -> throw new IllegalArgumentException("Invalid MemoryLoad Intrinsic: " + this);
                    } // [long, long]
                    visitor.visitInsn(Opcodes.LAND); // [long], as expected since type == ValType.I64
                } else if (type == ValType.I32) {
                    // Stack = [int]
                    switch (loadDescriptor) {
                        case "B" -> BytecodeHelper.constInt(visitor, 0xFF);
                        case "S" -> BytecodeHelper.constInt(visitor, 0xFFFF);
                        default -> throw new IllegalArgumentException("Invalid MemoryLoad Intrinsic: " + this);
                    } // [int, int]
                    visitor.visitInsn(Opcodes.IAND); // [int], as expected since type == ValType.I32
                } else {
                    throw new IllegalArgumentException("Invalid MemoryLoad Intrinsic: " + this);
                }
            } else if (type == ValType.I64 && !loadDescriptor.equals("J")) {
                // Stack = [int], because loadDescriptor was not J
                visitor.visitInsn(Opcodes.I2L); // [long], as expected since type == ValType.I64
            }

            // End off the visitor
            visitor.visitInsn(type.returnOpcode);
            visitor.visitMaxs(0, 0);
            visitor.visitEnd();

            // No callbacks were used
            return Set.of();
        });
    }
}
