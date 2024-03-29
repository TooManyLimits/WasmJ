package io.github.toomanylimits.wasmj.compiler;

import io.github.toomanylimits.wasmj.parsing.instruction.Expression;
import io.github.toomanylimits.wasmj.parsing.instruction.Instruction;
import io.github.toomanylimits.wasmj.parsing.instruction.StackType;
import io.github.toomanylimits.wasmj.parsing.module.Code;
import io.github.toomanylimits.wasmj.parsing.module.Import;
import io.github.toomanylimits.wasmj.parsing.module.WasmModule;
import io.github.toomanylimits.wasmj.parsing.types.FuncType;
import io.github.toomanylimits.wasmj.parsing.types.ValType;
import io.github.toomanylimits.wasmj.runtime.sandbox.InstanceLimiter;
import io.github.toomanylimits.wasmj.runtime.reflect.JavaModuleData;
import io.github.toomanylimits.wasmj.runtime.sandbox.RefCountable;
import io.github.toomanylimits.wasmj.util.ListUtils;
import org.objectweb.asm.*;

import java.lang.annotation.ElementType;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.*;

import static io.github.toomanylimits.wasmj.compiler.Compile.*;

public class MethodWritingVisitor extends InstructionVisitor<Void> {

    private final String moduleName;
    private final WasmModule module;
    private final Code code;
    private final FuncType funcType; // The type of the function we're in. Used only when returning.
    private final MethodVisitor visitor;
    private final Map<String, JavaModuleData<?>> javaModules;
    private final InstanceLimiter limiter;

    public MethodWritingVisitor(Map<String, JavaModuleData<?>> javaModules, InstanceLimiter limiter, String moduleName, WasmModule module, Code code, FuncType funcType, MethodVisitor visitor) {
        this.javaModules = javaModules;
        this.limiter = limiter;
        this.moduleName = moduleName;
        this.module = module;
        this.code = code;
        this.funcType = funcType;
        this.visitor = visitor;
    }

    private sealed interface AbstractStackElement {
        record ValueElement(ValType valueType, int localVariableIndex) implements AbstractStackElement {}
        record LabelElement(Label asmLabel, int arity) implements AbstractStackElement {}
    }

    private class AbstractStack implements Iterable<AbstractStackElement> {
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

        // Get the next local index
        public int nextLocalSlot() {
            for (AbstractStackElement e : elements)
                if (e instanceof AbstractStackElement.ValueElement v && v.localVariableIndex != -1) {
                    return v.localVariableIndex + v.valueType.stackSlots();
                }
            return MethodWritingVisitor.this.code.nextLocalSlot();
        }

        // Pop the top value of the stack and return its type
        public ValType popTakeType() {
            AbstractStackElement elem = pop();
            if (elem instanceof AbstractStackElement.ValueElement v)
                return v.valueType;
            throw new IllegalStateException("Malformed WASM code, or bug in compiler");
        }
        // Pop the top value of the stack, expecting the given type
        public AbstractStackElement.ValueElement popExpecting(ValType type) {
            AbstractStackElement elem = pop();
            if (!(elem instanceof AbstractStackElement.ValueElement v) || v.valueType != type)
                throw new IllegalStateException("Malformed WASM code, or bug in compiler. Expected " + type + ", found " + elem);
            return v;
        }
        public AbstractStackElement.ValueElement popExpecting(ValType... types) {
            AbstractStackElement elem = pop();
            if (!(elem instanceof AbstractStackElement.ValueElement v) || !(Arrays.asList(types).contains(v.valueType)))
                throw new IllegalStateException("Malformed WASM code, or bug in compiler. Expected " + Arrays.toString(types) + ", found " + elem);
            return v;
        }
        public AbstractStackElement.ValueElement popExpectingValue() {
            AbstractStackElement elem = pop();
            if (!(elem instanceof AbstractStackElement.ValueElement v))
                throw new IllegalStateException("Malformed WASM code, or bug in compiler. Expected value, found label");
            return v;
        }

        // Peek the top value of the stack, expecting the given type
        public AbstractStackElement.ValueElement peekExpecting(ValType type) {
            AbstractStackElement elem = peek();
            if (!(elem instanceof AbstractStackElement.ValueElement v) || v.valueType != type)
                throw new IllegalStateException("Malformed WASM code, or bug in compiler");
            return v;
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
            for (ValType type : stackType.inTypes()) {
                if (type instanceof ValType.RefType && limiter.countsMemory)
                    throw new IllegalArgumentException("Shouldn't use applyStackType with reference types as inTypes, when counting memory!");
                pop();
            }
            for (ValType type : stackType.outTypes()) {
                push(new AbstractStackElement.ValueElement(type, -1));
            }
        }


    }

    private final AbstractStack abstractStack = new AbstractStack();

    public void visitExpr(Expression expr) {
        // Works similarly to a block, just without the type-tracking business
        // Before emitting anything, first count up the number of instructions guaranteed to add by:
        CostCountingVisitor costCounter = new CostCountingVisitor();
        long currentCost = 0;
        Iterator<Instruction> iterator = expr.getInstrsAndIKnowWhatImDoing().iterator();
        Instruction next = null;
        while (iterator.hasNext()) {
            next = iterator.next();
            Long cost = next.accept(costCounter);
            if (cost == null) {
                break;
            } else {
                currentCost += cost;
            }
        }
        incrementInstructions(currentCost); // And emit those
        for (Instruction inner : expr.getInstrsAndIKnowWhatImDoing()) {
            inner.accept(this);
            if (inner == next) {
                // Do it again, counting up the elements that occur after "inner" does, until the next null-returner
                currentCost = 0;
                while (iterator.hasNext()) {
                    next = iterator.next();
                    Long cost = next.accept(costCounter);
                    if (cost == null) {
                        break;
                    } else {
                        currentCost += cost;
                    }
                }
                // Add the cost now, after the inner instruction happens
                incrementInstructions(currentCost);
            }
        }
    }

