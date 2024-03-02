package io.github.toomanylimits.wasmj.parsing.module;

import io.github.toomanylimits.wasmj.parsing.types.FuncType;
import io.github.toomanylimits.wasmj.parsing.types.Limits;
import io.github.toomanylimits.wasmj.parsing.types.TableType;
import io.github.toomanylimits.wasmj.util.ListUtils;
import io.github.toomanylimits.wasmj.parsing.ParseHelper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

public class WasmModule {
    public final List<FuncType> types;
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

    public WasmModule(InputStream stream) throws IOException, ModuleParseException {
        stream.skipNBytes(8); //remove magic and version

        var section = stream.read();
        while (section == 0) { stream.skipNBytes(ParseHelper.readUnsignedWasmInt(stream)); section = stream.read(); }
        if (section == 1) {
            int size = ParseHelper.readUnsignedWasmInt(stream);
            types = ParseHelper.readVector(stream, FuncType::read);
            section = stream.read();
        } else types = List.of();
        while (section == 0) { stream.skipNBytes(ParseHelper.readUnsignedWasmInt(stream)); section = stream.read(); }
        if (section == 2) {
            int size = ParseHelper.readUnsignedWasmInt(stream);
            imports = ParseHelper.readVector(stream, Import::read);
            section = stream.read();
        } else imports = List.of();
        while (section == 0) { stream.skipNBytes(ParseHelper.readUnsignedWasmInt(stream)); section = stream.read(); }
        if (section == 3) {
            int size = ParseHelper.readUnsignedWasmInt(stream);
            functions = ParseHelper.readVector(stream, ParseHelper::readUnsignedWasmInt);
            section = stream.read();
        } else functions = List.of();
        while (section == 0) { stream.skipNBytes(ParseHelper.readUnsignedWasmInt(stream)); section = stream.read(); }
        if (section == 4) {
            int size = ParseHelper.readUnsignedWasmInt(stream);
            tables = ParseHelper.readVector(stream, TableType::read);
            section = stream.read();
        } else tables = List.of();
        while (section == 0) { stream.skipNBytes(ParseHelper.readUnsignedWasmInt(stream)); section = stream.read(); }
        if (section == 5) {
            int size = ParseHelper.readUnsignedWasmInt(stream);
            memories = ParseHelper.readVector(stream, Limits::read);
            section = stream.read();
        } else memories = List.of();
        while (section == 0) { stream.skipNBytes(ParseHelper.readUnsignedWasmInt(stream)); section = stream.read(); }
        if (section == 6) {
            int size = ParseHelper.readUnsignedWasmInt(stream);
            globals = ParseHelper.readVector(stream, Global::read);
            section = stream.read();
        } else globals = List.of();
        while (section == 0) { stream.skipNBytes(ParseHelper.readUnsignedWasmInt(stream)); section = stream.read(); }
        if (section == 7) {
            int size = ParseHelper.readUnsignedWasmInt(stream);
            exports = ParseHelper.readVector(stream, Export::read);
            section = stream.read();
        } else exports = List.of();
        while (section == 0) { stream.skipNBytes(ParseHelper.readUnsignedWasmInt(stream)); section = stream.read(); }
        if (section == 8) {
            int size = ParseHelper.readUnsignedWasmInt(stream);
            start = ParseHelper.readUnsignedWasmInt(stream);
            section = stream.read();
        } else start = null;
        while (section == 0) { stream.skipNBytes(ParseHelper.readUnsignedWasmInt(stream)); section = stream.read(); }
        if (section == 9) {
            int size = ParseHelper.readUnsignedWasmInt(stream);
            elements = ParseHelper.readVector(stream, Element::read);
            section = stream.read();
        } else elements = List.of();
        while (section == 0) { stream.skipNBytes(ParseHelper.readUnsignedWasmInt(stream)); section = stream.read(); }
        if (section == 12) {
            int size = ParseHelper.readUnsignedWasmInt(stream);
            datacount = ParseHelper.readUnsignedWasmInt(stream);
            section = stream.read();
        } else datacount = null;
        while (section == 0) { stream.skipNBytes(ParseHelper.readUnsignedWasmInt(stream)); section = stream.read(); }
        if (section == 10) {
            int size = ParseHelper.readUnsignedWasmInt(stream);
            codes = ParseHelper.readVectorIndexed(stream, (index, s) -> Code.read(index, types, functions, s));
            section = stream.read();
        } else codes = List.of();
        while (section == 0) { stream.skipNBytes(ParseHelper.readUnsignedWasmInt(stream)); section = stream.read(); }
        if (section == 11) {
            int size = ParseHelper.readUnsignedWasmInt(stream);
            datas = ParseHelper.readVector(stream, Data::read);
            section = stream.read();
        } else datas = List.of();
        while (section == 0) {
            int MAX_PRINTED_BYTES = 3000;
            int len = ParseHelper.readUnsignedWasmInt(stream);
            if (len < MAX_PRINTED_BYTES) {
                byte[] contents = stream.readNBytes(len);
                ByteArrayInputStream data = new ByteArrayInputStream(contents);
                String name = ParseHelper.readString(data);
                byte[] bytes = data.readAllBytes();
                System.out.println("Custom section \"" + name + "\" = " + Arrays.toString(bytes));
            } else {
                stream.skipNBytes(len);
            }
            section = stream.read();
        }
    }

    // Helpers with cached, filtered lists of the different varieties

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

}

