package io.github.toomanylimits.wasmj.compiler;

import io.github.toomanylimits.wasmj.structure.instruction.Instruction;
import io.github.toomanylimits.wasmj.structure.instruction.StackType;
import io.github.toomanylimits.wasmj.structure.module.Code;
import io.github.toomanylimits.wasmj.structure.module.Import;
import io.github.toomanylimits.wasmj.structure.module.WasmModule;
import io.github.toomanylimits.wasmj.structure.types.FuncType;
import io.github.toomanylimits.wasmj.structure.types.ValType;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.invoke.VarHandle;
import java.util.*;

public class MethodWritingVisitor extends InstructionVisitor<Void> {

    private final String moduleName;
    private final WasmModule module;
    private final Code code;
    private final MethodVisitor visitor;

    public MethodWritingVisitor(String moduleName, WasmModule module, Code code, MethodVisitor visitor) {
        this.moduleName = moduleName;
        this.module = module;
        this.code = code;
        this.visitor = visitor;
    }

    private sealed interface AbstractStackElement {
        record ValueElement(ValType valueType) implements AbstractStackElement {}
        record LabelElement(Label asmLabel, int arity) implements AbstractStackElement {}
    }

    private static class AbstractStack implements Iterable<AbstractStackElement> {
        private final ArrayList<AbstractStackElement> elements = new ArrayList<>();

        // Stack operations
        public void push(AbstractStackElement elem) { elements.add(0, elem); }
        public AbstractStackElement peek() { return elements.get(0); }
        public AbstractStackElement pop() { return elements.remove(0); }

        // List operations
        public void add(int index, AbstractStackElement element) { elements.add(index, element); }
        public boolean remove(AbstractStackElement elem) { return elements.remove(elem); }
        public AbstractStackElement get(int index) { return elements.get(index); }
        public int size() { return elements.size(); }
        @Override public Iterator<AbstractStackElement> iterator() {
            return elements.iterator();
        }

        // WASM operations

        // Pop the top value of the stack and return its type
        public ValType popTakeType() {
            AbstractStackElement elem = pop();
            if (elem instanceof AbstractStackElement.ValueElement v)
                return v.valueType;
            throw new IllegalStateException("Malformed WASM code, or bug in compiler");
        }
        // Pop the top value of the stack, expecting the given type
        public void popExpecting(ValType type) {
            AbstractStackElement elem = pop();
            if (!(elem instanceof AbstractStackElement.ValueElement v) || v.valueType != type)
                throw new IllegalStateException("Malformed WASM code, or bug in compiler. Expected " + type + ", found " + elem);
        }
        public void popExpecting(ValType... types) {
            AbstractStackElement elem = pop();
            if (!(elem instanceof AbstractStackElement.ValueElement v) || !(Arrays.asList(types).contains(v.valueType)))
                throw new IllegalStateException("Malformed WASM code, or bug in compiler. Expected " + Arrays.toString(types) + ", found " + elem);
        }
        // Peek the top value of the stack, expecting the given type
        public void peekExpecting(ValType type) {
            AbstractStackElement elem = peek();
            if (!(elem instanceof AbstractStackElement.ValueElement v) || v.valueType != type)
                throw new IllegalStateException("Malformed WASM code, or bug in compiler");
        }
        // Ensure that the top of the stack matches the given expected types
        public void checkStackMatches(List<ValType> expectedTypes) {
            for (int index = 0; index < expectedTypes.size(); index++) {
                ValType valType = expectedTypes.get(index);
                AbstractStackElement elem = elements.get(expectedTypes.size() - index - 1);
                if (!(elem instanceof AbstractStackElement.ValueElement v) || v.valueType != valType)
                    throw new IllegalStateException("Malformed WASM code, or bug in compiler");
            }
        }
        // Check that the instruction's stack type matches what currently is on the stack,
        // and also modify the abstract stack accordingly
        public void applyStackType(StackType stackType) {
            checkStackMatches(stackType.inTypes());
            if (stackType.inTypes().equals(stackType.outTypes()))
                return;
            for (ValType type : stackType.inTypes())
                pop();
            for (ValType type : stackType.outTypes())
                push(new AbstractStackElement.ValueElement(type));
        }


    }

    private final AbstractStack abstractStack = new AbstractStack();

    @Override public Void visitEnd(Instruction.End inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitElse(Instruction.Else inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    // Throw a runtime error
    @Override public Void visitUnreachable(Instruction.Unreachable inst) {
        BytecodeHelper.throwRuntimeError(visitor, "Hit unreachable instruction - exiting");
        return null;
    }

    // Do nothing
    @Override public Void visitNop(Instruction.Nop inst) {
        return null;
    }

    // Declare label, visit inner instructions, emit label
    @Override public Void visitBlock(Instruction.Block inst) {
        StackType stackType = inst.bt().stackType(module);
        Label label = new Label();
        AbstractStackElement labelElem = new AbstractStackElement.LabelElement(label, stackType.outTypes().size());
        abstractStack.add(stackType.inTypes().size(), labelElem);
        int stackHeightBefore = abstractStack.size();
        for (Instruction inner : inst.inside())
            inner.accept(this);
        int stackHeightAfter = abstractStack.size();
        if (stackHeightAfter - stackHeightBefore != stackType.outTypes().size() - stackType.inTypes().size()) {
            if (stackHeightAfter - stackHeightBefore < stackType.outTypes().size() - stackType.inTypes().size())
                throw new IllegalStateException("Bug in compiler, please report! Stack height before was " + stackHeightBefore + ", stack height after was " + stackHeightAfter + ". Expected change was " + (stackType.outTypes().size() - stackType.inTypes().size()));
            int numToPop = (stackHeightAfter - stackHeightBefore) - (stackType.outTypes().size() - stackType.inTypes().size());
            for (int i = 0; i < numToPop; i++) {
                AbstractStackElement top = abstractStack.pop();
                if (!(top instanceof AbstractStackElement.ValueElement))
                    throw new IllegalStateException("Malformed WASM code, or bug in compiler");
            }
        }
        visitor.visitLabel(label);
        if (!abstractStack.remove(labelElem)) throw new IllegalStateException("Bug in compiler, please report");
        return null;
    }

    // Declare and emit label, visit inner instructions
    @Override public Void visitLoop(Instruction.Loop inst) {
        StackType stackType = inst.bt().stackType(module);
        Label label = new Label();
        AbstractStackElement labelElem = new AbstractStackElement.LabelElement(label, stackType.inTypes().size());
        abstractStack.add(stackType.inTypes().size(), labelElem);
        visitor.visitLabel(label);
        for (Instruction inner : inst.inside())
            inner.accept(this);
        if (!abstractStack.remove(labelElem)) throw new IllegalStateException("Bug in compiler, please report");
        return null;
    }

    // If false, jump to end
    @Override public Void visitIf(Instruction.If inst) {
        abstractStack.popExpecting(ValType.i32);
        Label end = new Label();
        visitor.visitJumpInsn(Opcodes.IFEQ, end);
        visitBlock(new Instruction.Block(inst.bt(), inst.inside())); // Delegate to Instruction.Block() implementation
        visitor.visitLabel(end);
        return null;
    }

    // Standard if-else
    @Override public Void visitIfElse(Instruction.IfElse inst) {
        abstractStack.popExpecting(ValType.i32);
        Label ifFalse = new Label();
        Label end = new Label();

        visitor.visitJumpInsn(Opcodes.IFEQ, ifFalse);
        visitBlock(new Instruction.Block(inst.bt(), inst.ifBlock()));
        visitor.visitJumpInsn(Opcodes.GOTO, end);
        visitor.visitLabel(ifFalse);
        visitBlock(new Instruction.Block(inst.bt(), inst.elseBlock()));
        visitor.visitLabel(end);
        // Only one of the paths will be taken, so only let one block affect the stack
        // Undo the actions of one block upon the stack
        StackType blocktype = inst.bt().stackType(module);
        int num = blocktype.outTypes().size() - blocktype.inTypes().size();
        for (int i = 0; i < num; i++) {
            AbstractStackElement top = abstractStack.pop();
            if (!(top instanceof AbstractStackElement.ValueElement))
                throw new IllegalStateException("Malformed WASM code, or bug in compiler");
        }
        return null;
    }

    // Go to the proper label
    @Override public Void visitBranch(Instruction.Branch inst) {
        // Find the i+1'th label on the stack
        // Also count how many values are on the stack past that point
        int valueCount = 0;
        int curIndex = 0;
        AbstractStackElement.LabelElement finalLabel = null;
        for (int i = 0; i <= inst.index(); i++) {
            while (abstractStack.get(curIndex) instanceof AbstractStackElement.ValueElement) {
                valueCount++;
                curIndex++;
            }
            AbstractStackElement label = abstractStack.get(curIndex);
            if (label instanceof AbstractStackElement.LabelElement labelElement) {
                finalLabel = labelElement;
                curIndex++;
            } else throw new IllegalStateException("Malformed WASM code, or bug in compiler");
        }

        // If the arity and the # of values are equal, just do a jump
        if (finalLabel.arity == valueCount) {
            visitor.visitJumpInsn(Opcodes.GOTO, finalLabel.asmLabel);
            return null;
        }
        if (valueCount < finalLabel.arity) throw new IllegalStateException("Malformed WASM code, or bug in compiler");
        // Otherwise... need to do some annoying things

        // Store the top `arity` values in local variables, pop the rest, then restore
        // the `arity` values.
        int curLocalIndex = code.nextLocalSlot();
        int valueIndex = 0;
        int labelIndex = 0;
        ArrayDeque<ValType> tempLocals = new ArrayDeque<>();
        for (AbstractStackElement elem : abstractStack) {
            if (elem instanceof AbstractStackElement.ValueElement valueElem) {
                if (valueIndex < finalLabel.arity) {
                    // Store as local
                    tempLocals.push(valueElem.valueType());
                    BytecodeHelper.storeLocal(visitor, curLocalIndex, valueElem.valueType());
                    curLocalIndex += valueElem.valueType().stackSlots();
                } else if (valueIndex < valueCount) {
                    // Pop it
                    BytecodeHelper.popValue(visitor, valueElem.valueType());
                } else {
                    throw new IllegalStateException("Bug in compiler");
                }
                valueIndex++;
                if (valueIndex == valueCount) break; // We're done
            } else if (elem instanceof AbstractStackElement.LabelElement) {
                if (labelIndex == inst.index())
                    throw new IllegalStateException("Malformed WASM code, or bug in compiler");
                labelIndex++;
            }
        }
        // Restore the locals
        for (ValType t : tempLocals) {
            curLocalIndex -= t.stackSlots();
            BytecodeHelper.loadLocal(visitor, curLocalIndex, t);
        }
        // Now finally jump.
        visitor.visitJumpInsn(Opcodes.GOTO, finalLabel.asmLabel);
        return null;
    }

    @Override public Void visitBranchIf(Instruction.BranchIf inst) {
        abstractStack.popExpecting(ValType.i32);
        // Value on top of the stack is an i32.
        // If it's != 0, then branch, otherwise not.
        Label end = new Label();
        visitor.visitJumpInsn(Opcodes.IFEQ, end); // Jump to end if 0
        visitBranch(new Instruction.Branch(inst.index())); // Emit a branch
        visitor.visitLabel(end); // Label
        return null;
    }

    @Override public Void visitBranchTable(Instruction.BranchTable inst) {
        abstractStack.popExpecting(ValType.i32);
        // Create all the labels and visit the table switch
        Label defaultLabel = new Label();
        Label[] labels = new Label[inst.indices().size()];
        for (int i = 0; i < labels.length; i++)
            labels[i] = new Label();
        visitor.visitTableSwitchInsn(0, inst.indices().size() - 1, defaultLabel, labels);
        // For each label, run a Branch instruction
        for (int index = 0; index < labels.length; index++) {
            Label label = labels[index];
            int wasmLabelDepth = inst.indices().get(index);
            visitor.visitLabel(label);
            visitBranch(new Instruction.Branch(wasmLabelDepth));
        }
        // Do the same for the default label
        visitor.visitLabel(defaultLabel);
        visitBranch(new Instruction.Branch(inst.index()));
        return null;
    }

    @Override public Void visitReturn(Instruction.Return inst) {
        BytecodeHelper.debugPrintln(visitor, "Returning");
        FuncType functionType = module.types.get(module.functions.get(code.index));
        for (int i = functionType.results.size() - 1; i >= 0; i--)
            abstractStack.popExpecting(functionType.results.get(i));
        switch (functionType.results.size()) {
            case 0 -> visitor.visitInsn(Opcodes.RETURN);
            case 1 -> BytecodeHelper.returnValue(visitor, functionType.results.get(0));
            default -> throw new UnsupportedOperationException("Multi-return functions");
        }
        return null;
    }

    @Override public Void visitCall(Instruction.Call inst) {
        BytecodeHelper.debugPrintln(visitor, "Calling func f$" + inst.index());
        if (inst.index() < module.funcImports().size()) {
            // Calling an imported function
            Import.Func imported = module.funcImports().get(inst.index());
            FuncType funcType = module.types.get(imported.typeIndex);
            if (funcType.results.size() > 1) throw new UnsupportedOperationException("Multi-return functions");
            abstractStack.applyStackType(funcType.asStackType());
            // Invoke static
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, Compile.getClassName(imported.moduleName), imported.elementName, funcType.descriptor(), false);
        } else {
            int adjustedIndex = inst.index() - module.funcImports().size();
            FuncType funcType = module.types.get(module.functions.get(adjustedIndex));
            if (funcType.results.size() > 1) throw new UnsupportedOperationException("Multi-return functions");
            abstractStack.applyStackType(funcType.asStackType());
            // Invoke static
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, Compile.getClassName(moduleName), Compile.getFuncName(adjustedIndex), funcType.descriptor(), false);
        }
        return null;
    }

