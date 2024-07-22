package io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.sandbox;

import io.github.toomanylimits.wasmj.compiling.compiler.CompilingSimpleInstructionVisitor;
import io.github.toomanylimits.wasmj.compiling.helpers.BytecodeHelper;
import io.github.toomanylimits.wasmj.compiling.helpers.Names;
import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleInstruction;
import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleModule;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.ClassGenCallback;
import io.github.toomanylimits.wasmj.runtime.sandbox.InstanceLimiter;
import io.github.toomanylimits.wasmj.runtime.sandbox.RefCountable;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Pops a RefCountable from the top of the stack and decrements its refcount.
 */
public class DecRefCount implements SimpleInstruction.Intrinsic {

    public static final DecRefCount INSTANCE = new DecRefCount();
    private DecRefCount() {}

    @Override
    public void atCallSite(SimpleModule module, MethodVisitor visitor, CompilingSimpleInstructionVisitor compilingVisitor) {
        if (!module.instance.limiter.countsMemory)
            throw new IllegalStateException("DecRefCount intrinsic should only be generated when the module counts memory - bug in compiler!");
        // If non-null, get limiter and call dec().
        // [obj]
        visitor.visitInsn(Opcodes.DUP); // [obj, obj]
        BytecodeHelper.writeIfElse(visitor, Opcodes.IFNULL, ifTrue -> {
            // [obj]
            ifTrue.visitFieldInsn(Opcodes.GETSTATIC, Names.className(module.moduleName), Names.limiterFieldName(), Type.getDescriptor(InstanceLimiter.class)); // [obj, limiter]
            ifTrue.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(RefCountable.class), "dec", Type.getMethodDescriptor(Type.getType(void.class), Type.getType(InstanceLimiter.class)), false); // []
        }, ifFalse -> {
            // [null]
            ifFalse.visitInsn(Opcodes.POP); // []
        });
        // []
    }

    @Override
    public ClassGenCallback classGenCallback() {
        return null;
    }

}
