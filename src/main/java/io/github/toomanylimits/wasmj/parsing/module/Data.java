package io.github.toomanylimits.wasmj.parsing.module;

import io.github.toomanylimits.wasmj.parsing.ParseHelper;
import io.github.toomanylimits.wasmj.parsing.instruction.Expression;
import io.github.toomanylimits.wasmj.parsing.instruction.StackType;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public record Data(byte[] init, Mode mode) {

    public sealed interface Mode {
        final class Passive implements Mode {
            public static final Passive INSTANCE = new Passive();
            private Passive() {}
        }
        record Active(int memIndex, Expression offset) implements Mode {}
    }

    public static Data read(InputStream stream, List<StackType> moduleTypes, List<Import> imports) throws IOException, ModuleParseException {
        int b = ParseHelper.readUnsignedWasmInt(stream);
        return switch (b) {
            case 0 -> {
                Mode mode = new Mode.Active(0, Expression.readConstant(stream, moduleTypes, imports));
                yield new Data(ParseHelper.readByteArray(stream), mode);
            }
            case 1 -> new Data(ParseHelper.readByteArray(stream), Mode.Passive.INSTANCE);
            case 2 -> {
                Mode mode = new Mode.Active(ParseHelper.readUnsignedWasmInt(stream), Expression.readConstant(stream, moduleTypes, imports));
                yield new Data(ParseHelper.readByteArray(stream), mode);
            }
            default -> throw new ModuleParseException("Invalid Data Object, expected 0,1, or 2, got " + b);
        };
    }

}