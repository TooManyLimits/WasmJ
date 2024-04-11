package io.github.toomanylimits.wasmj.parsing.instruction;

import io.github.toomanylimits.wasmj.parsing.module.Import;
import io.github.toomanylimits.wasmj.parsing.module.ModuleParseException;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class Expression {

    private final List<Instruction> instrs;

    public Expression(List<Instruction> instrs) {
        this.instrs = instrs;
    }

    public List<Instruction> getInstructions() {
        return instrs;
    }

    public static Expression read(InputStream stream, List<StackType> moduleTypes) throws IOException, ModuleParseException {
        return new Expression(Instruction.readMany(stream, moduleTypes, false).instrs());
    }

    public static Expression readConstant(InputStream stream, List<StackType> moduleTypes, List<Import> imports) throws IOException, ModuleParseException {
        Expression maybeConstant = read(stream, moduleTypes);
        // Check that it's constant:
        for (Instruction e : maybeConstant.instrs) {
            if (e instanceof Instruction.I32Const || e instanceof Instruction.I64Const || e instanceof Instruction.F32Const || e instanceof Instruction.F64Const || e instanceof Instruction.V128Const ||
                e instanceof Instruction.RefNull || e instanceof Instruction.RefFunc) {
                // Continue, this instruction is fine!
                continue;
            } else if (e instanceof Instruction.GlobalGet globalGet) {
                // Might be okay, need to check properties of the global get.
                // Currently, the only allowed "global get" in constant expressions are imported globals.
                List<Import.Global> globalImports = imports.stream().filter(it -> it instanceof Import.Global).map(it -> (Import.Global) it).toList();
                if (globalGet.globalIndex() < globalImports.size()) {
                    // This is an imported global. Ensure it's immutable:
                    if (!globalImports.get(globalGet.globalIndex()).globalType.mutable()) {
                        // It's immutable, so we're good! Continue.
                        continue;
                    }
                }
            }
            // Error, this instruction is not allowed in a constant expression.
            throw new ModuleParseException("Instruction \"" + e.getClass().getSimpleName() + "\" is not allowed in a constant expression!");
        }
        return maybeConstant;
    }

}