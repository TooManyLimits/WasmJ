package io.github.toomanylimits.wasmj.compiling.helpers;

import io.github.toomanylimits.wasmj.compiling.compiler.CompilingSimpleInstructionVisitor;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.sandbox.IncRefCount;
import io.github.toomanylimits.wasmj.parsing.instruction.StackType;
import io.github.toomanylimits.wasmj.parsing.types.ValType;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.List;

/**
 * Helpers related to bytecode for calling functions
 */
public class CallingHelpers {

    /**
     * Wrap the given return values from the top of the stack into an Object[].
     */
    public static void wrapReturnValues(MethodVisitor visitor, int nextLocalSlot, List<ValType> typesReturned) {
        // Stack = [...results]
        int arrayLocal = nextLocalSlot;
        BytecodeHelper.constInt(visitor, typesReturned.size()); // [...results, size]
        visitor.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object"); // [...results, arr]
        visitor.visitVarInsn(Opcodes.ASTORE, arrayLocal); // [...results]
        BytecodeHelper.constInt(visitor, typesReturned.size() - 1); // [...results, defaultIndex]
        for (ValType returnType : typesReturned) {
            // Dup the defaultIndex down and swap
            switch (returnType.stackSlots) {
                case 1 -> {
                    visitor.visitInsn(Opcodes.DUP_X1); // [...results, defaultIndex, lastResult, defaultIndex]
                    visitor.visitInsn(Opcodes.SWAP); // [...results, defaultIndex, defaultIndex, lastResult]
                }
                case 2 -> {
                    visitor.visitInsn(Opcodes.DUP_X2); // [...results, defaultIndex, lastResult, defaultIndex]
                    visitor.visitInsn(Opcodes.DUP_X2); // [...results, defaultIndex, defaultIndex, lastResult, defaultIndex]
                    visitor.visitInsn(Opcodes.POP); // [...results, defaultIndex, defaultIndex, lastResult]
                }
                case 4 -> throw new UnsupportedOperationException("Vectors not yet implemented");
                default -> throw new IllegalStateException();
            }
            // Box the value
            BytecodeHelper.boxValue(visitor, returnType); // [...results, defaultIndex, defaultIndex, boxedLastResult]
            // Get the array and send it down
            visitor.visitVarInsn(Opcodes.ALOAD, arrayLocal); // [...results, defaultIndex, defaultIndex, boxedLastResult, arr]
            visitor.visitInsn(Opcodes.DUP_X2); // [...results, defaultIndex, arr, defaultIndex, boxedLastResult, arr]
            visitor.visitInsn(Opcodes.POP); // [...results, defaultIndex, arr, defaultIndex, boxedLastResult]
            // Store in array
            visitor.visitInsn(Opcodes.AASTORE); // [...results, defaultIndex]
            // Decrement defaultIndex
            BytecodeHelper.constInt(visitor, 1); // [...results, defaultIndex, 1]
            visitor.visitInsn(Opcodes.ISUB); // [...results, defaultIndex - 1]
        }
        // Pop the defaultIndex, then grab the array
        visitor.visitInsn(Opcodes.POP); // [...]
        visitor.visitVarInsn(Opcodes.ALOAD, arrayLocal); // [..., arr]
    }

    /**
     * After calling a function, process the return values afterwards.
     * If the boolean is true, then increment the refcounts of the returned values.
     */
    public static void unwrapReturnValues(MethodVisitor visitor, CompilingSimpleInstructionVisitor compiler, StackType funcType, boolean incrementRefCounts) {
        // Now deal with the return values.
        List<ValType> outTypes = funcType.outTypes();
        switch (outTypes.size()) {
            case 0 -> {} // no returns, no worries
            case 1 -> {
                // If we count memory, and this is a reference type, then increment its ref count
                if (incrementRefCounts && outTypes.get(0).isRef()) {
                    visitor.visitInsn(Opcodes.DUP); // Duplicate it
                    compiler.visitIntrinsic(IncRefCount.INSTANCE); // Increment ref count
                }
            }
            default -> {
                // The return values were outputted in an Object[]. Take each one out onto the stack.
                // Stack = [arr]
                visitor.visitInsn(Opcodes.ICONST_0); // [arr, index]
                for (ValType t : outTypes) {
                    visitor.visitInsn(Opcodes.DUP2); // [arr, index, arr, index]
                    visitor.visitInsn(Opcodes.AALOAD); // [arr, index, boxed value]
                    BytecodeHelper.unboxValue(visitor, t); // [arr, index, unboxed value]
                    // If we count memory and this is a reference type, increment its ref count
                    if (incrementRefCounts && t.isRef()) {
                        visitor.visitInsn(Opcodes.DUP); // Duplicate it
                        compiler.visitIntrinsic(IncRefCount.INSTANCE); // Increment ref count
                    }
                    switch (t.stackSlots) {
                        case 1 -> visitor.visitInsn(Opcodes.DUP_X2);
                        case 2 -> visitor.visitInsn(Opcodes.DUP2_X2);
                        default -> throw new IllegalStateException();
                    } // [unboxed value, arr, index, unboxed value]
                    BytecodeHelper.popValue(visitor, t); // [unboxed value, arr, index]
                    visitor.visitInsn(Opcodes.ICONST_1); // [unboxed value, arr, index, 1]
                    visitor.visitInsn(Opcodes.IADD); // [unboxed value, arr, index + 1]
                }
                // [all unboxed values, arr, index]
                visitor.visitInsn(Opcodes.POP2); // [all unboxed values]
            }
        }
    }
}
