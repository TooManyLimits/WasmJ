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
 * Increment the instruction counter by the long value on top of the stack.
 */
public class IncInstructions implements SimpleInstruction.Intrinsic {

    public static final IncInstructions INSTANCE = new IncInstructions();
    private IncInstructions() {}

    @Override
    public void atCallSite(SimpleModule module, MethodVisitor visitor, CompilingSimpleInstructionVisitor compilingVisitor) {
        if (!module.instance.limiter.countsInstructions)
            throw new IllegalStateException("Should only call IncInstructions if the instance counts instructions!");
        // Get limiter, swap, call inc
        String className = Names.className(module.moduleName);
        String fieldName = Names.limiterFieldName();
        String descriptor = Type.getDescriptor(InstanceLimiter.class);
        visitor.visitFieldInsn(Opcodes.GETSTATIC, className, fieldName, descriptor);
        visitor.visitInsn(Opcodes.DUP_X2);
        visitor.visitInsn(Opcodes.POP);
        visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(InstanceLimiter.class), "incInstructions", "(J)V", false);
    }

    @Override
    public ClassGenCallback classGenCallback() {
        return null;
    }
}
