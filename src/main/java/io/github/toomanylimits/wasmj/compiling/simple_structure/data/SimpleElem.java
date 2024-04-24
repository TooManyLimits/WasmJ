package io.github.toomanylimits.wasmj.compiling.simple_structure.data;

import io.github.toomanylimits.wasmj.compiling.compiler.Compiler;
import io.github.toomanylimits.wasmj.compiling.compiler.CompilingSimpleInstructionVisitor;
import io.github.toomanylimits.wasmj.compiling.helpers.BytecodeHelper;
import io.github.toomanylimits.wasmj.compiling.helpers.Names;
import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleInstruction;
import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleModule;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.ClassGenCallback;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.sandbox.DecRefCount;
import io.github.toomanylimits.wasmj.runtime.sandbox.InstanceLimiter;
import io.github.toomanylimits.wasmj.runtime.sandbox.RefCountable;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.List;
import java.util.Set;

/**
 * activeTableIndex and activeOffset are null if this SimpleElem is passive.
 */
public record SimpleElem(int declaredIndex, List<List<SimpleInstruction>> elementInitializers, Integer activeTableIndex, List<SimpleInstruction> activeOffset) {

    private static final String descriptor = Type.getDescriptor(RefCountable[].class);

    private boolean isActive() {
        return activeTableIndex != null;
    }

    /**
     * Push the RefCountable[] on the stack
     */
    public void getArray(SimpleModule callingModule, MethodVisitor visitor) {
        // Just read the field
        visitor.visitFieldInsn(Opcodes.GETSTATIC, Names.className(callingModule.moduleName), Names.elemFieldName(declaredIndex), descriptor);
    }

    /**
     * Set the RefCountable[] field to the RefCountable[] on top of the stack.
     */
    public void setArray(SimpleModule callingModule, MethodVisitor visitor) {
        visitor.visitFieldInsn(Opcodes.PUTSTATIC, Names.className(callingModule.moduleName), Names.elemFieldName(declaredIndex), descriptor);
    }

    /**
     * Emit the elem to the class writer.
     */
    public void emitElem(SimpleModule declaringModule, ClassVisitor classWriter, MethodVisitor initFunction, Set<ClassGenCallback> classGenCallbacks) {

        if (!this.isActive())
            throw new UnsupportedOperationException("Passive tables are TODO");

        // Store each elem RefCountable[] in a field (if this isn't Active).
        // Increment memory usage by the RefCountable[]'s size times 8:
        if (declaringModule.instance.limiter.countsMemory && !isActive()) {
            // Increment memory use by the byte array size
            initFunction.visitFieldInsn(Opcodes.GETSTATIC, Names.className(declaringModule.moduleName), Names.limiterFieldName(), Type.getDescriptor(InstanceLimiter.class)); // [limiter]
            BytecodeHelper.constLong(initFunction, (long) elementInitializers.size() * 8L); // [limiter, size * 8]
            initFunction.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(InstanceLimiter.class), "incHeapMemoryUsed", "(J)V", false); // []
        }

        // Make a compiling visitor
        CompilingSimpleInstructionVisitor compilingVisitor = new CompilingSimpleInstructionVisitor(declaringModule, initFunction, Compiler.INIT_FUNCTION_NEXT_LOCAL, classGenCallbacks);

        // Obtain the array object and the index
        if (this.isActive()) {
            // Use the table as the array
            declaringModule.tables[activeTableIndex].getTable(declaringModule, initFunction);
            // The index is computed by the offset expr
            compilingVisitor.emitMultipleInstructions(activeOffset);
        } else {
            // Create the field for later use
            classWriter.visitField(Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC, Names.elemFieldName(declaredIndex), descriptor, null, null);
            // Create the array object
            initFunction.visitTypeInsn(Opcodes.ANEWARRAY, Type.getDescriptor(RefCountable.class)); // [arr]
            // The index is 0
            initFunction.visitInsn(Opcodes.ICONST_0);
        }
        // Stack = [arr, index]

        // For each of the element initializers...
        for (List<SimpleInstruction> elementInitializer : elementInitializers) {
            // Duplicate the array and index
            initFunction.visitInsn(Opcodes.DUP2); // [arr, index, arr, index]
            // If this is active, then decrement the refcount of the object already in the array:
            if (isActive() && declaringModule.instance.limiter.countsMemory) {
                initFunction.visitInsn(Opcodes.AALOAD); // [arr, index, prevValue]
                compilingVisitor.visitIntrinsic(DecRefCount.INSTANCE); // [arr, index]
                initFunction.visitInsn(Opcodes.DUP2); // [arr, index, arr, index]
            }
            // Evaluate the expression (this also increments the refcount of the new object)
            compilingVisitor.emitMultipleInstructions(elementInitializer); // [arr, index, arr, index, value]
            // Place the value in the array
            initFunction.visitInsn(Opcodes.AASTORE); // [arr, index]
            // Increment the index
            initFunction.visitInsn(Opcodes.ICONST_1);
            initFunction.visitInsn(Opcodes.IADD); // [arr, index + 1]
        }
        // Stack = [arr, index + len]

        // Clean the stack
        if (this.isActive()) {
            // Just pop these off
            initFunction.visitInsn(Opcodes.POP2); // []
        } else {
            // Pop off the int, store the array in the field we made earlier
            initFunction.visitInsn(Opcodes.POP); // [arr]
            initFunction.visitFieldInsn(Opcodes.PUTSTATIC, Names.className(declaringModule.moduleName), Names.elemFieldName(declaredIndex), descriptor); // []
        }

    }


}
