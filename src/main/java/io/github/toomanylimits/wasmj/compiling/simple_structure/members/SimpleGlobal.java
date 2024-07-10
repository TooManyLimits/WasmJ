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
            CompilingSimpleInstructionVisitor initCompilingVisitor = new CompilingSimpleInstructionVisitor(declaringModule, initFunction, Compiler.INIT_FUNCTION_NEXT_LOCAL, classGenCallbacks);
            for (SimpleInstruction inst : initializer)
                inst.accept(initCompilingVisitor);
            // Set this global to the value
            this.emitSet(declaringModule, initFunction, initCompilingVisitor);

            // Emit export
            if (exportedAs != null) {
                // Export getter and setter methods
                MethodVisitor getter = classWriter.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, Names.exportGlobalGetterName(exportedAs), "()" + descriptor, null, null);
                getter.visitCode();
                getter.visitFieldInsn(Opcodes.GETSTATIC, Names.className(declaringModule.moduleName), name, descriptor);
                // If it's a ref type, and we count memory, increment the ref count
                if (declaringModule.instance.limiter.countsMemory && globalType.valType().isRef()) {
                    CompilingSimpleInstructionVisitor getterCompilingVisitor = new CompilingSimpleInstructionVisitor(declaringModule, getter, 0, classGenCallbacks);
                    getter.visitInsn(Opcodes.DUP);
                    getterCompilingVisitor.visitIntrinsic(IncRefCount.INSTANCE);
                }
                getter.visitInsn(globalType.valType().returnOpcode);
                getter.visitMaxs(0, 0);
                getter.visitEnd();

                MethodVisitor setter = classWriter.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, Names.exportGlobalSetterName(exportedAs), "(" + descriptor + ")V", null, null);
                setter.visitCode();
                // If it's a ref type, and we count memory, decrement the ref count of the object previously inside the global
                if (declaringModule.instance.limiter.countsMemory && globalType.valType().isRef()) {
                    CompilingSimpleInstructionVisitor setterCompilingVisitor = new CompilingSimpleInstructionVisitor(declaringModule, setter, globalType.valType().stackSlots, classGenCallbacks);
                    setter.visitFieldInsn(Opcodes.GETSTATIC, Names.className(declaringModule.moduleName), name, descriptor);
                    setterCompilingVisitor.visitIntrinsic(DecRefCount.INSTANCE);
                }
                setter.visitVarInsn(globalType.valType().loadOpcode, 0);
                setter.visitFieldInsn(Opcodes.PUTSTATIC, Names.className(declaringModule.moduleName), name, descriptor);
                setter.visitInsn(Opcodes.RETURN);
                setter.visitMaxs(0, 0);
                setter.visitEnd();
            }
        }
    }

    record ImportedGlobal(String importModuleName, String globalName, String exportedAs, GlobalType globalType) implements SimpleGlobal {
        @Override
        public void emitGet(SimpleModule callingModule, MethodVisitor visitor, CompilingSimpleInstructionVisitor compilingVisitor) {
            // Just call the getter
            String className = Names.className(importModuleName);
            String getterName = Names.exportGlobalGetterName(globalName);
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, className, getterName, "()" + globalType.valType().descriptor, false);
        }
        @Override
        public void emitSet(SimpleModule callingModule, MethodVisitor visitor, CompilingSimpleInstructionVisitor compilingVisitor) {
            // Just call the setter
            String className = Names.className(importModuleName);
            String setterName = Names.exportGlobalSetterName(globalName);
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, className, setterName, "(" + globalType.valType().descriptor + ")V", false);
        }
        @Override
        public void emitGlobal(SimpleModule declaringModule, ClassVisitor classWriter, MethodVisitor initFunction, Set<ClassGenCallback> classGenCallbacks) {
            // Do nothing, the global was emitted in another module, unless we need to re-export it
            if (exportedAs != null) {
                throw new IllegalStateException("Re-exporting imported members is TODO");
            }
        }
    }

}
