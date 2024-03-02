package io.github.toomanylimits.wasmj.parsing.instruction;

import io.github.toomanylimits.wasmj.parsing.module.ModuleParseException;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public record Expression(List<Instruction> instrs) {
    public static Expression read(InputStream stream) throws IOException, ModuleParseException {
        return new Expression(Instruction.readMany(stream, false).instrs());
    }
}