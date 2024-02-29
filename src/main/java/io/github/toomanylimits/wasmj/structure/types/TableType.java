package io.github.toomanylimits.wasmj.structure.types;

import io.github.toomanylimits.wasmj.structure.module.ModuleParseException;

import java.io.IOException;
import java.io.InputStream;

public record TableType(ValType.RefType elementType, Limits limits) {
    public static TableType read(InputStream stream) throws IOException, ModuleParseException {
        return new TableType(ValType.RefType.read(stream), Limits.read(stream));
    }
}
