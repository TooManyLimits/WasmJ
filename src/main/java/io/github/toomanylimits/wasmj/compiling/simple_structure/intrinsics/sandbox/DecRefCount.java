package io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.sandbox;

import io.github.toomanylimits.wasmj.compiling.compiler.CompilingSimpleInstructionVisitor;
import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleInstruction;
import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleModule;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.ClassGenCallback;
import org.objectweb.asm.MethodVisitor;

/**
 * Decrement the refcount of the object on top of the stack.
 */
public class DecRefCount implements SimpleInstruction.Intrinsic {

    public static final DecRefCount INSTANCE = new DecRefCount();
    private DecRefCount() {}

    @Override
    public void atCallSite(SimpleModule module, MethodVisitor visitor, CompilingSimpleInstructionVisitor compilingVisitor) {
        if (!module.instance.limiter.countsMemory)
            throw new IllegalStateException("DecRefCount intrinsic should only be generated when the module counts memory - bug in compiler!");
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public ClassGenCallback classGenCallback() {
        return (module, classVisitor) -> {
            if (!module.instance.limiter.countsMemory)
                throw new IllegalStateException("DecRefCount intrinsic should only be generated when the module counts memory - bug in compiler!");
            throw new UnsupportedOperationException("TODO");
        };
    }

}
