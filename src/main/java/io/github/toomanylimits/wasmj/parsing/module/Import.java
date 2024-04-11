package io.github.toomanylimits.wasmj.parsing.module;

import io.github.toomanylimits.wasmj.parsing.ParseHelper;
import io.github.toomanylimits.wasmj.parsing.types.GlobalType;
import io.github.toomanylimits.wasmj.parsing.types.Limits;
import io.github.toomanylimits.wasmj.parsing.types.TableType;

import java.io.IOException;
import java.io.InputStream;

public abstract sealed class Import {
    public final String moduleName, elementName;
    public Import(String moduleName, String elementName) {
        this.moduleName = moduleName;
        this.elementName = elementName;
    }

    public static final class Func extends Import {
        public final int typeIndex;
        public Func(String moduleName, String elementName, int typeIndex) {
            super(moduleName, elementName);
            this.typeIndex = typeIndex;
        }
    }
    public static final class Table extends Import {
        public final TableType type;
        public Table(String moduleName, String elementName, TableType type) {
            super(moduleName, elementName);
            this.type = type;
        }
    }
    public static final class Mem extends Import {
        public final Limits limits;
        public Mem(String moduleName, String elementName, Limits limits) {
            super(moduleName, elementName);
            this.limits = limits;
        }
    }
    public static final class Global extends Import {
        public final GlobalType globalType;
        public Global(String moduleName, String elementName, GlobalType globalType) {
            super(moduleName, elementName);
            this.globalType = globalType;
        }
    }

    public static Import read(InputStream stream) throws IOException, ModuleParseException {
        String module = ParseHelper.readString(stream);
        String name = ParseHelper.readString(stream);
        int b = stream.read();
        return switch (b) {
            case 0 -> new Func(module, name, ParseHelper.readUnsignedWasmInt(stream));
            case 1 -> new Table(module, name, TableType.read(stream));
            case 2 -> new Mem(module, name, Limits.read(stream));
            case 3 -> new Global(module, name, GlobalType.read(stream));
            default -> throw new ModuleParseException("Expected import, got invalid description type " + b);
        };
    }

}
