package io.github.toomanylimits.wasmj.compiling.helpers;

import io.github.toomanylimits.wasmj.parsing.types.ValType;
import io.github.toomanylimits.wasmj.runtime.errors.WasmCodeException;
import io.github.toomanylimits.wasmj.runtime.sandbox.RefCountable;
import io.github.toomanylimits.wasmj.runtime.types.FuncRefInstance;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.PrintStream;
import java.util.function.Consumer;

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
        if (clazz == int.class) return ValType.I32;
        if (clazz == long.class) return ValType.I64;
        if (clazz == float.class) return ValType.F32;
        if (clazz == double.class) return ValType.F64;
        if (Object.class.isAssignableFrom(clazz)) return ValType.EXTERNREF;
        throw new IllegalArgumentException("Type " + clazz + " has no corresponding WASM type");
    }
    // Emit bytecode that throws a WasmJ runtime error with the given constant message
    public static void throwRuntimeError(MethodVisitor visitor, String message) {
        String errorName = Type.getInternalName(WasmCodeException.class);
        visitor.visitTypeInsn(Opcodes.NEW, errorName);
        visitor.visitInsn(Opcodes.DUP);
        visitor.visitLdcInsn(message);
        visitor.visitMethodInsn(Opcodes.INVOKESPECIAL, errorName, "<init>", "(Ljava/lang/String;)V", false);
        visitor.visitInsn(Opcodes.ATHROW);
    }
    // Throw a runtime error with the String message on top of the stack
    public static void throwRuntimeError(MethodVisitor visitor) {
        String errorName = Type.getInternalName(WasmCodeException.class);
        visitor.visitTypeInsn(Opcodes.NEW, errorName);
        visitor.visitInsn(Opcodes.DUP_X1);
        visitor.visitInsn(Opcodes.SWAP);
        visitor.visitMethodInsn(Opcodes.INVOKESPECIAL, errorName, "<init>", "(Ljava/lang/String;)V", false);
        visitor.visitInsn(Opcodes.ATHROW);
    }
    public static void createDefaultObject(MethodVisitor visitor, Class<?> clazz) {
        String typeName = Type.getInternalName(clazz);
        visitor.visitTypeInsn(Opcodes.NEW, typeName); // [obj]
        visitor.visitInsn(Opcodes.DUP); // [obj, obj]
        visitor.visitMethodInsn(Opcodes.INVOKESPECIAL, typeName, "<init>", "()V", false); // [initialized obj]
    }

    // Push a constant value on the stack
    public static void constValue(MethodVisitor visitor, Object obj) {
        if (obj instanceof Integer i) constInt(visitor, i);
        else if (obj instanceof Long l) constLong(visitor, l);
        else if (obj instanceof Float f) constFloat(visitor, f);
        else if (obj instanceof Double d) constDouble(visitor, d);
        else throw new IllegalArgumentException();
    }
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
    public static void boxValue(MethodVisitor visitor, ValType type) {
        switch (type) {
            case I32 -> visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
            case I64 -> visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
            case F32 -> visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
            case F64 -> visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
            case FUNCREF, EXTERNREF -> { /* Do nothing */ }
            default -> throw new UnsupportedOperationException("Cannot box value of given type - only int, long, float, double, reftype");
        }
    }
    public static void unboxValue(MethodVisitor visitor, ValType type) {
        switch (type) {
            case I32 -> {
                visitor.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Integer");
                visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
            }
            case I64 -> {
                visitor.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Long");
                visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false);
            }
            case F32 -> {
                visitor.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Float");
                visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F", false);
            }
            case F64 -> {
                visitor.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Double");
                visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false);
            }
            case FUNCREF -> visitor.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(FuncRefInstance.class));
            case EXTERNREF -> visitor.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(RefCountable.class));
            default -> throw new UnsupportedOperationException("Cannot unbox value of given type - only int, long, float, double, reftype");
        }
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

    public static void writeIfElse(MethodVisitor visitor, int skipOpcode, Consumer<MethodVisitor> trueBranch, Consumer<MethodVisitor> falseBranch) {
        Label elseBranch = new Label();
        Label done = new Label();
        visitor.visitJumpInsn(skipOpcode, elseBranch);
        trueBranch.accept(visitor);
        visitor.visitJumpInsn(Opcodes.GOTO, done);
        visitor.visitLabel(elseBranch);
        falseBranch.accept(visitor);
        visitor.visitLabel(done);
    }

    // Emit bytecode that pops a value of the given type
    public static void popValue(MethodVisitor visitor, ValType type) {
        switch (type.stackSlots) {
            case 0 -> {}
            case 1 -> visitor.visitInsn(Opcodes.POP);
            case 2 -> visitor.visitInsn(Opcodes.POP2);
            default -> throw new UnsupportedOperationException("Cannot pop value with " + type.stackSlots + " stack slots, jvm only supports 1 and 2!");
        }
    }
    // Emit bytecode that swaps two values of the given type
    public static void swapValues(MethodVisitor visitor, ValType type) {
        switch (type.stackSlots) {
            case 0 -> {}
            case 1 -> visitor.visitInsn(Opcodes.SWAP);
            case 2 -> {
                visitor.visitInsn(Opcodes.DUP2_X2);
                visitor.visitInsn(Opcodes.POP2);
            }
            default -> throw new UnsupportedOperationException("Cannot swap value with " + type.stackSlots + " stack slots, jvm only supports 1 and 2!");
        }
    }
    // Emit bytecode that dups a value of the given type
    public static void dupValue(MethodVisitor visitor, ValType type) {
        switch (type.stackSlots) {
            case 0 -> {}
            case 1 -> visitor.visitInsn(Opcodes.DUP);
            case 2 -> visitor.visitInsn(Opcodes.DUP2);
            default -> throw new UnsupportedOperationException("Cannot dup value with " + type.stackSlots + " stack slots, jvm only supports 1 and 2!");
        }
    }
    // Emit bytecode that stores a local of the given type at the given defaultIndex
    public static void storeLocal(MethodVisitor visitor, int index, ValType type) {
        if (type == ValType.I32) visitor.visitVarInsn(Opcodes.ISTORE, index);
        else if (type == ValType.I64) visitor.visitVarInsn(Opcodes.LSTORE, index);
        else if (type == ValType.F32) visitor.visitVarInsn(Opcodes.FSTORE, index);
        else if (type == ValType.F64) visitor.visitVarInsn(Opcodes.DSTORE, index);
        else if (type == ValType.FUNCREF || type == ValType.EXTERNREF) visitor.visitVarInsn(Opcodes.ASTORE, index);
        else throw new UnsupportedOperationException("Cannot store local of given type - only int, long, float, double, reftype");
    }
    // Emit bytecode that loads a local of the given type at the given defaultIndex
    public static void loadLocal(MethodVisitor visitor, int index, ValType type) {
        if (type == ValType.I32) visitor.visitVarInsn(Opcodes.ILOAD, index);
        else if (type == ValType.I64) visitor.visitVarInsn(Opcodes.LLOAD, index);
        else if (type == ValType.F32) visitor.visitVarInsn(Opcodes.FLOAD, index);
        else if (type == ValType.F64) visitor.visitVarInsn(Opcodes.DLOAD, index);
        else if (type == ValType.FUNCREF || type == ValType.EXTERNREF) visitor.visitVarInsn(Opcodes.ALOAD, index);
        else throw new UnsupportedOperationException("Cannot load local of given type - only int, long, float, double, reftype");
    }
    // Emit bytecode that stores a local of the given type at the given defaultIndex
    public static void returnValue(MethodVisitor visitor, ValType type) {
        if (type == null) visitor.visitInsn(Opcodes.RETURN); // null = void
        else if (type == ValType.I32) visitor.visitInsn(Opcodes.IRETURN);
        else if (type == ValType.I64) visitor.visitInsn(Opcodes.LRETURN);
        else if (type == ValType.F32) visitor.visitInsn(Opcodes.FRETURN);
        else if (type == ValType.F64) visitor.visitInsn(Opcodes.DRETURN);
        else if (type == ValType.FUNCREF || type == ValType.EXTERNREF) visitor.visitInsn(Opcodes.ARETURN);
        else throw new UnsupportedOperationException("Cannot return value of given type - only int, long, float, double, reftype");
    }

    // Emit bytecode that does debug printing of various kinds
    public static final boolean DEBUG_PRINTS_ENABLED = false;
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


