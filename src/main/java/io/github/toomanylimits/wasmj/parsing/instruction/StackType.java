package io.github.toomanylimits.wasmj.parsing.instruction;

import io.github.toomanylimits.wasmj.parsing.ParseHelper;
import io.github.toomanylimits.wasmj.parsing.module.ModuleParseException;
import io.github.toomanylimits.wasmj.parsing.types.ValType;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public record StackType(List<ValType> inTypes, List<ValType> outTypes) {

    public static final StackType NOP = new StackType(List.of(), List.of());

    public String descriptor() {
        StringBuilder res = new StringBuilder("(");
        for (ValType arg : inTypes)
            res.append(arg.descriptor);
        res.append(")");
        switch (outTypes.size()) {
            case 0 -> res.append("V"); // 0 returns = void
            case 1 -> res.append(outTypes.get(0).descriptor); // 1 return = that value type
            default -> res.append("[Ljava/lang/Object;"); // Multiple returns = Object[]
        }
        return res.toString();
    }

    public static StackType readFuncType(InputStream stream) throws IOException, ModuleParseException {
        int header = stream.read();
        if (header != 0x60)
            throw new ModuleParseException("Expected functype, did not find 0x60 byte. Got " + header);
        List<ValType> args = ParseHelper.readVector(stream, ValType::read);
        List<ValType> results = ParseHelper.readVector(stream, ValType::read);
        return new StackType(args, results);
    }

    /**
     * Read a Block Type out as a StackType.
     */
    public static StackType readBlockType(InputStream stream, List<StackType> moduleTypes) throws IOException, ModuleParseException {
        long b = stream.read();
        return switch ((int) b) {
            case 0x40 -> new StackType(List.of(), List.of());
            case 0x7F -> new StackType(List.of(), List.of(ValType.I32));
            case 0x7E -> new StackType(List.of(), List.of(ValType.I64));
            case 0x7D -> new StackType(List.of(), List.of(ValType.F32));
            case 0x7C -> new StackType(List.of(), List.of(ValType.F64));
            case 0x7B -> new StackType(List.of(), List.of(ValType.V128));
            case 0x70 -> new StackType(List.of(), List.of(ValType.FUNCREF));
            case 0x6F -> new StackType(List.of(), List.of(ValType.EXTERNREF));
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
                    throw new ModuleParseException("Blocktype defaultIndex cannot be negative! Got " + result);
                if (result > (long) Integer.MAX_VALUE)
                    throw new ModuleParseException("Blocktype defaultIndex too large (must be at most " + Integer.MAX_VALUE + ", got " + result + ")");
                yield moduleTypes.get((int) result);
            }
        };
    }

}