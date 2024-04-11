package io.github.toomanylimits.wasmj.parsing.types;

import io.github.toomanylimits.wasmj.runtime.types.FuncRefInstance;
import io.github.toomanylimits.wasmj.parsing.module.ModuleParseException;
import io.github.toomanylimits.wasmj.runtime.sandbox.RefCountable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.InputStream;

// Based on the algorithm at https://webassembly.github.io/spec/core/appendix/algorithm.html#algo-valid
public enum ValType {

    I32("I", 1, Opcodes.ILOAD, Opcodes.ISTORE, Opcodes.IRETURN),
    I64("J", 2, Opcodes.LLOAD, Opcodes.LSTORE, Opcodes.LRETURN),
    F32("F", 1, Opcodes.FLOAD, Opcodes.FSTORE, Opcodes.FRETURN),
    F64("D", 2, Opcodes.DLOAD, Opcodes.DSTORE, Opcodes.DRETURN),
    V128(null, 4, null, null, null),
    EXTERNREF(Type.getDescriptor(RefCountable.class), 1, Opcodes.ALOAD, Opcodes.ASTORE, Opcodes.ARETURN),
    FUNCREF(Type.getDescriptor(FuncRefInstance.class), 1, Opcodes.ALOAD, Opcodes.ASTORE, Opcodes.ARETURN),
    UNKNOWN(null, null, null, null, null);

    public final String descriptor;
    public final Integer stackSlots, loadOpcode, storeOpcode, returnOpcode;

    ValType(String descriptor, Integer stackSlots, Integer loadOpcode, Integer storeOpcode, Integer returnOpcode) {
        this.descriptor = descriptor;
        this.stackSlots = stackSlots;
        this.loadOpcode = loadOpcode;
        this.storeOpcode = storeOpcode;
        this.returnOpcode = returnOpcode;
    }

    public boolean isNum() {
        return this == I32 || this == I64 || this == F32 || this == F64 || this == UNKNOWN;
    }

    public boolean isVec() {
        return this == V128 || this == UNKNOWN;
    }

    public boolean isRef() {
        return this == EXTERNREF || this == FUNCREF || this == UNKNOWN;
    }

    public static ValType read(InputStream stream) throws IOException, ModuleParseException {
        int b = stream.read();
        return switch (b) {
            case 0x7F -> I32;
            case 0x7E -> I64;
            case 0x7D -> F32;
            case 0x7C -> F64;
            case 0x7B -> V128;
            case 0x70 -> FUNCREF;
            case 0x6F -> EXTERNREF;
            default -> throw new ModuleParseException("Expected ValType, got byte " + b);
        };
    }

    public static ValType readRefType(InputStream stream) throws IOException, ModuleParseException {
        int b = stream.read();
        return switch (b) {
            case 0x70 -> FUNCREF;
            case 0x6F -> EXTERNREF;
            default -> throw new ModuleParseException("Expected reference type, got byte " + b);
        };
    }

}