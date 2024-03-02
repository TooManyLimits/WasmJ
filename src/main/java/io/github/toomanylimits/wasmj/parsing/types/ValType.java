package io.github.toomanylimits.wasmj.parsing.types;

import io.github.toomanylimits.wasmj.parsing.module.ModuleParseException;

import java.io.IOException;
import java.io.InputStream;

public sealed interface ValType {

    String desc();
    int stackSlots();
    String typename();

    enum NumTypes {
        I32("I", 1, "i32"),
        I64("J", 2, "i64"),
        F32("F", 1, "f32"),
        F64("D", 2, "f64");

        public final String desc, typename;
        public final int stackSlots;
        NumTypes(String desc, int stackSlots, String typename) {
            this.desc = desc;
            this.stackSlots = stackSlots;
            this.typename = typename;
        }
    }
    enum VecTypes {
        V128
    }
    enum RefTypes {
        FUNCREF,
        EXTERNREF
    }
    record NumType(NumTypes t) implements ValType {
        public String desc() { return t.desc; }
        public int stackSlots() { return t.stackSlots; }
        public String typename() { return t.typename; }
    }
    record VecType(VecTypes t) implements ValType {
        public String desc() { return "V"; }
        public int stackSlots() { return 4; }
        public String typename() { return "v128"; }
    }
    record RefType(RefTypes t) implements ValType {
        public String desc() { return "Ljava/lang/Object;"; }
        public int stackSlots() { return 1; }
        public String typename() { return "ref"; }

        public static RefType read(InputStream stream) throws IOException, ModuleParseException {
            int b = stream.read();
            return switch (b) {
                case 0x70 -> funcref;
                case 0x6F -> externref;
                default -> throw new ModuleParseException("Expected RefType, got invalid byte " + b);
            };
        }
    }

    NumType i32 = new NumType(NumTypes.I32);
    NumType i64 = new NumType(NumTypes.I64);
    NumType f32 = new NumType(NumTypes.F32);
    NumType f64 = new NumType(NumTypes.F64);
    VecType v128 = new VecType(VecTypes.V128);
    RefType funcref = new RefType(RefTypes.FUNCREF);
    RefType externref = new RefType(RefTypes.EXTERNREF);

    static ValType read(InputStream stream) throws IOException, ModuleParseException {
        int b = stream.read();
        return switch (b) {
            case 0x7F -> i32;
            case 0x7E -> i64;
            case 0x7D -> f32;
            case 0x7C -> f64;
            case 0x7B -> v128;
            case 0x70 -> funcref;
            case 0x6F -> externref;
            default -> throw new ModuleParseException("Expected ValType, got byte " + b);
        };
    }
}