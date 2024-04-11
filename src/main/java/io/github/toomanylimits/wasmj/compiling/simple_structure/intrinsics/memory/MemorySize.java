package io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.memory;

import io.github.toomanylimits.wasmj.compiling.compiler.CompilingSimpleInstructionVisitor;
import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleInstruction;
import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleModule;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.ClassGenCallback;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class MemorySize implements SimpleInstruction.Intrinsic {

    public static final MemorySize INSTANCE = new MemorySize();
    private MemorySize() {}


    @Override
    public void atCallSite(SimpleModule module, MethodVisitor visitor, CompilingSimpleInstructionVisitor compilingVisitor) {
        module.memory.getMemory(module, visitor);
        visitor.visitInsn(Opcodes.ARRAYLENGTH);
    }

    @Override
    public ClassGenCallback classGenCallback() {
        return null;
    }
}
