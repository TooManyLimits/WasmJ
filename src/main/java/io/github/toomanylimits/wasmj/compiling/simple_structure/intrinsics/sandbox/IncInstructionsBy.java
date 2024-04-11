package io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.sandbox;

import io.github.toomanylimits.wasmj.compiling.helpers.Names;
import io.github.toomanylimits.wasmj.compiling.compiler.CompilingSimpleInstructionVisitor;
import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleInstruction;
import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleModule;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.ClassGenCallback;
import io.github.toomanylimits.wasmj.compiling.helpers.BytecodeHelper;
import io.github.toomanylimits.wasmj.runtime.sandbox.InstanceLimiter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Increment the instruction counter by the given amount.
 */
public record IncInstructionsBy(long amount) implements SimpleInstruction.Intrinsic {

    @Override
    public void atCallSite(SimpleModule module, MethodVisitor visitor, CompilingSimpleInstructionVisitor compilingVisitor) {
        if (!module.instance.limiter.countsInstructions)
            throw new IllegalStateException("Should only call IncInstructionsBy if the instance counts instructions!");
        if (amount > 0) {
            // Get limiter, get count, call inc
            String className = Names.className(module.moduleName);
            String fieldName = Names.limiterFieldName();
            String descriptor = Type.getDescriptor(InstanceLimiter.class);
            visitor.visitFieldInsn(Opcodes.GETSTATIC, className, fieldName, descriptor);
            BytecodeHelper.constLong(visitor, amount);
            visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(InstanceLimiter.class), "incInstructions", "(J)V", false);
        }
    }

    @Override
    public ClassGenCallback classGenCallback() {
        return null;
    }
}
