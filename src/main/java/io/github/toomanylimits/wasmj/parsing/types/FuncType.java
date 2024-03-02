package io.github.toomanylimits.wasmj.parsing.types;

import io.github.toomanylimits.wasmj.parsing.instruction.StackType;
import io.github.toomanylimits.wasmj.parsing.module.ModuleParseException;
import io.github.toomanylimits.wasmj.parsing.ParseHelper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class FuncType {

    public final List<ValType> args, results;

    public FuncType(List<ValType> args, List<ValType> results) {
        this.args = args;
        this.results = results;
    }

    private StackType cachedStackType = null;
    public StackType asStackType() {
        if (cachedStackType == null)
            cachedStackType = new StackType.Basic(args, results);
        return cachedStackType;
    }

    private String cachedDescriptor = null;
    public String descriptor() {
        if (cachedDescriptor == null) {
            StringBuilder res = new StringBuilder("(");
            for (ValType arg : args)
                res.append(arg.desc());
            res.append(")");
            if (results.size() == 0)
                res.append("V");
            else
                res.append(results.get(0).desc());
            cachedDescriptor = res.toString();
        }
        return cachedDescriptor;
    }

    public static FuncType read(InputStream stream) throws IOException, ModuleParseException {
        int header = stream.read();
        if (header != 0x60)
            throw new ModuleParseException("Expected functype, did not find 0x60 byte. Got " + header);
        List<ValType> args = ParseHelper.readVector(stream, ValType::read);
        List<ValType> results = ParseHelper.readVector(stream, ValType::read);
        return new FuncType(args, results);
    }

}
