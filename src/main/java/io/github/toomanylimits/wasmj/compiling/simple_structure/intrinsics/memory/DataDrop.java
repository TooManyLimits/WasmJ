package io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.memory;

import io.github.toomanylimits.wasmj.compiling.compiler.CompilingSimpleInstructionVisitor;
import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleInstruction;
import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleModule;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.ClassGenCallback;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.sandbox.DecMemoryBy;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public record DataDrop(int dataIndex) implements SimpleInstruction.Intrinsic {

    @Override
    public void atCallSite(SimpleModule module, MethodVisitor visitor, CompilingSimpleInstructionVisitor compilingVisitor) {
        if (module.instance.limiter.countsMemory) {
            // If we count memory, first get the field:
            module.datas[dataIndex].getBytes(module, visitor); // [bytes]
            // If it's already null, do nothing
            Label alreadyNull = new Label();
            visitor.visitJumpInsn(Opcodes.IFNULL, alreadyNull); // []
            // If it's not null, decrease the used memory by the data's size, and set it to null.
            compilingVisitor.visitIntrinsic(new DecMemoryBy(module.datas[dataIndex].bytes().length)); // []
            visitor.visitInsn(Opcodes.ACONST_NULL); // [null]
            module.datas[dataIndex].setBytes(module, visitor); // []
            // Emit alreadyNull label
            visitor.visitLabel(alreadyNull);
        } else {
            // If we don't count memory, just set the field to null
            visitor.visitInsn(Opcodes.ACONST_NULL); // [null]
            module.datas[dataIndex].setBytes(module, visitor); // []
        }
    }

    @Override
    public ClassGenCallback classGenCallback() {
        return null;
    }
}
