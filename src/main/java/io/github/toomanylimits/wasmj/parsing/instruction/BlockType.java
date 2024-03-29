package io.github.toomanylimits.wasmj.parsing.instruction;

import io.github.toomanylimits.wasmj.parsing.module.ModuleParseException;
import io.github.toomanylimits.wasmj.parsing.module.WasmModule;
import io.github.toomanylimits.wasmj.parsing.types.ValType;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public sealed interface BlockType {

    StackType stackType(WasmModule module);

    record ValueBlockType(ValType type) implements BlockType {
        @Override
        public StackType stackType(WasmModule module) {
            return new StackType.Basic(List.of(), List.of(type));
        }
    }
    record IndexBlockType(int index) implements BlockType {
        @Override
        public StackType stackType(WasmModule module) {
            return module.types.get(index).asStackType();
        }
    }
    record EmptyBlockType() implements BlockType {
        public static EmptyBlockType INSTANCE = new EmptyBlockType();
        @Override
        public StackType stackType(WasmModule module) {
            return StackType.nop;
        }
    }

    static BlockType read(InputStream stream) throws IOException, ModuleParseException {
        long b = stream.read();
        return switch ((int) b) {
            case 0x40 -> EmptyBlockType.INSTANCE;
            case 0x7F -> new ValueBlockType(ValType.I32);
            case 0x7E -> new ValueBlockType(ValType.I64);
            case 0x7D -> new ValueBlockType(ValType.F32);
            case 0x7C -> new ValueBlockType(ValType.F64);
            case 0x7B -> new ValueBlockType(ValType.V128);
            case 0x70 -> new ValueBlockType(ValType.FUNCREF);
            case 0x6F -> new ValueBlockType(ValType.EXTERNREF);
            default -> {
                //Weird hack, since this is a place in the WASM spec where
                //we would ordinarily need to backtrack or peek. So we use this
                //strange workaround method to prevent it.
                long result = 0L;
                int shift = 0;
                boolean readAlready = false;
                do {
                    if (shift > 4 * 7)
                        throw new ModuleParseException("Failed to read signed WASM integer - int too long!");
                    if (readAlready)
                        b = stream.read();
                    readAlready = true;
                    result = result | ((b & 0x7FL) << shift);
                    shift += 7;
                } while ((b & 0x80L) != 0L);

                if ((b & 0x40L) != 0L)
                    result = result | ((~0L) << shift);
                if (result < 0L)
                    throw new ModuleParseException("Blocktype index cannot be negative! Got $result");
                if (result > (long) Integer.MAX_VALUE)
                    throw new ModuleParseException("Blocktype index too large (must be at most ${Int.MAX_VALUE}, got $result)");
                yield new IndexBlockType((int) result);
            }
        };
    }

}
