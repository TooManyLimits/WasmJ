package io.github.toomanylimits.wasmj.parsing.instruction;

import io.github.toomanylimits.wasmj.parsing.module.ModuleParseException;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class Expression {

    private final List<Instruction> instrs;

    public Expression(List<Instruction> instrs) {
        this.instrs = instrs;
    }

    // Do not iterate over the instructions and feed them to
    // a MethodWritingVisitor, since the InstanceLimiter only
    // acts on Block and Loop.
    // Use MethodWritingVisitor.visitExpr().
    public List<Instruction> getInstrsAndIKnowWhatImDoing() {
        return instrs;
    }

    public static Expression read(InputStream stream) throws IOException, ModuleParseException {
        return new Expression(Instruction.readMany(stream, false).instrs());
    }
}