package io.github.toomanylimits.wasmj.parsing.module;

import io.github.toomanylimits.wasmj.parsing.ParseHelper;
import io.github.toomanylimits.wasmj.parsing.instruction.Expression;
import io.github.toomanylimits.wasmj.parsing.instruction.Instruction;
import io.github.toomanylimits.wasmj.parsing.instruction.StackType;
import io.github.toomanylimits.wasmj.parsing.types.ValType;
import io.github.toomanylimits.wasmj.util.ListUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public record Element(ValType type, List<Expression> exprs, Mode mode) {

    public sealed interface Mode {
        final class Passive implements Mode { public static final Passive INSTANCE = new Passive(); private Passive() {} }
        final class Declarative implements Mode { public static final Declarative INSTANCE = new Declarative(); private Declarative() {} }
        record Active(int tableIndex, Expression offset) implements Mode {}
    }

    private static List<Expression> readFunctionIndices(InputStream stream) throws IOException, ModuleParseException {
        return ListUtils.map(ParseHelper.readVector(stream, s -> ParseHelper.readUnsignedWasmInt(s)), y -> new Expression(List.of(new Instruction.RefFunc(y))));
    }

    private static ValType readElemKind(InputStream stream) throws IOException, ModuleParseException {
        int b = stream.read();
        if (b == 0) return ValType.FUNCREF;
        throw new ModuleParseException("Expected ElemKind, got invalid byte " + b);
    }

    public static Element read(InputStream stream, List<StackType> moduleTypes, List<Import> imports) throws IOException, ModuleParseException {
        int b = ParseHelper.readUnsignedWasmInt(stream);
        return switch (b) {
            case 0 -> {
                Mode mode = new Mode.Active(0, Expression.readConstant(stream, moduleTypes, imports));
                yield new Element(ValType.FUNCREF, readFunctionIndices(stream), mode);
            }
            case 1 -> new Element(readElemKind(stream), readFunctionIndices(stream), Mode.Passive.INSTANCE);
            case 2 -> {
                Mode mode = new Mode.Active(ParseHelper.readUnsignedWasmInt(stream), Expression.readConstant(stream, moduleTypes, imports));
                yield new Element(readElemKind(stream), readFunctionIndices(stream), mode);
            }
            case 3 -> new Element(readElemKind(stream), readFunctionIndices(stream), Mode.Declarative.INSTANCE);
            case 4 -> {
                Mode mode = new Mode.Active(0, Expression.readConstant(stream, moduleTypes, imports));
                yield new Element(ValType.FUNCREF, ParseHelper.readVector(stream, i -> Expression.readConstant(i, moduleTypes, imports)), mode);
            }
            case 5 -> new Element(ValType.readRefType(stream), ParseHelper.readVector(stream, i -> Expression.readConstant(i, moduleTypes, imports)), Mode.Passive.INSTANCE);
            case 6 -> {
                Mode mode = new Mode.Active(ParseHelper.readUnsignedWasmInt(stream), Expression.readConstant(stream, moduleTypes, imports));
                yield new Element(ValType.readRefType(stream), ParseHelper.readVector(stream, i -> Expression.readConstant(i, moduleTypes, imports)), mode);
            }
            case 7 -> new Element(ValType.readRefType(stream), ParseHelper.readVector(stream, i -> Expression.readConstant(i, moduleTypes, imports)), Mode.Declarative.INSTANCE);
            default -> throw new ModuleParseException("Invalid number provided for Element type flags: " + b);
        };
    }

}
