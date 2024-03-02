package io.github.toomanylimits.wasmj.parsing.module;

import io.github.toomanylimits.wasmj.parsing.instruction.Expression;
import io.github.toomanylimits.wasmj.parsing.types.FuncType;
import io.github.toomanylimits.wasmj.parsing.types.ValType;
import io.github.toomanylimits.wasmj.parsing.ParseHelper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class Code {

    public final int size, index;
    public final List<ValType> locals;
    public final Expression expr;

    public Code(int size, int index, List<ValType> locals, Expression expr) {
        this.size = size;
        this.index = index;
        this.locals = locals;
        this.expr = expr;
    }

    private int nextLocalSlotCache = -1;
    public int nextLocalSlot() {
        if (nextLocalSlotCache == -1) {
            nextLocalSlotCache = 0;
            for (ValType local : locals)
                nextLocalSlotCache += local.stackSlots();
        }
        return nextLocalSlotCache;
    }
    private List<Integer> localMappingsCache = null;
    public List<Integer> localMappings() {
        if (localMappingsCache == null) {
            localMappingsCache = new ArrayList<>(locals.size());
            int i = 0;
            for (ValType local : locals) {
                localMappingsCache.add(i);
                i += local.stackSlots();
            }
        }
        return localMappingsCache;
    }

    // Requires passing additional data:
    // The index of this code, the list of all types in this module, and the list of all funcs in this module.
    public static Code read(int index, List<FuncType> types, List<Integer> funcs, InputStream stream) throws IOException, ModuleParseException {
        int size = ParseHelper.readUnsignedWasmInt(stream);
        int numLocalObjs = ParseHelper.readUnsignedWasmInt(stream);
        ArrayList<ValType> locals = new ArrayList<>();
        // Add func params as locals
        for (ValType type : types.get(funcs.get(index)).args)
            locals.add(type);
        // Add declared locals
        for (int i = 0; i < numLocalObjs; i++) {
            int count = ParseHelper.readUnsignedWasmInt(stream);
            ValType type = ValType.read(stream);
            for (int j = 0; j < count; j++)
                locals.add(type);
        }
        Expression expr = Expression.read(stream);
        locals.trimToSize();
        return new Code(size, index, locals, expr);
    }
}