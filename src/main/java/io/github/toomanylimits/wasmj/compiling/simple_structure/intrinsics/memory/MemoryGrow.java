package io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.memory;

import io.github.toomanylimits.wasmj.compiling.helpers.Names;
import io.github.toomanylimits.wasmj.compiling.compiler.Compiler;
import io.github.toomanylimits.wasmj.compiling.compiler.CompilingSimpleInstructionVisitor;
import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleInstruction;
import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleModule;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.ClassGenCallback;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.sandbox.IncInstructions;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.sandbox.IncMemory;
import io.github.toomanylimits.wasmj.compiling.helpers.BytecodeHelper;
import io.github.toomanylimits.wasmj.runtime.errors.WasmCodeException;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.HashSet;
import java.util.Set;

public class MemoryGrow implements SimpleInstruction.Intrinsic {

    public static final MemoryGrow INSTANCE = new MemoryGrow();
    private MemoryGrow() {}

    private static final String helperMethodName = "memoryGrow";
    private static final String helperMethodDesc = "(I[B)[B";

    @Override
    public void atCallSite(SimpleModule module, MethodVisitor visitor, CompilingSimpleInstructionVisitor compilingVisitor) {
        // Stack = [growBy]
        module.memory.getMemory(module, visitor); // [growBy, oldMem]
        visitor.visitInsn(Opcodes.DUP); // [growBy, oldMem, oldMem]
        visitor.visitInsn(Opcodes.ARRAYLENGTH); // [growBy, oldMem, oldMem.length]
        BytecodeHelper.constInt(visitor, Compiler.WASM_PAGE_SIZE); // [growBy, oldMem, oldMem.length, page size]
        visitor.visitInsn(Opcodes.IDIV); // [growBy, oldMem, oldMem.length / page size]
        visitor.visitVarInsn(Opcodes.ISTORE, compilingVisitor.getNextLocalSlot()); // [growBy, oldMem]
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, Names.className(module.moduleName), helperMethodName, helperMethodDesc, false); // [newMem]
        module.memory.setMemory(module, visitor); // []
        visitor.visitVarInsn(Opcodes.ILOAD, compilingVisitor.getNextLocalSlot()); // [oldMem.length / page size]
    }

    // Helpers in java
    public static void boundsCheckHelper(int requested, byte[] oldMem) throws WasmCodeException {
        if (requested < 0 || (requested >= Integer.MAX_VALUE / Compiler.WASM_PAGE_SIZE))
            throw new WasmCodeException("Attempt to call memory.grow with too large of value: " + requested + " pages. WasmJ doesn't support this!");
        if (oldMem.length + (requested * Compiler.WASM_PAGE_SIZE) < 0)
            throw new WasmCodeException("memory.grow by " + requested + " pages caused memory size to overflow the i32 limit. WasmJ doesn't support this!");
    }

    public static byte[] growMemoryHelper(int requested, byte[] oldMem) {
        byte[] newMem = new byte[oldMem.length + requested * Compiler.WASM_PAGE_SIZE];
        System.arraycopy(oldMem, 0, newMem, 0, oldMem.length);
        return newMem;
    }

    @Override
    public ClassGenCallback classGenCallback() {
        // Emit the callback.
        return (module, classWriter) -> {
            int access = Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC;

            MethodVisitor visitor = classWriter.visitMethod(access, helperMethodName, helperMethodDesc, null, null);
            visitor.visitCode();
            // Method to grow the array!

            // Bounds check:
            visitor.visitVarInsn(Opcodes.ILOAD, 0); // [requested]
            visitor.visitVarInsn(Opcodes.ALOAD, 1); // [old memory]
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(MemoryGrow.class), "boundsCheckHelper", "(I[B)V", false);

            // Sandboxing
            Set<ClassGenCallback> usedCallbacks = new HashSet<>();
            // If we're counting memory, then increment memory usage by requested * WASM_PAGE_SIZE.
            if (module.instance.limiter.countsMemory) {
                visitor.visitVarInsn(Opcodes.ILOAD, 0); // [requested]
                visitor.visitInsn(Opcodes.I2L); // [(long) requested]
                BytecodeHelper.constLong(visitor, Compiler.WASM_PAGE_SIZE); // [(long) requested, page size]
                visitor.visitInsn(Opcodes.LMUL); // [(long) requested * page size]
                // Increment memory by the long.
                IncMemory.INSTANCE.atCallSite(module, visitor, null);
                usedCallbacks.add(IncMemory.INSTANCE.classGenCallback());
            }
            // If we're counting instructions, increment the instruction counter by oldArraySize / 8
            if (module.instance.limiter.countsInstructions) {
                visitor.visitVarInsn(Opcodes.ALOAD, 1); // [oldMem]
                visitor.visitInsn(Opcodes.ARRAYLENGTH); // [oldMem.length]
                BytecodeHelper.constInt(visitor, 8); // [oldMem.length, 8]
                visitor.visitInsn(Opcodes.IDIV); // [oldMem.length / 8]
                visitor.visitInsn(Opcodes.I2L); // [(long) oldMem.length / 8]
                IncInstructions.INSTANCE.atCallSite(module, visitor, null);
                usedCallbacks.add(IncInstructions.INSTANCE.classGenCallback());
            }

            // Do the actual memory grow:
            visitor.visitVarInsn(Opcodes.ILOAD, 0); // [requested]
            visitor.visitVarInsn(Opcodes.ALOAD, 1); // [requested, oldMem]
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(MemoryGrow.class), "growMemoryHelper", "(I[B)[B", false); // [newMem]

            // End the visitor
            visitor.visitInsn(Opcodes.ARETURN);
            visitor.visitMaxs(0, 0);
            visitor.visitEnd();

            // Return the used callbacks
            return usedCallbacks;
        };
    }


}
