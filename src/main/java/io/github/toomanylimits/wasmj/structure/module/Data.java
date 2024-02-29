package io.github.toomanylimits.wasmj.structure.module;

import io.github.toomanylimits.wasmj.structure.instruction.Expression;
import io.github.toomanylimits.wasmj.structure.utils.Util;

import java.io.IOException;
import java.io.InputStream;

public class Data {

    public final byte[] init;
    public final Mode mode;

    public Data(byte[] init, Mode mode) {
        this.init = init;
        this.mode = mode;
    }

    public sealed interface Mode {
        final class Passive implements Mode { public static final Passive INSTANCE = new Passive(); private Passive() {} }
        record Active(int memIndex, Expression offset) implements Mode {}
    }

    public static Data read(InputStream stream) throws IOException, ModuleParseException {
        int b = Util.readUnsignedWasmInt(stream);
        return switch (b) {
            case 0 -> {
                Mode mode = new Mode.Active(0, Expression.read(stream));
                yield new Data(Util.readByteArray(stream), mode);
            }
            case 1 -> new Data(Util.readByteArray(stream), Mode.Passive.INSTANCE);
            case 2 -> {
                Mode mode = new Mode.Active(Util.readUnsignedWasmInt(stream), Expression.read(stream));
                yield new Data(Util.readByteArray(stream), mode);
            }
            default -> throw new ModuleParseException("Invalid Data Object, expected 0,1, or 2, got " + b);
        };
    }

}