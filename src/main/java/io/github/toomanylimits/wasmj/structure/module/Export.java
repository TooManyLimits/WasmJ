package io.github.toomanylimits.wasmj.structure.module;

import io.github.toomanylimits.wasmj.structure.utils.Util;

import java.io.IOException;
import java.io.InputStream;

public record Export(String name, int index, ExportType type) {

    public enum ExportType {
        FUNC, TABLE, MEM, GLOBAL
    }

    public static Export read(InputStream stream) throws IOException, ModuleParseException {
        String name = Util.readString(stream);
        int b = stream.read();
        return switch (b) {
            case 0 -> new Export(name, Util.readUnsignedWasmInt(stream), ExportType.FUNC);
            case 1 -> new Export(name, Util.readUnsignedWasmInt(stream), ExportType.TABLE);
            case 2 -> new Export(name, Util.readUnsignedWasmInt(stream), ExportType.MEM);
            case 3 -> new Export(name, Util.readUnsignedWasmInt(stream), ExportType.GLOBAL);
            default -> throw new ModuleParseException("Unexpected byte for export description: " + b);
        };
    }

}
