package io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.misc;

import io.github.toomanylimits.wasmj.compiling.compiler.CompilingSimpleInstructionVisitor;
import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleInstruction;
import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleModule;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.ClassGenCallback;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.sandbox.DecRefCount;
import io.github.toomanylimits.wasmj.compiling.helpers.BytecodeHelper;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class RefIsNull implements SimpleInstruction.Intrinsic {

    public static final RefIsNull INSTANCE = new RefIsNull();
    private RefIsNull() {}

    @Override
    public void atCallSite(SimpleModule module, MethodVisitor visitor, CompilingSimpleInstructionVisitor compilingVisitor) {
        // If we count memory, need something more advanced
        if (module.instance.limiter.countsMemory) {
            Label isNull = new Label();
            Label end = new Label();
            visitor.visitJumpInsn(Opcodes.IFNULL, isNull); // Jump if it's null
            compilingVisitor.visitIntrinsic(DecRefCount.INSTANCE); // Not null, decrement the refcount
            visitor.visitInsn(Opcodes.ICONST_0); // Not null, push 0
            visitor.visitJumpInsn(Opcodes.GOTO, end);

            visitor.visitLabel(isNull);
            visitor.visitInsn(Opcodes.ICONST_1); // It's null, push 1

            visitor.visitLabel(end);
        } else {
            // Otherwise, just a simple BytecodeHelper.test().
            BytecodeHelper.test(visitor, Opcodes.IFNULL);
        }
    }

    @Override
    public ClassGenCallback classGenCallback() {
        return null;
    }
}
