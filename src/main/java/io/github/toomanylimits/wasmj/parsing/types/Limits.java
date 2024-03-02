package io.github.toomanylimits.wasmj.parsing.types;

import io.github.toomanylimits.wasmj.parsing.module.ModuleParseException;
import io.github.toomanylimits.wasmj.parsing.ParseHelper;

import java.io.IOException;
import java.io.InputStream;

public record Limits(int min, int max) {

    public static Limits read(InputStream stream) throws IOException, ModuleParseException {
        if (ParseHelper.readBoolean(stream)) {
            int min = ParseHelper.readUnsignedWasmInt(stream);
            int max = ParseHelper.readUnsignedWasmInt(stream);
            if (min > max) throw new ModuleParseException("Failed to parse Limits, min = " + min + ", max = " + max + "?");
            return new Limits(min, max);
        } else {
            int min = ParseHelper.readUnsignedWasmInt(stream);
            return new Limits(min, Integer.MAX_VALUE);
        }
    }

}
