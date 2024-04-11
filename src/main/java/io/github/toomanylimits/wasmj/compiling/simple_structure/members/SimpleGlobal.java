package io.github.toomanylimits.wasmj.compiling.simple_structure.members;

import io.github.toomanylimits.wasmj.compiling.helpers.Names;
import io.github.toomanylimits.wasmj.compiling.compiler.Compiler;
import io.github.toomanylimits.wasmj.compiling.compiler.CompilingSimpleInstructionVisitor;
import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleInstruction;
import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleModule;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.ClassGenCallback;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.sandbox.DecRefCount;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.sandbox.IncRefCount;
import io.github.toomanylimits.wasmj.parsing.types.GlobalType;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.List;
import java.util.Set;

public interface SimpleGlobal {

    void emitGet(SimpleModule callingModule, MethodVisitor visitor, CompilingSimpleInstructionVisitor compilingVisitor);
    void emitSet(SimpleModule callingModule, MethodVisitor visitor, CompilingSimpleInstructionVisitor compilingVisitor);
    void emitGlobal(SimpleModule declaringModule, ClassVisitor classWriter, MethodVisitor initFunction, Set<ClassGenCallback> classGenCallbacks);

    /**
     * A global variable defined in the same file!
     */
    record SameFileGlobal(int declaredIndex, GlobalType globalType, String/*?*/ exportedAs, List<SimpleInstruction> initializer) implements SimpleGlobal {
        @Override
        public void emitGet(SimpleModule callingModule, MethodVisitor visitor, CompilingSimpleInstructionVisitor compilingVisitor) {
            // Fetch the field:
            String className = Names.className(callingModule.moduleName);
            String fieldName = Names.globalName(declaredIndex);
            String descriptor = globalType.valType().descriptor;
            visitor.visitFieldInsn(Opcodes.GETSTATIC, className, fieldName, descriptor);
            // If we're counting memory usage and this is a ref type, then increment the refcount
            if (callingModule.instance.limiter.countsMemory && globalType.valType().isRef()) {
                visitor.visitInsn(Opcodes.DUP);
                compilingVisitor.visitIntrinsic(IncRefCount.INSTANCE);
            }
        }
        @Override
        public void emitSet(SimpleModule callingModule, MethodVisitor visitor, CompilingSimpleInstructionVisitor compilingVisitor) {
            // Fetch the field:
            String className = Names.className(callingModule.moduleName);
            String fieldName = Names.globalName(declaredIndex);
            String descriptor = globalType.valType().descriptor;
            // If we're counting memory, decrement refcount of the object formerly in the global
            if (callingModule.instance.limiter.countsMemory && globalType.valType().isRef()) {
                visitor.visitFieldInsn(Opcodes.GETSTATIC, className, fieldName, descriptor);
                compilingVisitor.visitIntrinsic(DecRefCount.INSTANCE);
            }
            // Store to the global!
            visitor.visitFieldInsn(Opcodes.PUTSTATIC, className, fieldName, descriptor);
        }
        @Override
        public void emitGlobal(SimpleModule declaringModule, ClassVisitor classWriter, MethodVisitor initFunction, Set<ClassGenCallback> classGenCallbacks) {
            // Emit the global field
            int access = Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC;
            String name = Names.globalName(declaredIndex);
            String descriptor = globalType.valType().descriptor;
            classWriter.visitField(access, name, descriptor, null, null).visitEnd();
            // Emit the initializer into the init function
            CompilingSimpleInstructionVisitor compilingVisitor = new CompilingSimpleInstructionVisitor(declaringModule, initFunction, Compiler.INIT_FUNCTION_NEXT_LOCAL, classGenCallbacks);
            for (SimpleInstruction inst : initializer)
                inst.accept(compilingVisitor);
            // Set this global to the value
            this.emitSet(declaringModule, initFunction, compilingVisitor);

            // Emit export
            if (exportedAs != null) {
//                throw new IllegalStateException("Global exports not yet implemented");
            }
        }
    }

}
