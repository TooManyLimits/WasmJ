package io.github.toomanylimits.wasmj.compiling.simple_structure;

import io.github.toomanylimits.wasmj.compiling.simple_structure.data.SimpleData;
import io.github.toomanylimits.wasmj.compiling.simple_structure.data.SimpleElem;
import io.github.toomanylimits.wasmj.compiling.simple_structure.members.SimpleFunction;
import io.github.toomanylimits.wasmj.compiling.simple_structure.members.SimpleGlobal;
import io.github.toomanylimits.wasmj.compiling.simple_structure.members.SimpleMemory;
import io.github.toomanylimits.wasmj.compiling.simple_structure.members.SimpleTable;
import io.github.toomanylimits.wasmj.compiling.simplify.InstructionConversionVisitor;
import io.github.toomanylimits.wasmj.compiling.simplify.Validator;
import io.github.toomanylimits.wasmj.parsing.instruction.Expression;
import io.github.toomanylimits.wasmj.parsing.instruction.Instruction;
import io.github.toomanylimits.wasmj.parsing.instruction.StackType;
import io.github.toomanylimits.wasmj.parsing.module.*;
import io.github.toomanylimits.wasmj.parsing.types.GlobalType;
import io.github.toomanylimits.wasmj.parsing.types.Limits;
import io.github.toomanylimits.wasmj.parsing.types.TableType;
import io.github.toomanylimits.wasmj.parsing.types.ValType;
import io.github.toomanylimits.wasmj.runtime.WasmInstance;
import io.github.toomanylimits.wasmj.runtime.reflect.JavaModuleData;
import io.github.toomanylimits.wasmj.util.ListUtils;
import org.objectweb.asm.Opcodes;

import javax.swing.plaf.ListUI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A module, after having been converted to the simplified format.
 * This module's data is structured in a way that's closer to the eventual
 * JVM bytecode representation.
 */
public class SimpleModule {

    public final String moduleName; // The name given to this module, for others to refer to it by
    public final WasmInstance instance;

    public final SimpleFunction[] functions; // All functions, including imported ones
    public final SimpleGlobal[] globals; // All globals, including imported ones
    public final SimpleTable[] tables; // All tables, including imported ones
    public final SimpleMemory memory; // All memories, including imported ones. WASM only supports 1 memory per module for now, so it's not an array.

    public final SimpleData[] datas; // All datas
    public final SimpleElem[] elems; // All elems


