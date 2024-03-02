package io.github.toomanylimits.wasmj.compiler;

import io.github.toomanylimits.wasmj.runtime.WasmRuntimeError;
import io.github.toomanylimits.wasmj.parsing.types.ValType;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.PrintStream;

public class BytecodeHelper {
    // Get the number of stack slots the given class takes up
    public static int stackSlots(Class<?> clazz) {
        if (clazz == long.class || clazz == double.class)
            return 2;
        else
            return 1;
    }
    // Get the type name of the given class for ASM
    public static ValType wasmType(Class<?> clazz) {
        if (clazz == int.class) return ValType.i32;
        if (clazz == long.class) return ValType.i64;
        if (clazz == float.class) return ValType.f32;
        if (clazz == double.class) return ValType.f64;
        if (clazz == Object.class) return ValType.externref;
        throw new IllegalArgumentException("Type " + clazz + " has no corresponding WASM type");
    }
    // Emit bytecode that throws a WasmJ runtime error with the given constant message
    public static void throwRuntimeError(MethodVisitor visitor, String message) {
        String errorName = Type.getInternalName(WasmRuntimeError.class);
        visitor.visitTypeInsn(Opcodes.NEW, errorName);
        visitor.visitInsn(Opcodes.DUP);
        visitor.visitLdcInsn(message);
        visitor.visitMethodInsn(Opcodes.INVOKESPECIAL, errorName, "<init>", "(Ljava/lang/String;)V", false);
        visitor.visitInsn(Opcodes.ATHROW);
    }
    // Push a constant value on the stack
    public static void constInt(MethodVisitor visitor, int value) {
        if (value >= -1 && value <= 5) visitor.visitInsn(Opcodes.ICONST_0 + value);
        else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) visitor.visitIntInsn(Opcodes.BIPUSH, value);
        else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) visitor.visitIntInsn(Opcodes.SIPUSH, value);
        else visitor.visitLdcInsn(value);
    }
    public static void constLong(MethodVisitor visitor, long value) {
        if (value == 0L || value == 1L) visitor.visitInsn(Opcodes.LCONST_0 + (int) value);
        else visitor.visitLdcInsn(value);
    }
    public static void constFloat(MethodVisitor visitor, float value) {
        if (value == 0f || value == 1f || value == 2f) visitor.visitInsn(Opcodes.FCONST_0 + (int) value);
        else visitor.visitLdcInsn(value);
    }
    public static void constDouble(MethodVisitor visitor, double value) {
        if (value == 0.0 || value == 1.0) visitor.visitInsn(Opcodes.DCONST_0 + (int) value);
        else visitor.visitLdcInsn(value);
    }
    // Give a jumping opcode to test the top of the stack.
    // If the opcode succeeds (a jump occurs), 1 is pushed.
    // Otherwise, 0.
    public static void test(MethodVisitor visitor, int opcode) {
        Label success = new Label();
        Label end = new Label();
        visitor.visitJumpInsn(opcode, success);
        visitor.visitInsn(Opcodes.ICONST_0); // Failed, push 0
        visitor.visitJumpInsn(Opcodes.GOTO, end);
        visitor.visitLabel(success);
        visitor.visitInsn(Opcodes.ICONST_1); // Succeeded, push 1
        visitor.visitLabel(end);
    }
    // Emit bytecode that pops a value of the given type
    public static void popValue(MethodVisitor visitor, ValType type) {
        switch (type.stackSlots()) {
            case 0 -> {}
            case 1 -> visitor.visitInsn(Opcodes.POP);
            case 2 -> visitor.visitInsn(Opcodes.POP2);
            default -> throw new UnsupportedOperationException("Cannot pop value with " + type.stackSlots() + " stack slots, jvm only supports 1 and 2!");
        }
    }
    // Emit bytecode that swaps two values of the given type
    public static void swapValues(MethodVisitor visitor, ValType type) {
        switch (type.stackSlots()) {
            case 0 -> {}
            case 1 -> visitor.visitInsn(Opcodes.SWAP);
            case 2 -> {
                visitor.visitInsn(Opcodes.DUP2_X2);
                visitor.visitInsn(Opcodes.POP2);
            }
            default -> throw new UnsupportedOperationException("Cannot swap value with " + type.stackSlots() + " stack slots, jvm only supports 1 and 2!");
        }
    }
    // Emit bytecode that dups a value of the given type
    public static void dupValue(MethodVisitor visitor, ValType type) {
        switch (type.stackSlots()) {
            case 0 -> {}
            case 1 -> visitor.visitInsn(Opcodes.DUP);
            case 2 -> visitor.visitInsn(Opcodes.DUP2);
            default -> throw new UnsupportedOperationException("Cannot dup value with " + type.stackSlots() + " stack slots, jvm only supports 1 and 2!");
        }
    }
    // Emit bytecode that stores a local of the given type at the given index
    public static void storeLocal(MethodVisitor visitor, int index, ValType type) {
        if (type == ValType.i32) visitor.visitVarInsn(Opcodes.ISTORE, index);
        else if (type == ValType.i64) visitor.visitVarInsn(Opcodes.LSTORE, index);
        else if (type == ValType.f32) visitor.visitVarInsn(Opcodes.FSTORE, index);
        else if (type == ValType.f64) visitor.visitVarInsn(Opcodes.DSTORE, index);
        else if (type == ValType.funcref || type == ValType.externref) visitor.visitVarInsn(Opcodes.ASTORE, index);
        else throw new UnsupportedOperationException("Cannot store local of given type - only int, long, float, double, reftype");
    }
    // Emit bytecode that loads a local of the given type at the given index
    public static void loadLocal(MethodVisitor visitor, int index, ValType type) {
        if (type == ValType.i32) visitor.visitVarInsn(Opcodes.ILOAD, index);
        else if (type == ValType.i64) visitor.visitVarInsn(Opcodes.LLOAD, index);
        else if (type == ValType.f32) visitor.visitVarInsn(Opcodes.FLOAD, index);
        else if (type == ValType.f64) visitor.visitVarInsn(Opcodes.DLOAD, index);
        else if (type == ValType.funcref || type == ValType.externref) visitor.visitVarInsn(Opcodes.ALOAD, index);
        else throw new UnsupportedOperationException("Cannot load local of given type - only int, long, float, double, reftype");
    }
    // Emit bytecode that stores a local of the given type at the given index
    public static void returnValue(MethodVisitor visitor, ValType type) {
        if (type == ValType.i32) visitor.visitInsn(Opcodes.IRETURN);
        else if (type == ValType.i64) visitor.visitInsn(Opcodes.LRETURN);
        else if (type == ValType.f32) visitor.visitInsn(Opcodes.FRETURN);
        else if (type == ValType.f64) visitor.visitInsn(Opcodes.DRETURN);
        else if (type == ValType.funcref || type == ValType.externref) visitor.visitInsn(Opcodes.ARETURN);
        else throw new UnsupportedOperationException("Cannot return value of given type - only int, long, float, double, reftype");
    }

    // Emit bytecode that does debug printing of various kinds
    private static final boolean DEBUG_PRINTS_ENABLED = false;
    public static void debugPrintln(MethodVisitor visitor, String message) {
        if (DEBUG_PRINTS_ENABLED) {
            visitor.visitFieldInsn(Opcodes.GETSTATIC, Type.getInternalName(System.class), "out", Type.getDescriptor(PrintStream.class));
            visitor.visitLdcInsn(message);
            visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(PrintStream.class), "println", "(Ljava/lang/String;)V", false);
        }
    }
    public static void debugPrint(MethodVisitor visitor, String message) {
        if (DEBUG_PRINTS_ENABLED) {
            visitor.visitFieldInsn(Opcodes.GETSTATIC, Type.getInternalName(System.class), "out", Type.getDescriptor(PrintStream.class));
            visitor.visitLdcInsn(message);
            visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(PrintStream.class), "print", "(Ljava/lang/String;)V", false);
        }
    }
    public static void debugPrintInt(MethodVisitor visitor) {
        if (DEBUG_PRINTS_ENABLED) {
            visitor.visitInsn(Opcodes.DUP);
            visitor.visitFieldInsn(Opcodes.GETSTATIC, Type.getInternalName(System.class), "out", Type.getDescriptor(PrintStream.class));
            visitor.visitInsn(Opcodes.SWAP);
            visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(PrintStream.class), "println", "(I)V", false);
        }
    }
    public static void debugPrintTwoInts(MethodVisitor visitor) {
        if (DEBUG_PRINTS_ENABLED) {
            // [a, b]
            visitor.visitInsn(Opcodes.DUP2); // [a, b, a, b]
            visitor.visitInsn(Opcodes.SWAP); // [a, b, b, a]
            visitor.visitFieldInsn(Opcodes.GETSTATIC, Type.getInternalName(System.class), "out", Type.getDescriptor(PrintStream.class)); // [a, b, b, a, System.out]
            visitor.visitInsn(Opcodes.DUP_X2); // [a, b, System.out, b, a, System.out]
            visitor.visitInsn(Opcodes.DUP_X1); // [a, b, System.out, b, System.out, a, System.out]
            visitor.visitInsn(Opcodes.SWAP); // [a, b, System.out, b, System.out, System.out, a]
            visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(PrintStream.class), "print", "(I)V", false); // [a, b, System.out, b, System.out]
            constInt(visitor, ' '); // [a, b, System.out, b, System.out, space]
            visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(PrintStream.class), "print", "(C)V", false); // [a, b, System.out, b]
            visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(PrintStream.class), "println", "(I)V", false); // [a, b]
        }
    }

}


