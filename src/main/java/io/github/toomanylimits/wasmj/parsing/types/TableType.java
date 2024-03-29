package io.github.toomanylimits.wasmj.parsing.types;

import io.github.toomanylimits.wasmj.parsing.module.ModuleParseException;

import java.io.IOException;
import java.io.InputStream;

public record TableType(ValType elementType, Limits limits) {
    public static TableType read(InputStream stream) throws IOException, ModuleParseException {
        return new TableType(ValType.readRefType(stream), Limits.read(stream));
    }
}
