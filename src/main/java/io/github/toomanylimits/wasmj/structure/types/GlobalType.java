package io.github.toomanylimits.wasmj.structure.types;

import io.github.toomanylimits.wasmj.structure.module.ModuleParseException;
import io.github.toomanylimits.wasmj.structure.utils.Util;

import java.io.IOException;
import java.io.InputStream;

public record GlobalType(ValType valType, boolean mutable) {
    public static GlobalType read(InputStream stream) throws IOException, ModuleParseException {
        return new GlobalType(ValType.read(stream), Util.readBoolean(stream));
    }
}
