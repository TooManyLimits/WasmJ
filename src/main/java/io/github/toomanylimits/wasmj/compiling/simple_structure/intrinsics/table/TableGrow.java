package io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.table;

import io.github.toomanylimits.wasmj.compiling.helpers.Names;
import io.github.toomanylimits.wasmj.compiling.compiler.CompilingSimpleInstructionVisitor;
import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleInstruction;
import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleModule;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.ClassGenCallback;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.sandbox.IncInstructions;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.sandbox.IncMemory;
import io.github.toomanylimits.wasmj.compiling.helpers.BytecodeHelper;
import io.github.toomanylimits.wasmj.runtime.errors.WasmCodeException;
import io.github.toomanylimits.wasmj.runtime.sandbox.RefCountable;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public record TableGrow(int tableIndex) implements SimpleInstruction.Intrinsic {

    private static final String helperMethodName = "tableGrow";
    private static final String helperMethodDesc = "(" + Type.getDescriptor(RefCountable.class) + "I" + Type.getDescriptor(RefCountable[].class) + ")" + Type.getDescriptor(RefCountable[].class);

    @Override
    public void atCallSite(SimpleModule module, MethodVisitor visitor, CompilingSimpleInstructionVisitor compilingVisitor) {
        // Stack = [fillValue, growBy]
        module.tables[tableIndex].getTable(module, visitor); // [fillValue, growBy, table]
        visitor.visitInsn(Opcodes.DUP); // [fillValue, growBy, table, table]
        visitor.visitInsn(Opcodes.ARRAYLENGTH); // [fillValue, growBy, table, table.length]
        visitor.visitVarInsn(Opcodes.ISTORE, compilingVisitor.getNextLocalSlot()); // [fillValue, growBy, table]
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, Names.className(module.moduleName), helperMethodName, helperMethodDesc, false); // [newTable]
        module.tables[tableIndex].setTable(module, visitor); // []
        visitor.visitVarInsn(Opcodes.ILOAD, compilingVisitor.getNextLocalSlot()); // [table.length]
    }

    // Helpers in java
    public static void boundsCheckHelper(int requested, RefCountable[] oldTable) throws WasmCodeException {
        if (requested < 0)
            throw new WasmCodeException("Attempt to call table.grow with value above i32_max. WasmJ doesn't support this!");
        if (oldTable.length + requested < 0)
            throw new WasmCodeException("table.grow caused table size to overflow the i32 limit. WasmJ doesn't support this!");
    }

    public static RefCountable[] growTableHelper(RefCountable fillValue, int requested, RefCountable[] oldTable) {
        RefCountable[] newArray = new RefCountable[oldTable.length + requested];
        System.arraycopy(oldTable, 0, newArray, 0, oldTable.length);
        if (fillValue != null)
            Arrays.fill(newArray, oldTable.length, newArray.length, fillValue);
        return newArray;
    }

    @Override
    public ClassGenCallback classGenCallback() {
        // Emit the callback.
        return (module, classWriter) -> {
            int access = Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC;

            MethodVisitor visitor = classWriter.visitMethod(access, helperMethodName, helperMethodDesc, null, null);
            visitor.visitCode();
            // Method to grow the array!

            // Bounds check:
            visitor.visitVarInsn(Opcodes.ILOAD, 1); // [requested]
            visitor.visitVarInsn(Opcodes.ALOAD, 2); // [old table]
            BytecodeHelper.callNamedStaticMethod("boundsCheckHelper", visitor, TableGrow.class); // []

            // Sandboxing
            Set<ClassGenCallback> usedCallbacks = new HashSet<>();
            // If we're counting memory, then increment memory usage by requested * 8.
            if (module.instance.limiter.countsMemory) {
                visitor.visitVarInsn(Opcodes.ILOAD, 1); // [requested]
                visitor.visitInsn(Opcodes.I2L); // [(long) requested]
                BytecodeHelper.constLong(visitor, 8L); // [(long) requested, 8L]
                visitor.visitInsn(Opcodes.LMUL); // [(long) requested * 8]
                // Increment memory by the long.
                IncMemory.INSTANCE.atCallSite(module, visitor, null);
                usedCallbacks.add(IncMemory.INSTANCE.classGenCallback());
            }
            // If we're counting instructions, increment the instruction counter by...
            if (module.instance.limiter.countsInstructions) {
                // If the fill value is null, only increment by the current size (pay for array copy)
                Label notNull = new Label();
                Label end = new Label();
                visitor.visitVarInsn(Opcodes.ALOAD, 0); // [fillValue]
                visitor.visitJumpInsn(Opcodes.IFNONNULL, notNull); // []
                // Fill value is null:
                visitor.visitVarInsn(Opcodes.ALOAD, 2); // [prev array]
                visitor.visitInsn(Opcodes.ARRAYLENGTH); // [prev array length]
                visitor.visitJumpInsn(Opcodes.GOTO, end);
                // Otherwise, increment by current size + requested (pay for array copy + fill)
                visitor.visitLabel(notNull);
                // Fill value is not null:
                visitor.visitVarInsn(Opcodes.ALOAD, 2); // [prev array]
                visitor.visitInsn(Opcodes.ARRAYLENGTH); // [prev array length]
                visitor.visitVarInsn(Opcodes.ILOAD, 1); // [prev array length, requested]
                visitor.visitInsn(Opcodes.IADD); // [prev array length + requested]
                // End
                visitor.visitLabel(end);
                // Now the amount to increment by is on the stack.
                visitor.visitInsn(Opcodes.I2L);
                IncInstructions.INSTANCE.atCallSite(module, visitor, null);
                usedCallbacks.add(IncInstructions.INSTANCE.classGenCallback());
            }

            // Do the actual table grow:
            visitor.visitVarInsn(Opcodes.ALOAD, 0); // [fillValue]
            visitor.visitVarInsn(Opcodes.ILOAD, 1); // [fillValue, requested]
            visitor.visitVarInsn(Opcodes.ALOAD, 2); // [fillValue, requested, oldTable]
            BytecodeHelper.callNamedStaticMethod("growTableHelper", visitor, TableGrow.class); // [new table]

            // End the visitor
            visitor.visitInsn(Opcodes.ARETURN);
            visitor.visitMaxs(0, 0);
            visitor.visitEnd();

            // Return the used callbacks
            return usedCallbacks;
        };
    }


}
