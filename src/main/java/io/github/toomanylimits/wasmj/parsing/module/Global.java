package io.github.toomanylimits.wasmj.parsing.module;

import io.github.toomanylimits.wasmj.parsing.instruction.Expression;
import io.github.toomanylimits.wasmj.parsing.instruction.StackType;
import io.github.toomanylimits.wasmj.parsing.types.GlobalType;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public record Global(GlobalType globalType, Expression initializer) {

    public static Global read(InputStream stream, List<StackType> moduleTypes, List<Import> imports) throws IOException, ModuleParseException {
        return new Global(GlobalType.read(stream), Expression.readConstant(stream, moduleTypes, imports));
    }

}
