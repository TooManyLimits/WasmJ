package io.github.toomanylimits.wasmj.parsing.types;

import io.github.toomanylimits.wasmj.parsing.module.ModuleParseException;
import io.github.toomanylimits.wasmj.parsing.ParseHelper;

import java.io.IOException;
import java.io.InputStream;

public record GlobalType(ValType valType, boolean mutable) {
    public static GlobalType read(InputStream stream) throws IOException, ModuleParseException {
        return new GlobalType(ValType.read(stream), ParseHelper.readBoolean(stream));
    }
}
