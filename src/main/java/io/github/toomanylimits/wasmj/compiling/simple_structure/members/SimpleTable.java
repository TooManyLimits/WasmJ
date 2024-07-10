package io.github.toomanylimits.wasmj.compiling.simple_structure.members;

import io.github.toomanylimits.wasmj.compiling.helpers.Names;
import io.github.toomanylimits.wasmj.compiling.compiler.Compiler;
import io.github.toomanylimits.wasmj.compiling.compiler.CompilingSimpleInstructionVisitor;
import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleModule;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.ClassGenCallback;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.sandbox.DecRefCount;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.sandbox.IncMemoryBy;
import io.github.toomanylimits.wasmj.compiling.helpers.BytecodeHelper;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.sandbox.IncRefCount;
import io.github.toomanylimits.wasmj.parsing.types.TableType;
import io.github.toomanylimits.wasmj.runtime.sandbox.RefCountable;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.Set;

public interface SimpleTable {

    // Get the array and push it on the stack
    void getTable(SimpleModule callingModule, MethodVisitor visitor);
    // Set the array to the array on top of the stack
    void setTable(SimpleModule callingModule, MethodVisitor visitor);
    void emitTable(SimpleModule declaringModule, ClassVisitor classWriter, MethodVisitor initFunction, Set<ClassGenCallback> classGenCallbacks);

    /**
     * A table defined in the same file
     */
    record SameFileTable(int declaredIndex, TableType tableType, String/*?*/ exportedAs) implements SimpleTable {
        @Override
        public void getTable(SimpleModule callingModule, MethodVisitor visitor) {
            // Fetch the field.
            String className = Names.className(callingModule.moduleName);
            String fieldName = Names.tableName(declaredIndex);
            String descriptor = Type.getDescriptor(RefCountable[].class);
            visitor.visitFieldInsn(Opcodes.GETSTATIC, className, fieldName, descriptor);
        }
        @Override
        public void setTable(SimpleModule callingModule, MethodVisitor visitor) {
            // Store in the field.
            String className = Names.className(callingModule.moduleName);
            String fieldName = Names.tableName(declaredIndex);
            String descriptor = Type.getDescriptor(RefCountable[].class);
            visitor.visitFieldInsn(Opcodes.PUTSTATIC, className, fieldName, descriptor);
        }
        @Override
        public void emitTable(SimpleModule declaringModule, ClassVisitor classWriter, MethodVisitor initFunction, Set<ClassGenCallback> classGenCallbacks) {
            // Emit the table field
            int access = Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC;
            String name = Names.tableName(declaredIndex);
            String descriptor = Type.getDescriptor(RefCountable[].class);
            classWriter.visitField(access, name, descriptor, null, null).visitEnd();
            // Create the array:
            int initialSize = tableType.limits().min();
            // Increment memory if needed
            if (declaringModule.instance.limiter.countsMemory) {
                CompilingSimpleInstructionVisitor compilingVisitor = new CompilingSimpleInstructionVisitor(declaringModule, initFunction, Compiler.INIT_FUNCTION_NEXT_LOCAL, classGenCallbacks);
                compilingVisitor.visitIntrinsic(new IncMemoryBy((long) initialSize * 8L));
            }
            BytecodeHelper.constInt(initFunction, initialSize);
            String type = Type.getInternalName(RefCountable.class);
            initFunction.visitTypeInsn(Opcodes.ANEWARRAY, type);
            // Store the array in the field
            setTable(declaringModule, initFunction);

            // Emit export
            if (exportedAs != null) {
                // Export getter and setter methods
                MethodVisitor getter = classWriter.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, Names.exportTableGetterName(exportedAs), "()" + descriptor, null, null);
                getter.visitCode();
                getter.visitFieldInsn(Opcodes.GETSTATIC, Names.className(declaringModule.moduleName), name, descriptor);
                getter.visitInsn(Opcodes.ARETURN);
                getter.visitMaxs(0, 0);
                getter.visitEnd();

                MethodVisitor setter = classWriter.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, Names.exportTableSetterName(exportedAs), "(" + descriptor + ")V", null, null);
                setter.visitCode();
                setter.visitVarInsn(Opcodes.ALOAD, 0);
                setter.visitFieldInsn(Opcodes.PUTSTATIC, Names.className(declaringModule.moduleName), name, descriptor);
                setter.visitInsn(Opcodes.RETURN);
                setter.visitMaxs(0, 0);
                setter.visitEnd();
            }
        }
    }

    record ImportedTable(String importModuleName, String tableName, String exportedAs) implements SimpleTable {
        @Override
        public void getTable(SimpleModule callingModule, MethodVisitor visitor) {
            // Call the getter
            String className = Names.className(importModuleName);
            String getterName = Names.exportTableGetterName(tableName);
            String descriptor = Type.getDescriptor(RefCountable[].class);
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, className, getterName, "()" + descriptor, false);
        }

        @Override
        public void setTable(SimpleModule callingModule, MethodVisitor visitor) {
            // Call the setter
            String className = Names.className(importModuleName);
            String setterName = Names.exportTableSetterName(tableName);
            String descriptor = Type.getDescriptor(RefCountable[].class);
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, className, setterName, "(" + descriptor + ")V", false);
        }

        @Override
        public void emitTable(SimpleModule declaringModule, ClassVisitor classWriter, MethodVisitor initFunction, Set<ClassGenCallback> classGenCallbacks) {
            if (exportedAs != null) {
                throw new IllegalStateException("Re-exporting imported members is TODO");
            }
        }
    }

}
