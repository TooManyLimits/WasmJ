package io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.misc;

import io.github.toomanylimits.wasmj.compiling.compiler.CompilingSimpleInstructionVisitor;
import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleInstruction;
import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleModule;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.ClassGenCallback;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.sandbox.IncRefCount;
import io.github.toomanylimits.wasmj.runtime.types.FuncRefInstance;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.invoke.MethodHandle;

public record RefFunc(int funcIndex) implements SimpleInstruction.Intrinsic {

    @Override
    public void atCallSite(SimpleModule module, MethodVisitor visitor, CompilingSimpleInstructionVisitor compilingVisitor) {
        Handle asmHandle = module.functions[funcIndex].getHandle(module);
        visitor.visitTypeInsn(Opcodes.NEW, Type.getInternalName(FuncRefInstance.class)); // [funcref]
        visitor.visitInsn(Opcodes.DUP); // [funcref, funcref]
        visitor.visitLdcInsn(asmHandle); // [funcref, funcref, MethodHandle]
        visitor.visitMethodInsn(Opcodes.INVOKESPECIAL, Type.getInternalName(FuncRefInstance.class), "<init>", "(" + Type.getDescriptor(MethodHandle.class) + ")V", false); // [funcref]
        if (module.instance.limiter.countsMemory) {
            // If it counts memory, increment the object's refcount
            visitor.visitInsn(Opcodes.DUP);
            compilingVisitor.visitIntrinsic(IncRefCount.INSTANCE);
        }
    }

    @Override
    public ClassGenCallback classGenCallback() {
        return null;
    }
}
