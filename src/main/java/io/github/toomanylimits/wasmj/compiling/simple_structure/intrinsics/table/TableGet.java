package io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.table;

import io.github.toomanylimits.wasmj.compiling.compiler.CompilingSimpleInstructionVisitor;
import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleInstruction;
import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleModule;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.ClassGenCallback;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.sandbox.IncRefCount;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public record TableGet(int tableIndex) implements SimpleInstruction.Intrinsic {
    @Override
    public void atCallSite(SimpleModule module, MethodVisitor visitor, CompilingSimpleInstructionVisitor compilingVisitor) {
        // Stack = [defaultIndex]
        module.tables[tableIndex].getTable(module, visitor); // [defaultIndex, array]
        visitor.visitInsn(Opcodes.SWAP); // [array, defaultIndex]
        visitor.visitInsn(Opcodes.AALOAD); // [array[defaultIndex]]
        // If we count memory usage, increment the refcount
        if (module.instance.limiter.countsMemory) {
            visitor.visitInsn(Opcodes.DUP);
            compilingVisitor.visitIntrinsic(IncRefCount.INSTANCE);
        }
    }

    @Override
    public ClassGenCallback classGenCallback() {
        return null;
    }
}