    public void incrementInstructions(long count) {
        if (limiter.countsInstructions && count > 0) {
            // Get limiter, get count, call inc
            visitor.visitFieldInsn(Opcodes.GETSTATIC, Compile.getClassName(moduleName), Compile.getLimiterName(), Type.getDescriptor(InstanceLimiter.class));
            BytecodeHelper.constLong(visitor, count);
            visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(InstanceLimiter.class), "incInstructions", "(J)V", false);
        }
    }

    // Increments instructions, using the long value on top of the stack as the count. Consumes the long.
    // Assumes that the limiter counts instructions.
    public void incrementInstructionsByTopElement() {
        if (!limiter.countsInstructions)
            throw new IllegalStateException("Method incrementInstructionsByTopElement should only be called if limiter.countsInstructions is true!");
        // Get limiter, swap, call inc
        visitor.visitFieldInsn(Opcodes.GETSTATIC, Compile.getClassName(moduleName), Compile.getLimiterName(), Type.getDescriptor(InstanceLimiter.class));
        visitor.visitInsn(Opcodes.DUP_X2);
        visitor.visitInsn(Opcodes.POP);
        visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(InstanceLimiter.class), "incInstructions", "(J)V", false);
    }

    // Increments memory usage, using the long value on top of the stack as the count. Consumes the long.
    // Assumes that the limiter counts memory.
    public void incrementMemoryByTopElement() {
        if (!limiter.countsMemory)
            throw new IllegalStateException("Method incrementMemoryByTopElement should only be called if limiter.countsMemory is true!");
        // Get limiter, swap, call inc
        visitor.visitFieldInsn(Opcodes.GETSTATIC, Compile.getClassName(moduleName), Compile.getLimiterName(), Type.getDescriptor(InstanceLimiter.class));
        visitor.visitInsn(Opcodes.DUP_X2);
        visitor.visitInsn(Opcodes.POP);
        visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(InstanceLimiter.class), "incHeapMemoryUsed", "(J)V", false);
    }

    // Decrements memory usage, using the long value on top of the stack as the count. Consumes the long.
    // Assumes that the limiter counts memory.
    public void decrementMemoryByTopElement() {
        if (!limiter.countsMemory)
            throw new IllegalStateException("Method decrementMemoryByTopElement should only be called if limiter.countsMemory is true!");
        // Get limiter, swap, call inc
        visitor.visitFieldInsn(Opcodes.GETSTATIC, Compile.getClassName(moduleName), Compile.getLimiterName(), Type.getDescriptor(InstanceLimiter.class));
        visitor.visitInsn(Opcodes.DUP_X2);
        visitor.visitInsn(Opcodes.POP);
        visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(InstanceLimiter.class), "decHeapMemoryUsed", "(J)V", false);
    }

    // Decrements/increments the ref count of the top element.
    // Consumes the element, so dup it first if you still want it.
    // Assumes that the limiter counts heap memory.
    public void decrementRefCountOfTopElement() {
        if (!limiter.countsMemory)
            throw new IllegalStateException("Method decrementRefCountOfTopElement should only be called if limiter.countsMemory is true!");
        // If null, skip
        Label nullLabel = new Label();
        Label end = new Label();
        visitor.visitInsn(Opcodes.DUP);
        visitor.visitJumpInsn(Opcodes.IFNULL, nullLabel);
        // Check cast
        visitor.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(RefCountable.class));
        // Get limiter, call decrement
        visitor.visitFieldInsn(Opcodes.GETSTATIC, Compile.getClassName(moduleName), Compile.getLimiterName(), Type.getDescriptor(InstanceLimiter.class));
        visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(RefCountable.class), "dec", "(" + Type.getDescriptor(InstanceLimiter.class) + ")V", false);
        visitor.visitJumpInsn(Opcodes.GOTO, end);
        // Null label
        visitor.visitLabel(nullLabel);
        visitor.visitInsn(Opcodes.POP);
        // End
        visitor.visitLabel(end);
    }
    public void incrementRefCountOfTopElement() {
        if (!limiter.countsMemory)
            throw new IllegalStateException("Method incrementRefCountOfTopElement should only be called if limiter.countsMemory is true!");
        // If null, skip
        Label nullLabel = new Label();
        Label end = new Label();
        visitor.visitInsn(Opcodes.DUP);
        visitor.visitJumpInsn(Opcodes.IFNULL, nullLabel);
        // Check cast
        visitor.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(RefCountable.class));
        // Get limiter, call increment
        visitor.visitFieldInsn(Opcodes.GETSTATIC, Compile.getClassName(moduleName), Compile.getLimiterName(), Type.getDescriptor(InstanceLimiter.class));
        visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(RefCountable.class), "inc", "(" + Type.getDescriptor(InstanceLimiter.class) + ")V", false);
        visitor.visitJumpInsn(Opcodes.GOTO, end);
        // Null label
        visitor.visitLabel(nullLabel);
        visitor.visitInsn(Opcodes.POP);
        // End
        visitor.visitLabel(end);
    }

    // Pushes the byte[] to the stack with the given memory index.
    // Handles imports.
    // memIndex should always be 0, since WASM doesn't support
    // multiple memories (yet?)
    private void pushMemory(int memIndex) {
        if (memIndex < module.memImports().size()) {
            Import.Mem memImport = module.memImports().get(memIndex);
            if (javaModules.containsKey(memImport.moduleName))
                throw new UnsupportedOperationException("Cannot import memories from java modules");
            else {
                // Call the getter
                String className = Compile.getClassName(memImport.moduleName);
                String methodName = Compile.getExportedMemGetterName(memImport.elementName); // Getter!
                visitor.visitMethodInsn(Opcodes.INVOKESTATIC, className, methodName, "()[B", false); // Call it
            }
        } else {
            // Fetch the field
            int adjustedIndex = memIndex - module.memImports().size();
            visitor.visitFieldInsn(Opcodes.GETSTATIC, Compile.getClassName(moduleName), Compile.getMemoryName(adjustedIndex), "[B");
        }
    }
    // Takes the top element of the stack, a byte[], and replaces the memory at the given index with it.
    // Handles imports.
    private void storeMemory(int memIndex) {
        if (memIndex < module.memImports().size()) {
            Import.Mem memImport = module.memImports().get(memIndex);
            if (javaModules.containsKey(memImport.moduleName))
                throw new UnsupportedOperationException("Cannot import memories from java modules");
            else {
                // Call the setter
                String className = Compile.getClassName(memImport.moduleName);
                String methodName = Compile.getExportedMemSetterName(memImport.elementName); // Setter!
                visitor.visitMethodInsn(Opcodes.INVOKESTATIC, className, methodName, "([B)V", false); // Call it
            }
        } else {
            // Store in the field
            int adjustedIndex = memIndex - module.memImports().size();
            visitor.visitFieldInsn(Opcodes.PUTSTATIC, Compile.getClassName(moduleName), Compile.getMemoryName(adjustedIndex), "[B");
        }
    }

    // Same as above but for tables
    private void pushTable(int tableIndex, boolean isFuncRef) {
        String descriptor = (isFuncRef ? FUNCREF_TABLE_DESCRIPTOR : TABLE_DESCRIPTOR);
        if (tableIndex < module.tableImports().size()) {
            Import.Table tableImport = module.tableImports().get(tableIndex);
            if (javaModules.containsKey(tableImport.moduleName))
                throw new UnsupportedOperationException("Cannot import tables from java modules");
            else {
                // Call the getter
                String className = Compile.getClassName(tableImport.moduleName);
                String methodName = Compile.getExportedTableGetterName(tableImport.elementName); // Getter!
                visitor.visitMethodInsn(Opcodes.INVOKESTATIC, className, methodName, "()" + descriptor, false); // Call it
            }
        } else {
            // Fetch the field
            int adjustedIndex = tableIndex - module.tableImports().size();
            visitor.visitFieldInsn(Opcodes.GETSTATIC, Compile.getClassName(moduleName), Compile.getTableName(adjustedIndex), descriptor);
        }
    }
    // Takes the top element of the stack, a table array, and replaces the table at the given index with it.
    // Handles imports.
    private void storeTable(int tableIndex, boolean isFuncRef) {
        String descriptor = (isFuncRef ? FUNCREF_TABLE_DESCRIPTOR : TABLE_DESCRIPTOR);
        if (tableIndex < module.tableImports().size()) {
            Import.Table tableImport = module.tableImports().get(tableIndex);
            if (javaModules.containsKey(tableImport.moduleName))
                throw new UnsupportedOperationException("Cannot import tables from java modules");
            else {
                // Call the setter
                String className = Compile.getClassName(tableImport.moduleName);
                String methodName = Compile.getExportedTableSetterName(tableImport.elementName); // Setter!
                visitor.visitMethodInsn(Opcodes.INVOKESTATIC, className, methodName, "(" + descriptor + ")V", false); // Call it
            }
        } else {
            // Store in the field
            int adjustedIndex = tableIndex - module.tableImports().size();
            visitor.visitFieldInsn(Opcodes.PUTSTATIC, Compile.getClassName(moduleName), Compile.getTableName(adjustedIndex), descriptor);
        }
    }

    /**
     * Return the elements of the given type, in an array.
     * Used when exporting helper functions.
     * Largely the same logic as the "visitReturn" method.
     * This is used when we return to a JAVA CALLER!
     * So refcounts of returned values should be decremented!
     */
    public void returnArrayToJavaCaller(List<ValType> returnTypes) {

        // Create an array of the appropriate length and store it in the next local
        int size = returnTypes.size();
        BytecodeHelper.constInt(visitor, size); // [...results, size]
        visitor.visitTypeInsn(Opcodes.ANEWARRAY, Type.getInternalName(Object.class)); // [...results, resultArray]
        visitor.visitVarInsn(Opcodes.ASTORE, abstractStack.nextLocalSlot()); // [...results]
        BytecodeHelper.constInt(visitor, size); // [...results, size]
        visitor.visitInsn(Opcodes.ICONST_1); // [...results, size, 1]
        visitor.visitInsn(Opcodes.ISUB); // [...results, index = size - 1]
        // For each type in return types... (reversed)
        ListUtils.iterReverse(returnTypes, t -> {
            // Pop it from the abstract stack:
            abstractStack.popExpecting(t);
            // Dup the index down and swap
            if (t.stackSlots() == 1) {
                visitor.visitInsn(Opcodes.DUP_X1); // [...results, index, lastResult, index]
                visitor.visitInsn(Opcodes.SWAP); // [...results, index, index, lastResult]
            } else if (t.stackSlots() == 2) {
                visitor.visitInsn(Opcodes.DUP_X2); // [...results, index, lastResult, index]
                visitor.visitInsn(Opcodes.DUP_X2); // [...results, index, index, lastResult, index]
                visitor.visitInsn(Opcodes.POP); // [...results, index, index, lastResult]
            } else throw new IllegalStateException();
            // If we count memory, decrement its refcount:
            if (limiter.countsMemory && t instanceof ValType.RefType) {
                visitor.visitInsn(Opcodes.DUP);
                decrementRefCountOfTopElement();
            }
            // Box it:
            BytecodeHelper.boxValue(visitor, t);
            // Store in the array at the index
            visitor.visitVarInsn(Opcodes.ALOAD, abstractStack.nextLocalSlot()); // [...results, index, index, lastResultBoxed, array]
            visitor.visitInsn(Opcodes.DUP_X2); // [...results, index, array, index, lastResultBoxed, array]
            visitor.visitInsn(Opcodes.POP); // [...results, index, array, index, lastResultBoxed]
            visitor.visitInsn(Opcodes.AASTORE); // [...results, index]
            // Decrement index and repeat!
            visitor.visitInsn(Opcodes.ICONST_1); // [...results, index, 1]
            visitor.visitInsn(Opcodes.ISUB); // [...results, index - 1]
        });

        // Iterate over remaining elements of stack, find objects and decrement their refcounts.
        if (limiter.countsMemory) { // If we count memory at all, that is
            for (var e: abstractStack) {
                if (e instanceof AbstractStackElement.ValueElement v && v.valueType instanceof ValType.RefType) {
                    if (v.localVariableIndex == -1)
                        throw new IllegalStateException("Value is ref type but doesnt have local variable index??");
                    BytecodeHelper.loadLocal(visitor, v.localVariableIndex, v.valueType);
                    BytecodeHelper.debugPrintln(visitor, "DecRefCount - return, removing from stack");
                    decrementRefCountOfTopElement();
                }
            }
            // Also iterate over the original local variables and decrement theirs
            for (int i = 0; i < code.locals.size(); i++) {
                ValType localType = code.locals.get(i);
                if (localType instanceof ValType.RefType) {
                    int mappedIndex = code.localMappings().get(i);
                    BytecodeHelper.loadLocal(visitor, mappedIndex, localType);
                    BytecodeHelper.debugPrintln(visitor, "DecRefCount - return, removing local vars");
                    decrementRefCountOfTopElement();
                }
            }
        }

        // Return the array
        visitor.visitVarInsn(Opcodes.ALOAD, abstractStack.nextLocalSlot());
        visitor.visitInsn(Opcodes.ARETURN);
    }

    @Override public Void visitEnd(Instruction.End inst) {
        throw new IllegalStateException("Should never happen? \"End\" is just a marker instruction!");
    }

    @Override public Void visitElse(Instruction.Else inst) {
        throw new IllegalStateException("Should never happen? \"Else\" is just a marker instruction!");
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
        // Before emitting anything, first count up the number of instructions guaranteed to add by:
        CostCountingVisitor costCounter = new CostCountingVisitor();
        long currentCost = 0;
        Iterator<Instruction> iterator = inst.inside().iterator();
        Instruction next = null;
        while (iterator.hasNext()) {
            next = iterator.next();
            Long cost = next.accept(costCounter);
            if (cost == null) {
                break;
            } else {
                currentCost += cost;
            }
        }
        incrementInstructions(currentCost); // And emit those

        StackType stackType = inst.bt().stackType(module);
        Label label = new Label();
        AbstractStackElement labelElem = new AbstractStackElement.LabelElement(label, stackType.outTypes().size());
        abstractStack.add(stackType.inTypes().size(), labelElem);
        int stackHeightBefore = abstractStack.size();
        for (Instruction inner : inst.inside()) {
            inner.accept(this);
            if (inner == next) {
                // Do it again, counting up the elements that occur after "inner" does, until the next null-returner
                currentCost = 0;
                while (iterator.hasNext()) {
                    next = iterator.next();
                    Long cost = next.accept(costCounter);
                    if (cost == null) {
                        break;
                    } else {
                        currentCost += cost;
                    }
                }
                // Add the cost now, after the inner instruction happens
                incrementInstructions(currentCost);
            }
        }
        int stackHeightAfter = abstractStack.size();
        if (stackHeightAfter - stackHeightBefore != stackType.outTypes().size() - stackType.inTypes().size()) {
            if (stackHeightAfter - stackHeightBefore < stackType.outTypes().size() - stackType.inTypes().size())
                throw new IllegalStateException("Bug in compiler, please report! Stack height before was " + stackHeightBefore + ", stack height after was " + stackHeightAfter + ". Expected change was " + (stackType.outTypes().size() - stackType.inTypes().size()));
            int numToPop = (stackHeightAfter - stackHeightBefore) - (stackType.outTypes().size() - stackType.inTypes().size());
            for (int i = 0; i < numToPop; i++) {
                AbstractStackElement top = abstractStack.pop();
                if (!(top instanceof AbstractStackElement.ValueElement v))
                    throw new IllegalStateException("Malformed WASM code, or bug in compiler");
            }
        }
        visitor.visitLabel(label);
        if (!abstractStack.remove(labelElem)) throw new IllegalStateException("Bug in compiler, please report");
        return null;
    }

    // Declare and emit label, visit inner instructions
    @Override public Void visitLoop(Instruction.Loop inst) {

        // Before emitting anything, first count up the number of instructions guaranteed to add by:
        CostCountingVisitor costCounter = new CostCountingVisitor();
        long currentCost = 0;
        Iterator<Instruction> iterator = inst.inside().iterator();
        Instruction next = null;
        while (iterator.hasNext()) {
            next = iterator.next();
            Long cost = next.accept(costCounter);
            if (cost == null) {
                break;
            } else {
                currentCost += cost;
            }
        }
        incrementInstructions(currentCost); // And emit those

        // Now proceed with the regular loop body
        StackType stackType = inst.bt().stackType(module);
        Label label = new Label();
        AbstractStackElement labelElem = new AbstractStackElement.LabelElement(label, stackType.inTypes().size());
        abstractStack.add(stackType.inTypes().size(), labelElem);
        visitor.visitLabel(label);
        for (Instruction inner : inst.inside()) {
            inner.accept(this);
            if (inner == next) {
                // Do it again, counting up the elements that occur after "inner" does, until the next null-returner
                currentCost = 0;
                while (iterator.hasNext()) {
                    next = iterator.next();
                    Long cost = next.accept(costCounter);
                    if (cost == null) {
                        break;
                    } else {
                        currentCost += cost;
                    }
                }
                // Add the cost now, after the inner instruction happens
                incrementInstructions(currentCost);
            }
        }
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
        int curLocalIndex = abstractStack.nextLocalSlot();
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
                    // Pop it, also decrement ref count if applicable
                    if (limiter.countsMemory && valueElem.localVariableIndex != -1) {
                        BytecodeHelper.debugPrintln(visitor, "DecRefCount - branching");
                        decrementRefCountOfTopElement();
                    } else {
                        BytecodeHelper.popValue(visitor, valueElem.valueType());
                    }
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
//        BytecodeHelper.debugPrintln(visitor, "Returning");

        // Pop off the first elements of the stack that are getting returned.
        // Their refcounts shouldn't be modified.
        FuncType functionType = this.funcType;
//        FuncType functionType = module.types.get(module.functions.get(code.index));
        for (int i = functionType.results.size() - 1; i >= 0; i--)
            abstractStack.popExpecting(functionType.results.get(i));

        // Iterate over remaining elements of stack, find objects and decrement their refcounts.
        if (limiter.countsMemory) { // If we count memory at all, that is
            for (var e: abstractStack) {
                if (e instanceof AbstractStackElement.ValueElement v && v.valueType instanceof ValType.RefType) {
                    if (v.localVariableIndex == -1)
                        throw new IllegalStateException("Value is ref type but doesnt have local variable index??");
                    BytecodeHelper.loadLocal(visitor, v.localVariableIndex, v.valueType);
                    BytecodeHelper.debugPrintln(visitor, "DecRefCount - return, removing from stack");
                    decrementRefCountOfTopElement();
                }
            }

            // Also iterate over the original local variables and decrement theirs
            for (int i = 0; i < code.locals.size(); i++) {
                ValType localType = code.locals.get(i);
                if (localType instanceof ValType.RefType) {
                    int mappedIndex = code.localMappings().get(i);
                    BytecodeHelper.loadLocal(visitor, mappedIndex, localType);
                    BytecodeHelper.debugPrintln(visitor, "DecRefCount - return, removing local vars");
                    decrementRefCountOfTopElement();
                }
            }
        }

        switch (functionType.results.size()) {
            case 0 -> visitor.visitInsn(Opcodes.RETURN);
            case 1 -> BytecodeHelper.returnValue(visitor, functionType.results.get(0));
            default -> throw new UnsupportedOperationException("Multi-return functions");
        }
        return null;
    }

    @Override public Void visitCall(Instruction.Call inst) {
//        BytecodeHelper.debugPrintln(visitor, "Calling func f$" + inst.index());
        if (inst.index() < module.funcImports().size()) {
            // Calling an imported function
            Import.Func imported = module.funcImports().get(inst.index());
            FuncType funcType = module.types.get(imported.typeIndex);
            if (funcType.results.size() > 1) throw new UnsupportedOperationException("Multi-return functions");

            // Do something different depending on if this is a java function or a WASM function
            // Important note for refcounting:
            // If a JAVA function returns an Object into WASM, then WASM will increment the refcounter.
            // If a WASM function returns an Object into WASM, then the refcounter will not be affected.
            // Why? Well, imagine a simple java function that creates an object and returns it:
            // Counter new_counter() { return new Counter(); }
            // This function does NOT increment the refcounter when creating the object, and it would be
            // an annoying task to make it do so. So the WASM function takes responsibility to increment
            // the refcounter itself, when obtaining this Object from a java function.

            if (javaModules.containsKey(imported.moduleName)) {

                // If the function has any reference types as parameters, decrement ref counts.
                // We're calling OUT OF WASM here. When we call another WASM function, we keep
                // ref counts of params the same, since the new stack frame just takes those
                // references. But here, the java code takes the references, so WASM doesn't have
                // access to them anymore, so we decrement.
                if (limiter.countsMemory) {
                    // Find the objects, decrement their ref counts
                    ListUtils.iterReverse(funcType.asStackType().inTypes(), t -> {
                        AbstractStackElement.ValueElement v = abstractStack.popExpecting(t);
                        if (v.localVariableIndex != -1) {
                            BytecodeHelper.loadLocal(visitor, v.localVariableIndex, v.valueType);
                            BytecodeHelper.debugPrintln(visitor, "DecRefCount - before imported call");
                            decrementRefCountOfTopElement();
                        }
                    });
                } else {
                    // Otherwise, can just apply stack type normally, no worries
                    abstractStack.applyStackType(funcType.asStackType());
                }

                // Java function, need some thought put into it
                JavaModuleData<?> moduleData = javaModules.get(imported.moduleName);
                JavaModuleData.MethodData funcData = moduleData.allowedMethods.get(imported.elementName);
                if (funcData == null)
                    throw new IllegalStateException("Java function \"" + imported.moduleName + "." + imported.elementName + "\" does not exist (or is not allowed)");

                // Fetch the byte[] and/or the limiter, if the function needs them
                if (funcData.hasByteArrayAccess()) {
                    // If the func has byte array access, put the byte array on the stack
                    if (module.memories.isEmpty()) {
                        // There are no memories, give null
                        visitor.visitInsn(Opcodes.ACONST_NULL);
                    } else {
                        // Get the memory byte[] and put on the stack
                        pushMemory(0);
                    }
                }
                if (funcData.hasLimiterAccess()) {
                    // Get the limiter and put on the stack
                    visitor.visitFieldInsn(Opcodes.GETSTATIC, Compile.getClassName(moduleName), Compile.getLimiterName(), Type.getDescriptor(InstanceLimiter.class));
                }

                // Call the function
                int invokeOpcode = funcData.needsGlue() ? Opcodes.INVOKESTATIC : (funcData.isStatic() ? Opcodes.INVOKESTATIC : Opcodes.INVOKEVIRTUAL);
                String className = funcData.needsGlue() ? Compile.getClassName(moduleName) : moduleData.className();
                String javaName = funcData.needsGlue() ? Compile.getGlueFuncName(inst.index()) : funcData.javaName();
                String desc = funcData.needsGlue() ? funcData.glueDescriptor() : funcData.descriptor();
                visitor.visitMethodInsn(invokeOpcode, className, javaName, desc, false);

                // Store locals if necessary. See above: if a java function returns an object, we increment its ref counter.
                // Also push result types to the stack
                if (limiter.countsMemory) {
                    // TODO: Multiple returns
                    int nextLocalSlot = abstractStack.nextLocalSlot();
                    for (ValType t : funcType.asStackType().outTypes()) {
                        if (funcType.asStackType().outTypes().size() > 1)
                            throw new IllegalStateException("Multiple returns are TODO");
                        if (t instanceof ValType.RefType) {
                            visitor.visitInsn(Opcodes.DUP);
                            BytecodeHelper.debugPrintln(visitor, "IncRefCount - After call");
                            incrementRefCountOfTopElement(); // Inc ref count
                            visitor.visitInsn(Opcodes.DUP);
                            BytecodeHelper.storeLocal(visitor, nextLocalSlot, t); // Store as local for later
                            abstractStack.push(new AbstractStackElement.ValueElement(t, nextLocalSlot++));
                        } else {
                            abstractStack.push(new AbstractStackElement.ValueElement(t, -1));
                        }
                    }
                }
            } else {
                // Apply type to abstract stack.
                abstractStack.applyStackType(funcType.asStackType());
                // Wasm function is called, simply invokestatic:
                String className = Compile.getClassName(imported.moduleName);
                String methodName = Compile.getWasmExportFuncName(imported.elementName);
                visitor.visitMethodInsn(Opcodes.INVOKESTATIC, className, methodName, funcType.descriptor(), false);
                // If a wasm function returns an Object, we don't update the refcounter, but we still update the local variable.
                if (limiter.countsMemory) {
                    // TODO: Multiple returns
                    int nextLocalSlot = abstractStack.nextLocalSlot();
                    if (funcType.asStackType().outTypes().size() > 0 && funcType.asStackType().outTypes().get(0) instanceof ValType.RefType t) {
                        if (funcType.asStackType().outTypes().size() > 1)
                            throw new IllegalStateException("Multiple returns are TODO");
//                        visitor.visitInsn(Opcodes.DUP); // Explicitly left out - do not increment the refcounter in this case!
//                        incrementRefCountOfTopElement();
                        visitor.visitInsn(Opcodes.DUP);
                        BytecodeHelper.storeLocal(visitor, nextLocalSlot, t); // Store as local for later
                    }
                }
            }
        } else {
            int adjustedIndex = inst.index() - module.funcImports().size();
            FuncType funcType = module.types.get(module.functions.get(adjustedIndex));
            if (funcType.results.size() > 1) throw new UnsupportedOperationException("Multi-return functions");

            // We're calling a WASM function, so the refcount of the params doesn't change.
            // They will lose 1 reference in this function, but gain 1 in the new function frame.
            // If the function has any reference types as parameters:
            abstractStack.applyStackType(funcType.asStackType());

            // Invoke static
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, Compile.getClassName(moduleName), Compile.getFuncName(inst.index()), funcType.descriptor(), false);

            if (limiter.countsMemory) {
                // TODO: Multiple returns
                int nextLocalSlot = abstractStack.nextLocalSlot();
                for (ValType t : funcType.asStackType().outTypes()) {
                    if (funcType.asStackType().outTypes().size() > 1)
                        throw new IllegalStateException("Multiple returns are TODO");
                    if (t instanceof ValType.RefType) {
                        visitor.visitInsn(Opcodes.DUP);
                        BytecodeHelper.debugPrintln(visitor, "IncRefCount - After call");
//                        incrementRefCountOfTopElement(); // Explicitly left out - do not increment the refcounter in this case!
//                        visitor.visitInsn(Opcodes.DUP);
                        BytecodeHelper.storeLocal(visitor, nextLocalSlot, t); // Store as local for later
                        abstractStack.push(new AbstractStackElement.ValueElement(t, nextLocalSlot++));
                    } else {
                        abstractStack.push(new AbstractStackElement.ValueElement(t, -1));
                    }
                }
            }
        }
        return null;
    }

    @Override public Void visitCallIndirect(Instruction.CallIndirect inst) {
        // Manage the abstract stack and get the function type data
        abstractStack.popExpecting(ValType.i32);
        FuncType funcType = module.types.get(inst.typeIndex());
        if (funcType.results.size() > 1)
            throw new UnsupportedOperationException("Multiple returns not yet implemented");
        abstractStack.applyStackType(funcType.asStackType());
        String exactDescriptor = funcType.descriptor();

        // Stack = [index]
        // Fetch the element from the table:
        visitor.visitFieldInsn(Opcodes.GETSTATIC, getClassName(moduleName), getTableName(inst.tableIndex()), FUNCREF_TABLE_DESCRIPTOR); // [index, table]
        visitor.visitInsn(Opcodes.SWAP); // [table, index]
        visitor.visitInsn(Opcodes.AALOAD); // [func = table[index]]
        visitor.visitFieldInsn(Opcodes.GETFIELD, Type.getInternalName(FuncRefInstance.class), "handle", Type.getDescriptor(MethodHandle.class)); // [handle]
        // Now we need to put the handle below all the args of the function...
        int stackSlotSum = funcType.args.stream().map(ValType::stackSlots).reduce(0, Integer::sum);
        switch (stackSlotSum) {
            case 0 -> {} // Nothing happens
            case 1 -> visitor.visitInsn(Opcodes.SWAP);
            case 2 -> { visitor.visitInsn(Opcodes.DUP_X2); visitor.visitInsn(Opcodes.POP); }
            default -> {
                // Impose small instruction penalty
                if (limiter.countsInstructions) {
                    incrementInstructions((funcType.args.size() - 2) / 4);
                }
                // Store the handle as a local:
                visitor.visitVarInsn(Opcodes.ASTORE, abstractStack.nextLocalSlot()); // Store handle as local
                // Store excess locals:
                int paramIndex = funcType.args.size() - 1;
                int localOffset = 1;
                while (stackSlotSum > 2) {
                    BytecodeHelper.storeLocal(visitor, abstractStack.nextLocalSlot() + localOffset, funcType.args.get(paramIndex));
                    int stackSlotChange = funcType.args.get(paramIndex).stackSlots();
                    paramIndex--;
                    localOffset += stackSlotChange;
                    stackSlotSum -= stackSlotChange;
                }
                // Move the method handle down
                visitor.visitVarInsn(Opcodes.ALOAD, abstractStack.nextLocalSlot()); // Load handle from local
                switch (stackSlotSum) {
                    case 0 -> {} // Nothing happens
                    case 1 -> visitor.visitInsn(Opcodes.SWAP);
                    case 2 -> { visitor.visitInsn(Opcodes.DUP_X2); visitor.visitInsn(Opcodes.POP); }
                    default -> throw new IllegalStateException("Should be impossible");
                }
                // Load the locals again
                while (paramIndex < funcType.args.size() - 1) {
                    paramIndex++;
                    int stackSlotChange = funcType.args.get(paramIndex).stackSlots();
                    localOffset -= stackSlotChange;
                    BytecodeHelper.loadLocal(visitor, abstractStack.nextLocalSlot() + localOffset, funcType.args.get(paramIndex));
                }
            }
        }
        // Finally, invoke the method handle
        visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(MethodHandle.class), "invokeExact", exactDescriptor, false); // [result]
        return null;
    }

    // Just push null
    @Override public Void visitRefNull(Instruction.RefNull inst) {
        // Push a funcref or an externref, depending on the type
        int nextSlot = abstractStack.nextLocalSlot();
        abstractStack.push(new AbstractStackElement.ValueElement(inst.type(), nextSlot));
        visitor.visitInsn(Opcodes.ACONST_NULL);
        return null;
    }

    // Check if null, push 1 if yes, 0 if no
    @Override public Void visitRefIsNull(Instruction.RefIsNull inst) {
        // Can check nullness of a funcref OR an externref
        AbstractStackElement.ValueElement v = abstractStack.popExpecting(ValType.funcref, ValType.externref);
        abstractStack.push(new AbstractStackElement.ValueElement(ValType.i32, -1)); // Push i32 as result
        // If we're memory-counting, then decrement the ref count of this object
        if (limiter.countsMemory && v.localVariableIndex != -1) {
            BytecodeHelper.loadLocal(visitor, v.localVariableIndex, v.valueType);
            BytecodeHelper.debugPrintln(visitor, "DecRefCount - refIsNull");
            decrementRefCountOfTopElement();
        }
        BytecodeHelper.test(visitor, Opcodes.IFNULL);
        return null;
    }

    @Override public Void visitRefFunc(Instruction.RefFunc inst) {
        abstractStack.applyStackType(inst.stackType());
        Handle handle;
        int numReturns;
        if (inst.funcIndex() < module.funcImports().size()) {
            // Calling an imported function
            Import.Func imported = module.funcImports().get(inst.funcIndex());
            // Apply the stack type
            FuncType funcType = module.types.get(imported.typeIndex);
            if (funcType.results.size() > 1) throw new UnsupportedOperationException("Multi-return functions");
            // Check if it's a java function
            if (javaModules.containsKey(imported.moduleName)) {
                throw new UnsupportedOperationException("Taking references to imported java functions is not yet supported");
            } else {
                handle = new Handle(Opcodes.H_INVOKESTATIC, getClassName(imported.moduleName), imported.elementName, funcType.descriptor(), false);
                numReturns = funcType.results.size();
            }
        } else {
            // Not an imported function
            int adjustedIndex = inst.funcIndex() - module.funcImports().size();
            FuncType funcType = module.types.get(module.functions.get(adjustedIndex));
            if (funcType.results.size() > 1) throw new UnsupportedOperationException("Multi-return functions");
            // Create the handle
            handle = new Handle(Opcodes.H_INVOKESTATIC, getClassName(moduleName), getFuncName(inst.funcIndex()), funcType.descriptor(), false);
            numReturns = funcType.results.size();
        }
        // We now have the ASM "Handle" object. Time to construct a FuncRefInstance:
        visitor.visitTypeInsn(Opcodes.NEW, Type.getInternalName(FuncRefInstance.class)); // [funcref]
        visitor.visitInsn(Opcodes.DUP); // [funcref, funcref]
//        BytecodeHelper.debugPrintln(visitor, "Loading handle");
        visitor.visitLdcInsn(handle); // [funcref, funcref, MethodHandle]
//        BytecodeHelper.debugPrintln(visitor, "Loaded handle");
        BytecodeHelper.constInt(visitor, numReturns); // [funcref, funcref, MethodHandle, numReturns]
        visitor.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                Type.getInternalName(FuncRefInstance.class),
                "<init>",
                "(" + Type.getDescriptor(MethodHandle.class) + "I)V",
                false); // [funcref]
        // Increment ref count if we track memory
        if (limiter.countsMemory) {
            visitor.visitInsn(Opcodes.DUP);
            incrementRefCountOfTopElement();
        }

        return null;
    }

    @Override public Void visitDrop(Instruction.Drop inst) {
        AbstractStackElement topElem = abstractStack.pop(); // Modifies abstract stack
        if (!(topElem instanceof AbstractStackElement.ValueElement v))
            throw new IllegalStateException("Malformed WASM?");
        if (limiter.countsMemory && v.localVariableIndex != -1) {
            BytecodeHelper.debugPrintln(visitor, "DecRefCount - drop");
            decrementRefCountOfTopElement();
        } else {
            BytecodeHelper.popValue(visitor, v.valueType);
        }
        return null;
    }

    @Override public Void visitSelect(Instruction.Select inst) {
        abstractStack.popExpecting(ValType.i32); // Expect i32 on top of stack
        AbstractStackElement.ValueElement elem2 = abstractStack.popExpectingValue();
        AbstractStackElement.ValueElement elem1 = abstractStack.peekExpecting(elem2.valueType); // Expect 2 of the same type below it
        // If top of stack is not 0, pop. If it is 0, swap-pop.
        Label isZero = new Label();
        Label end = new Label();
        visitor.visitJumpInsn(Opcodes.IFEQ, isZero);
        if (limiter.countsMemory && elem2.localVariableIndex != -1) {
            BytecodeHelper.debugPrintln(visitor, "DecRefCount - select");
            decrementRefCountOfTopElement();
        } else {
            BytecodeHelper.popValue(visitor, elem2.valueType);
        }
        visitor.visitJumpInsn(Opcodes.GOTO, end);
        visitor.visitLabel(isZero);
        BytecodeHelper.swapValues(visitor, elem1.valueType);
        if (limiter.countsMemory && elem1.localVariableIndex != -1) {
            BytecodeHelper.debugPrintln(visitor, "DecRefCount - select 2");
            decrementRefCountOfTopElement();
        } else {
            BytecodeHelper.popValue(visitor, elem1.valueType);
        }
        visitor.visitLabel(end);
        return null;
    }

    @Override public Void visitSelectFrom(Instruction.SelectFrom inst) {
        throw new UnsupportedOperationException("SelectFrom instruction not yet implemented");
    }

    @Override public Void visitLocalGet(Instruction.LocalGet inst) {
        ValType type = code.locals.get(inst.localIndex());
        int mappedIndex = code.localMappings().get(inst.localIndex());
        if (limiter.countsMemory && type instanceof ValType.RefType) {
            // It's a reftype, let's track it for refcounting
            int nextSlot = abstractStack.nextLocalSlot();
            abstractStack.push(new AbstractStackElement.ValueElement(type, nextSlot));
            BytecodeHelper.loadLocal(visitor, mappedIndex, type);
            // Duplicate it and increment, then dup and store in next slot
            visitor.visitInsn(Opcodes.DUP);
            BytecodeHelper.debugPrintln(visitor, "IncRefCount - Local get");
            incrementRefCountOfTopElement();
            visitor.visitInsn(Opcodes.DUP);
            BytecodeHelper.storeLocal(visitor, nextSlot, type);
        } else {
            // Otherwise, just load the local normally
            abstractStack.push(new AbstractStackElement.ValueElement(type, -1));
            BytecodeHelper.loadLocal(visitor, mappedIndex, type);
        }
        return null;
    }

    @Override public Void visitLocalSet(Instruction.LocalSet inst) {
        ValType type = code.locals.get(inst.localIndex());
        int mappedIndex = code.localMappings().get(inst.localIndex());
        abstractStack.pop(); // Modifies abstract stack

        if (limiter.countsMemory && type instanceof ValType.RefType) {
            // The object formerly in the local should be decremented:
            BytecodeHelper.loadLocal(visitor, mappedIndex, type);
            BytecodeHelper.debugPrintln(visitor, "DecRefCount - local set");
            decrementRefCountOfTopElement();
        }

        // Now store the new object into the local:
        BytecodeHelper.storeLocal(visitor, mappedIndex, type);
        return null;
    }

    @Override public Void visitLocalTee(Instruction.LocalTee inst) {
        ValType type = code.locals.get(inst.localIndex());
        int mappedIndex = code.localMappings().get(inst.localIndex());

        if (limiter.countsMemory && type instanceof ValType.RefType) {
            // The object formerly in the local should be decremented,
            // and the new object should be incremented.
            BytecodeHelper.loadLocal(visitor, mappedIndex, type);
            BytecodeHelper.debugPrintln(visitor, "DecRefCount - local tee");
            decrementRefCountOfTopElement();
            BytecodeHelper.dupValue(visitor, type);
            BytecodeHelper.debugPrintln(visitor, "IncRefCount - Local tee");
            incrementRefCountOfTopElement();
        }

        // Dup and store normally
        BytecodeHelper.dupValue(visitor, type); // Dup before storing
        BytecodeHelper.storeLocal(visitor, mappedIndex, type);

        return null;
    }

    @Override public Void visitGlobalGet(Instruction.GlobalGet inst) {
        if (inst.globalIndex() < module.globalImports().size()) {
            throw new UnsupportedOperationException("Global imports not yet implemented");
        } else {
            int adjustedIndex = inst.globalIndex() - module.globalImports().size();
            ValType globalType = module.globals.get(adjustedIndex).globalType().valType();
            visitor.visitFieldInsn(Opcodes.GETSTATIC, Compile.getClassName(moduleName), Compile.getGlobalName(adjustedIndex), globalType.desc());
            // If tracking memory, store in local
            if (limiter.countsMemory && globalType instanceof ValType.RefType) {
                int nextLocal = abstractStack.nextLocalSlot();
                abstractStack.push(new AbstractStackElement.ValueElement(globalType, nextLocal));
                // Increment refcount and store in local
                visitor.visitInsn(Opcodes.DUP);
                BytecodeHelper.debugPrintln(visitor, "IncRefCount - global get");
                incrementRefCountOfTopElement();
                visitor.visitInsn(Opcodes.DUP);
                BytecodeHelper.storeLocal(visitor, nextLocal, globalType);
            } else {
                // Otherwise just push the element normally
                abstractStack.push(new AbstractStackElement.ValueElement(globalType, -1));
            }
        }
        return null;
    }

    @Override public Void visitGlobalSet(Instruction.GlobalSet inst) {
        if (inst.globalIndex() < module.globalImports().size()) {
            throw new UnsupportedOperationException("Global imports not yet implemented");
        } else {
            int adjustedIndex = inst.globalIndex() - module.globalImports().size();
            ValType globalType = module.globals.get(adjustedIndex).globalType().valType();
            abstractStack.pop();

            if (limiter.countsMemory && globalType instanceof ValType.RefType) {
                // The object formerly in the global should be decremented:
                visitor.visitFieldInsn(Opcodes.GETSTATIC, Compile.getClassName(moduleName), Compile.getGlobalName(adjustedIndex), globalType.desc());
                BytecodeHelper.debugPrintln(visitor, "DecRefCount - global set");
                decrementRefCountOfTopElement();
            }

            // Store the field normally
            visitor.visitFieldInsn(Opcodes.PUTSTATIC, Compile.getClassName(moduleName), Compile.getGlobalName(adjustedIndex), globalType.desc());
        }
        return null;
    }

    @Override public Void visitTableGet(Instruction.TableGet inst) {
        BytecodeHelper.debugPrintln(visitor, "Table get");

        ValType elemType;
        if (inst.tableIndex() < module.tableImports().size()) {
            elemType = module.tableImports().get(inst.tableIndex()).type.elementType();
        } else {
            elemType = module.tables.get(inst.tableIndex() - module.tableImports().size()).elementType();
        }

        abstractStack.popExpecting(ValType.i32);

        // Stack = [index]
        pushTable(inst.tableIndex(), elemType == ValType.funcref); // [index, table]
        visitor.visitInsn(Opcodes.SWAP); // [table, index]
        visitor.visitInsn(Opcodes.AALOAD); // [table[index]]

        if (limiter.countsMemory) {
            // It's always a reftype, let's track it for refcounting
            int nextSlot = abstractStack.nextLocalSlot();
            abstractStack.push(new AbstractStackElement.ValueElement(elemType, nextSlot));
            // Duplicate it and increment, then dup and store in next slot
            visitor.visitInsn(Opcodes.DUP);
            BytecodeHelper.debugPrintln(visitor, "IncRefCount - table get");
            incrementRefCountOfTopElement();
            visitor.visitInsn(Opcodes.DUP);
            BytecodeHelper.storeLocal(visitor, nextSlot, elemType);
        } else {
            // Just push normally
            abstractStack.push(new AbstractStackElement.ValueElement(elemType, -1));
        }

        return null;
    }

    @Override public Void visitTableSet(Instruction.TableSet inst) {
        BytecodeHelper.debugPrintln(visitor, "Table set");

        ValType elemType;
        if (inst.tableIndex() < module.tableImports().size()) {
            elemType = module.tableImports().get(inst.tableIndex()).type.elementType();
        } else {
            elemType = module.tables.get(inst.tableIndex() - module.tableImports().size()).elementType();
        }

        abstractStack.popExpecting(elemType);
        abstractStack.popExpecting(ValType.i32);

        // [index, elem]
        pushTable(inst.tableIndex(), elemType == ValType.funcref); // [index, elem, table]
        visitor.visitInsn(Opcodes.DUP_X2); // [table, index, elem, table]
        visitor.visitInsn(Opcodes.POP); // [table, index, elem]

        // If refcounting, decrement the count of the object previously in the table
        if (limiter.countsMemory) {
            int nextLocal = abstractStack.nextLocalSlot();
            BytecodeHelper.storeLocal(visitor, nextLocal, elemType); // [table, index], locals = [elem]
            visitor.visitInsn(Opcodes.DUP2); // [table, index, table, index], locals = [elem]
            visitor.visitInsn(Opcodes.AALOAD); // [table, index, table[index]], locals = [elem]
            BytecodeHelper.debugPrintln(visitor, "DecRefCount - table set");
            decrementRefCountOfTopElement(); // [table, index], locals = [elem]
            BytecodeHelper.loadLocal(visitor, nextLocal, elemType); // [table, index, elem]
        }

        visitor.visitInsn(Opcodes.AASTORE); // []

        return null;
    }

    @Override public Void visitTableInit(Instruction.TableInit inst) {
        throw new UnsupportedOperationException("Table.init not yet implemented");
        // TODO: Impl below doesn't deal with refcounts
//        abstractStack.applyStackType(inst.stackType());
//        // Stack = [dest, src, count]
//
//        // Verify args:
//        // Ensure count > 0:
//        visitor.visitInsn(Opcodes.DUP);
//        Label okay = new Label();
//        visitor.visitJumpInsn(Opcodes.IFGE, okay);
//        BytecodeHelper.throwRuntimeError(visitor, "Attempt to call table.init with \"count\" above i32_max. WasmJ doesn't support this!");
//        visitor.visitLabel(okay);
//
//        // Apply instruction penalty equal to "count", for the arraycopy.
//        if (limiter.countsInstructions) {
//            visitor.visitInsn(Opcodes.DUP); // [dest, src, count, count]
//            visitor.visitInsn(Opcodes.I2L); // [dest, src, count, (long) count]
//            incrementInstructionsByTopElement(); // [dest, src, count]
//        }
//
//        ValType elementType = module.tables.get(inst.tableIndex()).elementType();
//        String tableDescriptor = elementType == ValType.externref ? TABLE_DESCRIPTOR : FUNCREF_TABLE_DESCRIPTOR;
//        // Shuffle things around to get them in order for the arraycopy() call:
//        visitor.visitVarInsn(Opcodes.ISTORE, abstractStack.nextLocalSlot()); // nextLocal = count, [dest, src]
//        visitor.visitFieldInsn(Opcodes.GETSTATIC, getClassName(moduleName), getElemFieldName(inst.elemIndex()), tableDescriptor); // [dest, src, elem array]
//        visitor.visitInsn(Opcodes.DUP_X2); // [elem array, dest, src, elem array]
//        visitor.visitInsn(Opcodes.POP); // [elem array, dest, src]
//        visitor.visitInsn(Opcodes.SWAP); // [elem array, src, dest]
//        visitor.visitFieldInsn(Opcodes.GETSTATIC, getClassName(moduleName), getTableName(inst.tableIndex()), tableDescriptor); // [elem array, src, dest, table array]
//        visitor.visitInsn(Opcodes.SWAP); // [elem array, src, table array, dest]
//        visitor.visitVarInsn(Opcodes.ILOAD, abstractStack.nextLocalSlot()); // [elems, src, table, dest, count]
//        // Call arraycopy():
//        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(System.class), "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V", false);
//        // Stack is now empty.
//        return null;
    }

    @Override public Void visitElemDrop(Instruction.ElemDrop inst) {
        throw new UnsupportedOperationException("Elem.drop not yet implemented");
        // TODO: Impl below doesn't deal with refcounts
//        abstractStack.applyStackType(inst.stackType());
//        // Decrement memory counter, if needed:
//        if (limiter.countsMemory) {
//            long len = module.elements.get(inst.elemIndex()).exprs().size();
//            BytecodeHelper.constLong(visitor, len * 8L);
//            decrementMemoryByTopElement();
//        }
//        // Set the given elem field to null, freeing the memory.
//        visitor.visitInsn(Opcodes.ACONST_NULL);
//        ValType elementType = module.elements.get(inst.elemIndex()).type();
//        String tableDescriptor = elementType == ValType.externref ? TABLE_DESCRIPTOR : FUNCREF_TABLE_DESCRIPTOR;
//        visitor.visitFieldInsn(Opcodes.PUTSTATIC, getClassName(moduleName), getElemFieldName(inst.elemIndex()), tableDescriptor);
//        return null;
    }

    @Override public Void visitTableGrow(Instruction.TableGrow inst) {

        abstractStack.popExpecting(ValType.i32);
        abstractStack.popExpecting(ValType.funcref, ValType.externref);
        abstractStack.push(new AbstractStackElement.ValueElement(ValType.i32, -1));

        // [fillValue, requestedEntries]

        // If requested entries are negative, error
        visitor.visitInsn(Opcodes.DUP);
        Label okay = new Label();
        visitor.visitJumpInsn(Opcodes.IFGE, okay);
        BytecodeHelper.throwRuntimeError(visitor, "Attempt to call table.grow with value above i32_max. WasmJ doesn't support this!");
        visitor.visitLabel(okay);

        ValType elementType = module.tables.get(inst.tableIndex()).elementType();
        String tableDescriptor = elementType == ValType.externref ? TABLE_DESCRIPTOR : FUNCREF_TABLE_DESCRIPTOR;

        // If counting instructions, then increment instruction counter by
        // currentSize + requestedEntries. This is to pay for the arraycopy + fill.
        if (limiter.countsInstructions) {
            // [fillValue, requestedEntries]
            visitor.visitInsn(Opcodes.DUP); // [fillValue, requestedEntries, requestedEntries]
            pushTable(inst.tableIndex(), elementType == ValType.funcref); // [fillValue, requestedEntries, requestedEntries, table]
            visitor.visitInsn(Opcodes.ARRAYLENGTH); // [fillValue, requestedEntries, requestedEntries, table.length]
            visitor.visitInsn(Opcodes.IADD); // [fillValue, requestedEntries, requestedEntries + table.length]
            visitor.visitInsn(Opcodes.I2L); // [fillValue, requestedEntries, (long) (requestedEntries + table.length)]
            incrementInstructionsByTopElement(); // [fillValue, requestedEntries]
        }
        // If counting memory, increment memory usage by requestedEntries * 8.
        if (limiter.countsMemory) {
            // [fillValue, requestedEntries]
            visitor.visitInsn(Opcodes.DUP); // [fillValue, requestedEntries, requestedEntries]
            visitor.visitInsn(Opcodes.I2L); // [fillValue, requestedEntries, (long) requestedEntries]
            BytecodeHelper.constLong(visitor, 8L); // [fillValue, requestedEntries, (long) requestedEntries, 8L]
            visitor.visitInsn(Opcodes.LMUL); // fillValue, requestedEntries, (long) requestedEntries * 8]
            incrementMemoryByTopElement();
        }

        // Stack = [fillValue, requestedEntries]
        pushTable(inst.tableIndex(), elementType == ValType.funcref); // [fillValue, requestedEntries, table]
        visitor.visitInsn(Opcodes.DUP); // [fillValue, requestedEntries, table, table]
        visitor.visitInsn(Opcodes.ARRAYLENGTH); // [fillValue, requestedEntries, table, table.length]
        BytecodeHelper.storeLocal(visitor, abstractStack.nextLocalSlot(), ValType.i32); // [fillValue, requestedEntries, table]. Locals = [table.length]
        visitor.visitInsn(Opcodes.SWAP); // [fillValue, table, requestedEntries]
        BytecodeHelper.loadLocal(visitor, abstractStack.nextLocalSlot(), ValType.i32); // [fillValue, table, requestedEntries, table.length]
        visitor.visitInsn(Opcodes.IADD); // [fillValue, table, newTableLength]
        visitor.visitInsn(Opcodes.DUP); // [fillValue, table, newTableLength, newTableLength]
        BytecodeHelper.storeLocal(visitor, abstractStack.nextLocalSlot() + 1, ValType.i32); // [fillValue, table, newTableLength], locals = [table.length, newTableLength]
        //TODO: Check for overflow, or mem usage too high, and output -1
        visitor.visitTypeInsn(Opcodes.ANEWARRAY, tableDescriptor.substring(2, tableDescriptor.length() - 1)); // [fillValue, table, newTable]
        BytecodeHelper.constInt(visitor, 0); // [fillValue, table, newTable, 0]
        visitor.visitInsn(Opcodes.SWAP); // [fillValue, table, 0, newTable]
        visitor.visitInsn(Opcodes.DUP_X2); // [fillValue, newTable, table, 0, newTable]
        BytecodeHelper.constInt(visitor, 0); // [fillValue, newTable, table, 0, newTable, 0]
        BytecodeHelper.loadLocal(visitor, abstractStack.nextLocalSlot(), ValType.i32); // [fillValue, newTable, table, 0, newTable, 0, table.length]
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(System.class), "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V", false); // [fillValue, newTable]
        visitor.visitInsn(Opcodes.DUP_X1); // [newTable, fillValue, newTable]
        storeTable(inst.tableIndex(), elementType == ValType.funcref); // [newTable, fillValue]
        BytecodeHelper.loadLocal(visitor, abstractStack.nextLocalSlot(), ValType.i32); // [newTable, fillValue, table.length]
        visitor.visitInsn(Opcodes.SWAP); // [newTable, table.length, fillValue]
        BytecodeHelper.loadLocal(visitor, abstractStack.nextLocalSlot() + 1, ValType.i32); // [newTable, table.length, fillValue, newTableLength]
        visitor.visitInsn(Opcodes.SWAP); // [newTable, table.length, newTableLength, fillValue]
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(Arrays.class), "fill", "([Ljava/lang/Object;IILjava/lang/Object;)V", false); // []
        BytecodeHelper.loadLocal(visitor, abstractStack.nextLocalSlot(), ValType.i32); // [table.length]
        return null;
    }

    @Override public Void visitTableSize(Instruction.TableSize inst) {
        abstractStack.applyStackType(inst.stackType());

        ValType elementType = module.tables.get(inst.tableIndex()).elementType();
        pushTable(inst.tableIndex(), elementType == ValType.funcref); // [table]
        visitor.visitInsn(Opcodes.ARRAYLENGTH); // [table.length]
        return null;
    }

    @Override public Void visitTableCopy(Instruction.TableCopy inst) {
        throw new UnsupportedOperationException("Table.copy not yet implemented");
    }

    @Override public Void visitTableFill(Instruction.TableFill inst) {
        throw new UnsupportedOperationException("Table.fill not yet implemented");
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
            pushMemory(memIndex); // [index + offset, memArray]
            visitor.visitInsn(Opcodes.SWAP); // [memArray, index + offset]
            visitor.visitInsn(Opcodes.BALOAD); // [memArray[index + offset]]
        } else {
            // VarHandle version
            // [index], locals = []
            BytecodeHelper.constInt(visitor, offset); // [index, offset], locals = []
            visitor.visitInsn(Opcodes.IADD); // [index + offset], locals = []
            BytecodeHelper.storeLocal(visitor, abstractStack.nextLocalSlot(), ValType.i32); // [], locals = [index + offset]
            visitor.visitFieldInsn(Opcodes.GETSTATIC, className, Compile.getMemoryVarHandleName(typeDesc), Type.getDescriptor(VarHandle.class)); // [VarHandle], locals = [index + offset]
            pushMemory(memIndex); // [VarHandle, memArray], locals = [index + offset]
            BytecodeHelper.loadLocal(visitor, abstractStack.nextLocalSlot(), ValType.i32); // [VarHandle, memArray, index + offset]
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
            pushMemory(memIndex); // [index + offset, value, memArray]
            visitor.visitInsn(Opcodes.DUP_X2); // [memArray, index + offset, value, memArray]
            visitor.visitInsn(Opcodes.POP); // [memArray, index + offset, value]
            visitor.visitInsn(Opcodes.BASTORE); // []. memArray[index + offset] is now value.
        } else {
            // VarHandle version
            // [index, value], locals = []
            BytecodeHelper.storeLocal(visitor, abstractStack.nextLocalSlot(), wasmType); // [index], locals = [value]
            BytecodeHelper.constInt(visitor, offset); // [index, offset], locals = [value]
            visitor.visitInsn(Opcodes.IADD); // [index + offset], locals = [value]
            BytecodeHelper.storeLocal(visitor, abstractStack.nextLocalSlot() + wasmType.stackSlots(), ValType.i32); // [], locals = [value, index + offset]
            visitor.visitFieldInsn(Opcodes.GETSTATIC, className, Compile.getMemoryVarHandleName(typeDesc), Type.getDescriptor(VarHandle.class)); // [VarHandle], locals = [value, index + offset]
            pushMemory(memIndex); // [VarHandle, memArray], locals = [value, index + offset]
            BytecodeHelper.loadLocal(visitor, abstractStack.nextLocalSlot() + wasmType.stackSlots(), ValType.i32); // [VarHandle, memArray, index + offset], locals = [value]
            BytecodeHelper.loadLocal(visitor, abstractStack.nextLocalSlot(), wasmType); // [VarHandle, memArray, index + offset, value], locals = []
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
        pushMemory(0);
        visitor.visitInsn(Opcodes.ARRAYLENGTH);
        BytecodeHelper.constInt(visitor, Compile.WASM_PAGE_SIZE);
        visitor.visitInsn(Opcodes.IDIV);
        return null;
    }
    @Override public Void visitMemoryGrow(Instruction.MemoryGrow inst) {
        BytecodeHelper.debugPrintln(visitor, "memory.grow was called");
        abstractStack.applyStackType(inst.stackType());
        // Stack = [requestedPages]

        // If requested pages are negative, error
        visitor.visitInsn(Opcodes.DUP);
        Label okay = new Label();
        visitor.visitJumpInsn(Opcodes.IFGE, okay);
        BytecodeHelper.throwRuntimeError(visitor, "Attempt to call memory.grow with value above i32_max. WasmJ doesn't support this!");
        visitor.visitLabel(okay);

        // If counting instructions, then increment instruction counter by
        // currentSize.length / 8 (arbitrary). This is to pay for the arraycopy.
        if (limiter.countsInstructions) {
            pushMemory(0);
            visitor.visitInsn(Opcodes.ARRAYLENGTH);
            visitor.visitInsn(Opcodes.I2L);
            BytecodeHelper.constLong(visitor, 8L);
            visitor.visitInsn(Opcodes.LDIV);
            incrementInstructionsByTopElement();
        }
        // If counting memory, increment memory counter by the requested pages * PAGE_SIZE.
        if (limiter.countsMemory) {
            visitor.visitInsn(Opcodes.DUP); // [requestedPages, requestedPages]
            visitor.visitInsn(Opcodes.I2L); // [requestedPages, (long) requestedPages]
            BytecodeHelper.constLong(visitor, Compile.WASM_PAGE_SIZE); // [requestedPages, (long) requestedPages, (long) PAGE_SIZE]
            visitor.visitInsn(Opcodes.LMUL); // [requestedPages, (long) requestedPages * (long) PAGE_SIZE]
            incrementMemoryByTopElement(); // [requestedPages]
        }

        // Stack = [requestedPages]
        BytecodeHelper.constInt(visitor, Compile.WASM_PAGE_SIZE); // [requestedPages, pageSize]
        visitor.visitInsn(Opcodes.IMUL); // [requestedPages * pageSize]
        pushMemory(0); // [requestedPages * pageSize, memArray]
        visitor.visitInsn(Opcodes.DUP); // [requestedPages * pageSize, memArray, memArray]
        visitor.visitInsn(Opcodes.ARRAYLENGTH); // [requestedPages * pageSize, memArray, memArray.length]
        BytecodeHelper.storeLocal(visitor, abstractStack.nextLocalSlot(), ValType.i32); // [requestedPages * pageSize, memArray]. Locals = [memArray.length]
        visitor.visitInsn(Opcodes.SWAP); // [memArray, requestedPages * pageSize]
        BytecodeHelper.loadLocal(visitor, abstractStack.nextLocalSlot(), ValType.i32); // [memArray, requestedPages * pageSize, memArray.length]
        visitor.visitInsn(Opcodes.IADD); // [memArray, requestedPages * pageSize + memArray.length]
        //TODO: Check for overflow, or mem usage too high, and output -1
        visitor.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_BYTE); // [memArray, newArray]
        BytecodeHelper.constInt(visitor, 0); // [memArray, newArray, 0]
        visitor.visitInsn(Opcodes.SWAP); // [memArray, 0, newArray]
        visitor.visitInsn(Opcodes.DUP_X2); // [newArray, memArray, 0, newArray]
        BytecodeHelper.constInt(visitor, 0); // [newArray, memArray, 0, newArray, 0]
        BytecodeHelper.loadLocal(visitor, abstractStack.nextLocalSlot(), ValType.i32); // [newArray, memArray, 0, newArray, 0, memArray.length]
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(System.class), "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V", false); // [newArray]
        storeMemory(0); // []
        BytecodeHelper.loadLocal(visitor, abstractStack.nextLocalSlot(), ValType.i32); // [memArray.length]
        BytecodeHelper.constInt(visitor, Compile.WASM_PAGE_SIZE); // [memArray.length, PAGE_SIZE]
        visitor.visitInsn(Opcodes.IDIV); // [memArray.length / PAGE_SIZE]
        return null;
    }
    @Override public Void visitMemoryInit(Instruction.MemoryInit inst) {
        abstractStack.applyStackType(inst.stackType());
        // Stack = [dest, src, count]

        // Verify args:
        // Ensure count > 0:
        visitor.visitInsn(Opcodes.DUP);
        Label okay = new Label();
        visitor.visitJumpInsn(Opcodes.IFGE, okay);
        BytecodeHelper.throwRuntimeError(visitor, "Attempt to call memory.init with \"count\" above i32_max. WasmJ doesn't support this!");
        visitor.visitLabel(okay);

        // Apply instruction penalty equal to "count / 8", for the arraycopy.
        if (limiter.countsInstructions) {
            visitor.visitInsn(Opcodes.DUP); // [dest, src, count, count]
            BytecodeHelper.constInt(visitor, 8); // [dest, src, count, count, 8]
            visitor.visitInsn(Opcodes.IDIV); // [dest, src, count, count / 8]
            visitor.visitInsn(Opcodes.I2L);
            incrementInstructionsByTopElement(); // [dest, src, count]
        }

        // Shuffle things around to get them in order for the arraycopy() call:
        visitor.visitVarInsn(Opcodes.ISTORE, abstractStack.nextLocalSlot()); // nextLocal = count, [dest, src]
        visitor.visitFieldInsn(Opcodes.GETSTATIC, getClassName(moduleName), getDataFieldName(inst.dataIndex()), "[B"); // [dest, src, data array]
        visitor.visitInsn(Opcodes.DUP_X2); // [data array, dest, src, data array]
        visitor.visitInsn(Opcodes.POP); // [data array, dest, src]
        visitor.visitInsn(Opcodes.SWAP); // [data array, src, dest]
        pushMemory(0); // [data array, src, dest, mem array]
        visitor.visitInsn(Opcodes.SWAP); // [data array, src, mem array, dest]
        visitor.visitVarInsn(Opcodes.ILOAD, abstractStack.nextLocalSlot()); // [data, src, mem, dest, count]
        // Call arraycopy():
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(System.class), "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V", false);
        // Stack is now empty.
        return null;
    }
    @Override public Void visitDataDrop(Instruction.DataDrop inst) {
        abstractStack.applyStackType(inst.stackType());
        // Decrement memory counter, if needed:
        if (limiter.countsMemory) {
            int len = module.datas.get(inst.dataIndex()).init.length;
            BytecodeHelper.constLong(visitor, len);
            decrementMemoryByTopElement();
        }
        // Set the given data field to null, freeing the memory.
        visitor.visitInsn(Opcodes.ACONST_NULL);
        visitor.visitFieldInsn(Opcodes.PUTSTATIC, getClassName(moduleName), getDataFieldName(inst.dataIndex()), "[B");
        return null;
    }
    @Override public Void visitMemoryCopy(Instruction.MemoryCopy inst) {
        // Stack = [dest, src, count]
        abstractStack.applyStackType(inst.stackType());

        // If count is negative, error
        visitor.visitInsn(Opcodes.DUP);
        Label okay = new Label();
        visitor.visitJumpInsn(Opcodes.IFGE, okay);
        BytecodeHelper.throwRuntimeError(visitor, "Attempt to call memory.copy with \"count\" above i32_max. WasmJ doesn't support this!");
        visitor.visitLabel(okay);

        // Apply instruction penalty equal to "count / 8", for the arraycopy.
        if (limiter.countsInstructions) {
            visitor.visitInsn(Opcodes.DUP); // [dest, src, count, count]
            BytecodeHelper.constInt(visitor, 8); // [dest, src, count, count, 8]
            visitor.visitInsn(Opcodes.IDIV); // [dest, src, count, count / 8]
            visitor.visitInsn(Opcodes.I2L);
            incrementInstructionsByTopElement(); // [dest, src, count]
        }

        // Shuffle things around to get them in order for the arraycopy() call:
        visitor.visitVarInsn(Opcodes.ISTORE, abstractStack.nextLocalSlot()); // nextLocal = count, [dest, src]
        visitor.visitInsn(Opcodes.SWAP); // [src, dest]
        pushMemory(0); // [src, dest, array]
        visitor.visitInsn(Opcodes.DUP_X2); // [array, src, dest, array]
        visitor.visitInsn(Opcodes.SWAP); // [array, src, array, dest]
        visitor.visitVarInsn(Opcodes.ILOAD, abstractStack.nextLocalSlot()); // [array, src, array, dest, count]
        // Call arraycopy():
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(System.class), "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V", false);
        // Stack is now empty.
        return null;
    }
    @Override public Void visitMemoryFill(Instruction.MemoryFill inst) {
        // Stack = [dest, value, count] // all i32s
        abstractStack.applyStackType(inst.stackType());

        // If count is negative, error
        visitor.visitInsn(Opcodes.DUP);
        Label okay = new Label();
        visitor.visitJumpInsn(Opcodes.IFGE, okay);
        BytecodeHelper.throwRuntimeError(visitor, "Attempt to call memory.fill with \"count\" above i32_max. WasmJ doesn't support this!");
        visitor.visitLabel(okay);

        // Apply instruction penalty equal to "count / 8", for the array fill.
        if (limiter.countsInstructions) {
            visitor.visitInsn(Opcodes.DUP); // [dest, src, count, count]
            BytecodeHelper.constInt(visitor, 8); // [dest, src, count, count, 8]
            visitor.visitInsn(Opcodes.IDIV); // [dest, src, count, count / 8]
            visitor.visitInsn(Opcodes.I2L);
            incrementInstructionsByTopElement(); // [dest, src, count]
        }

        // Get args set up for an "Arrays.fill(arr, from, to, val)" call:
        visitor.visitVarInsn(Opcodes.ISTORE, abstractStack.nextLocalSlot()); // nextLocal = count, [dest, value]
        visitor.visitVarInsn(Opcodes.ISTORE, abstractStack.nextLocalSlot() + 1); // nextLocal = count, nextLocal+1 = value, [dest]
        pushMemory(0); // [dest, arr]
        visitor.visitInsn(Opcodes.SWAP); // [arr, dest]
        visitor.visitInsn(Opcodes.DUP); // [arr, dest, dest]
        visitor.visitVarInsn(Opcodes.ILOAD, abstractStack.nextLocalSlot()); // [arr, dest, dest, count]
        visitor.visitInsn(Opcodes.IADD); // [arr, dest, dest + count]
        visitor.visitVarInsn(Opcodes.ILOAD, abstractStack.nextLocalSlot() + 1); // [arr, dest, dest + count, value]
        // Call fill():
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(Arrays.class), "fill", "([BIIB)V", false); // []
        // Stack now empty
        return null;
    }

    @Override public Void visitI32Const(Instruction.I32Const inst) {
        abstractStack.applyStackType(inst.stackType());
        BytecodeHelper.constInt(visitor, inst.n());
//        BytecodeHelper.debugPrint(visitor, "Const int: ");
//        BytecodeHelper.debugPrintInt(visitor);
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
        abstractStack.applyStackType(inst.stackType());
        visitor.visitInsn(Opcodes.F2D);
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "rint", "(F)F", false);
        visitor.visitInsn(Opcodes.D2F);
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
        abstractStack.applyStackType(inst.stackType());
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "rint", "(D)D", false);
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
        visitor.visitInsn(Opcodes.L2I);
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
        visitor.visitInsn(Opcodes.L2I);
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
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitV128Load8x8S(Instruction.V128Load8x8S inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitV128Load8x8U(Instruction.V128Load8x8U inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitV128Load16x4S(Instruction.V128Load16x4S inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitV128Load16x4U(Instruction.V128Load16x4U inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitV128Load32x2S(Instruction.V128Load32x2S inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitV128Load32x2U(Instruction.V128Load32x2U inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitV128Load8Splat(Instruction.V128Load8Splat inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitV128Load16Splat(Instruction.V128Load16Splat inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitV128Load32Splat(Instruction.V128Load32Splat inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitV128Load64Splat(Instruction.V128Load64Splat inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitV128Load32Zero(Instruction.V128Load32Zero inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitV128Load64Zero(Instruction.V128Load64Zero inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitV128Store(Instruction.V128Store inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitV128Load8Lane(Instruction.V128Load8Lane inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitV128Load16Lane(Instruction.V128Load16Lane inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitV128Load32Lane(Instruction.V128Load32Lane inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitV128Load64Lane(Instruction.V128Load64Lane inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitV128Store8Lane(Instruction.V128Store8Lane inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitV128Store16Lane(Instruction.V128Store16Lane inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitV128Store32Lane(Instruction.V128Store32Lane inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitV128Store64Lane(Instruction.V128Store64Lane inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitV128Const(Instruction.V128Const inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI8x16Shuffle(Instruction.I8x16Shuffle inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI8x16ExtractLaneS(Instruction.I8x16ExtractLaneS inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI8x16ExtractLaneU(Instruction.I8x16ExtractLaneU inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI8x16ReplaceLane(Instruction.I8x16ReplaceLane inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI16x8ExtractLaneS(Instruction.I16x8ExtractLaneS inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI16x8ExtractLaneU(Instruction.I16x8ExtractLaneU inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI16x8ReplaceLane(Instruction.I16x8ReplaceLane inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI32x4ExtractLane(Instruction.I32x4ExtractLane inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI32x4ReplaceLane(Instruction.I32x4ReplaceLane inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI64x2ExtractLane(Instruction.I64x2ExtractLane inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI64x2ReplaceLane(Instruction.I64x2ReplaceLane inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF32x4ExtractLane(Instruction.F32x4ExtractLane inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF32x4ReplaceLane(Instruction.F32x4ReplaceLane inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF64x2ExtractLane(Instruction.F64x2ExtractLane inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF64x2ReplaceLane(Instruction.F64x2ReplaceLane inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI8x16Swizzle(Instruction.I8x16Swizzle inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI8x16Splat(Instruction.I8x16Splat inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI16x8Splat(Instruction.I16x8Splat inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI32x4Splat(Instruction.I32x4Splat inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI64x2Splat(Instruction.I64x2Splat inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF32x4Splat(Instruction.F32x4Splat inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF64x2Splat(Instruction.F64x2Splat inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI8x16Eq(Instruction.I8x16Eq inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI8x16Ne(Instruction.I8x16Ne inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI8x16LtS(Instruction.I8x16LtS inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI8x16LtU(Instruction.I8x16LtU inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI8x16GtS(Instruction.I8x16GtS inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI8x16GtU(Instruction.I8x16GtU inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI8x16LeS(Instruction.I8x16LeS inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI8x16LeU(Instruction.I8x16LeU inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI8x16GeS(Instruction.I8x16GeS inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI8x16GeU(Instruction.I8x16GeU inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI16x8Eq(Instruction.I16x8Eq inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI16x8Ne(Instruction.I16x8Ne inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI16x8LtS(Instruction.I16x8LtS inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI16x8LtU(Instruction.I16x8LtU inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI16x8GtS(Instruction.I16x8GtS inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI16x8GtU(Instruction.I16x8GtU inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI16x8LeS(Instruction.I16x8LeS inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI16x8LeU(Instruction.I16x8LeU inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI16x8GeS(Instruction.I16x8GeS inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI16x8GeU(Instruction.I16x8GeU inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI32x4Eq(Instruction.I32x4Eq inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI32x4Ne(Instruction.I32x4Ne inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI32x4LtS(Instruction.I32x4LtS inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI32x4LtU(Instruction.I32x4LtU inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI32x4GtS(Instruction.I32x4GtS inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI32x4GtU(Instruction.I32x4GtU inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI32x4LeS(Instruction.I32x4LeS inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI32x4LeU(Instruction.I32x4LeU inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI32x4GeS(Instruction.I32x4GeS inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI32x4GeU(Instruction.I32x4GeU inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI64x2Eq(Instruction.I64x2Eq inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI64x2Ne(Instruction.I64x2Ne inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI64x2LtS(Instruction.I64x2LtS inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI64x2GtS(Instruction.I64x2GtS inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI64x2LeS(Instruction.I64x2LeS inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI64x2GeS(Instruction.I64x2GeS inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF32x4Eq(Instruction.F32x4Eq inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF32x4Ne(Instruction.F32x4Ne inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF32x4Lt(Instruction.F32x4Lt inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF32x4Gt(Instruction.F32x4Gt inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF32x4Le(Instruction.F32x4Le inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF32x4Ge(Instruction.F32x4Ge inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF64x2Eq(Instruction.F64x2Eq inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF64x2Ne(Instruction.F64x2Ne inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF64x2Lt(Instruction.F64x2Lt inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF64x2Gt(Instruction.F64x2Gt inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF64x2Le(Instruction.F64x2Le inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF64x2Ge(Instruction.F64x2Ge inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitV128Not(Instruction.V128Not inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitV128And(Instruction.V128And inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitV128AndNot(Instruction.V128AndNot inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitV128Or(Instruction.V128Or inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitV128Xor(Instruction.V128Xor inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitV128Bitselect(Instruction.V128Bitselect inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitV128AnyTrue(Instruction.V128AnyTrue inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI8x16Abs(Instruction.I8x16Abs inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI8x16Neg(Instruction.I8x16Neg inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI8x16PopCnt(Instruction.I8x16PopCnt inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI8x16AllTrue(Instruction.I8x16AllTrue inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI8x16Bitmask(Instruction.I8x16Bitmask inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI8x16NarrowI16x8S(Instruction.I8x16NarrowI16x8S inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI8x16NarrowI16x8U(Instruction.I8x16NarrowI16x8U inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI8x16Shl(Instruction.I8x16Shl inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI8x16ShrS(Instruction.I8x16ShrS inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI8x16ShrU(Instruction.I8x16ShrU inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI8x16Add(Instruction.I8x16Add inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI8x16AddSatS(Instruction.I8x16AddSatS inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI8x16AddSatU(Instruction.I8x16AddSatU inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI8x16Sub(Instruction.I8x16Sub inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI8x16SubSatS(Instruction.I8x16SubSatS inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI8x16SubSatU(Instruction.I8x16SubSatU inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI8x16MinS(Instruction.I8x16MinS inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI8x16MinU(Instruction.I8x16MinU inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI8x16MaxS(Instruction.I8x16MaxS inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI8x16MaxU(Instruction.I8x16MaxU inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI8x16AvgrU(Instruction.I8x16AvgrU inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI16x8ExtAddPairwiseI8x16S(Instruction.I16x8ExtAddPairwiseI8x16S inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI16x8ExtAddPairwiseI8x16U(Instruction.I16x8ExtAddPairwiseI8x16U inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI16x8Abs(Instruction.I16x8Abs inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI16x8Neg(Instruction.I16x8Neg inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI16x8Q15MulrSatS(Instruction.I16x8Q15MulrSatS inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI16x8AllTrue(Instruction.I16x8AllTrue inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI16x8Bitmask(Instruction.I16x8Bitmask inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI16x8NarrowI32x4S(Instruction.I16x8NarrowI32x4S inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI16x8NarrowI32x4U(Instruction.I16x8NarrowI32x4U inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI16x8ExtendLowI8x16S(Instruction.I16x8ExtendLowI8x16S inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI16x8ExtendHighI8x16S(Instruction.I16x8ExtendHighI8x16S inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI16x8ExtendLowI8x16U(Instruction.I16x8ExtendLowI8x16U inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI16x8ExtendHighI8x16U(Instruction.I16x8ExtendHighI8x16U inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI16x8Shl(Instruction.I16x8Shl inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI16x8ShrS(Instruction.I16x8ShrS inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI16x8ShrU(Instruction.I16x8ShrU inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI16x8Add(Instruction.I16x8Add inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI16x8AddSatS(Instruction.I16x8AddSatS inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI16x8AddSatU(Instruction.I16x8AddSatU inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI16x8Sub(Instruction.I16x8Sub inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI16x8SubSatS(Instruction.I16x8SubSatS inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI16x8SubSatU(Instruction.I16x8SubSatU inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI16x8Mul(Instruction.I16x8Mul inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI16x8MinS(Instruction.I16x8MinS inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI16x8MinU(Instruction.I16x8MinU inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI16x8MaxS(Instruction.I16x8MaxS inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI16x8MaxU(Instruction.I16x8MaxU inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI16x8AvgrU(Instruction.I16x8AvgrU inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI16x8ExtMulLowI8x16S(Instruction.I16x8ExtMulLowI8x16S inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI16x8ExtMulHighI8x16S(Instruction.I16x8ExtMulHighI8x16S inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI16x8ExtMulLowI8x16U(Instruction.I16x8ExtMulLowI8x16U inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI16x8ExtMulHighI8x16U(Instruction.I16x8ExtMulHighI8x16U inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI32x4ExtAddPairwiseI16x8S(Instruction.I32x4ExtAddPairwiseI16x8S inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI32x4ExtAddPairwiseI16x8U(Instruction.I32x4ExtAddPairwiseI16x8U inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI32x4Abs(Instruction.I32x4Abs inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI32x4Neg(Instruction.I32x4Neg inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI32x4AllTrue(Instruction.I32x4AllTrue inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI32x4Bitmask(Instruction.I32x4Bitmask inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI32x4ExtendLowI16x8S(Instruction.I32x4ExtendLowI16x8S inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI32x4ExtendHighI16x8S(Instruction.I32x4ExtendHighI16x8S inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI32x4ExtendLowI16x8U(Instruction.I32x4ExtendLowI16x8U inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI32x4ExtendHighI16x8U(Instruction.I32x4ExtendHighI16x8U inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI32x4Shl(Instruction.I32x4Shl inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI32x4ShrS(Instruction.I32x4ShrS inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI32x4ShrU(Instruction.I32x4ShrU inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI32x4Add(Instruction.I32x4Add inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI32x4Sub(Instruction.I32x4Sub inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI32x4Mul(Instruction.I32x4Mul inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI32x4MinS(Instruction.I32x4MinS inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI32x4MinU(Instruction.I32x4MinU inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI32x4MaxS(Instruction.I32x4MaxS inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI32x4MaxU(Instruction.I32x4MaxU inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI32x4DotI16x8S(Instruction.I32x4DotI16x8S inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI32x4ExtMulLowI16x8S(Instruction.I32x4ExtMulLowI16x8S inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI32x4ExtMulHighI16x8S(Instruction.I32x4ExtMulHighI16x8S inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI32x4ExtMulLowI16x8U(Instruction.I32x4ExtMulLowI16x8U inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI32x4ExtMulHighI16x8U(Instruction.I32x4ExtMulHighI16x8U inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI64x2Abs(Instruction.I64x2Abs inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI64x2Neg(Instruction.I64x2Neg inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI64x2AllTrue(Instruction.I64x2AllTrue inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI64x2Bitmask(Instruction.I64x2Bitmask inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI64x2ExtendLowI32x4S(Instruction.I64x2ExtendLowI32x4S inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI64x2ExtendHighI32x4S(Instruction.I64x2ExtendHighI32x4S inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI64x2ExtendLowI32x4U(Instruction.I64x2ExtendLowI32x4U inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI64x2ExtendHighI32x4U(Instruction.I64x2ExtendHighI32x4U inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI64x2Shl(Instruction.I64x2Shl inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI64x2ShrS(Instruction.I64x2ShrS inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI64x2ShrU(Instruction.I64x2ShrU inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI64x2Add(Instruction.I64x2Add inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI64x2Sub(Instruction.I64x2Sub inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI64x2Mul(Instruction.I64x2Mul inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI64x2ExtMulLowI32x4S(Instruction.I64x2ExtMulLowI32x4S inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI64x2ExtMulHighI32x4S(Instruction.I64x2ExtMulHighI32x4S inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI64x2ExtMulLowI32x4U(Instruction.I64x2ExtMulLowI32x4U inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI64x2ExtMulHighI32x4U(Instruction.I64x2ExtMulHighI32x4U inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF32x4Ceil(Instruction.F32x4Ceil inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF32x4Floor(Instruction.F32x4Floor inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF32x4Trunc(Instruction.F32x4Trunc inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF32x4Nearest(Instruction.F32x4Nearest inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF32x4Abs(Instruction.F32x4Abs inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF32x4Neg(Instruction.F32x4Neg inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF32x4Sqrt(Instruction.F32x4Sqrt inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF32x4Add(Instruction.F32x4Add inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF32x4Sub(Instruction.F32x4Sub inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF32x4Mul(Instruction.F32x4Mul inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF32x4Div(Instruction.F32x4Div inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF32x4Min(Instruction.F32x4Min inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF32x4Max(Instruction.F32x4Max inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF32x4PMin(Instruction.F32x4PMin inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF32x4PMax(Instruction.F32x4PMax inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF64x2Ceil(Instruction.F64x2Ceil inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF64x2Floor(Instruction.F64x2Floor inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF64x2Trunc(Instruction.F64x2Trunc inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF64x2Nearest(Instruction.F64x2Nearest inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF64x2Abs(Instruction.F64x2Abs inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF64x2Neg(Instruction.F64x2Neg inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF64x2Sqrt(Instruction.F64x2Sqrt inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF64x2Add(Instruction.F64x2Add inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF64x2Sub(Instruction.F64x2Sub inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF64x2Mul(Instruction.F64x2Mul inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF64x2Div(Instruction.F64x2Div inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF64x2Min(Instruction.F64x2Min inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF64x2Max(Instruction.F64x2Max inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF64x2PMin(Instruction.F64x2PMin inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF64x2PMax(Instruction.F64x2PMax inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI32x4TruncSatF32x4S(Instruction.I32x4TruncSatF32x4S inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI32x4TruncSatF32x4U(Instruction.I32x4TruncSatF32x4U inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF32x4ConvertI32x4S(Instruction.F32x4ConvertI32x4S inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF32x4ConvertI32x4U(Instruction.F32x4ConvertI32x4U inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI32x4TruncSatF64x2SZero(Instruction.I32x4TruncSatF64x2SZero inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitI32x4TruncSatF64x2UZero(Instruction.I32x4TruncSatF64x2UZero inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF64x2ConvertLowI32x4S(Instruction.F64x2ConvertLowI32x4S inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF64x2ConvertLowI32x4U(Instruction.F64x2ConvertLowI32x4U inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF32x4DemoteF64x2Zero(Instruction.F32x4DemoteF64x2Zero inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

    @Override public Void visitF64x2PromoteLowF32x4(Instruction.F64x2PromoteLowF32x4 inst) {
        throw new UnsupportedOperationException("WasmJ does not implement SIMD operations! (yet?)");
    }

}