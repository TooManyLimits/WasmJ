package io.github.toomanylimits.wasmj.parsing.types;

import io.github.toomanylimits.wasmj.compiler.FuncRefInstance;
import io.github.toomanylimits.wasmj.parsing.module.ModuleParseException;
import io.github.toomanylimits.wasmj.runtime.sandbox.RefCountable;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.InputStream;

// Based on the algorithm at https://webassembly.github.io/spec/core/appendix/algorithm.html#algo-valid
public enum ValType {

    I32("I", 1),
    I64("J", 2),
    F32("F", 1),
    F64("D", 2),
    V128(null, 4),
    EXTERNREF(Type.getDescriptor(RefCountable.class), 1),
    FUNCREF(Type.getDescriptor(FuncRefInstance.class), 1),
    UNKNOWN(null, null);

    public final String descriptor;
    public final Integer stackSlots;

    ValType(String descriptor, Integer stackSlots) {
        this.descriptor = descriptor;
        this.stackSlots = stackSlots;
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