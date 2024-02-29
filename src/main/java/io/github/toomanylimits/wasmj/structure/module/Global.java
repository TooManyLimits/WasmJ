package io.github.toomanylimits.wasmj.structure.module;

import io.github.toomanylimits.wasmj.structure.instruction.Expression;
import io.github.toomanylimits.wasmj.structure.types.GlobalType;

import java.io.IOException;
import java.io.InputStream;

public record Global(GlobalType globalType, Expression initializer) {

    public static Global read(InputStream stream) throws IOException, ModuleParseException {
        return new Global(GlobalType.read(stream), Expression.read(stream));
    }

}
