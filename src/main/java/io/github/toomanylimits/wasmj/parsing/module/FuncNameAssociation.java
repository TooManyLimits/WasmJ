package io.github.toomanylimits.wasmj.parsing.module;

import io.github.toomanylimits.wasmj.parsing.ParseHelper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

// Note: the funcIndex INCLUDES IMPORTS!
public record FuncNameAssociation(int funcIndex, String name) {

    // Illegal characters are these 6: < > ; [ . /
    private static String sanitize(String rawName) {
        return rawName
                .replace('<', '(').replace("$LT$", "(") // < > in rust export
                .replace('>', ')').replace("$GT$", ")") // < > in rust export
                .replace(';', '!') // Replace semicolon with exclamation? idk probably close enough
                .replace('[', '(').replace(']', ')') // replace both square bracket types
                .replace('.', ':') // Replace . with :
                .replace('/', '\\') // Replace slash with backslash

                .replace("$u20$", " "); // Spaces in rust
    }

    public static FuncNameAssociation read(InputStream stream) throws IOException, ModuleParseException {
        int index = ParseHelper.readUnsignedWasmInt(stream);
        String name = ParseHelper.readString(stream);
        return new FuncNameAssociation(index, sanitize(name));
    }

    public static String find(List<FuncNameAssociation> associations, int funcIndex) {
        for (FuncNameAssociation association : associations) {
            if (association.funcIndex() == funcIndex) return association.name;
            if (association.funcIndex() > funcIndex) return null;
        }
        return null;
    }

}