    @Override public Void visitCallIndirect(Instruction.CallIndirect inst) {
        // TODO: Implement,,, for now using a cursed hack workaround that doesn't actually do anything but lets my code run
        abstractStack.popExpecting(ValType.i32);
        abstractStack.applyStackType(module.types.get(inst.typeIndex()).asStackType());

        BytecodeHelper.throwRuntimeError(visitor, "Attempt to use call_indirect, which isn't implemented really");

        BytecodeHelper.popValue(visitor, ValType.i32);
        for (ValType t : module.types.get(inst.typeIndex()).args)
            BytecodeHelper.popValue(visitor, t);
        for (ValType t : module.types.get(inst.typeIndex()).results) {
            if (t == ValType.i32) BytecodeHelper.constInt(visitor, -1000);
            else if (t == ValType.i64) BytecodeHelper.constLong(visitor, -1000);
            else if (t == ValType.f32) BytecodeHelper.constFloat(visitor, -1000);
            else if (t == ValType.f64) BytecodeHelper.constDouble(visitor, -1000);
            else if (t == ValType.funcref || t == ValType.externref) visitor.visitInsn(Opcodes.ACONST_NULL);
            else throw new IllegalStateException();
        }
        return null;
    }

    // Just push null
    @Override public Void visitRefNull(Instruction.RefNull inst) {
        // Push a funcref or an externref depending
        abstractStack.push(new AbstractStackElement.ValueElement(inst.type()));
        visitor.visitInsn(Opcodes.ACONST_NULL);
        return null;
    }

    // Check if null, push 1 if yes, 0 if no
    @Override public Void visitRefIsNull(Instruction.RefIsNull inst) {
        // Can check nullness of a funcref OR an externref
        abstractStack.popExpecting(ValType.funcref, ValType.externref);
        abstractStack.push(new AbstractStackElement.ValueElement(ValType.i32)); // Push i32 as result
        BytecodeHelper.test(visitor, Opcodes.IFNULL);
        return null;
    }

