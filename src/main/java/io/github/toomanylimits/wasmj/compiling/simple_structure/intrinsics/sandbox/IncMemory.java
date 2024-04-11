package io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.sandbox;

import io.github.toomanylimits.wasmj.compiling.helpers.Names;
import io.github.toomanylimits.wasmj.compiling.compiler.CompilingSimpleInstructionVisitor;
import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleInstruction;
import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleModule;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.ClassGenCallback;
import io.github.toomanylimits.wasmj.runtime.sandbox.InstanceLimiter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Increment memory by the long amount on top of the stack.
 */
public class IncMemory implements SimpleInstruction.Intrinsic {

    public static final IncMemory INSTANCE = new IncMemory();
    private IncMemory() {}

    @Override
    public void atCallSite(SimpleModule module, MethodVisitor visitor, CompilingSimpleInstructionVisitor compilingVisitor) {
        if (!module.instance.limiter.countsMemory)
            throw new IllegalStateException("IncMemory intrinsic should only be called if limiter.countsMemory is true!");
        // Get limiter, swap, call inc
        String className = Names.className(module.moduleName);
        String fieldName = Names.limiterFieldName();
        String descriptor = Type.getDescriptor(InstanceLimiter.class);
        visitor.visitFieldInsn(Opcodes.GETSTATIC, className, fieldName, descriptor);
        visitor.visitInsn(Opcodes.DUP_X2);
        visitor.visitInsn(Opcodes.POP);
        visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(InstanceLimiter.class), "incHeapMemoryUsed", "(J)V", false);
    }

    @Override
    public ClassGenCallback classGenCallback() {
        return null;
    }
}
