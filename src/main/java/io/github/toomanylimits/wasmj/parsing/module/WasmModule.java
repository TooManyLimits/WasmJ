package io.github.toomanylimits.wasmj.parsing.module;

import io.github.toomanylimits.wasmj.parsing.ParseHelper;
import io.github.toomanylimits.wasmj.parsing.instruction.StackType;
import io.github.toomanylimits.wasmj.parsing.types.GlobalType;
import io.github.toomanylimits.wasmj.parsing.types.Limits;
import io.github.toomanylimits.wasmj.parsing.types.TableType;
import io.github.toomanylimits.wasmj.util.ListUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WasmModule {
    public final List<StackType> types;
    public final List<Import> imports;
    public final List<Integer> functions;
    public final List<TableType> tables;
    public final List<Limits> memories;
    public final List<Global> globals;
    public final List<Export> exports;
    public final Integer start;
    public final List<Element> elements;
    public final List<Code> codes;
    public final List<Data> datas;
    public final Integer datacount;

    // Names
    public List<FuncNameAssociation> debugFuncNames = List.of();

    // Current section during parsing
    private int section;

    public WasmModule(InputStream stream) throws IOException, ModuleParseException {
        stream.skipNBytes(8); //remove magic and version

        section = stream.read();
        handleCustomSections(stream);
        if (section == 1) {
            int size = ParseHelper.readUnsignedWasmInt(stream);
            types = ParseHelper.readVector(stream, StackType::readFuncType);
            section = stream.read();
        } else types = List.of();
        handleCustomSections(stream);
        if (section == 2) {
            int size = ParseHelper.readUnsignedWasmInt(stream);
            imports = ParseHelper.readVector(stream, Import::read);
            section = stream.read();
        } else imports = List.of();
        handleCustomSections(stream);
        if (section == 3) {
            int size = ParseHelper.readUnsignedWasmInt(stream);
            functions = ParseHelper.readVector(stream, ParseHelper::readUnsignedWasmInt);
            section = stream.read();
        } else functions = List.of();
        handleCustomSections(stream);
        if (section == 4) {
            int size = ParseHelper.readUnsignedWasmInt(stream);
            tables = ParseHelper.readVector(stream, TableType::read);
            section = stream.read();
        } else tables = List.of();
        handleCustomSections(stream);
        if (section == 5) {
            int size = ParseHelper.readUnsignedWasmInt(stream);
            memories = ParseHelper.readVector(stream, Limits::read);
            section = stream.read();
        } else memories = List.of();
        handleCustomSections(stream);
        if (section == 6) {
            int size = ParseHelper.readUnsignedWasmInt(stream);
            globals = ParseHelper.readVector(stream, i -> Global.read(i, types, imports));
            section = stream.read();
        } else globals = List.of();
        handleCustomSections(stream);
        if (section == 7) {
            int size = ParseHelper.readUnsignedWasmInt(stream);
            exports = ParseHelper.readVector(stream, Export::read);
            section = stream.read();
        } else exports = List.of();
        handleCustomSections(stream);
        if (section == 8) {
            int size = ParseHelper.readUnsignedWasmInt(stream);
            start = ParseHelper.readUnsignedWasmInt(stream);
            section = stream.read();
        } else start = null;
        handleCustomSections(stream);
        if (section == 9) {
            int size = ParseHelper.readUnsignedWasmInt(stream);
            elements = ParseHelper.readVector(stream, i -> Element.read(i, types, imports));
            section = stream.read();
        } else elements = List.of();
        handleCustomSections(stream);
        if (section == 12) {
            int size = ParseHelper.readUnsignedWasmInt(stream);
            datacount = ParseHelper.readUnsignedWasmInt(stream);
            section = stream.read();
        } else datacount = null;
        handleCustomSections(stream);
        if (section == 10) {
            int size = ParseHelper.readUnsignedWasmInt(stream);
            codes = ParseHelper.readVectorIndexed(stream, (index, s) -> Code.read(index, types, functions, s));
            section = stream.read();
        } else codes = List.of();
        handleCustomSections(stream);
        if (section == 11) {
            int size = ParseHelper.readUnsignedWasmInt(stream);
            datas = ParseHelper.readVector(stream, i -> Data.read(i, types, imports));
            section = stream.read();
        } else datas = List.of();
        handleCustomSections(stream);
    }

    // Helpers!
    private List<Import.Func> funcImportsCache = null;
    public List<Import.Func> funcImports() {
        if (funcImportsCache == null)
            funcImportsCache = ListUtils.map(ListUtils.filter(imports, it -> it instanceof Import.Func), it -> (Import.Func) it);
        return funcImportsCache;
    }
    private List<Import.Table> tableImportsCache = null;
    public List<Import.Table> tableImports() {
        if (tableImportsCache == null)
            tableImportsCache = ListUtils.map(ListUtils.filter(imports, it -> it instanceof Import.Table), it -> (Import.Table) it);
        return tableImportsCache;
    }
    private List<Import.Mem> memImportsCache = null;
    public List<Import.Mem> memImports() {
        if (memImportsCache == null)
            memImportsCache = ListUtils.map(ListUtils.filter(imports, it -> it instanceof Import.Mem), it -> (Import.Mem) it);
        return memImportsCache;
    }
    private List<Import.Global> globalImportsCache = null;
    public List<Import.Global> globalImports() {
        if (globalImportsCache == null)
            globalImportsCache = ListUtils.map(ListUtils.filter(imports, it -> it instanceof Import.Global), it -> (Import.Global) it);
        return globalImportsCache;
    }

    // Gets the type of the function at the given defaultIndex, taking imports into account
    public StackType getFunctionType(int funcIndex) {
        if (funcIndex < funcImports().size()) {
            return types.get(funcImports().get(funcIndex).typeIndex);
        } else {
            int adjustedIndex = funcIndex - funcImports().size();
            return types.get(functions.get(adjustedIndex));
        }
    }
    public GlobalType getGlobalType(int globalIndex) {
        if (globalIndex < globalImports().size()) {
            return globalImports().get(globalIndex).globalType;
        } else {
            int adjustedIndex = globalIndex - globalImports().size();
            return globals.get(adjustedIndex).globalType();
        }
    }
    public TableType getTableType(int tableIndex) {
        if (tableIndex < tableImports().size()) {
            return tableImports().get(tableIndex).type;
        } else {
            int adjustedIndex = tableIndex - tableImports().size();
            return tables.get(adjustedIndex);
        }
    }

    private void handleCustomSections(InputStream stream) throws IOException, ModuleParseException {
        while (section == 0) {
            int len = ParseHelper.readUnsignedWasmInt(stream); // Read length
            ByteArrayInputStream customData = new ByteArrayInputStream(stream.readNBytes(len)); // Read that many bytes and put aside
            String name = ParseHelper.readString(customData);
            CustomSectionHandler handler = CUSTOM_SECTION_HANDLERS.get(name);
            // If we have a handler, use it.
            if (handler != null)
                handler.handle(this, customData);
            section = stream.read();
        }
    }

    @FunctionalInterface
    public interface CustomSectionHandler {
        void handle(WasmModule inProgressModule, InputStream stream) throws IOException, ModuleParseException;
    }

    public static final Map<String, CustomSectionHandler> CUSTOM_SECTION_HANDLERS = new HashMap<>() {{
        // Name handler. This deals with debug symbols.
        // https://webassembly.github.io/spec/core/appendix/custom.html
        put("name", (inProgressModule, stream) -> {
            int subsection = stream.read();
            if (subsection == 0) {
                // Module name
                int len = ParseHelper.readUnsignedWasmInt(stream);
                stream.skipNBytes(len); // Ignore for now
                subsection = stream.read();
            }
            if (subsection == 1) {
                // Function names
                int len = ParseHelper.readUnsignedWasmInt(stream);
                inProgressModule.debugFuncNames = ParseHelper.readVector(stream, FuncNameAssociation::read);
                subsection = stream.read();
            }
            if (subsection == 2) {
                // Local variable names
                int len = ParseHelper.readUnsignedWasmInt(stream);
                stream.skipNBytes(len); // Ignore for now
            }
        });
    }};


}

