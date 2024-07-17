package io.github.toomanylimits.wasmj.compiling.simple_structure.members;

import io.github.toomanylimits.wasmj.compiling.helpers.Names;
import io.github.toomanylimits.wasmj.compiling.compiler.Compiler;
import io.github.toomanylimits.wasmj.compiling.compiler.CompilingSimpleInstructionVisitor;
import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleModule;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.ClassGenCallback;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.sandbox.IncInstructionsBy;
import io.github.toomanylimits.wasmj.compiling.helpers.BytecodeHelper;
import io.github.toomanylimits.wasmj.parsing.types.Limits;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.Set;

public interface SimpleMemory {

    // Get the array and push it on the stack
    void getMemory(SimpleModule callingModule, MethodVisitor visitor);
    // Set the array to the array on top of the stack
    void setMemory(SimpleModule callingModule, MethodVisitor visitor);
    void emitMemory(SimpleModule declaringModule, ClassVisitor classWriter, MethodVisitor initFunction, Set<ClassGenCallback> classGenCallbacks);

    /**
     * A global variable defined in the same file!
     */
    record SameFileMemory(int declaredIndex, Limits limits, String/*?*/ exportedAs) implements SimpleMemory {
        @Override
        public void getMemory(SimpleModule callingModule, MethodVisitor visitor) {
            // Fetch the field
            String className = Names.className(callingModule.moduleName);
            String fieldName = Names.memoryName(declaredIndex);
            String descriptor = "[B";
            visitor.visitFieldInsn(Opcodes.GETSTATIC, className, fieldName, descriptor);
        }
        @Override
        public void setMemory(SimpleModule callingModule, MethodVisitor visitor) {
            // Set the field
            String className = Names.className(callingModule.moduleName);
            String fieldName = Names.memoryName(declaredIndex);
            String descriptor = "[B";
            visitor.visitFieldInsn(Opcodes.PUTSTATIC, className, fieldName, descriptor);
        }
        @Override
        public void emitMemory(SimpleModule declaringModule, ClassVisitor classWriter, MethodVisitor initFunction, Set<ClassGenCallback> classGenCallbacks) {
            // Emit the byte[] field
            int access = Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC;
            String name = Names.memoryName(declaredIndex);
            String descriptor = "[B";
            classWriter.visitField(access, name, descriptor, null, null).visitEnd();
            // Create the array:
            int initialSize = Math.multiplyExact(limits.min(), Compiler.WASM_PAGE_SIZE);
            // Increment memory if needed
            var compilingVisitor = new CompilingSimpleInstructionVisitor(declaringModule, initFunction, Compiler.INIT_FUNCTION_NEXT_LOCAL, classGenCallbacks);
            if (declaringModule.instance.limiter.countsMemory)
                compilingVisitor.visitIntrinsic(new IncInstructionsBy(initialSize));
            BytecodeHelper.constInt(initFunction, initialSize);
            initFunction.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_BYTE);
            // Store the array in the field
            setMemory(declaringModule, initFunction);

            // Emit export
            if (exportedAs != null) {
                throw new UnsupportedOperationException("Memory exports not yet implemented");
            }
        }
    }

}