    /**
     * To create a SimpleModule, we need to give it:
     * - A name for the module
     * - A WasmModule to base it off of
     * - The instance in which this module is being created
     */
    public SimpleModule(String moduleName, WasmModule wasmModule, WasmInstance instance) throws Validator.ValidationException {
        // Get basic values
        this.moduleName = moduleName;
        this.instance = instance;

        // Functions:
        this.functions = new SimpleFunction[wasmModule.funcImports().size() + wasmModule.functions.size()];
        Map<Integer, String> exportedFuncs = ListUtils.associateByTo(ListUtils.filter(wasmModule.exports, e -> e.type() == Export.ExportType.FUNC), Export::index, Export::name);
        for (int i = 0; i < wasmModule.funcImports().size(); i++) {
            Import.Func funcImport = wasmModule.funcImports().get(i);
            String importModule = funcImport.moduleName;
            StackType funcType = wasmModule.types.get(funcImport.typeIndex);
            if (instance.instanceJavaModules.containsKey(importModule)) { // Check if import module is in the map of java modules
                // It's an imported java function
                JavaModuleData<?> javaModuleData = instance.instanceJavaModules.get(importModule);
                JavaModuleData.MethodData methodData = javaModuleData.allowedMethods.get(funcImport.elementName);
                if (methodData == null)
                    throw new Validator.ValidationException("Attempt to import nonexistent function. Module \"" + moduleName + "\" tried to import (" + funcImport.moduleName + " . " + funcImport.elementName + ")");
                String exportedAs = exportedFuncs.get(i);
                if (exportedAs != null)
                    throw new UnsupportedOperationException("Cannot re-export imported java functions! Module \"" + moduleName + "\" tried to export ( " + funcImport.moduleName + " . " + funcImport.elementName + ") as \"" + exportedAs + "\"");
                this.functions[i] = new SimpleFunction.ImportedJavaFunction(i, funcType, importModule, javaModuleData, methodData);
            } else {
                // It's an imported WASM function
                String exportedAs = exportedFuncs.get(i);
                this.functions[i] = new SimpleFunction.ImportedWasmFunction(importModule, exportedAs, funcImport.elementName, funcType);
            }
        }
        for (int i = wasmModule.funcImports().size(); i < functions.length; i++) {
            // Get adjusted defaultIndex and type
            int adjustedIndex = i - wasmModule.funcImports().size();
            StackType funcType = wasmModule.types.get(wasmModule.functions.get(adjustedIndex));
            // Convert the instructions
            Code code = wasmModule.codes.get(adjustedIndex);
            InstructionConversionVisitor converter = new InstructionConversionVisitor(wasmModule, code.locals, funcType.outTypes());
            // Initialize the locals to 0/null, since WASM expects this to be the case
            ArrayList<SimpleInstruction> funcBody = new ArrayList<>();
            for (int localIndex = funcType.inTypes().size(); localIndex < code.locals.size(); localIndex++) {
                ValType local = code.locals.get(localIndex);
                switch (local) {
                    case I32 -> funcBody.add(new Instruction.I32Const(0).accept(converter));
                    case I64 -> funcBody.add(new Instruction.I64Const(0).accept(converter));
                    case F32 -> funcBody.add(new Instruction.F32Const(0).accept(converter));
                    case F64 -> funcBody.add(new Instruction.F64Const(0).accept(converter));
                    case EXTERNREF, FUNCREF -> funcBody.add(new Instruction.RefNull(local).accept(converter));
                    default -> throw new IllegalArgumentException();
                }
                funcBody.add(new Instruction.LocalSet(localIndex).accept(converter));
            }
            // Write the main function body
            funcBody.addAll(ListUtils.flatMapNonNull(code.expr.getInstructions(), x -> x.accept(converter)));
            funcBody.add(converter.visitReturn(Instruction.Return.INSTANCE)); // Return at the end!
            // Create the function and store it in the array
            String exportedAs = exportedFuncs.get(i);
            this.functions[i] = new SimpleFunction.SameFileFunction(adjustedIndex, funcType, exportedAs, funcBody, converter.nextLocalSlot);
        }

        // Globals
        this.globals = new SimpleGlobal[wasmModule.globalImports().size() + wasmModule.globals.size()];
        Map<Integer, String> exportedGlobals = ListUtils.associateByTo(ListUtils.filter(wasmModule.exports, e -> e.type() == Export.ExportType.GLOBAL), Export::index, Export::name);
        for (int i = 0; i < wasmModule.globalImports().size(); i++) {
            Import.Global globalImport = wasmModule.globalImports().get(i);
            String importModule = globalImport.moduleName;
            String importName = globalImport.elementName;
            GlobalType globalType = globalImport.globalType;
            String exportedAs = exportedGlobals.get(i);
            this.globals[i] = new SimpleGlobal.ImportedGlobal(importModule, importName, exportedAs, globalType);
        }
        for (int i = wasmModule.globalImports().size(); i < globals.length; i++) {
            // Get adjusted defaultIndex and type
            int adjustedIndex = i - wasmModule.globalImports().size();
            GlobalType globalType = wasmModule.globals.get(adjustedIndex).globalType();
            // Convert instructions
            Expression initializer = wasmModule.globals.get(adjustedIndex).initializer();
            InstructionConversionVisitor converter = new InstructionConversionVisitor(wasmModule, List.of(), null);
            ArrayList<SimpleInstruction> initInstructions = ListUtils.flatMapNonNull(initializer.getInstructions(), x -> x.accept(converter));
            // Create the global and store it
            String exportedAs = exportedGlobals.get(i);
            this.globals[i] = new SimpleGlobal.SameFileGlobal(adjustedIndex, globalType, exportedAs, initInstructions);
        }

        // Tables
        this.tables = new SimpleTable[wasmModule.tableImports().size() + wasmModule.tables.size()];
        Map<Integer, String> exportedTables = ListUtils.associateByTo(ListUtils.filter(wasmModule.exports, e -> e.type() == Export.ExportType.TABLE), Export::index, Export::name);
        for (int i = 0; i < wasmModule.tableImports().size(); i++) {
            Import.Table tableImport = wasmModule.tableImports().get(i);
            String importModule = tableImport.moduleName;
            String importName = tableImport.elementName;
            String exportedAs = exportedTables.get(i);
            this.tables[i] = new SimpleTable.ImportedTable(importModule, importName, exportedAs);
        }
        for (int i = wasmModule.tableImports().size(); i < tables.length; i++) {
            // Get adjusted defaultIndex and type
            int adjustedIndex = i - wasmModule.tableImports().size();
            TableType tableType = wasmModule.tables.get(adjustedIndex);
            // Create the global and store it
            String exportedAs = exportedTables.get(i);
            this.tables[i] = new SimpleTable.SameFileTable(adjustedIndex, tableType, exportedAs);
        }

        // Memories (really memory, since max of 1 is allowed)
        Map<Integer, String> exportedMemories = ListUtils.associateByTo(ListUtils.filter(wasmModule.exports, e -> e.type() == Export.ExportType.MEM), Export::index, Export::name);
        if (wasmModule.memImports().size() + wasmModule.memories.size() > 1)
            throw new Validator.ValidationException("Too many memories in module \"" + moduleName + "\"; WASM only supports one!");
        if (wasmModule.memImports().size() + wasmModule.memories.size() == 0) {
            // No memories at all, create a dummy empty memory of 0 bytes
            memory = new SimpleMemory.SameFileMemory(0, new Limits(0, 0), null);
        } else if (wasmModule.memImports().size() == 1) {
            // The memory is imported
            throw new IllegalStateException("Memory imports not yet implemented");
        } else if (wasmModule.memories.size() == 1) {
            // The memory is defined in this file
            memory = new SimpleMemory.SameFileMemory(0, wasmModule.memories.get(0), exportedMemories.get(0));
        } else throw new IllegalStateException();

        // Datas
        this.datas = new SimpleData[wasmModule.datas.size()];
        for (int i = 0; i < wasmModule.datas.size(); i++) {
            Data data = wasmModule.datas.get(i);
            if (data.mode() instanceof Data.Mode.Active active) {
                InstructionConversionVisitor converter = new InstructionConversionVisitor(wasmModule, List.of(), null);

                // This data is active, so "memory.init" it right away, then "data.drop" it.
                ArrayList<SimpleInstruction> instructionsToExecute = ListUtils.flatMapNonNull(active.offset().getInstructions(), x -> x.accept(converter)); // Stack = [offset (destination)]
                instructionsToExecute.add(converter.visitI32Const(new Instruction.I32Const(0))); // [offset, 0 (source)]
                instructionsToExecute.add(converter.visitI32Const(new Instruction.I32Const(data.init().length))); // [dest, src, len]
                instructionsToExecute.add(converter.visitMemoryInit(new Instruction.MemoryInit(i))); // []
                instructionsToExecute.add(converter.visitDataDrop(new Instruction.DataDrop(i))); // Drop the data
                instructionsToExecute.trimToSize();

                datas[i] = new SimpleData(i, data.init(), instructionsToExecute);
            } else {
                datas[i] = new SimpleData(i, data.init(), null);
            }
        }

        // Elems
        this.elems = new SimpleElem[wasmModule.elements.size()];
        for (int i = 0; i < wasmModule.elements.size(); i++) {
            Element elem = wasmModule.elements.get(i);

            InstructionConversionVisitor converter = new InstructionConversionVisitor(wasmModule, List.of(), null);
            List<List<SimpleInstruction>> initializers = ListUtils.map(elem.exprs(), expr -> ListUtils.flatMapNonNull(expr.getInstructions(), x -> x.accept(converter)));

            if (elem.mode() instanceof Element.Mode.Active active) {
                List<SimpleInstruction> offset = ListUtils.flatMapNonNull(active.offset().getInstructions(), x -> x.accept(converter));
                elems[i] = new SimpleElem(i, initializers, active.tableIndex(), offset);
            } else if (elem.mode() == Element.Mode.Passive.INSTANCE) {
                elems[i] = new SimpleElem(i, initializers, null, null);
            } else {
                throw new UnsupportedOperationException("Declarative element segments are not supported, because I genuinely don't get what the point of them is and I don't know how or why to implement them");
            }
        }

    }



}
