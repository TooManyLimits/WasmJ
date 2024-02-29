package io.github.toomanylimits.wasmj.structure.types;

import io.github.toomanylimits.wasmj.structure.module.ModuleParseException;
import io.github.toomanylimits.wasmj.structure.utils.Util;

import java.io.IOException;
import java.io.InputStream;

public record Limits(int min, int max) {

    public static Limits read(InputStream stream) throws IOException, ModuleParseException {
        if (Util.readBoolean(stream)) {
            int min = Util.readUnsignedWasmInt(stream);
            int max = Util.readUnsignedWasmInt(stream);
            if (min > max) throw new ModuleParseException("Failed to parse Limits, min = " + min + ", max = " + max + "?");
            return new Limits(min, max);
        } else {
            int min = Util.readUnsignedWasmInt(stream);
            return new Limits(min, Integer.MAX_VALUE);
        }
    }

}
