package io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.table;

import io.github.toomanylimits.wasmj.compiling.compiler.CompilingSimpleInstructionVisitor;
import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleInstruction;
import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleModule;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.ClassGenCallback;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public record TableSize(int tableIndex) implements SimpleInstruction.Intrinsic {

    @Override
    public void atCallSite(SimpleModule module, MethodVisitor visitor, CompilingSimpleInstructionVisitor compilingVisitor) {
        module.tables[tableIndex].getTable(module, visitor); // [array]
        visitor.visitInsn(Opcodes.ARRAYLENGTH); // [array.length]
    }

    @Override
    public ClassGenCallback classGenCallback() {
        return null;
    }
}
