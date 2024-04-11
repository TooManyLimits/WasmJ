package io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.table;

import io.github.toomanylimits.wasmj.compiling.compiler.CompilingSimpleInstructionVisitor;
import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleInstruction;
import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleModule;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.ClassGenCallback;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.sandbox.DecRefCount;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public record TableSet(int tableIndex) implements SimpleInstruction.Intrinsic {
    @Override
    public void atCallSite(SimpleModule module, MethodVisitor visitor, CompilingSimpleInstructionVisitor compilingVisitor) {
        // Stack = [defaultIndex, valueToStore]
        if (module.instance.limiter.countsMemory) {
            // We do memory counting
            int local = compilingVisitor.getNextLocalSlot();
            visitor.visitVarInsn(Opcodes.ASTORE, local); // [defaultIndex]
            module.tables[tableIndex].getTable(module, visitor); // [defaultIndex, array]
            visitor.visitInsn(Opcodes.SWAP); // [array, defaultIndex]
            // Decrement refcount of the previous element in the table
            visitor.visitInsn(Opcodes.DUP2); // [array, defaultIndex, array, defaultIndex]
            visitor.visitInsn(Opcodes.AALOAD); // [array, defaultIndex, array[defaultIndex]]
            compilingVisitor.visitIntrinsic(DecRefCount.INSTANCE); // [array, defaultIndex]
            // Load the variable and store
            visitor.visitVarInsn(Opcodes.ALOAD, local); // [array, defaultIndex, valueToStore]
            visitor.visitInsn(Opcodes.AASTORE); // [], array[defaultIndex] = valueToStore
        } else {
            // No memory counting, just store the value
            module.tables[tableIndex].getTable(module, visitor); // [defaultIndex, valueToStore, array]
            visitor.visitInsn(Opcodes.DUP_X2); // [array, defaultIndex, valueToStore, array]
            visitor.visitInsn(Opcodes.POP); // [array, defaultIndex, valueToStore]
            visitor.visitInsn(Opcodes.AASTORE); // [], array[defaultIndex] = valueToStore
        }
    }

    @Override
    public ClassGenCallback classGenCallback() {
        return null;
    }
}