    @Override public Void visitRefFunc(Instruction.RefFunc inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitDrop(Instruction.Drop inst) {
        AbstractStackElement topElem = abstractStack.pop(); // Modifies abstract stack
        if (!(topElem instanceof AbstractStackElement.ValueElement v))
            throw new IllegalStateException("Malformed WASM?");
        BytecodeHelper.popValue(visitor, v.valueType);
        return null;
    }

    @Override public Void visitSelect(Instruction.Select inst) {
        abstractStack.popExpecting(ValType.i32); // Expect i32 on top of stack
        ValType selectType = abstractStack.popTakeType(); // Expect 2 of the same type below it
        abstractStack.peekExpecting(selectType);
        // If top of stack is not 0, pop. If it is 0, swap-pop.
        Label isZero = new Label();
        Label end = new Label();
        visitor.visitJumpInsn(Opcodes.IFEQ, isZero);
        BytecodeHelper.popValue(visitor, selectType);
        visitor.visitJumpInsn(Opcodes.GOTO, end);
        visitor.visitLabel(isZero);
        BytecodeHelper.swapValues(visitor, selectType);
        BytecodeHelper.popValue(visitor, selectType);
        visitor.visitLabel(end);
        return null;
    }

    @Override public Void visitSelectFrom(Instruction.SelectFrom inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitLocalGet(Instruction.LocalGet inst) {
        ValType type = code.locals.get(inst.localIndex());
        abstractStack.push(new AbstractStackElement.ValueElement(type)); // Modifies abstract stack
        BytecodeHelper.loadLocal(visitor, code.localMappings().get(inst.localIndex()), type);
        return null;
    }

    @Override public Void visitLocalSet(Instruction.LocalSet inst) {
        ValType type = code.locals.get(inst.localIndex());
        abstractStack.pop(); // Modifies abstract stack
        BytecodeHelper.storeLocal(visitor, code.localMappings().get(inst.localIndex()), type);
        return null;
    }

    @Override public Void visitLocalTee(Instruction.LocalTee inst) {
        ValType type = code.locals.get(inst.localIndex());
        BytecodeHelper.dupValue(visitor, type); // Dup before storing
        BytecodeHelper.storeLocal(visitor, code.localMappings().get(inst.localIndex()), type);
        return null;
    }

    @Override public Void visitGlobalGet(Instruction.GlobalGet inst) {
        if (inst.globalIndex() < module.globalImports().size()) {
            throw new UnsupportedOperationException();
        } else {
            int adjustedIndex = inst.globalIndex() - module.globalImports().size();
            ValType globalType = module.globals.get(adjustedIndex).globalType().valType();
            abstractStack.push(new AbstractStackElement.ValueElement(globalType));
            visitor.visitFieldInsn(Opcodes.GETSTATIC, Compile.getClassName(moduleName), Compile.getGlobalName(adjustedIndex), globalType.desc());
        }
        return null;
    }

    @Override public Void visitGlobalSet(Instruction.GlobalSet inst) {
        if (inst.globalIndex() < module.globalImports().size()) {
            throw new UnsupportedOperationException();
        } else {
            int adjustedIndex = inst.globalIndex() - module.globalImports().size();
            ValType globalType = module.globals.get(adjustedIndex).globalType().valType();
            abstractStack.pop();
            visitor.visitFieldInsn(Opcodes.PUTSTATIC, Compile.getClassName(moduleName), Compile.getGlobalName(adjustedIndex), globalType.desc());
        }
        return null;
    }

    @Override public Void visitTableGet(Instruction.TableGet inst) {
        BytecodeHelper.debugPrintln(visitor, "Table get");
        if (inst.tableIndex() < module.tableImports().size()) {
            throw new UnsupportedOperationException();
        } else {
            int adjustedIndex = inst.tableIndex() - module.tableImports().size();
            abstractStack.popExpecting(ValType.i32);
            abstractStack.push(new AbstractStackElement.ValueElement(module.tables.get(adjustedIndex).elementType()));

            visitor.visitFieldInsn(Opcodes.GETSTATIC, Compile.getClassName(moduleName), Compile.getTableName(adjustedIndex), Compile.TABLE_DESCRIPTOR);
            visitor.visitInsn(Opcodes.SWAP);
            visitor.visitInsn(Opcodes.AALOAD);
        }
        return null;
    }

    @Override public Void visitTableSet(Instruction.TableSet inst) {
        BytecodeHelper.debugPrintln(visitor, "Table set");
        if (inst.tableIndex() < module.tableImports().size()) {
            throw new UnsupportedOperationException();
        } else {
            int adjustedIndex = inst.tableIndex() - module.tableImports().size();
            abstractStack.popExpecting(module.tables.get(adjustedIndex).elementType());
            abstractStack.popExpecting(ValType.i32);
            visitor.visitFieldInsn(Opcodes.GETSTATIC, Compile.getClassName(moduleName), Compile.getTableName(adjustedIndex), Compile.TABLE_DESCRIPTOR);
            visitor.visitInsn(Opcodes.DUP_X2);
            visitor.visitInsn(Opcodes.POP);
            visitor.visitInsn(Opcodes.AASTORE);
        }
        return null;
    }

    @Override public Void visitTableInit(Instruction.TableInit inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitElemDrop(Instruction.ElemDrop inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitTableGrow(Instruction.TableGrow inst) {
        abstractStack.popExpecting(ValType.i32);
        abstractStack.popExpecting(ValType.funcref, ValType.externref);
        abstractStack.push(new AbstractStackElement.ValueElement(ValType.i32));
        // Stack = [fillValue, requestedEntries]
        visitor.visitFieldInsn(Opcodes.GETSTATIC, Compile.getClassName(moduleName), Compile.getTableName(inst.tableIndex()), Compile.TABLE_DESCRIPTOR); // [fillValue, requestedEntries, table]
        visitor.visitInsn(Opcodes.DUP); // [fillValue, requestedEntries, table, table]
        visitor.visitInsn(Opcodes.ARRAYLENGTH); // [fillValue, requestedEntries, table, table.length]
        BytecodeHelper.storeLocal(visitor, code.nextLocalSlot(), ValType.i32); // [fillValue, requestedEntries, table]. Locals = [table.length]
        visitor.visitInsn(Opcodes.SWAP); // [fillValue, table, requestedEntries]
        BytecodeHelper.loadLocal(visitor, code.nextLocalSlot(), ValType.i32); // [fillValue, table, requestedEntries, table.length]
        visitor.visitInsn(Opcodes.IADD); // [fillValue, table, newTableLength]
        visitor.visitInsn(Opcodes.DUP); // [fillValue, table, newTableLength, newTableLength]
        BytecodeHelper.storeLocal(visitor, code.nextLocalSlot() + 1, ValType.i32); // [fillValue, table, newTableLength], locals = [table.length, newTableLength]
        //TODO: Check for overflow, or mem usage too high, and output -1
        visitor.visitTypeInsn(Opcodes.ANEWARRAY, Compile.TABLE_DESCRIPTOR); // [fillValue, table, newTable]
        BytecodeHelper.constInt(visitor, 0); // [fillValue, table, newTable, 0]
        visitor.visitInsn(Opcodes.SWAP); // [fillValue, table, 0, newTable]
        visitor.visitInsn(Opcodes.DUP_X2); // [fillValue, newTable, table, 0, newTable]
        BytecodeHelper.constInt(visitor, 0); // [fillValue, newTable, table, 0, newTable, 0]
        BytecodeHelper.loadLocal(visitor, code.nextLocalSlot(), ValType.i32); // [fillValue, newTable, table, 0, newTable, 0, table.length]
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(System.class), "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V", false); // [fillValue, newTable]
        visitor.visitInsn(Opcodes.DUP_X1); // [newTable, fillValue, newTable]
        visitor.visitFieldInsn(Opcodes.PUTSTATIC, Compile.getClassName(moduleName), Compile.getTableName(inst.tableIndex()), Compile.TABLE_DESCRIPTOR); // [newTable, fillValue]
        BytecodeHelper.loadLocal(visitor, code.nextLocalSlot(), ValType.i32); // [newTable, fillValue, table.length]
        visitor.visitInsn(Opcodes.SWAP); // [newTable, table.length, fillValue]
        BytecodeHelper.loadLocal(visitor, code.nextLocalSlot() + 1, ValType.i32); // [newTable, table.length, fillValue, newTableLength]
        visitor.visitInsn(Opcodes.SWAP); // [newTable, table.length, newTableLength, fillValue]
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(Arrays.class), "fill", "(Ljava/lang/Object;IILjava/lang/Object;)V", false); // []
        BytecodeHelper.loadLocal(visitor, code.nextLocalSlot(), ValType.i32); // [table.length]
        return null;
    }

    @Override public Void visitTableSize(Instruction.TableSize inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitFieldInsn(Opcodes.GETSTATIC, Compile.getClassName(moduleName), Compile.getTableName(inst.tableIndex()), Compile.TABLE_DESCRIPTOR);
        visitor.visitInsn(Opcodes.ARRAYLENGTH);
        return null;
    }

    @Override public Void visitTableCopy(Instruction.TableCopy inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitTableFill(Instruction.TableFill inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    // Will need to change memIndex in the future if multiple memories are implemented
    private void loadPrimitive(String typeDesc, int offset, int memIndex) {
        // If the desc is "B" for byte, then do different code
        String className = Compile.getClassName(moduleName);
        if (typeDesc.equals("B")) {
            // Just indexing version
            // [index], locals = []
            BytecodeHelper.constInt(visitor, offset); // [index, offset]
            visitor.visitInsn(Opcodes.IADD); // [index + offset]
            visitor.visitFieldInsn(Opcodes.GETSTATIC, className, Compile.getMemoryName(memIndex), Type.getDescriptor(byte[].class)); // [index + offset, memArray]
            visitor.visitInsn(Opcodes.SWAP); // [memArray, index + offset]
            visitor.visitInsn(Opcodes.BALOAD); // [memArray[index + offset]]
        } else {
            // VarHandle version
            // [index], locals = []
            BytecodeHelper.constInt(visitor, offset); // [index, offset], locals = []
            visitor.visitInsn(Opcodes.IADD); // [index + offset], locals = []
            BytecodeHelper.storeLocal(visitor, code.nextLocalSlot(), ValType.i32); // [], locals = [index + offset]
            visitor.visitFieldInsn(Opcodes.GETSTATIC, className, Compile.getMemoryVarHandleName(typeDesc), Type.getDescriptor(VarHandle.class)); // [VarHandle], locals = [index + offset]
            visitor.visitFieldInsn(Opcodes.GETSTATIC, className, Compile.getMemoryName(memIndex), Type.getDescriptor(byte[].class)); // [VarHandle, memArray], locals = [index + offset]
            BytecodeHelper.loadLocal(visitor, code.nextLocalSlot(), ValType.i32); // [VarHandle, memArray, index + offset]
            visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(VarHandle.class), "get", "([BI)" + typeDesc, false); // memArray[index + offset] as <type>
        }
    }
    private void loadPrimitive(String typeDesc, int offset) { loadPrimitive(typeDesc, offset, 0); }

    @Override public Void visitI32Load(Instruction.I32Load inst) {
        abstractStack.applyStackType(inst.stackType());
        loadPrimitive(ValType.i32.desc(), inst.offset());
        return null;
    }
    @Override public Void visitI64Load(Instruction.I64Load inst) {
        abstractStack.applyStackType(inst.stackType());
        loadPrimitive(ValType.i64.desc(), inst.offset());
        return null;
    }
    @Override public Void visitF32Load(Instruction.F32Load inst) {
        abstractStack.applyStackType(inst.stackType());
        loadPrimitive(ValType.f32.desc(), inst.offset());
        return null;
    }
    @Override public Void visitF64Load(Instruction.F64Load inst) {
        abstractStack.applyStackType(inst.stackType());
        loadPrimitive(ValType.f64.desc(), inst.offset());
        return null;
    }
    @Override public Void visitI32Load8S(Instruction.I32Load8S inst) {
        abstractStack.applyStackType(inst.stackType());
        loadPrimitive("B", inst.offset());
        return null;
    }
    @Override public Void visitI32Load8U(Instruction.I32Load8U inst) {
        abstractStack.applyStackType(inst.stackType());
        loadPrimitive("B", inst.offset());
        BytecodeHelper.constInt(visitor, 0xFF); // Mask
        visitor.visitInsn(Opcodes.IAND);
        return null;
    }
    @Override public Void visitI32Load16S(Instruction.I32Load16S inst) {
        abstractStack.applyStackType(inst.stackType());
        loadPrimitive("S", inst.offset());
        return null;
    }
    @Override public Void visitI32Load16U(Instruction.I32Load16U inst) {
        abstractStack.applyStackType(inst.stackType());
        loadPrimitive("S", inst.offset());
        BytecodeHelper.constInt(visitor, 0xFFFF); // Mask
        visitor.visitInsn(Opcodes.IAND);
        return null;
    }
    @Override public Void visitI64Load8S(Instruction.I64Load8S inst) {
        abstractStack.applyStackType(inst.stackType());
        loadPrimitive("B", inst.offset());
        visitor.visitInsn(Opcodes.I2L);
        return null;
    }
    @Override public Void visitI64Load8U(Instruction.I64Load8U inst) {
        abstractStack.applyStackType(inst.stackType());
        loadPrimitive("B", inst.offset());
        BytecodeHelper.constInt(visitor, 0xFF);
        visitor.visitInsn(Opcodes.IAND); // Mask
        visitor.visitInsn(Opcodes.I2L);
        return null;
    }
    @Override public Void visitI64Load16S(Instruction.I64Load16S inst) {
        abstractStack.applyStackType(inst.stackType());
        loadPrimitive("S", inst.offset());
        visitor.visitInsn(Opcodes.I2L);
        return null;
    }
    @Override public Void visitI64Load16U(Instruction.I64Load16U inst) {
        abstractStack.applyStackType(inst.stackType());
        loadPrimitive("S", inst.offset());
        BytecodeHelper.constInt(visitor, 0xFFFF);
        visitor.visitInsn(Opcodes.IAND); // Mask
        visitor.visitInsn(Opcodes.I2L);
        return null;
    }
    @Override public Void visitI64Load32S(Instruction.I64Load32S inst) {
        abstractStack.applyStackType(inst.stackType());
        loadPrimitive(ValType.i32.desc(), inst.offset());
        visitor.visitInsn(Opcodes.I2L);
        return null;
    }
    @Override public Void visitI64Load32U(Instruction.I64Load32U inst) {
        abstractStack.applyStackType(inst.stackType());
        loadPrimitive(ValType.i32.desc(), inst.offset());
        visitor.visitInsn(Opcodes.I2L);
        BytecodeHelper.constLong(visitor, 0xFFFFFFFFL);
        visitor.visitInsn(Opcodes.LAND);
        return null;
    }

    // Will need to change memIndex in the future if multiple memories are implemented
    // "wasmType" is the corresponding type in wasm for this type.
    // For example if you want to use a java byte, choose wasmType = ValType.i32.
    private void storePrimitive(String typeDesc, ValType wasmType, int offset, int memIndex) {
        String className = Compile.getClassName(moduleName);
        if (typeDesc.equals("B")) {
            // Raw byte array indexing version
            // [index, value]
            visitor.visitInsn(Opcodes.SWAP); // [value, index]
            BytecodeHelper.constInt(visitor, offset); // [value, index, offset]
            visitor.visitInsn(Opcodes.IADD); // [value, index + offset]
            visitor.visitInsn(Opcodes.SWAP); // [index + offset, value]
            visitor.visitFieldInsn(Opcodes.GETSTATIC, className, Compile.getMemoryName(memIndex), Type.getDescriptor(byte[].class)); // [index + offset, value, memArray]
            visitor.visitInsn(Opcodes.DUP_X2); // [memArray, index + offset, value, memArray]
            visitor.visitInsn(Opcodes.POP); // [memArray, index + offset, value]
            visitor.visitInsn(Opcodes.BASTORE); // []. memArray[index + offset] is now value.
        } else {
            // VarHandle version
            // [index, value], locals = []
            BytecodeHelper.storeLocal(visitor, code.nextLocalSlot(), wasmType); // [index], locals = [value]
            BytecodeHelper.constInt(visitor, offset); // [index, offset], locals = [value]
            visitor.visitInsn(Opcodes.IADD); // [index + offset], locals = [value]
            BytecodeHelper.storeLocal(visitor, code.nextLocalSlot() + wasmType.stackSlots(), ValType.i32); // [], locals = [value, index + offset]
            visitor.visitFieldInsn(Opcodes.GETSTATIC, className, Compile.getMemoryVarHandleName(typeDesc), Type.getDescriptor(VarHandle.class)); // [VarHandle], locals = [value, index + offset]
            visitor.visitFieldInsn(Opcodes.GETSTATIC, className, Compile.getMemoryName(memIndex), Type.getDescriptor(byte[].class)); // [VarHandle, memArray], locals = [value, index + offset]
            BytecodeHelper.loadLocal(visitor, code.nextLocalSlot() + wasmType.stackSlots(), ValType.i32); // [VarHandle, memArray, index + offset], locals = [value]
            BytecodeHelper.loadLocal(visitor, code.nextLocalSlot(), wasmType); // [VarHandle, memArray, index + offset, value], locals = []
            visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(VarHandle.class), "set", "([BI" + typeDesc + ")V", false); // Stack = []. memArray[index + offset] is now value.
        }
    }
    private void storePrimitive(String typeDesc, ValType wasmType, int offset) { storePrimitive(typeDesc, wasmType, offset, 0); }

    @Override public Void visitI32Store(Instruction.I32Store inst) {
        abstractStack.applyStackType(inst.stackType());
        storePrimitive(ValType.i32.desc(), ValType.i32, inst.offset());
        return null;
    }
    @Override public Void visitI64Store(Instruction.I64Store inst) {
        abstractStack.applyStackType(inst.stackType());
        storePrimitive(ValType.i64.desc(), ValType.i64, inst.offset());
        return null;
    }
    @Override public Void visitF32Store(Instruction.F32Store inst) {
        abstractStack.applyStackType(inst.stackType());
        storePrimitive(ValType.f32.desc(), ValType.f32, inst.offset());
        return null;
    }
    @Override public Void visitF64Store(Instruction.F64Store inst) {
        abstractStack.applyStackType(inst.stackType());
        storePrimitive(ValType.f64.desc(), ValType.f64, inst.offset());
        return null;
    }
    @Override public Void visitI32Store8(Instruction.I32Store8 inst) {
        abstractStack.applyStackType(inst.stackType());
        BytecodeHelper.constInt(visitor, 0xFF);
        visitor.visitInsn(Opcodes.IAND);
        storePrimitive("B", ValType.i32, inst.offset());
        return null;
    }
    @Override public Void visitI32Store16(Instruction.I32Store16 inst) {
        abstractStack.applyStackType(inst.stackType());
        BytecodeHelper.constInt(visitor, 0xFFFF);
        visitor.visitInsn(Opcodes.IAND);
        storePrimitive("S", ValType.i32, inst.offset());
        return null;
    }
    @Override public Void visitI64Store8(Instruction.I64Store8 inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.L2I);
        BytecodeHelper.constInt(visitor, 0xFF);
        visitor.visitInsn(Opcodes.IAND);
        storePrimitive("B", ValType.i32, inst.offset());
        return null;
    }
    @Override public Void visitI64Store16(Instruction.I64Store16 inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.L2I);
        BytecodeHelper.constInt(visitor, 0xFFFF);
        visitor.visitInsn(Opcodes.IAND);
        storePrimitive("S", ValType.i32, inst.offset());
        return null;
    }
    @Override public Void visitI64Store32(Instruction.I64Store32 inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.L2I);
        storePrimitive(ValType.i32.desc(), ValType.i32, inst.offset());
        return null;
    }

    @Override public Void visitMemorySize(Instruction.MemorySize inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitFieldInsn(Opcodes.GETSTATIC, Compile.getClassName(moduleName), Compile.getMemoryName(0), "[B");
        visitor.visitInsn(Opcodes.ARRAYLENGTH);
        BytecodeHelper.constInt(visitor, Compile.WASM_PAGE_SIZE);
        visitor.visitInsn(Opcodes.IDIV);
        return null;
    }
    @Override public Void visitMemoryGrow(Instruction.MemoryGrow inst) {
        BytecodeHelper.debugPrintln(visitor, "memory.grow was called");
        abstractStack.applyStackType(inst.stackType());
        // Stack = [requestedPages]
        BytecodeHelper.constInt(visitor, Compile.WASM_PAGE_SIZE); // [requestedPages, pageSize]
        visitor.visitInsn(Opcodes.IMUL); // [requestedPages * pageSize]
        visitor.visitFieldInsn(Opcodes.GETSTATIC, Compile.getClassName(moduleName), Compile.getMemoryName(0), "[B"); // [requestedPages * pageSize, memArray]
        visitor.visitInsn(Opcodes.DUP); // [requestedPages * pageSize, memArray, memArray]
        visitor.visitInsn(Opcodes.ARRAYLENGTH); // [requestedPages * pageSize, memArray, memArray.length]
        BytecodeHelper.storeLocal(visitor, code.nextLocalSlot(), ValType.i32); // [requestedPages * pageSize, memArray]. Locals = [memArray.length]
        visitor.visitInsn(Opcodes.SWAP); // [memArray, requestedPages * pageSize]
        BytecodeHelper.loadLocal(visitor, code.nextLocalSlot(), ValType.i32); // [memArray, requestedPages * pageSize, memArray.length]
        visitor.visitInsn(Opcodes.IADD); // [memArray, requestedPages * pageSize + memArray.length]
        //TODO: Check for overflow, or mem usage too high, and output -1
        visitor.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_BYTE); // [memArray, newArray]
        BytecodeHelper.constInt(visitor, 0); // [memArray, newArray, 0]
        visitor.visitInsn(Opcodes.SWAP); // [memArray, 0, newArray]
        visitor.visitInsn(Opcodes.DUP_X2); // [newArray, memArray, 0, newArray]
        BytecodeHelper.constInt(visitor, 0); // [newArray, memArray, 0, newArray, 0]
        BytecodeHelper.loadLocal(visitor, code.nextLocalSlot(), ValType.i32); // [newArray, memArray, 0, newArray, 0, memArray.length]
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(System.class), "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V", false); // [newArray]
        visitor.visitFieldInsn(Opcodes.PUTSTATIC, Compile.getClassName(moduleName), Compile.getMemoryName(0), "[B"); // []
        BytecodeHelper.loadLocal(visitor, code.nextLocalSlot(), ValType.i32); // [memArray.length]
        BytecodeHelper.constInt(visitor, Compile.WASM_PAGE_SIZE); // [memArray.length, PAGE_SIZE]
        visitor.visitInsn(Opcodes.IDIV); // [memArray.length / PAGE_SIZE]
        return null;
    }
    @Override public Void visitMemoryInit(Instruction.MemoryInit inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
    @Override public Void visitDataDrop(Instruction.DataDrop inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
    @Override public Void visitMemoryCopy(Instruction.MemoryCopy inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
    @Override public Void visitMemoryFill(Instruction.MemoryFill inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI32Const(Instruction.I32Const inst) {
        abstractStack.applyStackType(inst.stackType());
        BytecodeHelper.constInt(visitor, inst.n());
        BytecodeHelper.debugPrint(visitor, "Const int: ");
        BytecodeHelper.debugPrintInt(visitor);
        return null;
    }
    @Override public Void visitI64Const(Instruction.I64Const inst) {
        abstractStack.applyStackType(inst.stackType());
        BytecodeHelper.constLong(visitor, inst.n());
        return null;
    }
    @Override public Void visitF32Const(Instruction.F32Const inst) {
        abstractStack.applyStackType(inst.stackType());
        BytecodeHelper.constFloat(visitor, inst.z());
        return null;
    }
    @Override public Void visitF64Const(Instruction.F64Const inst) {
        abstractStack.applyStackType(inst.stackType());
        BytecodeHelper.constDouble(visitor, inst.z());
        return null;
    }

    @Override public Void visitI32Eqz(Instruction.I32Eqz inst) {
        abstractStack.applyStackType(inst.stackType());
        BytecodeHelper.test(visitor, Opcodes.IFEQ);
        return null;
    }
    @Override public Void visitI32Eq(Instruction.I32Eq inst) {
        abstractStack.applyStackType(inst.stackType());
        BytecodeHelper.test(visitor, Opcodes.IF_ICMPEQ);
        return null;
    }
    @Override public Void visitI32Ne(Instruction.I32Ne inst) {
        abstractStack.applyStackType(inst.stackType());
        BytecodeHelper.test(visitor, Opcodes.IF_ICMPNE);
        return null;
    }
    @Override public Void visitI32LtS(Instruction.I32LtS inst) {
        abstractStack.applyStackType(inst.stackType());
        BytecodeHelper.test(visitor, Opcodes.IF_ICMPLT);
        return null;
    }
    @Override public Void visitI32LtU(Instruction.I32LtU inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "compareUnsigned", "(II)I", false);
        BytecodeHelper.test(visitor, Opcodes.IFLT);
        return null;
    }
    @Override public Void visitI32GtS(Instruction.I32GtS inst) {
        abstractStack.applyStackType(inst.stackType());
        BytecodeHelper.test(visitor, Opcodes.IF_ICMPGT);
        return null;
    }
    @Override public Void visitI32GtU(Instruction.I32GtU inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "compareUnsigned", "(II)I", false);
        BytecodeHelper.test(visitor, Opcodes.IFGT);
        return null;
    }
    @Override public Void visitI32LeS(Instruction.I32LeS inst) {
        abstractStack.applyStackType(inst.stackType());
        BytecodeHelper.test(visitor, Opcodes.IF_ICMPLE);
        return null;
    }
    @Override public Void visitI32LeU(Instruction.I32LeU inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "compareUnsigned", "(II)I", false);
        BytecodeHelper.test(visitor, Opcodes.IFLE);
        return null;
    }
    @Override public Void visitI32GeS(Instruction.I32GeS inst) {
        abstractStack.applyStackType(inst.stackType());
        BytecodeHelper.test(visitor, Opcodes.IF_ICMPGE);
        return null;
    }
    @Override public Void visitI32GeU(Instruction.I32GeU inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "compareUnsigned", "(II)I", false);
        BytecodeHelper.test(visitor, Opcodes.IFGE);
        return null;
    }

    @Override public Void visitI64Eqz(Instruction.I64Eqz inst) {
        abstractStack.applyStackType(inst.stackType());
        BytecodeHelper.constLong(visitor, 0);
        visitor.visitInsn(Opcodes.LCMP);
        BytecodeHelper.test(visitor, Opcodes.IFEQ);
        return null;
    }
    @Override public Void visitI64Eq(Instruction.I64Eq inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.LCMP);
        BytecodeHelper.test(visitor, Opcodes.IFEQ);
        return null;
    }
    @Override public Void visitI64Ne(Instruction.I64Ne inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.LCMP);
        BytecodeHelper.test(visitor, Opcodes.IFNE);
        return null;
    }
    @Override public Void visitI64LtS(Instruction.I64LtS inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.LCMP);
        BytecodeHelper.test(visitor, Opcodes.IFLT);
        return null;
    }
    @Override public Void visitI64LtU(Instruction.I64LtU inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "compareUnsigned", "(JJ)I", false);
        BytecodeHelper.test(visitor, Opcodes.IFLT);
        return null;
    }
    @Override public Void visitI64GtS(Instruction.I64GtS inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.LCMP);
        BytecodeHelper.test(visitor, Opcodes.IFGT);
        return null;
    }
    @Override public Void visitI64GtU(Instruction.I64GtU inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "compareUnsigned", "(JJ)I", false);
        BytecodeHelper.test(visitor, Opcodes.IFGT);
        return null;
    }
    @Override public Void visitI64LeS(Instruction.I64LeS inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.LCMP);
        BytecodeHelper.test(visitor, Opcodes.IFLE);
        return null;
    }
    @Override public Void visitI64LeU(Instruction.I64LeU inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "compareUnsigned", "(JJ)I", false);
        BytecodeHelper.test(visitor, Opcodes.IFLE);
        return null;
    }
    @Override public Void visitI64GeS(Instruction.I64GeS inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.LCMP);
        BytecodeHelper.test(visitor, Opcodes.IFGE);
        return null;
    }
    @Override public Void visitI64GeU(Instruction.I64GeU inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "compareUnsigned", "(JJ)I", false);
        BytecodeHelper.test(visitor, Opcodes.IFGE);
        return null;
    }

    @Override public Void visitF32Eq(Instruction.F32Eq inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.FCMPG);
        BytecodeHelper.test(visitor, Opcodes.IFEQ);
        return null;
    }
    @Override public Void visitF32Ne(Instruction.F32Ne inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.FCMPG);
        BytecodeHelper.test(visitor, Opcodes.IFNE);
        return null;
    }
    @Override public Void visitF32Lt(Instruction.F32Lt inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.FCMPG);
        BytecodeHelper.test(visitor, Opcodes.IFLT);
        return null;
    }
    @Override public Void visitF32Gt(Instruction.F32Gt inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.FCMPL);
        BytecodeHelper.test(visitor, Opcodes.IFGT);
        return null;
    }
    @Override public Void visitF32Le(Instruction.F32Le inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.FCMPG);
        BytecodeHelper.test(visitor, Opcodes.IFLE);
        return null;
    }
    @Override public Void visitF32Ge(Instruction.F32Ge inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.FCMPL);
        BytecodeHelper.test(visitor, Opcodes.IFGE);
        return null;
    }

    @Override public Void visitF64Eq(Instruction.F64Eq inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.DCMPG);
        BytecodeHelper.test(visitor, Opcodes.IFEQ);
        return null;
    }
    @Override public Void visitF64Ne(Instruction.F64Ne inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.DCMPG);
        BytecodeHelper.test(visitor, Opcodes.IFNE);
        return null;
    }
    @Override public Void visitF64Lt(Instruction.F64Lt inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.DCMPG);
        BytecodeHelper.test(visitor, Opcodes.IFLT);
        return null;
    }
    @Override public Void visitF64Gt(Instruction.F64Gt inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.DCMPL);
        BytecodeHelper.test(visitor, Opcodes.IFGT);
        return null;
    }
    @Override public Void visitF64Le(Instruction.F64Le inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.DCMPG);
        BytecodeHelper.test(visitor, Opcodes.IFLE);
        return null;
    }
    @Override public Void visitF64Ge(Instruction.F64Ge inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.DCMPL);
        BytecodeHelper.test(visitor, Opcodes.IFGE);
        return null;
    }

    @Override public Void visitI32Clz(Instruction.I32Clz inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "numberOfLeadingZeros", "(I)I", false);
        return null;
    }
    @Override public Void visitI32Ctz(Instruction.I32Ctz inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "numberOfTrailingZeros", "(I)I", false);
        return null;
    }
    @Override public Void visitI32PopCnt(Instruction.I32PopCnt inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "bitCount", "(I)I", false);
        return null;
    }
    @Override public Void visitI32Add(Instruction.I32Add inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.IADD);
        return null;
    }
    @Override public Void visitI32Sub(Instruction.I32Sub inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.ISUB);
        return null;
    }
    @Override public Void visitI32Mul(Instruction.I32Mul inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.IMUL);
        return null;
    }
    @Override public Void visitI32DivS(Instruction.I32DivS inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.IDIV);
        return null;
    }
    @Override public Void visitI32DivU(Instruction.I32DivU inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "divideUnsigned", "(II)I", false);
        return null;
    }
    @Override public Void visitI32RemS(Instruction.I32RemS inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.IREM);
        return null;
    }
    @Override public Void visitI32RemU(Instruction.I32RemU inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "remainderUnsigned", "(II)I", false);
        return null;
    }
    @Override public Void visitI32And(Instruction.I32And inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.IAND);
        return null;
    }
    @Override public Void visitI32Or(Instruction.I32Or inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.IOR);
        return null;
    }
    @Override public Void visitI32Xor(Instruction.I32Xor inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.IXOR);
        return null;
    }
    @Override public Void visitI32Shl(Instruction.I32Shl inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.ISHL);
        return null;
    }
    @Override public Void visitI32ShrS(Instruction.I32ShrS inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.ISHR);
        return null;
    }
    @Override public Void visitI32ShrU(Instruction.I32ShrU inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.IUSHR);
        return null;
    }
    @Override public Void visitI32Rotl(Instruction.I32Rotl inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "rotateLeft", "(II)I", false);
        return null;
    }
    @Override public Void visitI32Rotr(Instruction.I32Rotr inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "rotateRight", "(II)I", false);
        return null;
    }

    @Override public Void visitI64Clz(Instruction.I64Clz inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "numberOfLeadingZeros", "(J)I", false);
        visitor.visitInsn(Opcodes.I2L);
        return null;
    }
    @Override public Void visitI64Ctz(Instruction.I64Ctz inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "numberOfTrailingZeros", "(J)I", false);
        visitor.visitInsn(Opcodes.I2L);
        return null;
    }
    @Override public Void visitI64PopCnt(Instruction.I64PopCnt inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "bitCount", "(J)I", false);
        visitor.visitInsn(Opcodes.I2L);
        return null;
    }
    @Override public Void visitI64Add(Instruction.I64Add inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.LADD);
        return null;
    }
    @Override public Void visitI64Sub(Instruction.I64Sub inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.LSUB);
        return null;
    }
    @Override public Void visitI64Mul(Instruction.I64Mul inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.LMUL);
        return null;
    }
    @Override public Void visitI64DivS(Instruction.I64DivS inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.LDIV);
        return null;
    }
    @Override public Void visitI64DivU(Instruction.I64DivU inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "divideUnsigned", "(JJ)J", false);
        return null;
    }
    @Override public Void visitI64RemS(Instruction.I64RemS inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.LREM);
        return null;
    }
    @Override public Void visitI64RemU(Instruction.I64RemU inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "remainderUnsigned", "(JJ)J", false);
        return null;
    }
    @Override public Void visitI64And(Instruction.I64And inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.LAND);
        return null;
    }
    @Override public Void visitI64Or(Instruction.I64Or inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.LOR);
        return null;
    }
    @Override public Void visitI64Xor(Instruction.I64Xor inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.LXOR);
        return null;
    }
    @Override public Void visitI64Shl(Instruction.I64Shl inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.LSHL);
        return null;
    }
    @Override public Void visitI64ShrS(Instruction.I64ShrS inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.LSHR);
        return null;
    }
    @Override public Void visitI64ShrU(Instruction.I64ShrU inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.LUSHR);
        return null;
    }
    @Override public Void visitI64Rotl(Instruction.I64Rotl inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.L2I);
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "rotateLeft", "(JI)J", false);
        return null;
    }
    @Override public Void visitI64Rotr(Instruction.I64Rotr inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.L2I);
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "rotateRight", "(JI)J", false);
        return null;
    }

    @Override public Void visitF32Abs(Instruction.F32Abs inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "abs", "(F)F", false);
        return null;
    }
    @Override public Void visitF32Neg(Instruction.F32Neg inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.FNEG);
        return null;
    }
    @Override public Void visitF32Ceil(Instruction.F32Ceil inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.F2D);
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "ceil", "(D)D", false);
        visitor.visitInsn(Opcodes.D2F);
        return null;
    }
    @Override public Void visitF32Floor(Instruction.F32Floor inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.F2D);
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "floor", "(D)D", false);
        visitor.visitInsn(Opcodes.D2F);
        return null;
    }
    @Override public Void visitF32Trunc(Instruction.F32Trunc inst) {
        // Need to handle NaN correctly, can't just cast to int/long then back to float
        abstractStack.applyStackType(inst.stackType());
        Label positive = new Label();
        Label end = new Label();
        visitor.visitInsn(Opcodes.F2D);
        visitor.visitInsn(Opcodes.DUP2);
        visitor.visitInsn(Opcodes.DCMPG);
        visitor.visitJumpInsn(Opcodes.IFGT, positive);
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "ceil", "(D)D", false);
        visitor.visitJumpInsn(Opcodes.GOTO, end);
        visitor.visitLabel(positive);
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "floor", "(D)D", false);
        visitor.visitLabel(end);
        visitor.visitInsn(Opcodes.D2F);
        return null;
    }
    @Override public Void visitF32Nearest(Instruction.F32Nearest inst) {
        // It's supposed to round to even instead of rounding up but I dont care leave me alone
        abstractStack.applyStackType(inst.stackType());
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "round", "(F)F", false);
        return null;
    }
    @Override public Void visitF32Sqrt(Instruction.F32Sqrt inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.F2D);
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "sqrt", "(D)D", false);
        visitor.visitInsn(Opcodes.D2F);
        return null;
    }
    @Override public Void visitF32Add(Instruction.F32Add inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.FADD);
        return null;
    }
    @Override public Void visitF32Sub(Instruction.F32Sub inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.FSUB);
        return null;
    }
    @Override public Void visitF32Mul(Instruction.F32Mul inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.FMUL);
        return null;
    }
    @Override public Void visitF32Div(Instruction.F32Div inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.FDIV);
        return null;
    }
    @Override public Void visitF32Min(Instruction.F32Min inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "min", "(FF)F", false);
        return null;
    }
    @Override public Void visitF32Max(Instruction.F32Max inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "max", "(FF)F", false);
        return null;
    }
    @Override public Void visitF32Copysign(Instruction.F32Copysign inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "copySign", "(FF)F", false);
        return null;
    }

    @Override public Void visitF64Abs(Instruction.F64Abs inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "abs", "(D)D", false);
        return null;
    }
    @Override public Void visitF64Neg(Instruction.F64Neg inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.DNEG);
        return null;
    }
    @Override public Void visitF64Ceil(Instruction.F64Ceil inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "ceil", "(D)D", false);
        return null;
    }
    @Override public Void visitF64Floor(Instruction.F64Floor inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "floor", "(D)D", false);
        return null;
    }
    @Override public Void visitF64Trunc(Instruction.F64Trunc inst) {
        // Need to handle NaN correctly, can't just cast to int/long then back to float
        abstractStack.applyStackType(inst.stackType());
        Label positive = new Label();
        Label end = new Label();
        visitor.visitInsn(Opcodes.DUP2);
        visitor.visitInsn(Opcodes.DCMPG);
        visitor.visitJumpInsn(Opcodes.IFGT, positive);
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "ceil", "(D)D", false);
        visitor.visitJumpInsn(Opcodes.GOTO, end);
        visitor.visitLabel(positive);
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "floor", "(D)D", false);
        visitor.visitLabel(end);
        return null;
    }
    @Override public Void visitF64Nearest(Instruction.F64Nearest inst) {
        // It's supposed to round to even instead of rounding up but I dont care leave me alone
        abstractStack.applyStackType(inst.stackType());
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "round", "(D)D", false);
        return null;
    }
    @Override public Void visitF64Sqrt(Instruction.F64Sqrt inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "sqrt", "(D)D", false);
        return null;
    }
    @Override public Void visitF64Add(Instruction.F64Add inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.DADD);
        return null;
    }
    @Override public Void visitF64Sub(Instruction.F64Sub inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.DSUB);
        return null;
    }
    @Override public Void visitF64Mul(Instruction.F64Mul inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.DMUL);
        return null;
    }
    @Override public Void visitF64Div(Instruction.F64Div inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.DDIV);
        return null;
    }
    @Override public Void visitF64Min(Instruction.F64Min inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "min", "(DD)D", false);
        return null;
    }
    @Override public Void visitF64Max(Instruction.F64Max inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "max", "(DD)D", false);
        return null;
    }
    @Override public Void visitF64Copysign(Instruction.F64Copysign inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "copySign", "(DD)D", false);
        return null;
    }

    @Override public Void visitI32WrapI64(Instruction.I32WrapI64 inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.L2I);
        return null;
    }
    @Override public Void visitI32TruncF32S(Instruction.I32TruncF32S inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.F2I);
        return null;
    }
    @Override public Void visitI32TruncF32U(Instruction.I32TruncF32U inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.F2L);
        visitor.visitInsn(Opcodes.F2I);
        return null;
    }
    @Override public Void visitI32TruncF64S(Instruction.I32TruncF64S inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.D2I);
        return null;
    }
    @Override public Void visitI32TruncF64U(Instruction.I32TruncF64U inst) {
        abstractStack.applyStackType(inst.stackType());
        //TODO: Might not be correct? Unsure
        visitor.visitInsn(Opcodes.D2L);
        visitor.visitInsn(Opcodes.D2I);
        return null;
    }

    @Override public Void visitI64ExtendI32S(Instruction.I64ExtendI32S inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.I2L);
        return null;
    }
    @Override public Void visitI64ExtendI32U(Instruction.I64ExtendI32U inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.I2L);
        BytecodeHelper.constLong(visitor, 0xFFFFFFFFL);
        visitor.visitInsn(Opcodes.LAND);
        return null;
    }
    @Override public Void visitI64TruncF32S(Instruction.I64TruncF32S inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.F2L);
        return null;
    }
    @Override public Void visitI64TruncF32U(Instruction.I64TruncF32U inst) {
        abstractStack.applyStackType(inst.stackType());
        // Plan for converting float or double -> ulong:
        // long fromDouble(double v) {
        //   if (v <= 0) return 0;
        //   long oddBit = (long) (v % 2)
        //   return (((long) (v / 2)) << 1) | oddBit;
        // }
        //[v]
        visitor.visitInsn(Opcodes.DUP); // [v, v]
        visitor.visitInsn(Opcodes.FCONST_0); // [v, v, 0]
        visitor.visitInsn(Opcodes.FCMPL); // [v, (v <= 0 or v is NaN) ? (-1 or 0) : 1]
        Label continuing = new Label();
        visitor.visitJumpInsn(Opcodes.IFGT, continuing); // If it's 1, continue along
        visitor.visitInsn(Opcodes.POP); // []
        visitor.visitInsn(Opcodes.FCONST_0); // [0]
        visitor.visitInsn(Opcodes.FRETURN); // Gone
        visitor.visitLabel(continuing); // [v], v is strictly positive
        visitor.visitInsn(Opcodes.DUP); // [v, v]
        visitor.visitInsn(Opcodes.FCONST_2); // [v, v, 2]
        visitor.visitInsn(Opcodes.FREM); // [v, v % 2]
        visitor.visitInsn(Opcodes.F2L); // [v, (long) (v % 2)]
        visitor.visitInsn(Opcodes.DUP2_X1); // [(long) (v % 2), v, (long) v % 2]
        visitor.visitInsn(Opcodes.POP2); // [(long) (v % 2), v]
        visitor.visitInsn(Opcodes.FCONST_2); // [(long) (v % 2), v, 2]
        visitor.visitInsn(Opcodes.FDIV); // [(long) (v % 2), v / 2]
        visitor.visitInsn(Opcodes.F2L); // [(long) (v % 2), (long) (v / 2)]
        visitor.visitInsn(Opcodes.ICONST_1); // [(long) (v % 2), (long) (v / 2), 1]
        visitor.visitInsn(Opcodes.LSHL); // [(long) (v % 2), ((long) (v / 2)) << 1]
        visitor.visitInsn(Opcodes.LOR); // [(long) (v % 2) | (((long) (v / 2)) << 1)]
        // Done!
        return null;
    }
    @Override public Void visitI64TruncF64S(Instruction.I64TruncF64S inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.D2L);
        return null;
    }
    @Override public Void visitI64TruncF64U(Instruction.I64TruncF64U inst) {
        abstractStack.applyStackType(inst.stackType());
        // [v]
        visitor.visitInsn(Opcodes.DUP2); // [v, v]
        visitor.visitInsn(Opcodes.DCONST_0); // [v, v, 0]
        visitor.visitInsn(Opcodes.DCMPL); // [v, (v <= 0 or v is NaN) ? (-1 or 0) : 1]
        Label continuing = new Label();
        visitor.visitJumpInsn(Opcodes.IFGT, continuing); // If it's 1, continue along
        visitor.visitInsn(Opcodes.POP); // []
        visitor.visitInsn(Opcodes.DCONST_0); // [0]
        visitor.visitInsn(Opcodes.DRETURN); // Gone
        visitor.visitLabel(continuing); // [v], v is strictly positive
        visitor.visitInsn(Opcodes.DUP2); // [v, v]
        visitor.visitLdcInsn(2.0); // [v, v, 2]
        visitor.visitInsn(Opcodes.DREM); // [v, v % 2]
        visitor.visitInsn(Opcodes.D2L); // [v, (long) (v % 2)]
        visitor.visitInsn(Opcodes.DUP2_X2); // [(long) (v % 2), v, (long) v % 2]
        visitor.visitInsn(Opcodes.POP2); // [(long) (v % 2), v]
        visitor.visitLdcInsn(2.0); // [(long) (v % 2), v, 2]
        visitor.visitInsn(Opcodes.DDIV); // [(long) (v % 2), v / 2]
        visitor.visitInsn(Opcodes.D2L); // [(long) (v % 2), (long) (v / 2)]
        visitor.visitInsn(Opcodes.ICONST_1); // [(long) (v % 2), (long) (v / 2), 1]
        visitor.visitInsn(Opcodes.LSHL); // [(long) (v % 2), ((long) (v / 2)) << 1]
        visitor.visitInsn(Opcodes.LOR); // [(long) (v % 2) | (((long) (v / 2)) << 1)]
        // Done!
        return null;
    }

    @Override public Void visitF32ConvertI32S(Instruction.F32ConvertI32S inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.I2F);
        return null;
    }
    @Override public Void visitF32ConvertI32U(Instruction.F32ConvertI32U inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.I2L);
        BytecodeHelper.constLong(visitor, 0xFFFFFFFFL);
        visitor.visitInsn(Opcodes.LAND);
        visitor.visitInsn(Opcodes.L2F);
        return null;
    }
    @Override public Void visitF32ConvertI64S(Instruction.F32ConvertI64S inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.L2F);
        return null;
    }
    @Override public Void visitF32ConvertI64U(Instruction.F32ConvertI64U inst) {
        abstractStack.applyStackType(inst.stackType());
        // double dValue = (double) (value & 0x7fffffffffffffffL);
        // if (value < 0) {
        //     dValue += 0x1.0p63;
        // }
        // From https://stackoverflow.com/questions/24193788/convert-unsigned-64-bit-decimal-to-java-double
        // start: [long]
        visitor.visitInsn(Opcodes.DUP2); // [long, long]
        visitor.visitLdcInsn(0x7fffffffffffffffL); // [long, long, mask]
        visitor.visitInsn(Opcodes.LAND); // [long, masked long]
        visitor.visitInsn(Opcodes.L2F); // [long, float]
        visitor.visitInsn(Opcodes.DUP_X2); // [float, long, float]
        visitor.visitInsn(Opcodes.POP); // [float, long]
        Label ifNonNegative = new Label();
        visitor.visitInsn(Opcodes.LCONST_0); // [float, long, 0L]
        visitor.visitInsn(Opcodes.LCMP); // [float, int]
        visitor.visitJumpInsn(Opcodes.IFGE, ifNonNegative); // jump if int non-negative. [float]
        visitor.visitLdcInsn(9223372036854775808f); // 2^63 as a float. [float, 0x1.0p63f]
        visitor.visitInsn(Opcodes.FADD); // [float]
        visitor.visitLabel(ifNonNegative); // non-negative [float]
        return null;
    }
    @Override public Void visitF32DemoteF64(Instruction.F32DemoteF64 inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.D2F);
        return null;
    }

    @Override public Void visitF64ConvertI32S(Instruction.F64ConvertI32S inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.I2D);
        return null;
    }
    @Override public Void visitF64ConvertI32U(Instruction.F64ConvertI32U inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.I2L);
        BytecodeHelper.constLong(visitor, 0xFFFFFFFFL);
        visitor.visitInsn(Opcodes.LAND);
        visitor.visitInsn(Opcodes.L2D);
        return null;
    }
    @Override public Void visitF64ConvertI64S(Instruction.F64ConvertI64S inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.L2D);
        return null;
    }
    @Override public Void visitF64ConvertI64U(Instruction.F64ConvertI64U inst) {
        abstractStack.applyStackType(inst.stackType());
        // start: [long]
        visitor.visitInsn(Opcodes.DUP2); // [long, long]
        visitor.visitLdcInsn(0x7fffffffffffffffL); // [long, long, mask]
        visitor.visitInsn(Opcodes.LAND); // [long, masked long]
        visitor.visitInsn(Opcodes.L2D); // [long, double]
        visitor.visitInsn(Opcodes.DUP2_X2); // [double, long, double]
        visitor.visitInsn(Opcodes.POP2); // [double, long]
        Label ifNonNegative = new Label();
        visitor.visitInsn(Opcodes.LCONST_0); // [double, long, 0L]
        visitor.visitInsn(Opcodes.LCMP); // [double, int]
        visitor.visitJumpInsn(Opcodes.IFGE, ifNonNegative); // jump if int non-negative. [double]
        visitor.visitLdcInsn(9223372036854775808.0); // 2^63 as a double. [double, 0x1.0p63d]
        visitor.visitInsn(Opcodes.DADD); // [double]
        visitor.visitLabel(ifNonNegative); // non-negative [double]
        return null;
    }
    @Override public Void visitF64PromoteF32(Instruction.F64PromoteF32 inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.F2D);
        return null;
    }

    @Override public Void visitI32ReinterpretF32(Instruction.I32ReinterpretF32 inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Float", "floatToRawIntBits", "(F)I", false);
        return null;
    }
    @Override public Void visitI64ReinterpretF64(Instruction.I64ReinterpretF64 inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Double", "doubleToRawLongBits", "(D)J", false);
        return null;
    }
    @Override public Void visitF32ReinterpretI32(Instruction.F32ReinterpretI32 inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Float", "intBitsToFloat", "(I)F", false);
        return null;
    }
    @Override public Void visitF64ReinterpretI64(Instruction.F64ReinterpretI64 inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Double", "longBitsToDouble", "(J)D", false);
        return null;
    }

    @Override public Void visitI32Extend8S(Instruction.I32Extend8S inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.I2B);
        return null;
    }
    @Override public Void visitI32Extend16S(Instruction.I32Extend16S inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.I2S);
        return null;
    }
    @Override public Void visitI64Extend8S(Instruction.I64Extend8S inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.L2I);
        visitor.visitInsn(Opcodes.I2B);
        visitor.visitInsn(Opcodes.I2L);
        return null;
    }
    @Override public Void visitI64Extend16S(Instruction.I64Extend16S inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.L2I);
        visitor.visitInsn(Opcodes.I2S);
        visitor.visitInsn(Opcodes.I2L);
        return null;
    }
    @Override public Void visitI64Extend32S(Instruction.I64Extend32S inst) {
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.L2I);
        visitor.visitInsn(Opcodes.I2L);
        return null;
    }

    // I think these trunc_sat are the same as convert??? i have no idea
    @Override public Void visitI32TruncSatF32S(Instruction.I32TruncSatF32S inst) {
        visitI32TruncF32S(Instruction.I32TruncF32S.INSTANCE);
        return null;
    }
    @Override public Void visitI32TruncSatF32U(Instruction.I32TruncSatF32U inst) {
        visitI32TruncF32U(Instruction.I32TruncF32U.INSTANCE);
        return null;
    }
    @Override public Void visitI32TruncSatF64S(Instruction.I32TruncSatF64S inst) {
        visitI32TruncF64S(Instruction.I32TruncF64S.INSTANCE);
        return null;
    }
    @Override public Void visitI32TruncSatF64U(Instruction.I32TruncSatF64U inst) {
        visitI32TruncF64U(Instruction.I32TruncF64U.INSTANCE);
        return null;
    }
    @Override public Void visitI64TruncSatF32S(Instruction.I64TruncSatF32S inst) {
        visitI64TruncF32S(Instruction.I64TruncF32S.INSTANCE);
        return null;
    }
    @Override public Void visitI64TruncSatF32U(Instruction.I64TruncSatF32U inst) {
        visitI64TruncF32U(Instruction.I64TruncF32U.INSTANCE);
        return null;
    }
    @Override public Void visitI64TruncSatF64S(Instruction.I64TruncSatF64S inst) {
        visitI64TruncF64S(Instruction.I64TruncF64S.INSTANCE);
        return null;
    }
    @Override public Void visitI64TruncSatF64U(Instruction.I64TruncSatF64U inst) {
        visitI64TruncF64U(Instruction.I64TruncF64U.INSTANCE);
        return null;
    }

    @Override public Void visitV128Load(Instruction.V128Load inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitV128Load8x8S(Instruction.V128Load8x8S inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitV128Load8x8U(Instruction.V128Load8x8U inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitV128Load16x4S(Instruction.V128Load16x4S inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitV128Load16x4U(Instruction.V128Load16x4U inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitV128Load32x2S(Instruction.V128Load32x2S inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitV128Load32x2U(Instruction.V128Load32x2U inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitV128Load8Splat(Instruction.V128Load8Splat inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitV128Load16Splat(Instruction.V128Load16Splat inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitV128Load32Splat(Instruction.V128Load32Splat inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitV128Load64Splat(Instruction.V128Load64Splat inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitV128Load32Zero(Instruction.V128Load32Zero inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitV128Load64Zero(Instruction.V128Load64Zero inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitV128Store(Instruction.V128Store inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitV128Load8Lane(Instruction.V128Load8Lane inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitV128Load16Lane(Instruction.V128Load16Lane inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitV128Load32Lane(Instruction.V128Load32Lane inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitV128Load64Lane(Instruction.V128Load64Lane inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitV128Store8Lane(Instruction.V128Store8Lane inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitV128Store16Lane(Instruction.V128Store16Lane inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitV128Store32Lane(Instruction.V128Store32Lane inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitV128Store64Lane(Instruction.V128Store64Lane inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitV128Const(Instruction.V128Const inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI8x16Shuffle(Instruction.I8x16Shuffle inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI8x16ExtractLaneS(Instruction.I8x16ExtractLaneS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI8x16ExtractLaneU(Instruction.I8x16ExtractLaneU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI8x16ReplaceLane(Instruction.I8x16ReplaceLane inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI16x8ExtractLaneS(Instruction.I16x8ExtractLaneS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI16x8ExtractLaneU(Instruction.I16x8ExtractLaneU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI16x8ReplaceLane(Instruction.I16x8ReplaceLane inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI32x4ExtractLane(Instruction.I32x4ExtractLane inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI32x4ReplaceLane(Instruction.I32x4ReplaceLane inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI64x2ExtractLane(Instruction.I64x2ExtractLane inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI64x2ReplaceLane(Instruction.I64x2ReplaceLane inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF32x4ExtractLane(Instruction.F32x4ExtractLane inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF32x4ReplaceLane(Instruction.F32x4ReplaceLane inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF64x2ExtractLane(Instruction.F64x2ExtractLane inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF64x2ReplaceLane(Instruction.F64x2ReplaceLane inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI8x16Swizzle(Instruction.I8x16Swizzle inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI8x16Splat(Instruction.I8x16Splat inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI16x8Splat(Instruction.I16x8Splat inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI32x4Splat(Instruction.I32x4Splat inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI64x2Splat(Instruction.I64x2Splat inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF32x4Splat(Instruction.F32x4Splat inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF64x2Splat(Instruction.F64x2Splat inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI8x16Eq(Instruction.I8x16Eq inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI8x16Ne(Instruction.I8x16Ne inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI8x16LtS(Instruction.I8x16LtS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI8x16LtU(Instruction.I8x16LtU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI8x16GtS(Instruction.I8x16GtS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI8x16GtU(Instruction.I8x16GtU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI8x16LeS(Instruction.I8x16LeS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI8x16LeU(Instruction.I8x16LeU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI8x16GeS(Instruction.I8x16GeS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI8x16GeU(Instruction.I8x16GeU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI16x8Eq(Instruction.I16x8Eq inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI16x8Ne(Instruction.I16x8Ne inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI16x8LtS(Instruction.I16x8LtS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI16x8LtU(Instruction.I16x8LtU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI16x8GtS(Instruction.I16x8GtS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI16x8GtU(Instruction.I16x8GtU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI16x8LeS(Instruction.I16x8LeS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI16x8LeU(Instruction.I16x8LeU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI16x8GeS(Instruction.I16x8GeS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI16x8GeU(Instruction.I16x8GeU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI32x4Eq(Instruction.I32x4Eq inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI32x4Ne(Instruction.I32x4Ne inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI32x4LtS(Instruction.I32x4LtS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI32x4LtU(Instruction.I32x4LtU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI32x4GtS(Instruction.I32x4GtS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI32x4GtU(Instruction.I32x4GtU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI32x4LeS(Instruction.I32x4LeS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI32x4LeU(Instruction.I32x4LeU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI32x4GeS(Instruction.I32x4GeS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI32x4GeU(Instruction.I32x4GeU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI64x2Eq(Instruction.I64x2Eq inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI64x2Ne(Instruction.I64x2Ne inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI64x2LtS(Instruction.I64x2LtS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI64x2GtS(Instruction.I64x2GtS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI64x2LeS(Instruction.I64x2LeS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI64x2GeS(Instruction.I64x2GeS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF32x4Eq(Instruction.F32x4Eq inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF32x4Ne(Instruction.F32x4Ne inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF32x4Lt(Instruction.F32x4Lt inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF32x4Gt(Instruction.F32x4Gt inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF32x4Le(Instruction.F32x4Le inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF32x4Ge(Instruction.F32x4Ge inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF64x2Eq(Instruction.F64x2Eq inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF64x2Ne(Instruction.F64x2Ne inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF64x2Lt(Instruction.F64x2Lt inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF64x2Gt(Instruction.F64x2Gt inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF64x2Le(Instruction.F64x2Le inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF64x2Ge(Instruction.F64x2Ge inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitV128Not(Instruction.V128Not inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitV128And(Instruction.V128And inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitV128AndNot(Instruction.V128AndNot inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitV128Or(Instruction.V128Or inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitV128Xor(Instruction.V128Xor inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitV128Bitselect(Instruction.V128Bitselect inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitV128AnyTrue(Instruction.V128AnyTrue inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI8x16Abs(Instruction.I8x16Abs inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI8x16Neg(Instruction.I8x16Neg inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI8x16PopCnt(Instruction.I8x16PopCnt inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI8x16AllTrue(Instruction.I8x16AllTrue inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI8x16Bitmask(Instruction.I8x16Bitmask inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI8x16NarrowI16x8S(Instruction.I8x16NarrowI16x8S inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI8x16NarrowI16x8U(Instruction.I8x16NarrowI16x8U inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI8x16Shl(Instruction.I8x16Shl inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI8x16ShrS(Instruction.I8x16ShrS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI8x16ShrU(Instruction.I8x16ShrU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI8x16Add(Instruction.I8x16Add inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI8x16AddSatS(Instruction.I8x16AddSatS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI8x16AddSatU(Instruction.I8x16AddSatU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI8x16Sub(Instruction.I8x16Sub inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI8x16SubSatS(Instruction.I8x16SubSatS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI8x16SubSatU(Instruction.I8x16SubSatU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI8x16MinS(Instruction.I8x16MinS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI8x16MinU(Instruction.I8x16MinU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI8x16MaxS(Instruction.I8x16MaxS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI8x16MaxU(Instruction.I8x16MaxU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI8x16AvgrU(Instruction.I8x16AvgrU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI16x8ExtAddPairwiseI8x16S(Instruction.I16x8ExtAddPairwiseI8x16S inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI16x8ExtAddPairwiseI8x16U(Instruction.I16x8ExtAddPairwiseI8x16U inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI16x8Abs(Instruction.I16x8Abs inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI16x8Neg(Instruction.I16x8Neg inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI16x8Q15MulrSatS(Instruction.I16x8Q15MulrSatS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI16x8AllTrue(Instruction.I16x8AllTrue inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI16x8Bitmask(Instruction.I16x8Bitmask inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI16x8NarrowI32x4S(Instruction.I16x8NarrowI32x4S inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI16x8NarrowI32x4U(Instruction.I16x8NarrowI32x4U inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI16x8ExtendLowI8x16S(Instruction.I16x8ExtendLowI8x16S inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI16x8ExtendHighI8x16S(Instruction.I16x8ExtendHighI8x16S inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI16x8ExtendLowI8x16U(Instruction.I16x8ExtendLowI8x16U inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI16x8ExtendHighI8x16U(Instruction.I16x8ExtendHighI8x16U inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI16x8Shl(Instruction.I16x8Shl inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI16x8ShrS(Instruction.I16x8ShrS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI16x8ShrU(Instruction.I16x8ShrU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI16x8Add(Instruction.I16x8Add inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI16x8AddSatS(Instruction.I16x8AddSatS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI16x8AddSatU(Instruction.I16x8AddSatU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI16x8Sub(Instruction.I16x8Sub inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI16x8SubSatS(Instruction.I16x8SubSatS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI16x8SubSatU(Instruction.I16x8SubSatU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI16x8Mul(Instruction.I16x8Mul inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI16x8MinS(Instruction.I16x8MinS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI16x8MinU(Instruction.I16x8MinU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI16x8MaxS(Instruction.I16x8MaxS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI16x8MaxU(Instruction.I16x8MaxU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI16x8AvgrU(Instruction.I16x8AvgrU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI16x8ExtMulLowI8x16S(Instruction.I16x8ExtMulLowI8x16S inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI16x8ExtMulHighI8x16S(Instruction.I16x8ExtMulHighI8x16S inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI16x8ExtMulLowI8x16U(Instruction.I16x8ExtMulLowI8x16U inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI16x8ExtMulHighI8x16U(Instruction.I16x8ExtMulHighI8x16U inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI32x4ExtAddPairwiseI16x8S(Instruction.I32x4ExtAddPairwiseI16x8S inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI32x4ExtAddPairwiseI16x8U(Instruction.I32x4ExtAddPairwiseI16x8U inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI32x4Abs(Instruction.I32x4Abs inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI32x4Neg(Instruction.I32x4Neg inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI32x4AllTrue(Instruction.I32x4AllTrue inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI32x4Bitmask(Instruction.I32x4Bitmask inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI32x4ExtendLowI16x8S(Instruction.I32x4ExtendLowI16x8S inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI32x4ExtendHighI16x8S(Instruction.I32x4ExtendHighI16x8S inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI32x4ExtendLowI16x8U(Instruction.I32x4ExtendLowI16x8U inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI32x4ExtendHighI16x8U(Instruction.I32x4ExtendHighI16x8U inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI32x4Shl(Instruction.I32x4Shl inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI32x4ShrS(Instruction.I32x4ShrS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI32x4ShrU(Instruction.I32x4ShrU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI32x4Add(Instruction.I32x4Add inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI32x4Sub(Instruction.I32x4Sub inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI32x4Mul(Instruction.I32x4Mul inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI32x4MinS(Instruction.I32x4MinS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI32x4MinU(Instruction.I32x4MinU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI32x4MaxS(Instruction.I32x4MaxS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI32x4MaxU(Instruction.I32x4MaxU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI32x4DotI16x8S(Instruction.I32x4DotI16x8S inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI32x4ExtMulLowI16x8S(Instruction.I32x4ExtMulLowI16x8S inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI32x4ExtMulHighI16x8S(Instruction.I32x4ExtMulHighI16x8S inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI32x4ExtMulLowI16x8U(Instruction.I32x4ExtMulLowI16x8U inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI32x4ExtMulHighI16x8U(Instruction.I32x4ExtMulHighI16x8U inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI64x2Abs(Instruction.I64x2Abs inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI64x2Neg(Instruction.I64x2Neg inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI64x2AllTrue(Instruction.I64x2AllTrue inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI64x2Bitmask(Instruction.I64x2Bitmask inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI64x2ExtendLowI32x4S(Instruction.I64x2ExtendLowI32x4S inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI64x2ExtendHighI32x4S(Instruction.I64x2ExtendHighI32x4S inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI64x2ExtendLowI32x4U(Instruction.I64x2ExtendLowI32x4U inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI64x2ExtendHighI32x4U(Instruction.I64x2ExtendHighI32x4U inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI64x2Shl(Instruction.I64x2Shl inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI64x2ShrS(Instruction.I64x2ShrS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI64x2ShrU(Instruction.I64x2ShrU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI64x2Add(Instruction.I64x2Add inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI64x2Sub(Instruction.I64x2Sub inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI64x2Mul(Instruction.I64x2Mul inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI64x2ExtMulLowI32x4S(Instruction.I64x2ExtMulLowI32x4S inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI64x2ExtMulHighI32x4S(Instruction.I64x2ExtMulHighI32x4S inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI64x2ExtMulLowI32x4U(Instruction.I64x2ExtMulLowI32x4U inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI64x2ExtMulHighI32x4U(Instruction.I64x2ExtMulHighI32x4U inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF32x4Ceil(Instruction.F32x4Ceil inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF32x4Floor(Instruction.F32x4Floor inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF32x4Trunc(Instruction.F32x4Trunc inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF32x4Nearest(Instruction.F32x4Nearest inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF32x4Abs(Instruction.F32x4Abs inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF32x4Neg(Instruction.F32x4Neg inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF32x4Sqrt(Instruction.F32x4Sqrt inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF32x4Add(Instruction.F32x4Add inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF32x4Sub(Instruction.F32x4Sub inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF32x4Mul(Instruction.F32x4Mul inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF32x4Div(Instruction.F32x4Div inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF32x4Min(Instruction.F32x4Min inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF32x4Max(Instruction.F32x4Max inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF32x4PMin(Instruction.F32x4PMin inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF32x4PMax(Instruction.F32x4PMax inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF64x2Ceil(Instruction.F64x2Ceil inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF64x2Floor(Instruction.F64x2Floor inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF64x2Trunc(Instruction.F64x2Trunc inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF64x2Nearest(Instruction.F64x2Nearest inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF64x2Abs(Instruction.F64x2Abs inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF64x2Neg(Instruction.F64x2Neg inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF64x2Sqrt(Instruction.F64x2Sqrt inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF64x2Add(Instruction.F64x2Add inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF64x2Sub(Instruction.F64x2Sub inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF64x2Mul(Instruction.F64x2Mul inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF64x2Div(Instruction.F64x2Div inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF64x2Min(Instruction.F64x2Min inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF64x2Max(Instruction.F64x2Max inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF64x2PMin(Instruction.F64x2PMin inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF64x2PMax(Instruction.F64x2PMax inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI32x4TruncSatF32x4S(Instruction.I32x4TruncSatF32x4S inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI32x4TruncSatF32x4U(Instruction.I32x4TruncSatF32x4U inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF32x4ConvertI32x4S(Instruction.F32x4ConvertI32x4S inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF32x4ConvertI32x4U(Instruction.F32x4ConvertI32x4U inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI32x4TruncSatF64x2SZero(Instruction.I32x4TruncSatF64x2SZero inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitI32x4TruncSatF64x2UZero(Instruction.I32x4TruncSatF64x2UZero inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF64x2ConvertLowI32x4S(Instruction.F64x2ConvertLowI32x4S inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF64x2ConvertLowI32x4U(Instruction.F64x2ConvertLowI32x4U inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF32x4DemoteF64x2Zero(Instruction.F32x4DemoteF64x2Zero inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override public Void visitF64x2PromoteLowF32x4(Instruction.F64x2PromoteLowF32x4 inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

}