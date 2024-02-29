package io.github.toomanylimits.wasmj.structure.module;

import io.github.toomanylimits.wasmj.structure.instruction.Expression;
import io.github.toomanylimits.wasmj.structure.instruction.Instruction;
import io.github.toomanylimits.wasmj.structure.types.ValType;
import io.github.toomanylimits.wasmj.structure.utils.ListUtils;
import io.github.toomanylimits.wasmj.structure.utils.Util;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public record Element(ValType.RefType type, List<Expression> exprs, Mode mode) {

    public sealed interface Mode {
        final class Passive implements Mode { public static final Passive INSTANCE = new Passive(); private Passive() {} }
        final class Declarative implements Mode { public static final Declarative INSTANCE = new Declarative(); private Declarative() {} }
        record Active(int tableIndex, Expression offset) implements Mode {}
    }

    private static List<Expression> readFunctionIndices(InputStream stream) throws IOException, ModuleParseException {
        return ListUtils.map(Util.readVector(stream, s -> Util.readUnsignedWasmInt(s)), y -> new Expression(List.of(new Instruction.RefFunc(y))));
    }

    private static ValType.RefType readElemKind(InputStream stream) throws IOException, ModuleParseException {
        int b = stream.read();
        if (b == 0) return ValType.RefType.funcref;
        throw new ModuleParseException("Expected ElemKind, got invalid byte " + b);
    }

    public static Element read(InputStream stream) throws IOException, ModuleParseException {
        int b = Util.readUnsignedWasmInt(stream);
        return switch (b) {
            case 0 -> {
                Mode mode = new Mode.Active(0, Expression.read(stream));
                yield new Element(ValType.funcref, readFunctionIndices(stream), mode);
            }
            case 1 -> new Element(readElemKind(stream), readFunctionIndices(stream), Mode.Passive.INSTANCE);
            case 2 -> {
                Mode mode = new Mode.Active(Util.readUnsignedWasmInt(stream), Expression.read(stream));
                yield new Element(readElemKind(stream), readFunctionIndices(stream), mode);
            }
            case 3 -> new Element(readElemKind(stream), readFunctionIndices(stream), Mode.Declarative.INSTANCE);
            case 4 -> {
                Mode mode = new Mode.Active(0, Expression.read(stream));
                yield new Element(ValType.funcref, Util.readVector(stream, Expression::read), mode);
            }
            case 5 -> new Element(ValType.RefType.read(stream), Util.readVector(stream, Expression::read), Mode.Passive.INSTANCE);
            case 6 -> {
                Mode mode = new Mode.Active(Util.readUnsignedWasmInt(stream), Expression.read(stream));
                yield new Element(ValType.RefType.read(stream), Util.readVector(stream, Expression::read), mode);
            }
            case 7 -> new Element(ValType.RefType.read(stream), Util.readVector(stream, Expression::read), Mode.Declarative.INSTANCE);
            default -> throw new ModuleParseException("Invalid number provided for Element type flags: " + b);
        };
    }

}
