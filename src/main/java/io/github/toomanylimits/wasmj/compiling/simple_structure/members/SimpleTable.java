package io.github.toomanylimits.wasmj.compiling.simple_structure.members;

import io.github.toomanylimits.wasmj.compiling.helpers.Names;
import io.github.toomanylimits.wasmj.compiling.compiler.Compiler;
import io.github.toomanylimits.wasmj.compiling.compiler.CompilingSimpleInstructionVisitor;
import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleModule;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.ClassGenCallback;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.sandbox.IncMemoryBy;
import io.github.toomanylimits.wasmj.compiling.helpers.BytecodeHelper;
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
            var compilingVisitor = new CompilingSimpleInstructionVisitor(declaringModule, initFunction, Compiler.INIT_FUNCTION_NEXT_LOCAL, classGenCallbacks);
            if (declaringModule.instance.limiter.countsMemory)
                compilingVisitor.visitIntrinsic(new IncMemoryBy((long) initialSize * 8L));
            BytecodeHelper.constInt(initFunction, initialSize);
            String type = Type.getInternalName(RefCountable.class);
            initFunction.visitTypeInsn(Opcodes.ANEWARRAY, type);
            // Store the array in the field
            setTable(declaringModule, initFunction);

            // Emit export
            if (exportedAs != null) {
                throw new IllegalStateException("Global exports not yet implemented");
            }
        }
    }

}
