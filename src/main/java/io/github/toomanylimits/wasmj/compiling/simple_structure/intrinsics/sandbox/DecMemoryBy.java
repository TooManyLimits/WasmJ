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
 * Decrement memory by the long amount given.
 */
public record DecMemoryBy(long amount) implements SimpleInstruction.Intrinsic {

    @Override
    public void atCallSite(SimpleModule module, MethodVisitor visitor, CompilingSimpleInstructionVisitor compilingVisitor) {
        if (!module.instance.limiter.countsMemory)
            throw new IllegalStateException("DecMemoryBy intrinsic should only be called if limiter.countsMemory is true!");
        if (amount > 0) {
            // Get limiter, get count, call dec
            String className = Names.className(module.moduleName);
            String fieldName = Names.limiterFieldName();
            String descriptor = Type.getDescriptor(InstanceLimiter.class);
            visitor.visitFieldInsn(Opcodes.GETSTATIC, className, fieldName, descriptor);
            BytecodeHelper.constLong(visitor, amount);
            visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(InstanceLimiter.class), "decHeapMemoryUsed", "(J)V", false);
        }
    }

    @Override
    public ClassGenCallback classGenCallback() {
        return null;
    }
}
