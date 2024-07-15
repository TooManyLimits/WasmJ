package io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.memory;

import io.github.toomanylimits.wasmj.compiling.compiler.CompilingSimpleInstructionVisitor;
import io.github.toomanylimits.wasmj.compiling.helpers.Names;
import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleInstruction;
import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleModule;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.ClassGenCallback;
import io.github.toomanylimits.wasmj.compiling.helpers.BytecodeHelper;
import io.github.toomanylimits.wasmj.parsing.types.ValType;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.invoke.VarHandle;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * - The type that's being converted then stored
 * - The descriptor with which to store this value
 * - The offset at which to store
 */
public record MemoryStore(ValType type, String storeDescriptor, int offset) implements SimpleInstruction.Intrinsic {

    private static final Map<ValType, Map<String, ClassGenCallback>> CLASS_GEN_CALLBACKS = new HashMap<>();

    @Override
    public void atCallSite(SimpleModule module, MethodVisitor visitor, CompilingSimpleInstructionVisitor compilingVisitor) {
        // Stack = [defaultIndex, value]
        // Push the offset and call the helper!
        BytecodeHelper.constInt(visitor, offset); // [defaultIndex, value, offset]
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, Names.className(module.moduleName), helperName(), helperDescriptor(), false); // []
    }

    private String helperName() {
        return "memoryStoreHelper_" + type.name() + "_as_" + storeDescriptor;
    }
    private String helperDescriptor() {
        return "(I" + type.descriptor + "I)V";
    }

    @Override
    public ClassGenCallback classGenCallback() {
        return CLASS_GEN_CALLBACKS.computeIfAbsent(type, type -> new HashMap<>()).computeIfAbsent(storeDescriptor, storeDescriptor -> (module, classWriter) -> {

            MethodVisitor visitor = classWriter.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, helperName(), helperDescriptor(), null, null);
            visitor.visitCode();

            if (BytecodeHelper.DEBUG_PRINTS_ENABLED) {
                 BytecodeHelper.debugPrint(visitor, "Storing " + storeDescriptor + " to memory. Offset = ");
                 visitor.visitVarInsn(Opcodes.ILOAD, 1 + type.stackSlots);
                 BytecodeHelper.debugPrintInt(visitor);
                 visitor.visitInsn(Opcodes.POP);
            }

            // Params: [index, value, offset]
            // Stack: []

            // If we're not storing a byte, we need to push a VarHandle first:
            if (!storeDescriptor.equals("B")) {
                switch (storeDescriptor) {
                    case "S" -> visitor.visitFieldInsn(Opcodes.GETSTATIC, Type.getInternalName(MemoryLoad.class), "SHORT_HANDLE", Type.getDescriptor(VarHandle.class));
                    case "I" -> visitor.visitFieldInsn(Opcodes.GETSTATIC, Type.getInternalName(MemoryLoad.class), "INT_HANDLE", Type.getDescriptor(VarHandle.class));
                    case "J" -> visitor.visitFieldInsn(Opcodes.GETSTATIC, Type.getInternalName(MemoryLoad.class), "LONG_HANDLE", Type.getDescriptor(VarHandle.class));
                    case "F" -> visitor.visitFieldInsn(Opcodes.GETSTATIC, Type.getInternalName(MemoryLoad.class), "FLOAT_HANDLE", Type.getDescriptor(VarHandle.class));
                    case "D" -> visitor.visitFieldInsn(Opcodes.GETSTATIC, Type.getInternalName(MemoryLoad.class), "DOUBLE_HANDLE", Type.getDescriptor(VarHandle.class));
                    default -> throw new IllegalArgumentException();
                } // [varHandle]
            }
            // Now push the byte array, then index, then value
            module.memory.getMemory(module, visitor); // [varHandle?, byte array]
            visitor.visitVarInsn(Opcodes.ILOAD, 0); // [varHandle?, byte array, index]
            visitor.visitVarInsn(Opcodes.ILOAD, 1 + type.stackSlots); // [varHandle?, byte array, index, offset]
            visitor.visitInsn(Opcodes.IADD); // [varHandle?, byte array, index + offset]
            visitor.visitVarInsn(type.loadOpcode, 1); // [varHandle?, byte array, index + offset, value]
            // Downcast from a long if necessary:
            if (type == ValType.I64 && !storeDescriptor.equals("J")) visitor.visitInsn(Opcodes.L2I);

            // If it's a byte array, just use BASTORE, otherwise use the VarHandle
            if (storeDescriptor.equals("B"))
                visitor.visitInsn(Opcodes.BASTORE);
            else
                visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(VarHandle.class), "set", "([BI" + storeDescriptor + ")V", false); // []

            // End off the method visitor
            visitor.visitInsn(Opcodes.RETURN);
            visitor.visitMaxs(0, 0);
            visitor.visitEnd();

            // No other callbacks were needed
            return Set.of();
        });
    }
}
