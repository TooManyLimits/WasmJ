package io.github.toomanylimits.wasmj.compiling.simplify;

import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleInstruction;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.memory.*;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.misc.RefFunc;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.misc.RefIsNull;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.table.TableGet;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.table.TableGrow;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.table.TableSet;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.table.TableSize;
import io.github.toomanylimits.wasmj.compiling.visitor.InstructionVisitor;
import io.github.toomanylimits.wasmj.compiling.helpers.BytecodeHelper;
import io.github.toomanylimits.wasmj.parsing.instruction.Instruction;
import io.github.toomanylimits.wasmj.parsing.instruction.StackType;
import io.github.toomanylimits.wasmj.parsing.module.WasmModule;
import io.github.toomanylimits.wasmj.parsing.types.GlobalType;
import io.github.toomanylimits.wasmj.parsing.types.ValType;
import io.github.toomanylimits.wasmj.util.ListUtils;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Validates instructions, while also converting them into simplified instructions
 * to make future code simpler.
 */
public class InstructionConversionVisitor extends InstructionVisitor<SimpleInstruction, Validator.ValidationException> {

    // Keep a validator. This is updated while we go, for 2 reasons:
    // - Ensuring the wasm code is well-formed (of course)
    // - Knowing what types are on the stack is required for various parametric operations, like
    //   "drop". It drops 1 value, but in the JVM, how will it know whether to emit POP or POP2?
    public final Validator validator = new Validator();

    // Get these values from the constructor.
    private final WasmModule wasmModule;
    private final List<ValType> localVarTypes; // The types of the local variables
    private final List<ValType> returnTypes; // The types to be returned. If null, returning is illegal.

    // localVarIndexMap[i] = the jvm defaultIndex of the i'th local variable. On the JVM, some variables take 2 slots.
    private final List<Integer> localVarIndexMap;
    public final int nextLocalSlot;
    public InstructionConversionVisitor(WasmModule wasmModule, List<ValType> localVars, List<ValType> returnTypes) {
        this.wasmModule = wasmModule;
        this.localVarTypes = localVars;
        this.returnTypes = returnTypes;

        // Compute local var defaultIndex map and next local slot
        localVarIndexMap = new ArrayList<>(localVarTypes.size());
        int i = 0;
        for (ValType local : localVarTypes) {
            localVarIndexMap.add(i);
            i += local.stackSlots;
        }
        nextLocalSlot = i;
    }

    @Override
    public SimpleInstruction visitEnd(Instruction.End inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Shouldn't visit end? It's a marker instruction!");
    }

    @Override
    public SimpleInstruction visitElse(Instruction.Else inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Shouldn't visit else? It's a marker instruction!");
    }

    @Override
    public SimpleInstruction visitUnreachable(Instruction.Unreachable inst) throws Validator.ValidationException {
        validator.unreachable();
        return new SimpleInstruction.RawBytecode(visitor -> {
            BytecodeHelper.throwRuntimeError(visitor, "Hit (unreachable) instruction in WASM code!");
        });
    }

    @Override
    public SimpleInstruction visitNop(Instruction.Nop inst) throws Validator.ValidationException {
        // Do nothing, it's a nop lmao
        return null;
    }

    @Override
    public SimpleInstruction visitBlock(Instruction.Block inst) throws Validator.ValidationException {
        // Validate before block starts:
        validator.popVals(inst.blockType().inTypes());
        validator.pushControl(false, inst.blockType().inTypes(), inst.blockType().outTypes());
        // Visit inner instructions and put them in a new list:
        List<SimpleInstruction> inner = ListUtils.flatMapNonNull(inst.inside(), x -> x.accept(this));
        // Validate at end of block:
        validator.pushVals(validator.popControl().endTypes);
        // Return simple block expr
        return new SimpleInstruction.Block(inst.blockType(), inner);
    }

    @Override
    public SimpleInstruction visitLoop(Instruction.Loop inst) throws Validator.ValidationException {
        // Practically identical to visitBlock!
        // Validate before loop starts:
        validator.popVals(inst.blockType().inTypes());
        validator.pushControl(true, inst.blockType().inTypes(), inst.blockType().outTypes());
        // Visit inner instructions and put them in a new list:
        List<SimpleInstruction> inner = ListUtils.flatMapNonNull(inst.inside(), x -> x.accept(this));
        // Validate at end of loop:
        validator.pushVals(validator.popControl().endTypes);
        // Return simple loop expr
        return new SimpleInstruction.Loop(inst.blockType(), inner);
    }

    @Override
    public SimpleInstruction visitIf(Instruction.If inst) throws Validator.ValidationException {
        if (!inst.blockType().inTypes().equals(inst.blockType().outTypes()))
            throw new Validator.ValidationException("If without an else must have the same input types as output types!");
        // Validate
        validator.popVal(ValType.I32);
        validator.popVals(inst.blockType().inTypes());
        validator.pushControl(false, inst.blockType().inTypes(), inst.blockType().outTypes());
        // Visit inner instructions
        List<SimpleInstruction> inner = ListUtils.flatMapNonNull(inst.inside(), x -> x.accept(this));
        // Validate at the end
        validator.pushVals(validator.popControl().endTypes);
        // Return an if-else, with empty else
        return new SimpleInstruction.IfElse(inst.blockType(), inner, List.of());
    }

    @Override
    public SimpleInstruction visitIfElse(Instruction.IfElse inst) throws Validator.ValidationException {
        // Validate
        validator.popVal(ValType.I32);
        validator.popVals(inst.blockType().inTypes());
        validator.pushControl(false, inst.blockType().inTypes(), inst.blockType().outTypes());
        // Visit true inner
        List<SimpleInstruction> ifTrue = ListUtils.flatMapNonNull(inst.ifTrue(), x -> x.accept(this));
        // Prepare validating for else branch:
        Validator.ControlFrame frame = validator.popControl();
        validator.pushControl(false, frame.startTypes, frame.endTypes);
        // Visit else branch
        List<SimpleInstruction> ifFalse = ListUtils.flatMapNonNull(inst.ifFalse(), x -> x.accept(this));
        // Validate at the end
        validator.pushVals(validator.popControl().endTypes);
        // Return the if-else
        return new SimpleInstruction.IfElse(inst.blockType(), ifTrue, ifFalse);
    }

    @Override
    public SimpleInstruction visitBranch(Instruction.Branch inst) throws Validator.ValidationException {
        // Validate
        Validator.ControlFrame frame = validator.peekControl(inst.index());
        List<ValType> typesMaintained = frame.labelTypes(); // The types which stay on the JVM stack
        validator.popVals(typesMaintained);
        List<ValType> typesPopped = validator.allValsAfter(inst.index()); // The types which are removed from the JVM stack
        validator.unreachable();
        // Return jump instruction
        return new SimpleInstruction.Jump(inst.index(), ListUtils.reversed(typesMaintained), typesPopped);
    }

    @Override
    public SimpleInstruction visitBranchIf(Instruction.BranchIf inst) throws Validator.ValidationException {
        // Simplify it into an "if" with a "branch" inside.
        // Increase the branch defaultIndex by 1 to account for the extra "if" wrapper.
        List<ValType> labelTypes = validator.peekControl(inst.index()).labelTypes();
        StackType stackType = new StackType(labelTypes, labelTypes);
        return new Instruction.If(stackType, List.of(new Instruction.Branch(inst.index() + 1))).accept(this);
    }

    @Override
    public SimpleInstruction visitBranchTable(Instruction.BranchTable inst) throws Validator.ValidationException {
        validator.popVal(ValType.I32);
        List<ValType> labelTypes = validator.peekControl(inst.defaultIndex()).labelTypes();
        List<List<ValType>> typesPopped = new ArrayList<>();
        for (int n : inst.indices()) {
            List<ValType> thisLabelTypes = validator.peekControl(n).labelTypes();
            if (!labelTypes.equals(thisLabelTypes))
                throw new Validator.ValidationException("Unexpected label types in branch_table instruction");
            validator.popVals(labelTypes);
            typesPopped.add(validator.allValsAfter(n));
            validator.pushVals(labelTypes);
        }
        validator.popVals(labelTypes);
        List<ValType> defaultTypesPopped = validator.allValsAfter(inst.defaultIndex());
        validator.unreachable();
        return new SimpleInstruction.JumpTable(inst.indices(), inst.defaultIndex(), ListUtils.reversed(labelTypes), defaultTypesPopped, typesPopped);
    }

    @Override
    public SimpleInstruction visitReturn(Instruction.Return inst) throws Validator.ValidationException {
        // Validate:
        if (returnTypes == null)
            throw new Validator.ValidationException("Attempt to return in situation where return is not allowed!");
        validator.popVals(returnTypes); // Pop return types
        List<ValType> restOfStack = validator.allVals(); // Get everything else on the stack
        validator.unreachable();
        // Return return instruction
        return new SimpleInstruction.Return(ListUtils.reversed(returnTypes), restOfStack);
    }

    @Override
    public SimpleInstruction visitCall(Instruction.Call inst) throws Validator.ValidationException {
        // Validate
        StackType functionType = wasmModule.getFunctionType(inst.index());
        validator.popVals(functionType.inTypes()); // Pop args
        validator.pushVals(functionType.outTypes()); // Push results
        // Result in the simple instruction
        return new SimpleInstruction.Call(inst.index());
    }

    @Override
    public SimpleInstruction visitCallIndirect(Instruction.CallIndirect inst) throws Validator.ValidationException {
        StackType funcType = wasmModule.types.get(inst.typeIndex());
        validator.popVal(ValType.I32);
        validator.popVals(funcType.inTypes());
        validator.pushVals(funcType.outTypes());
        return new SimpleInstruction.CallIndirect(inst.tableIndex(), funcType);
    }

    @Override
    public SimpleInstruction visitRefNull(Instruction.RefNull inst) throws Validator.ValidationException {
        validator.pushVal(inst.type()); // Push the value
        // Just raw bytecode emitting null
        return new SimpleInstruction.RawBytecode(visitor -> visitor.visitInsn(Opcodes.ACONST_NULL));
    }

    @Override
    public SimpleInstruction visitRefIsNull(Instruction.RefIsNull inst) throws Validator.ValidationException {
        ValType top = validator.popVal();
        if (!top.isRef())
            throw new Validator.ValidationException("Expected reference type on top of stack for instruction ref_is_null, but found " + top);
        validator.pushVal(ValType.I32);
        return RefIsNull.INSTANCE; // Return the intrinsic
    }

    @Override
    public SimpleInstruction visitRefFunc(Instruction.RefFunc inst) throws Validator.ValidationException {
        validator.pushVal(ValType.FUNCREF);
        return new RefFunc(inst.funcIndex());
    }

    @Override
    public SimpleInstruction visitDrop(Instruction.Drop inst) throws Validator.ValidationException {
        // Check type, then emit
        ValType topOfStack = validator.popVal();
        // Unknown means this was after an unreachable, so do nothing
        if (topOfStack == ValType.UNKNOWN)
            return null;
        // Return a pop
        return new SimpleInstruction.Pop(topOfStack);
    }

    @Override
    public SimpleInstruction visitSelect(Instruction.Select inst) throws Validator.ValidationException {
        validator.popVal(ValType.I32); // I32 on top of stack indicating which to choose
        // Expect two of the same type below it:
        ValType type = validator.popVal();
        validator.popVal(type);
        validator.pushVal(type);
        // Emit a select instruction
        return new SimpleInstruction.Select(type);
    }

    @Override
    public SimpleInstruction visitSelectFrom(Instruction.SelectFrom inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitLocalGet(Instruction.LocalGet inst) throws Validator.ValidationException {
        int localIndex = inst.localIndex();
        ValType localType = localVarTypes.get(localIndex);
        // Validate:
        validator.pushVal(localType);
        // Return expr
        int jvmIndex = localVarIndexMap.get(localIndex);
        return new SimpleInstruction.LocalLoad(localType, jvmIndex);
    }

    @Override
    public SimpleInstruction visitLocalSet(Instruction.LocalSet inst) throws Validator.ValidationException {
        int localIndex = inst.localIndex();
        ValType localType = localVarTypes.get(localIndex);
        // Validate:
        validator.popVal(localType);
        // Return expr
        int jvmIndex = localVarIndexMap.get(localIndex);
        return new SimpleInstruction.LocalStore(localType, jvmIndex);
    }

    @Override
    public SimpleInstruction visitLocalTee(Instruction.LocalTee inst) throws Validator.ValidationException {
        int localIndex = inst.localIndex();
        ValType localType = localVarTypes.get(localIndex);
        // Validate:
        validator.popVal(localType);
        validator.pushVal(localType);
        // Return expr
        int jvmIndex = localVarIndexMap.get(localIndex);
        return new SimpleInstruction.LocalTee(localType, jvmIndex);
    }

    @Override
    public SimpleInstruction visitGlobalGet(Instruction.GlobalGet inst) throws Validator.ValidationException {
        int globalIndex = inst.globalIndex();
        GlobalType globalType = wasmModule.getGlobalType(globalIndex);
        // Validate:
        validator.pushVal(globalType.valType());
        // Return expr
        return new SimpleInstruction.GlobalGet(globalIndex);
    }

    @Override
    public SimpleInstruction visitGlobalSet(Instruction.GlobalSet inst) throws Validator.ValidationException {
        int globalIndex = inst.globalIndex();
        GlobalType globalType = wasmModule.getGlobalType(globalIndex);
        // Validate:
        if (!globalType.mutable())
            throw new Validator.ValidationException("Attempt to use global.set on an immutable global!");
        validator.popVal(globalType.valType());
        // Return expr
        return new SimpleInstruction.GlobalSet(globalIndex);
    }

    @Override
    public SimpleInstruction visitTableGet(Instruction.TableGet inst) throws Validator.ValidationException {
        validator.popVal(ValType.I32); // Pop i32 defaultIndex
        validator.pushVal(wasmModule.getTableType(inst.tableIndex()).elementType()); // Push ref type
        return new TableGet(inst.tableIndex());
    }

    @Override
    public SimpleInstruction visitTableSet(Instruction.TableSet inst) throws Validator.ValidationException {
        validator.popVal(wasmModule.getTableType(inst.tableIndex()).elementType()); // Pop ref type
        validator.popVal(ValType.I32); // Pop i32 defaultIndex
        return new TableSet(inst.tableIndex());
    }

    @Override
    public SimpleInstruction visitTableInit(Instruction.TableInit inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitElemDrop(Instruction.ElemDrop inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitTableGrow(Instruction.TableGrow inst) throws Validator.ValidationException {
        validator.popVal(ValType.I32); // Pop i32 requestedEntries
        validator.popVal(wasmModule.getTableType(inst.tableIndex()).elementType()); // Pop ref type
        validator.pushVal(ValType.I32); // Push i32 result
        return new TableGrow(inst.tableIndex());
    }

    @Override
    public SimpleInstruction visitTableSize(Instruction.TableSize inst) throws Validator.ValidationException {
        validator.pushVal(ValType.I32);
        return new TableSize(inst.tableIndex());
    }

    @Override
    public SimpleInstruction visitTableCopy(Instruction.TableCopy inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitTableFill(Instruction.TableFill inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI32Load(Instruction.I32Load inst) throws Validator.ValidationException {
        validator.popVal(ValType.I32);
        validator.pushVal(ValType.I32);
        return new MemoryLoad("I", ValType.I32, false, inst.offset());
    }

    @Override
    public SimpleInstruction visitI64Load(Instruction.I64Load inst) throws Validator.ValidationException {
        validator.popVal(ValType.I32);
        validator.pushVal(ValType.I64);
        return new MemoryLoad("J", ValType.I64, false, inst.offset());
    }

    @Override
    public SimpleInstruction visitF32Load(Instruction.F32Load inst) throws Validator.ValidationException {
        validator.popVal(ValType.I32);
        validator.pushVal(ValType.F32);
        return new MemoryLoad("F", ValType.F32, false, inst.offset());
    }

    @Override
    public SimpleInstruction visitF64Load(Instruction.F64Load inst) throws Validator.ValidationException {
        validator.popVal(ValType.I32);
        validator.pushVal(ValType.F64);
        return new MemoryLoad("D", ValType.F64, false, inst.offset());
    }

    @Override
    public SimpleInstruction visitI32Load8S(Instruction.I32Load8S inst) throws Validator.ValidationException {
        validator.popVal(ValType.I32);
        validator.pushVal(ValType.I32);
        return new MemoryLoad("B", ValType.I32, false, inst.offset());
    }

    @Override
    public SimpleInstruction visitI32Load8U(Instruction.I32Load8U inst) throws Validator.ValidationException {
        validator.popVal(ValType.I32);
        validator.pushVal(ValType.I32);
        return new MemoryLoad("B", ValType.I32, true, inst.offset());
    }

    @Override
    public SimpleInstruction visitI32Load16S(Instruction.I32Load16S inst) throws Validator.ValidationException {
        validator.popVal(ValType.I32);
        validator.pushVal(ValType.I32);
        return new MemoryLoad("S", ValType.I32, false, inst.offset());
    }

    @Override
    public SimpleInstruction visitI32Load16U(Instruction.I32Load16U inst) throws Validator.ValidationException {
        validator.popVal(ValType.I32);
        validator.pushVal(ValType.I32);
        return new MemoryLoad("S", ValType.I32, true, inst.offset());
    }

    @Override
    public SimpleInstruction visitI64Load8S(Instruction.I64Load8S inst) throws Validator.ValidationException {
        validator.popVal(ValType.I32);
        validator.pushVal(ValType.I64);
        return new MemoryLoad("B", ValType.I64, false, inst.offset());
    }

    @Override
    public SimpleInstruction visitI64Load8U(Instruction.I64Load8U inst) throws Validator.ValidationException {
        validator.popVal(ValType.I32);
        validator.pushVal(ValType.I64);
        return new MemoryLoad("B", ValType.I64, true, inst.offset());
    }

    @Override
    public SimpleInstruction visitI64Load16S(Instruction.I64Load16S inst) throws Validator.ValidationException {
        validator.popVal(ValType.I32);
        validator.pushVal(ValType.I64);
        return new MemoryLoad("S", ValType.I64, false, inst.offset());
    }

    @Override
    public SimpleInstruction visitI64Load16U(Instruction.I64Load16U inst) throws Validator.ValidationException {
        validator.popVal(ValType.I32);
        validator.pushVal(ValType.I64);
        return new MemoryLoad("S", ValType.I64, true, inst.offset());
    }

    @Override
    public SimpleInstruction visitI64Load32S(Instruction.I64Load32S inst) throws Validator.ValidationException {
        validator.popVal(ValType.I32);
        validator.pushVal(ValType.I64);
        return new MemoryLoad("I", ValType.I64, false, inst.offset());
    }

    @Override
    public SimpleInstruction visitI64Load32U(Instruction.I64Load32U inst) throws Validator.ValidationException {
        validator.popVal(ValType.I32);
        validator.pushVal(ValType.I64);
        return new MemoryLoad("I", ValType.I64, true, inst.offset());
    }

    @Override
    public SimpleInstruction visitI32Store(Instruction.I32Store inst) throws Validator.ValidationException {
        validator.popVal(ValType.I32);
        validator.popVal(ValType.I32);
        return new MemoryStore(ValType.I32, "I", inst.offset());
    }

    @Override
    public SimpleInstruction visitI64Store(Instruction.I64Store inst) throws Validator.ValidationException {
        validator.popVal(ValType.I64);
        validator.popVal(ValType.I32);
        return new MemoryStore(ValType.I64, "J", inst.offset());
    }

    @Override
    public SimpleInstruction visitF32Store(Instruction.F32Store inst) throws Validator.ValidationException {
        validator.popVal(ValType.F32);
        validator.popVal(ValType.I32);
        return new MemoryStore(ValType.F32, "F", inst.offset());
    }

    @Override
    public SimpleInstruction visitF64Store(Instruction.F64Store inst) throws Validator.ValidationException {
        validator.popVal(ValType.F64);
        validator.popVal(ValType.I32);
        return new MemoryStore(ValType.F64, "D", inst.offset());
    }

    @Override
    public SimpleInstruction visitI32Store8(Instruction.I32Store8 inst) throws Validator.ValidationException {
        validator.popVal(ValType.I32);
        validator.popVal(ValType.I32);
        return new MemoryStore(ValType.I32, "B", inst.offset());
    }

    @Override
    public SimpleInstruction visitI32Store16(Instruction.I32Store16 inst) throws Validator.ValidationException {
        validator.popVal(ValType.I32);
        validator.popVal(ValType.I32);
        return new MemoryStore(ValType.I32, "S", inst.offset());
    }

    @Override
    public SimpleInstruction visitI64Store8(Instruction.I64Store8 inst) throws Validator.ValidationException {
        validator.popVal(ValType.I64);
        validator.popVal(ValType.I32);
        return new MemoryStore(ValType.I64, "B", inst.offset());
    }

    @Override
    public SimpleInstruction visitI64Store16(Instruction.I64Store16 inst) throws Validator.ValidationException {
        validator.popVal(ValType.I64);
        validator.popVal(ValType.I32);
        return new MemoryStore(ValType.I64, "S", inst.offset());
    }

    @Override
    public SimpleInstruction visitI64Store32(Instruction.I64Store32 inst) throws Validator.ValidationException {
        validator.popVal(ValType.I64);
        validator.popVal(ValType.I32);
        return new MemoryStore(ValType.I64, "I", inst.offset());
    }

    @Override
    public SimpleInstruction visitMemorySize(Instruction.MemorySize inst) throws Validator.ValidationException {
        validator.pushVal(ValType.I32);
        return MemorySize.INSTANCE;
    }

    @Override
    public SimpleInstruction visitMemoryGrow(Instruction.MemoryGrow inst) throws Validator.ValidationException {
        validator.popVal(ValType.I32);
        validator.pushVal(ValType.I32);
        return MemoryGrow.INSTANCE;
    }

    @Override
    public SimpleInstruction visitMemoryInit(Instruction.MemoryInit inst) throws Validator.ValidationException {
        validator.popVal(ValType.I32);
        validator.popVal(ValType.I32);
        validator.popVal(ValType.I32);
        return new MemoryInit(inst.dataIndex());
    }

    @Override
    public SimpleInstruction visitDataDrop(Instruction.DataDrop inst) throws Validator.ValidationException {
        return new DataDrop(inst.dataIndex());
    }

    @Override
    public SimpleInstruction visitMemoryCopy(Instruction.MemoryCopy inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitMemoryFill(Instruction.MemoryFill inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI32Const(Instruction.I32Const inst) throws Validator.ValidationException {
        validator.pushVal(ValType.I32);
        return new SimpleInstruction.Constant(inst.n());
    }

    @Override
    public SimpleInstruction visitI64Const(Instruction.I64Const inst) throws Validator.ValidationException {
        validator.pushVal(ValType.I64);
        return new SimpleInstruction.Constant(inst.n());
    }

    @Override
    public SimpleInstruction visitF32Const(Instruction.F32Const inst) throws Validator.ValidationException {
        validator.pushVal(ValType.F32);
        return new SimpleInstruction.Constant(inst.z());
    }

    @Override
    public SimpleInstruction visitF64Const(Instruction.F64Const inst) throws Validator.ValidationException {
        validator.pushVal(ValType.F64);
        return new SimpleInstruction.Constant(inst.z());
    }

    /**
     * Various arithmetic helpers!
     */
    // [type type] -> [type]
    private SimpleInstruction binaryBytecode(ValType type, Consumer<MethodVisitor> consumer) throws Validator.ValidationException {
        validator.popVal(type);
        validator.popVal(type);
        validator.pushVal(type);
        return new SimpleInstruction.RawBytecode(consumer);
    }
    // [type] -> [type]
    private SimpleInstruction unaryBytecode(ValType type, Consumer<MethodVisitor> consumer) throws Validator.ValidationException {
        validator.popVal(type);
        validator.pushVal(type);
        return new SimpleInstruction.RawBytecode(consumer);
    }
    // [type] -> [i32]
    private SimpleInstruction unaryTest(ValType type, Consumer<MethodVisitor> consumer) throws Validator.ValidationException {
        validator.popVal(type);
        validator.pushVal(ValType.I32);
        return new SimpleInstruction.RawBytecode(consumer);
    }
    // [type type] -> [i32]
    private SimpleInstruction binaryTest(ValType type, Consumer<MethodVisitor> consumer) throws Validator.ValidationException {
        validator.popVal(type);
        validator.popVal(type);
        validator.pushVal(ValType.I32);
        return new SimpleInstruction.RawBytecode(consumer);
    }
    // [from] -> [to]
    private SimpleInstruction convert(ValType from, ValType to, Consumer<MethodVisitor> consumer) throws Validator.ValidationException {
        validator.popVal(from);
        validator.pushVal(to);
        return new SimpleInstruction.RawBytecode(consumer);
    }

    // Helper for binary/unary operators which have a jvm opcode
    // No need for synchronization on the arrays, since writes/reads of reference types are guaranteed atomic:
    // https://docs.oracle.com/javase/specs/jls/se9/html/jls-17.html#jls-17.7
    private static final Consumer<MethodVisitor>[] cachedBinaryLambdas = new Consumer[255];
    private static final Consumer<MethodVisitor>[] cachedUnaryLambdas = new Consumer[255];
    private SimpleInstruction binaryOp(ValType type, int jvmOpcode) throws Validator.ValidationException {
        if (cachedBinaryLambdas[jvmOpcode] == null)
            cachedBinaryLambdas[jvmOpcode] = visitor -> visitor.visitInsn(jvmOpcode);
        return binaryBytecode(type, cachedBinaryLambdas[jvmOpcode]);
    }
    private SimpleInstruction unaryOp(ValType type, int jvmOpcode) throws Validator.ValidationException {
        if (cachedUnaryLambdas[jvmOpcode] == null)
            cachedUnaryLambdas[jvmOpcode] = visitor -> visitor.visitInsn(jvmOpcode);
        return unaryBytecode(type, cachedUnaryLambdas[jvmOpcode]);
    }


    @Override
    public SimpleInstruction visitI32Eqz(Instruction.I32Eqz inst) throws Validator.ValidationException {
        return unaryTest(ValType.I32, visitor -> {
            BytecodeHelper.test(visitor, Opcodes.IFEQ);
        });
    }

    @Override
    public SimpleInstruction visitI32Eq(Instruction.I32Eq inst) throws Validator.ValidationException {
        return binaryTest(ValType.I32, visitor -> {
            BytecodeHelper.test(visitor, Opcodes.IF_ICMPEQ);
        });
    }

    @Override
    public SimpleInstruction visitI32Ne(Instruction.I32Ne inst) throws Validator.ValidationException {
        return binaryTest(ValType.I32, visitor -> {
            BytecodeHelper.test(visitor, Opcodes.IF_ICMPNE);
        });
    }

    @Override
    public SimpleInstruction visitI32LtS(Instruction.I32LtS inst) throws Validator.ValidationException {
        return binaryTest(ValType.I32, visitor -> {
            BytecodeHelper.test(visitor, Opcodes.IF_ICMPLT);
        });
    }

    @Override
    public SimpleInstruction visitI32LtU(Instruction.I32LtU inst) throws Validator.ValidationException {
        return binaryTest(ValType.I32, visitor -> {
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "compareUnsigned", "(II)I", false);
            BytecodeHelper.test(visitor, Opcodes.IFLT);
        });
    }

    @Override
    public SimpleInstruction visitI32GtS(Instruction.I32GtS inst) throws Validator.ValidationException {
        return binaryTest(ValType.I32, visitor -> {
            BytecodeHelper.test(visitor, Opcodes.IF_ICMPGT);
        });
    }

    @Override
    public SimpleInstruction visitI32GtU(Instruction.I32GtU inst) throws Validator.ValidationException {
        return binaryTest(ValType.I32, visitor -> {
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "compareUnsigned", "(II)I", false);
            BytecodeHelper.test(visitor, Opcodes.IFGT);
        });
    }

    @Override
    public SimpleInstruction visitI32LeS(Instruction.I32LeS inst) throws Validator.ValidationException {
        return binaryTest(ValType.I32, visitor -> {
            BytecodeHelper.test(visitor, Opcodes.IF_ICMPLE);
        });
    }

    @Override
    public SimpleInstruction visitI32LeU(Instruction.I32LeU inst) throws Validator.ValidationException {
        return binaryTest(ValType.I32, visitor -> {
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "compareUnsigned", "(II)I", false);
            BytecodeHelper.test(visitor, Opcodes.IFLE);
        });
    }

    @Override
    public SimpleInstruction visitI32GeS(Instruction.I32GeS inst) throws Validator.ValidationException {
        return binaryTest(ValType.I32, visitor -> {
            BytecodeHelper.test(visitor, Opcodes.IF_ICMPGE);
        });
    }

    @Override
    public SimpleInstruction visitI32GeU(Instruction.I32GeU inst) throws Validator.ValidationException {
        return binaryTest(ValType.I32, visitor -> {
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "compareUnsigned", "(II)I", false);
            BytecodeHelper.test(visitor, Opcodes.IFGE);
        });
    }

    @Override
    public SimpleInstruction visitI64Eqz(Instruction.I64Eqz inst) throws Validator.ValidationException {
        return unaryTest(ValType.I64, visitor -> {
            visitor.visitInsn(Opcodes.LCONST_0);
            visitor.visitInsn(Opcodes.LCMP);
            BytecodeHelper.test(visitor, Opcodes.IFEQ);
        });
    }

    @Override
    public SimpleInstruction visitI64Eq(Instruction.I64Eq inst) throws Validator.ValidationException {
        return binaryTest(ValType.I64, visitor -> {
            visitor.visitInsn(Opcodes.LCMP);
            BytecodeHelper.test(visitor, Opcodes.IFEQ);
        });
    }

    @Override
    public SimpleInstruction visitI64Ne(Instruction.I64Ne inst) throws Validator.ValidationException {
        return binaryTest(ValType.I64, visitor -> {
            visitor.visitInsn(Opcodes.LCMP);
            BytecodeHelper.test(visitor, Opcodes.IFNE);
        });
    }

    @Override
    public SimpleInstruction visitI64LtS(Instruction.I64LtS inst) throws Validator.ValidationException {
        return binaryTest(ValType.I64, visitor -> {
            visitor.visitInsn(Opcodes.LCMP);
            BytecodeHelper.test(visitor, Opcodes.IFLT);
        });
    }

    @Override
    public SimpleInstruction visitI64LtU(Instruction.I64LtU inst) throws Validator.ValidationException {
        return binaryTest(ValType.I64, visitor -> {
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "compareUnsigned", "(JJ)I", false);
            BytecodeHelper.test(visitor, Opcodes.IFLT);
        });
    }

    @Override
    public SimpleInstruction visitI64GtS(Instruction.I64GtS inst) throws Validator.ValidationException {
        return binaryTest(ValType.I64, visitor -> {
            visitor.visitInsn(Opcodes.LCMP);
            BytecodeHelper.test(visitor, Opcodes.IFGT);
        });
    }

    @Override
    public SimpleInstruction visitI64GtU(Instruction.I64GtU inst) throws Validator.ValidationException {
        return binaryTest(ValType.I64, visitor -> {
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "compareUnsigned", "(JJ)I", false);
            BytecodeHelper.test(visitor, Opcodes.IFGT);
        });
    }

    @Override
    public SimpleInstruction visitI64LeS(Instruction.I64LeS inst) throws Validator.ValidationException {
        return binaryTest(ValType.I64, visitor -> {
            visitor.visitInsn(Opcodes.LCMP);
            BytecodeHelper.test(visitor, Opcodes.IFLE);
        });
    }

    @Override
    public SimpleInstruction visitI64LeU(Instruction.I64LeU inst) throws Validator.ValidationException {
        return binaryTest(ValType.I64, visitor -> {
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "compareUnsigned", "(JJ)I", false);
            BytecodeHelper.test(visitor, Opcodes.IFLE);
        });
    }

    @Override
    public SimpleInstruction visitI64GeS(Instruction.I64GeS inst) throws Validator.ValidationException {
        return binaryTest(ValType.I64, visitor -> {
            visitor.visitInsn(Opcodes.LCMP);
            BytecodeHelper.test(visitor, Opcodes.IFGE);
        });
    }

    @Override
    public SimpleInstruction visitI64GeU(Instruction.I64GeU inst) throws Validator.ValidationException {
        return binaryTest(ValType.I64, visitor -> {
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "compareUnsigned", "(JJ)I", false);
            BytecodeHelper.test(visitor, Opcodes.IFGE);
        });
    }

    @Override
    public SimpleInstruction visitF32Eq(Instruction.F32Eq inst) throws Validator.ValidationException {
        return binaryTest(ValType.F32, visitor -> {
            visitor.visitInsn(Opcodes.FCMPG);
            BytecodeHelper.test(visitor, Opcodes.IFEQ);
        });
    }

    @Override
    public SimpleInstruction visitF32Ne(Instruction.F32Ne inst) throws Validator.ValidationException {
        return binaryTest(ValType.F32, visitor -> {
            visitor.visitInsn(Opcodes.FCMPG);
            BytecodeHelper.test(visitor, Opcodes.IFNE);
        });
    }

    @Override
    public SimpleInstruction visitF32Lt(Instruction.F32Lt inst) throws Validator.ValidationException {
        return binaryTest(ValType.F32, visitor -> {
            visitor.visitInsn(Opcodes.FCMPG);
            BytecodeHelper.test(visitor, Opcodes.IFLT);
        });
    }

    @Override
    public SimpleInstruction visitF32Gt(Instruction.F32Gt inst) throws Validator.ValidationException {
        return binaryTest(ValType.F32, visitor -> {
            visitor.visitInsn(Opcodes.FCMPL);
            BytecodeHelper.test(visitor, Opcodes.IFGT);
        });
    }

    @Override
    public SimpleInstruction visitF32Le(Instruction.F32Le inst) throws Validator.ValidationException {
        return binaryTest(ValType.F32, visitor -> {
            visitor.visitInsn(Opcodes.FCMPG);
            BytecodeHelper.test(visitor, Opcodes.IFLE);
        });
    }

    @Override
    public SimpleInstruction visitF32Ge(Instruction.F32Ge inst) throws Validator.ValidationException {
        return binaryTest(ValType.F32, visitor -> {
            visitor.visitInsn(Opcodes.FCMPL);
            BytecodeHelper.test(visitor, Opcodes.IFGE);
        });
    }

    @Override
    public SimpleInstruction visitF64Eq(Instruction.F64Eq inst) throws Validator.ValidationException {
        return binaryTest(ValType.F64, visitor -> {
            visitor.visitInsn(Opcodes.DCMPG);
            BytecodeHelper.test(visitor, Opcodes.IFEQ);
        });
    }

    @Override
    public SimpleInstruction visitF64Ne(Instruction.F64Ne inst) throws Validator.ValidationException {
        return binaryTest(ValType.F64, visitor -> {
            visitor.visitInsn(Opcodes.DCMPG);
            BytecodeHelper.test(visitor, Opcodes.IFNE);
        });
    }

    @Override
    public SimpleInstruction visitF64Lt(Instruction.F64Lt inst) throws Validator.ValidationException {
        return binaryTest(ValType.F64, visitor -> {
            visitor.visitInsn(Opcodes.DCMPG);
            BytecodeHelper.test(visitor, Opcodes.IFLT);
        });
    }

    @Override
    public SimpleInstruction visitF64Gt(Instruction.F64Gt inst) throws Validator.ValidationException {
        return binaryTest(ValType.F64, visitor -> {
            visitor.visitInsn(Opcodes.DCMPL);
            BytecodeHelper.test(visitor, Opcodes.IFGT);
        });
    }

    @Override
    public SimpleInstruction visitF64Le(Instruction.F64Le inst) throws Validator.ValidationException {
        return binaryTest(ValType.F64, visitor -> {
            visitor.visitInsn(Opcodes.DCMPG);
            BytecodeHelper.test(visitor, Opcodes.IFLE);
        });
    }

    @Override
    public SimpleInstruction visitF64Ge(Instruction.F64Ge inst) throws Validator.ValidationException {
        return binaryTest(ValType.F64, visitor -> {
            visitor.visitInsn(Opcodes.DCMPL);
            BytecodeHelper.test(visitor, Opcodes.IFGE);
        });
    }

    @Override
    public SimpleInstruction visitI32Clz(Instruction.I32Clz inst) throws Validator.ValidationException {
        return unaryBytecode(ValType.I32, visitor -> {
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "numberOfLeadingZeros", "(I)I", false);
        });
    }

    @Override
    public SimpleInstruction visitI32Ctz(Instruction.I32Ctz inst) throws Validator.ValidationException {
        return unaryBytecode(ValType.I32, visitor -> {
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "numberOfTrailingZeros", "(I)I", false);
        });
    }

    @Override
    public SimpleInstruction visitI32PopCnt(Instruction.I32PopCnt inst) throws Validator.ValidationException {
        return unaryBytecode(ValType.I32, visitor -> {
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "bitCount", "(I)I", false);
        });
    }

    @Override
    public SimpleInstruction visitI32Add(Instruction.I32Add inst) throws Validator.ValidationException {
        return binaryOp(ValType.I32, Opcodes.IADD);
    }

    @Override
    public SimpleInstruction visitI32Sub(Instruction.I32Sub inst) throws Validator.ValidationException {
        return binaryOp(ValType.I32, Opcodes.ISUB);
    }

    @Override
    public SimpleInstruction visitI32Mul(Instruction.I32Mul inst) throws Validator.ValidationException {
        return binaryOp(ValType.I32, Opcodes.IMUL);
    }

    @Override
    public SimpleInstruction visitI32DivS(Instruction.I32DivS inst) throws Validator.ValidationException {
        return binaryOp(ValType.I32, Opcodes.IDIV);
    }

    @Override
    public SimpleInstruction visitI32DivU(Instruction.I32DivU inst) throws Validator.ValidationException {
        return binaryBytecode(ValType.I32, visitor -> {
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "divideUnsigned", "(II)I", false);
        });
    }

    @Override
    public SimpleInstruction visitI32RemS(Instruction.I32RemS inst) throws Validator.ValidationException {
        return binaryOp(ValType.I32, Opcodes.IREM);
    }

    @Override
    public SimpleInstruction visitI32RemU(Instruction.I32RemU inst) throws Validator.ValidationException {
        return binaryBytecode(ValType.I32, visitor -> {
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "remainderUnsigned", "(II)I", false);
        });
    }

    @Override
    public SimpleInstruction visitI32And(Instruction.I32And inst) throws Validator.ValidationException {
        return binaryOp(ValType.I32, Opcodes.IAND);
    }

    @Override
    public SimpleInstruction visitI32Or(Instruction.I32Or inst) throws Validator.ValidationException {
        return binaryOp(ValType.I32, Opcodes.IOR);
    }

    @Override
    public SimpleInstruction visitI32Xor(Instruction.I32Xor inst) throws Validator.ValidationException {
        return binaryOp(ValType.I32, Opcodes.IXOR);
    }

    @Override
    public SimpleInstruction visitI32Shl(Instruction.I32Shl inst) throws Validator.ValidationException {
        return binaryOp(ValType.I32, Opcodes.ISHL);
    }

    @Override
    public SimpleInstruction visitI32ShrS(Instruction.I32ShrS inst) throws Validator.ValidationException {
        return binaryOp(ValType.I32, Opcodes.ISHR);
    }

    @Override
    public SimpleInstruction visitI32ShrU(Instruction.I32ShrU inst) throws Validator.ValidationException {
        return binaryOp(ValType.I32, Opcodes.IUSHR);
    }

    @Override
    public SimpleInstruction visitI32Rotl(Instruction.I32Rotl inst) throws Validator.ValidationException {
        return binaryBytecode(ValType.I32, visitor -> {
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "rotateLeft", "(II)I", false);
        });
    }

    @Override
    public SimpleInstruction visitI32Rotr(Instruction.I32Rotr inst) throws Validator.ValidationException {
        return binaryBytecode(ValType.I32, visitor -> {
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "rotateRight", "(II)I", false);
        });
    }

    @Override
    public SimpleInstruction visitI64Clz(Instruction.I64Clz inst) throws Validator.ValidationException {
        return unaryBytecode(ValType.I64, visitor -> {
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "numberOfLeadingZeros", "(J)I", false);
            visitor.visitInsn(Opcodes.I2L);
        });
    }

    @Override
    public SimpleInstruction visitI64Ctz(Instruction.I64Ctz inst) throws Validator.ValidationException {
        return unaryBytecode(ValType.I64, visitor -> {
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "numberOfTrailingZeros", "(J)I", false);
            visitor.visitInsn(Opcodes.I2L);
        });
    }

    @Override
    public SimpleInstruction visitI64PopCnt(Instruction.I64PopCnt inst) throws Validator.ValidationException {
        return unaryBytecode(ValType.I64, visitor -> {
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "bitCount", "(J)I", false);
            visitor.visitInsn(Opcodes.I2L);
        });
    }

    @Override
    public SimpleInstruction visitI64Add(Instruction.I64Add inst) throws Validator.ValidationException {
        return binaryOp(ValType.I64, Opcodes.LADD);
    }

    @Override
    public SimpleInstruction visitI64Sub(Instruction.I64Sub inst) throws Validator.ValidationException {
        return binaryOp(ValType.I64, Opcodes.LSUB);
    }

    @Override
    public SimpleInstruction visitI64Mul(Instruction.I64Mul inst) throws Validator.ValidationException {
        return binaryOp(ValType.I64, Opcodes.LMUL);
    }

    @Override
    public SimpleInstruction visitI64DivS(Instruction.I64DivS inst) throws Validator.ValidationException {
        return binaryOp(ValType.I64, Opcodes.LDIV);
    }

    @Override
    public SimpleInstruction visitI64DivU(Instruction.I64DivU inst) throws Validator.ValidationException {
        return binaryBytecode(ValType.I64, visitor -> {
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "divideUnsigned", "(JJ)J", false);
        });
    }

    @Override
    public SimpleInstruction visitI64RemS(Instruction.I64RemS inst) throws Validator.ValidationException {
        return binaryOp(ValType.I64, Opcodes.LREM);
    }

    @Override
    public SimpleInstruction visitI64RemU(Instruction.I64RemU inst) throws Validator.ValidationException {
        return binaryBytecode(ValType.I64, visitor -> {
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "remainderUnsigned", "(JJ)J", false);
        });
    }

    @Override
    public SimpleInstruction visitI64And(Instruction.I64And inst) throws Validator.ValidationException {
        return binaryOp(ValType.I64, Opcodes.LAND);
    }

    @Override
    public SimpleInstruction visitI64Or(Instruction.I64Or inst) throws Validator.ValidationException {
        return binaryOp(ValType.I64, Opcodes.LOR);
    }

    @Override
    public SimpleInstruction visitI64Xor(Instruction.I64Xor inst) throws Validator.ValidationException {
        return binaryOp(ValType.I64, Opcodes.LXOR);
    }

    @Override
    public SimpleInstruction visitI64Shl(Instruction.I64Shl inst) throws Validator.ValidationException {
        return binaryBytecode(ValType.I64, visitor -> {
            visitor.visitInsn(Opcodes.L2I);
            visitor.visitInsn(Opcodes.LSHL);
        });
    }

    @Override
    public SimpleInstruction visitI64ShrS(Instruction.I64ShrS inst) throws Validator.ValidationException {
        return binaryBytecode(ValType.I64, visitor -> {
            visitor.visitInsn(Opcodes.L2I);
            visitor.visitInsn(Opcodes.LSHR);
        });
    }

    @Override
    public SimpleInstruction visitI64ShrU(Instruction.I64ShrU inst) throws Validator.ValidationException {
        return binaryBytecode(ValType.I64, visitor -> {
            visitor.visitInsn(Opcodes.L2I);
            visitor.visitInsn(Opcodes.LUSHR);
        });
    }

    @Override
    public SimpleInstruction visitI64Rotl(Instruction.I64Rotl inst) throws Validator.ValidationException {
        return binaryBytecode(ValType.I64, visitor -> {
            visitor.visitInsn(Opcodes.L2I);
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "rotateLeft", "(JI)J", false);
        });
    }

    @Override
    public SimpleInstruction visitI64Rotr(Instruction.I64Rotr inst) throws Validator.ValidationException {
        return binaryBytecode(ValType.I64, visitor -> {
            visitor.visitInsn(Opcodes.L2I);
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "rotateRight", "(JI)J", false);
        });
    }

    @Override
    public SimpleInstruction visitF32Abs(Instruction.F32Abs inst) throws Validator.ValidationException {
        return unaryBytecode(ValType.F32, visitor -> {
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "abs", "(F)F", false);
        });
    }

    @Override
    public SimpleInstruction visitF32Neg(Instruction.F32Neg inst) throws Validator.ValidationException {
        return unaryOp(ValType.F32, Opcodes.FNEG);
    }

    @Override
    public SimpleInstruction visitF32Ceil(Instruction.F32Ceil inst) throws Validator.ValidationException {
        return unaryBytecode(ValType.F32, visitor -> {
            visitor.visitInsn(Opcodes.F2D);
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "ceil", "(D)D", false);
            visitor.visitInsn(Opcodes.D2F);
        });
    }

    @Override
    public SimpleInstruction visitF32Floor(Instruction.F32Floor inst) throws Validator.ValidationException {
        return unaryBytecode(ValType.F32, visitor -> {
            visitor.visitInsn(Opcodes.F2D);
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "floor", "(D)D", false);
            visitor.visitInsn(Opcodes.D2F);
        });
    }

    @Override
    public SimpleInstruction visitF32Trunc(Instruction.F32Trunc inst) throws Validator.ValidationException {
        return unaryBytecode(ValType.F32, visitor -> {
            // Need to handle NaN correctly, can't just cast to int/long then back to float
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
        });
    }

    @Override
    public SimpleInstruction visitF32Nearest(Instruction.F32Nearest inst) throws Validator.ValidationException {
        return unaryBytecode(ValType.F32, visitor -> {
            visitor.visitInsn(Opcodes.F2D);
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "rint", "(D)D", false);
            visitor.visitInsn(Opcodes.D2F);
        });
    }

    @Override
    public SimpleInstruction visitF32Sqrt(Instruction.F32Sqrt inst) throws Validator.ValidationException {
        return unaryBytecode(ValType.F32, visitor -> {
            visitor.visitInsn(Opcodes.F2D);
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "sqrt", "(D)D", false);
            visitor.visitInsn(Opcodes.D2F);
        });
    }

    @Override
    public SimpleInstruction visitF32Add(Instruction.F32Add inst) throws Validator.ValidationException {
        return binaryOp(ValType.F32, Opcodes.FADD);
    }

    @Override
    public SimpleInstruction visitF32Sub(Instruction.F32Sub inst) throws Validator.ValidationException {
        return binaryOp(ValType.F32, Opcodes.FSUB);
    }

    @Override
    public SimpleInstruction visitF32Mul(Instruction.F32Mul inst) throws Validator.ValidationException {
        return binaryOp(ValType.F32, Opcodes.FMUL);
    }

    @Override
    public SimpleInstruction visitF32Div(Instruction.F32Div inst) throws Validator.ValidationException {
        return binaryOp(ValType.F32, Opcodes.FDIV);
    }

    @Override
    public SimpleInstruction visitF32Min(Instruction.F32Min inst) throws Validator.ValidationException {
        return binaryBytecode(ValType.F32, visitor -> {
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "min", "(FF)F", false);
        });
    }

    @Override
    public SimpleInstruction visitF32Max(Instruction.F32Max inst) throws Validator.ValidationException {
        return binaryBytecode(ValType.F32, visitor -> {
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "max", "(FF)F", false);
        });
    }

    @Override
    public SimpleInstruction visitF32Copysign(Instruction.F32Copysign inst) throws Validator.ValidationException {
        return binaryBytecode(ValType.F32, visitor -> {
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "copySign", "(FF)F", false);
        });
    }

    @Override
    public SimpleInstruction visitF64Abs(Instruction.F64Abs inst) throws Validator.ValidationException {
        return unaryBytecode(ValType.F64, visitor -> {
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "abs", "(D)D", false);
        });
    }

    @Override
    public SimpleInstruction visitF64Neg(Instruction.F64Neg inst) throws Validator.ValidationException {
        return unaryOp(ValType.F64, Opcodes.DNEG);
    }

    @Override
    public SimpleInstruction visitF64Ceil(Instruction.F64Ceil inst) throws Validator.ValidationException {
        return unaryBytecode(ValType.F64, visitor -> {
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "ceil", "(D)D", false);
        });
    }

    @Override
    public SimpleInstruction visitF64Floor(Instruction.F64Floor inst) throws Validator.ValidationException {
        return unaryBytecode(ValType.F64, visitor -> {
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "floor", "(D)D", false);
        });
    }

    @Override
    public SimpleInstruction visitF64Trunc(Instruction.F64Trunc inst) throws Validator.ValidationException {
        return unaryBytecode(ValType.F64, visitor -> {
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
        });
    }

    @Override
    public SimpleInstruction visitF64Nearest(Instruction.F64Nearest inst) throws Validator.ValidationException {
        return unaryBytecode(ValType.F64, visitor -> {
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "rint", "(D)D", false);
        });
    }

    @Override
    public SimpleInstruction visitF64Sqrt(Instruction.F64Sqrt inst) throws Validator.ValidationException {
        return unaryBytecode(ValType.F64, visitor -> {
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "sqrt", "(D)D", false);
        });
    }

    @Override
    public SimpleInstruction visitF64Add(Instruction.F64Add inst) throws Validator.ValidationException {
        return binaryOp(ValType.F64, Opcodes.DADD);
    }

    @Override
    public SimpleInstruction visitF64Sub(Instruction.F64Sub inst) throws Validator.ValidationException {
        return binaryOp(ValType.F64, Opcodes.DSUB);
    }

    @Override
    public SimpleInstruction visitF64Mul(Instruction.F64Mul inst) throws Validator.ValidationException {
        return binaryOp(ValType.F64, Opcodes.DMUL);
    }

    @Override
    public SimpleInstruction visitF64Div(Instruction.F64Div inst) throws Validator.ValidationException {
        return binaryOp(ValType.F64, Opcodes.DDIV);
    }

    @Override
    public SimpleInstruction visitF64Min(Instruction.F64Min inst) throws Validator.ValidationException {
        return binaryBytecode(ValType.F64, visitor -> {
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "min", "(DD)D", false);
        });
    }

    @Override
    public SimpleInstruction visitF64Max(Instruction.F64Max inst) throws Validator.ValidationException {
        return binaryBytecode(ValType.F64, visitor -> {
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "max", "(DD)D", false);
        });
    }

    @Override
    public SimpleInstruction visitF64Copysign(Instruction.F64Copysign inst) throws Validator.ValidationException {
        return binaryBytecode(ValType.F64, visitor -> {
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "copySign", "(DD)D", false);
        });
    }

    @Override
    public SimpleInstruction visitI32WrapI64(Instruction.I32WrapI64 inst) throws Validator.ValidationException {
        return convert(ValType.I64, ValType.I32, visitor -> {
            visitor.visitInsn(Opcodes.L2I);
        });
    }

    @Override
    public SimpleInstruction visitI32TruncF32S(Instruction.I32TruncF32S inst) throws Validator.ValidationException {
        return convert(ValType.F32, ValType.I32, visitor -> {
            visitor.visitInsn(Opcodes.F2I);
        });
    }

    @Override
    public SimpleInstruction visitI32TruncF32U(Instruction.I32TruncF32U inst) throws Validator.ValidationException {
        return convert(ValType.F32, ValType.I32, visitor -> {
            visitor.visitInsn(Opcodes.F2L);
            visitor.visitInsn(Opcodes.L2I);
        });
    }

    @Override
    public SimpleInstruction visitI32TruncF64S(Instruction.I32TruncF64S inst) throws Validator.ValidationException {
        return convert(ValType.F64, ValType.I32, visitor -> {
            visitor.visitInsn(Opcodes.D2I);
        });
    }

    @Override
    public SimpleInstruction visitI32TruncF64U(Instruction.I32TruncF64U inst) throws Validator.ValidationException {
        return convert(ValType.F64, ValType.I32, visitor -> {
            //TODO: Might not be correct? Unsure
            visitor.visitInsn(Opcodes.D2L);
            visitor.visitInsn(Opcodes.L2I);
        });
    }

    @Override
    public SimpleInstruction visitI64ExtendI32S(Instruction.I64ExtendI32S inst) throws Validator.ValidationException {
        return convert(ValType.I32, ValType.I64, visitor -> {
            visitor.visitInsn(Opcodes.I2L);
        });
    }

    @Override
    public SimpleInstruction visitI64ExtendI32U(Instruction.I64ExtendI32U inst) throws Validator.ValidationException {
        return convert(ValType.I32, ValType.I64, visitor -> {
            visitor.visitInsn(Opcodes.I2L);
            BytecodeHelper.constLong(visitor, 0xFFFFFFFFL);
            visitor.visitInsn(Opcodes.LAND);
        });
    }

    @Override
    public SimpleInstruction visitI64TruncF32S(Instruction.I64TruncF32S inst) throws Validator.ValidationException {
        return convert(ValType.F32, ValType.I64, visitor -> {
            visitor.visitInsn(Opcodes.F2L);
        });
    }

    public static long doubleToUnsignedLong(double value) {
        if (value <= 0) return 0;
        long oddBit = (long) (value % 2);
        return (((long) (value / 2)) << 1) | oddBit;
    }

    @Override
    public SimpleInstruction visitI64TruncF32U(Instruction.I64TruncF32U inst) throws Validator.ValidationException {
        return convert(ValType.F32, ValType.I64, visitor -> {
            visitor.visitInsn(Opcodes.F2D);
            BytecodeHelper.callNamedStaticMethod("doubleToUnsignedLong", visitor, InstructionConversionVisitor.class);
        });
    }

    @Override
    public SimpleInstruction visitI64TruncF64S(Instruction.I64TruncF64S inst) throws Validator.ValidationException {
        return convert(ValType.F64, ValType.I64, visitor -> {
            visitor.visitInsn(Opcodes.D2L);
        });
    }

    @Override
    public SimpleInstruction visitI64TruncF64U(Instruction.I64TruncF64U inst) throws Validator.ValidationException {
        return convert(ValType.F64, ValType.I64, visitor -> {
            BytecodeHelper.callNamedStaticMethod("doubleToUnsignedLong", visitor, InstructionConversionVisitor.class);
        });
    }

    @Override
    public SimpleInstruction visitF32ConvertI32S(Instruction.F32ConvertI32S inst) throws Validator.ValidationException {
        return convert(ValType.I32, ValType.F32, visitor -> {
            visitor.visitInsn(Opcodes.I2F);
        });
    }

    @Override
    public SimpleInstruction visitF32ConvertI32U(Instruction.F32ConvertI32U inst) throws Validator.ValidationException {
        return convert(ValType.I32, ValType.F32, visitor -> {
            visitor.visitInsn(Opcodes.I2L);
            BytecodeHelper.constLong(visitor, 0xFFFFFFFFL);
            visitor.visitInsn(Opcodes.LAND);
            visitor.visitInsn(Opcodes.L2F);
        });
    }

    @Override
    public SimpleInstruction visitF32ConvertI64S(Instruction.F32ConvertI64S inst) throws Validator.ValidationException {
        return convert(ValType.I64, ValType.F32, visitor -> {
            visitor.visitInsn(Opcodes.L2F);
        });
    }

    public static double unsignedLongToDouble(long value) {
        double dValue = (double) (value & 0x7fffffffffffffffL);
        if (value < 0) dValue += 0x1.0p63;
        return dValue;
    }

    @Override
    public SimpleInstruction visitF32ConvertI64U(Instruction.F32ConvertI64U inst) throws Validator.ValidationException {
        return convert(ValType.I64, ValType.F32, visitor -> {
            BytecodeHelper.callNamedStaticMethod("unsignedLongToDouble", visitor, InstructionConversionVisitor.class);
            visitor.visitInsn(Opcodes.D2F);
        });
    }

    @Override
    public SimpleInstruction visitF32DemoteF64(Instruction.F32DemoteF64 inst) throws Validator.ValidationException {
        return convert(ValType.F64, ValType.F32, visitor -> {
            visitor.visitInsn(Opcodes.D2F);
        });
    }

    @Override
    public SimpleInstruction visitF64ConvertI32S(Instruction.F64ConvertI32S inst) throws Validator.ValidationException {
        return convert(ValType.I32, ValType.F64, visitor -> {
            visitor.visitInsn(Opcodes.I2D);
        });
    }

    @Override
    public SimpleInstruction visitF64ConvertI32U(Instruction.F64ConvertI32U inst) throws Validator.ValidationException {
        return convert(ValType.I32, ValType.F64, visitor -> {
            visitor.visitInsn(Opcodes.I2L);
            BytecodeHelper.constLong(visitor, 0xFFFFFFFFL);
            visitor.visitInsn(Opcodes.LAND);
            visitor.visitInsn(Opcodes.L2D);
        });
    }

    @Override
    public SimpleInstruction visitF64ConvertI64S(Instruction.F64ConvertI64S inst) throws Validator.ValidationException {
        return convert(ValType.I64, ValType.F64, visitor -> {
            visitor.visitInsn(Opcodes.L2D);
        });
    }

    @Override
    public SimpleInstruction visitF64ConvertI64U(Instruction.F64ConvertI64U inst) throws Validator.ValidationException {
        return convert(ValType.I64, ValType.F64, visitor -> {
            BytecodeHelper.callNamedStaticMethod("unsignedLongToDouble", visitor, InstructionConversionVisitor.class);
        });
    }

    @Override
    public SimpleInstruction visitF64PromoteF32(Instruction.F64PromoteF32 inst) throws Validator.ValidationException {
        return convert(ValType.F32, ValType.F64, visitor -> {
            visitor.visitInsn(Opcodes.F2D);
        });
    }

    @Override
    public SimpleInstruction visitI32ReinterpretF32(Instruction.I32ReinterpretF32 inst) throws Validator.ValidationException {
        return convert(ValType.F32, ValType.I32, visitor -> {
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Float", "floatToRawIntBits", "(F)I", false);
        });
    }

    @Override
    public SimpleInstruction visitI64ReinterpretF64(Instruction.I64ReinterpretF64 inst) throws Validator.ValidationException {
        return convert(ValType.F64, ValType.I64, visitor -> {
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Double", "doubleToRawLongBits", "(D)J", false);
        });
    }

    @Override
    public SimpleInstruction visitF32ReinterpretI32(Instruction.F32ReinterpretI32 inst) throws Validator.ValidationException {
        return convert(ValType.I32, ValType.F32, visitor -> {
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Float", "intBitsToFloat", "(I)F", false);
        });
    }

    @Override
    public SimpleInstruction visitF64ReinterpretI64(Instruction.F64ReinterpretI64 inst) throws Validator.ValidationException {
        return convert(ValType.I64, ValType.F64, visitor -> {
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Double", "longBitsToDouble", "(J)D", false);
        });
    }

    @Override
    public SimpleInstruction visitI32Extend8S(Instruction.I32Extend8S inst) throws Validator.ValidationException {
        return convert(ValType.I32, ValType.I32, visitor -> {
            visitor.visitInsn(Opcodes.I2B);
        });
    }

    @Override
    public SimpleInstruction visitI32Extend16S(Instruction.I32Extend16S inst) throws Validator.ValidationException {
        return convert(ValType.I32, ValType.I32, visitor -> {
            visitor.visitInsn(Opcodes.I2S);
        });
    }

    @Override
    public SimpleInstruction visitI64Extend8S(Instruction.I64Extend8S inst) throws Validator.ValidationException {
        return convert(ValType.I64, ValType.I64, visitor -> {
            visitor.visitInsn(Opcodes.L2I);
            visitor.visitInsn(Opcodes.I2B);
            visitor.visitInsn(Opcodes.I2L);
        });
    }

    @Override
    public SimpleInstruction visitI64Extend16S(Instruction.I64Extend16S inst) throws Validator.ValidationException {
        return convert(ValType.I64, ValType.I64, visitor -> {
            visitor.visitInsn(Opcodes.L2I);
            visitor.visitInsn(Opcodes.I2S);
            visitor.visitInsn(Opcodes.I2L);
        });
    }

    @Override
    public SimpleInstruction visitI64Extend32S(Instruction.I64Extend32S inst) throws Validator.ValidationException {
        return convert(ValType.I64, ValType.I64, visitor -> {
            visitor.visitInsn(Opcodes.L2I);
            visitor.visitInsn(Opcodes.I2L);
        });
    }

    public static int truncSatFloatToIntSigned(float z) {
        if (Float.isNaN(z)) return 0;
        if (z < Integer.MIN_VALUE) return Integer.MIN_VALUE;
        if (z > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) z;
    }
    @Override
    public SimpleInstruction visitI32TruncSatF32S(Instruction.I32TruncSatF32S inst) throws Validator.ValidationException {
        return convert(ValType.F32, ValType.I32, visitor -> {
            BytecodeHelper.callNamedStaticMethod("truncSatFloatToIntSigned", visitor, InstructionConversionVisitor.class);
        });
    }

    public static int truncSatFloatToIntUnsigned(float z) {
        if (Float.isNaN(z)) return 0;
        if (z > 0xFFFFFFFFL) return -1;
        if (z < 0) return 0;
        return (int) z;
    }
    @Override
    public SimpleInstruction visitI32TruncSatF32U(Instruction.I32TruncSatF32U inst) throws Validator.ValidationException {
        return convert(ValType.F32, ValType.I32, visitor -> {
            BytecodeHelper.callNamedStaticMethod("truncSatFloatToIntUnsigned", visitor, InstructionConversionVisitor.class);
        });
    }

    public static int truncSatDoubleToIntSigned(double z) {
        if (Double.isNaN(z)) return 0;
        if (z < Integer.MIN_VALUE) return Integer.MIN_VALUE;
        if (z > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) z;
    }
    @Override
    public SimpleInstruction visitI32TruncSatF64S(Instruction.I32TruncSatF64S inst) throws Validator.ValidationException {
        return convert(ValType.F64, ValType.I32, visitor -> {
            BytecodeHelper.callNamedStaticMethod("truncSatDoubleToIntSigned", visitor, InstructionConversionVisitor.class);
        });
    }

    public static int truncSatDoubleToIntUnsigned(double z) {
        if (Double.isNaN(z)) return 0;
        if (z > 0xFFFFFFFFL) return -1;
        if (z < 0) return 0;
        return (int) z;
    }
    @Override
    public SimpleInstruction visitI32TruncSatF64U(Instruction.I32TruncSatF64U inst) throws Validator.ValidationException {
        return convert(ValType.F64, ValType.I32, visitor -> {
            BytecodeHelper.callNamedStaticMethod("truncSatDoubleToIntUnsigned", visitor, InstructionConversionVisitor.class);
        });
    }

    public static long truncSatFloatToLongSigned(float z) {
        if (Float.isNaN(z)) return 0;
        if (z < Long.MIN_VALUE) return Long.MIN_VALUE;
        if (z > Long.MAX_VALUE) return Long.MAX_VALUE;
        return (long) z;
    }
    @Override
    public SimpleInstruction visitI64TruncSatF32S(Instruction.I64TruncSatF32S inst) throws Validator.ValidationException {
        return convert(ValType.F32, ValType.I64, visitor -> {
            BytecodeHelper.callNamedStaticMethod("truncSatFloatToLongSigned", visitor, InstructionConversionVisitor.class);
        });
    }

    public static long truncSatFloatToLongUnsigned(float z) {
        if (Float.isNaN(z)) return 0;
        if (z > (Long.MAX_VALUE * 2f + 1f)) return -1;
        if (z < 0) return 0;
        return (long) z;
    }
    @Override
    public SimpleInstruction visitI64TruncSatF32U(Instruction.I64TruncSatF32U inst) throws Validator.ValidationException {
        return convert(ValType.F32, ValType.I64, visitor -> {
            BytecodeHelper.callNamedStaticMethod("truncSatFloatToLongUnsigned", visitor, InstructionConversionVisitor.class);
        });
    }

    public static long truncSatDoubleToLongSigned(double z) {
        if (Double.isNaN(z)) return 0;
        if (z < Long.MIN_VALUE) return Long.MIN_VALUE;
        if (z > Long.MAX_VALUE) return Long.MAX_VALUE;
        return (long) z;
    }
    @Override
    public SimpleInstruction visitI64TruncSatF64S(Instruction.I64TruncSatF64S inst) throws Validator.ValidationException {
        return convert(ValType.F64, ValType.I64, visitor -> {
            BytecodeHelper.callNamedStaticMethod("truncSatDoubleToLongSigned", visitor, InstructionConversionVisitor.class);
        });
    }

    public static long truncSatDoubleToLongUnsigned(double z) {
        if (Double.isNaN(z)) return 0;
        if (z > (Long.MAX_VALUE * 2d + 1d)) return -1;
        if (z < 0) return 0;
        return (long) z;
    }
    @Override
    public SimpleInstruction visitI64TruncSatF64U(Instruction.I64TruncSatF64U inst) throws Validator.ValidationException {
        return convert(ValType.F64, ValType.I64, visitor -> {
            BytecodeHelper.callNamedStaticMethod("truncSatDoubleToLongUnsigned", visitor, InstructionConversionVisitor.class);
        });
    }

    @Override
    public SimpleInstruction visitV128Load(Instruction.V128Load inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitV128Load8x8S(Instruction.V128Load8x8S inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitV128Load8x8U(Instruction.V128Load8x8U inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitV128Load16x4S(Instruction.V128Load16x4S inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitV128Load16x4U(Instruction.V128Load16x4U inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitV128Load32x2S(Instruction.V128Load32x2S inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitV128Load32x2U(Instruction.V128Load32x2U inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitV128Load8Splat(Instruction.V128Load8Splat inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitV128Load16Splat(Instruction.V128Load16Splat inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitV128Load32Splat(Instruction.V128Load32Splat inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitV128Load64Splat(Instruction.V128Load64Splat inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitV128Load32Zero(Instruction.V128Load32Zero inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitV128Load64Zero(Instruction.V128Load64Zero inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitV128Store(Instruction.V128Store inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitV128Load8Lane(Instruction.V128Load8Lane inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitV128Load16Lane(Instruction.V128Load16Lane inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitV128Load32Lane(Instruction.V128Load32Lane inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitV128Load64Lane(Instruction.V128Load64Lane inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitV128Store8Lane(Instruction.V128Store8Lane inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitV128Store16Lane(Instruction.V128Store16Lane inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitV128Store32Lane(Instruction.V128Store32Lane inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitV128Store64Lane(Instruction.V128Store64Lane inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitV128Const(Instruction.V128Const inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI8x16Shuffle(Instruction.I8x16Shuffle inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI8x16ExtractLaneS(Instruction.I8x16ExtractLaneS inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI8x16ExtractLaneU(Instruction.I8x16ExtractLaneU inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI8x16ReplaceLane(Instruction.I8x16ReplaceLane inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI16x8ExtractLaneS(Instruction.I16x8ExtractLaneS inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI16x8ExtractLaneU(Instruction.I16x8ExtractLaneU inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI16x8ReplaceLane(Instruction.I16x8ReplaceLane inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI32x4ExtractLane(Instruction.I32x4ExtractLane inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI32x4ReplaceLane(Instruction.I32x4ReplaceLane inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI64x2ExtractLane(Instruction.I64x2ExtractLane inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI64x2ReplaceLane(Instruction.I64x2ReplaceLane inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF32x4ExtractLane(Instruction.F32x4ExtractLane inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF32x4ReplaceLane(Instruction.F32x4ReplaceLane inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF64x2ExtractLane(Instruction.F64x2ExtractLane inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF64x2ReplaceLane(Instruction.F64x2ReplaceLane inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI8x16Swizzle(Instruction.I8x16Swizzle inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI8x16Splat(Instruction.I8x16Splat inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI16x8Splat(Instruction.I16x8Splat inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI32x4Splat(Instruction.I32x4Splat inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI64x2Splat(Instruction.I64x2Splat inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF32x4Splat(Instruction.F32x4Splat inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF64x2Splat(Instruction.F64x2Splat inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI8x16Eq(Instruction.I8x16Eq inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI8x16Ne(Instruction.I8x16Ne inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI8x16LtS(Instruction.I8x16LtS inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI8x16LtU(Instruction.I8x16LtU inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI8x16GtS(Instruction.I8x16GtS inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI8x16GtU(Instruction.I8x16GtU inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI8x16LeS(Instruction.I8x16LeS inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI8x16LeU(Instruction.I8x16LeU inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI8x16GeS(Instruction.I8x16GeS inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI8x16GeU(Instruction.I8x16GeU inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI16x8Eq(Instruction.I16x8Eq inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI16x8Ne(Instruction.I16x8Ne inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI16x8LtS(Instruction.I16x8LtS inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI16x8LtU(Instruction.I16x8LtU inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI16x8GtS(Instruction.I16x8GtS inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI16x8GtU(Instruction.I16x8GtU inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI16x8LeS(Instruction.I16x8LeS inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI16x8LeU(Instruction.I16x8LeU inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI16x8GeS(Instruction.I16x8GeS inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI16x8GeU(Instruction.I16x8GeU inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI32x4Eq(Instruction.I32x4Eq inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI32x4Ne(Instruction.I32x4Ne inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI32x4LtS(Instruction.I32x4LtS inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI32x4LtU(Instruction.I32x4LtU inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI32x4GtS(Instruction.I32x4GtS inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI32x4GtU(Instruction.I32x4GtU inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI32x4LeS(Instruction.I32x4LeS inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI32x4LeU(Instruction.I32x4LeU inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI32x4GeS(Instruction.I32x4GeS inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI32x4GeU(Instruction.I32x4GeU inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI64x2Eq(Instruction.I64x2Eq inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI64x2Ne(Instruction.I64x2Ne inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI64x2LtS(Instruction.I64x2LtS inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI64x2GtS(Instruction.I64x2GtS inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI64x2LeS(Instruction.I64x2LeS inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI64x2GeS(Instruction.I64x2GeS inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF32x4Eq(Instruction.F32x4Eq inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF32x4Ne(Instruction.F32x4Ne inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF32x4Lt(Instruction.F32x4Lt inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF32x4Gt(Instruction.F32x4Gt inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF32x4Le(Instruction.F32x4Le inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF32x4Ge(Instruction.F32x4Ge inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF64x2Eq(Instruction.F64x2Eq inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF64x2Ne(Instruction.F64x2Ne inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF64x2Lt(Instruction.F64x2Lt inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF64x2Gt(Instruction.F64x2Gt inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF64x2Le(Instruction.F64x2Le inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF64x2Ge(Instruction.F64x2Ge inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitV128Not(Instruction.V128Not inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitV128And(Instruction.V128And inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitV128AndNot(Instruction.V128AndNot inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitV128Or(Instruction.V128Or inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitV128Xor(Instruction.V128Xor inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitV128Bitselect(Instruction.V128Bitselect inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitV128AnyTrue(Instruction.V128AnyTrue inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI8x16Abs(Instruction.I8x16Abs inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI8x16Neg(Instruction.I8x16Neg inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI8x16PopCnt(Instruction.I8x16PopCnt inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI8x16AllTrue(Instruction.I8x16AllTrue inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI8x16Bitmask(Instruction.I8x16Bitmask inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI8x16NarrowI16x8S(Instruction.I8x16NarrowI16x8S inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI8x16NarrowI16x8U(Instruction.I8x16NarrowI16x8U inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI8x16Shl(Instruction.I8x16Shl inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI8x16ShrS(Instruction.I8x16ShrS inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI8x16ShrU(Instruction.I8x16ShrU inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI8x16Add(Instruction.I8x16Add inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI8x16AddSatS(Instruction.I8x16AddSatS inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI8x16AddSatU(Instruction.I8x16AddSatU inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI8x16Sub(Instruction.I8x16Sub inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI8x16SubSatS(Instruction.I8x16SubSatS inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI8x16SubSatU(Instruction.I8x16SubSatU inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI8x16MinS(Instruction.I8x16MinS inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI8x16MinU(Instruction.I8x16MinU inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI8x16MaxS(Instruction.I8x16MaxS inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI8x16MaxU(Instruction.I8x16MaxU inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI8x16AvgrU(Instruction.I8x16AvgrU inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI16x8ExtAddPairwiseI8x16S(Instruction.I16x8ExtAddPairwiseI8x16S inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI16x8ExtAddPairwiseI8x16U(Instruction.I16x8ExtAddPairwiseI8x16U inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI16x8Abs(Instruction.I16x8Abs inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI16x8Neg(Instruction.I16x8Neg inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI16x8Q15MulrSatS(Instruction.I16x8Q15MulrSatS inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI16x8AllTrue(Instruction.I16x8AllTrue inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI16x8Bitmask(Instruction.I16x8Bitmask inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI16x8NarrowI32x4S(Instruction.I16x8NarrowI32x4S inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI16x8NarrowI32x4U(Instruction.I16x8NarrowI32x4U inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI16x8ExtendLowI8x16S(Instruction.I16x8ExtendLowI8x16S inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI16x8ExtendHighI8x16S(Instruction.I16x8ExtendHighI8x16S inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI16x8ExtendLowI8x16U(Instruction.I16x8ExtendLowI8x16U inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI16x8ExtendHighI8x16U(Instruction.I16x8ExtendHighI8x16U inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI16x8Shl(Instruction.I16x8Shl inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI16x8ShrS(Instruction.I16x8ShrS inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI16x8ShrU(Instruction.I16x8ShrU inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI16x8Add(Instruction.I16x8Add inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI16x8AddSatS(Instruction.I16x8AddSatS inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI16x8AddSatU(Instruction.I16x8AddSatU inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI16x8Sub(Instruction.I16x8Sub inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI16x8SubSatS(Instruction.I16x8SubSatS inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI16x8SubSatU(Instruction.I16x8SubSatU inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI16x8Mul(Instruction.I16x8Mul inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI16x8MinS(Instruction.I16x8MinS inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI16x8MinU(Instruction.I16x8MinU inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI16x8MaxS(Instruction.I16x8MaxS inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI16x8MaxU(Instruction.I16x8MaxU inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI16x8AvgrU(Instruction.I16x8AvgrU inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI16x8ExtMulLowI8x16S(Instruction.I16x8ExtMulLowI8x16S inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI16x8ExtMulHighI8x16S(Instruction.I16x8ExtMulHighI8x16S inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI16x8ExtMulLowI8x16U(Instruction.I16x8ExtMulLowI8x16U inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI16x8ExtMulHighI8x16U(Instruction.I16x8ExtMulHighI8x16U inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI32x4ExtAddPairwiseI16x8S(Instruction.I32x4ExtAddPairwiseI16x8S inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI32x4ExtAddPairwiseI16x8U(Instruction.I32x4ExtAddPairwiseI16x8U inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI32x4Abs(Instruction.I32x4Abs inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI32x4Neg(Instruction.I32x4Neg inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI32x4AllTrue(Instruction.I32x4AllTrue inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI32x4Bitmask(Instruction.I32x4Bitmask inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI32x4ExtendLowI16x8S(Instruction.I32x4ExtendLowI16x8S inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI32x4ExtendHighI16x8S(Instruction.I32x4ExtendHighI16x8S inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI32x4ExtendLowI16x8U(Instruction.I32x4ExtendLowI16x8U inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI32x4ExtendHighI16x8U(Instruction.I32x4ExtendHighI16x8U inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI32x4Shl(Instruction.I32x4Shl inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI32x4ShrS(Instruction.I32x4ShrS inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI32x4ShrU(Instruction.I32x4ShrU inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI32x4Add(Instruction.I32x4Add inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI32x4Sub(Instruction.I32x4Sub inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI32x4Mul(Instruction.I32x4Mul inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI32x4MinS(Instruction.I32x4MinS inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI32x4MinU(Instruction.I32x4MinU inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI32x4MaxS(Instruction.I32x4MaxS inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI32x4MaxU(Instruction.I32x4MaxU inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI32x4DotI16x8S(Instruction.I32x4DotI16x8S inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI32x4ExtMulLowI16x8S(Instruction.I32x4ExtMulLowI16x8S inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI32x4ExtMulHighI16x8S(Instruction.I32x4ExtMulHighI16x8S inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI32x4ExtMulLowI16x8U(Instruction.I32x4ExtMulLowI16x8U inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI32x4ExtMulHighI16x8U(Instruction.I32x4ExtMulHighI16x8U inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI64x2Abs(Instruction.I64x2Abs inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI64x2Neg(Instruction.I64x2Neg inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI64x2AllTrue(Instruction.I64x2AllTrue inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI64x2Bitmask(Instruction.I64x2Bitmask inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI64x2ExtendLowI32x4S(Instruction.I64x2ExtendLowI32x4S inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI64x2ExtendHighI32x4S(Instruction.I64x2ExtendHighI32x4S inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI64x2ExtendLowI32x4U(Instruction.I64x2ExtendLowI32x4U inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI64x2ExtendHighI32x4U(Instruction.I64x2ExtendHighI32x4U inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI64x2Shl(Instruction.I64x2Shl inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI64x2ShrS(Instruction.I64x2ShrS inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI64x2ShrU(Instruction.I64x2ShrU inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI64x2Add(Instruction.I64x2Add inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI64x2Sub(Instruction.I64x2Sub inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI64x2Mul(Instruction.I64x2Mul inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI64x2ExtMulLowI32x4S(Instruction.I64x2ExtMulLowI32x4S inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI64x2ExtMulHighI32x4S(Instruction.I64x2ExtMulHighI32x4S inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI64x2ExtMulLowI32x4U(Instruction.I64x2ExtMulLowI32x4U inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI64x2ExtMulHighI32x4U(Instruction.I64x2ExtMulHighI32x4U inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF32x4Ceil(Instruction.F32x4Ceil inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF32x4Floor(Instruction.F32x4Floor inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF32x4Trunc(Instruction.F32x4Trunc inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF32x4Nearest(Instruction.F32x4Nearest inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF32x4Abs(Instruction.F32x4Abs inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF32x4Neg(Instruction.F32x4Neg inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF32x4Sqrt(Instruction.F32x4Sqrt inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF32x4Add(Instruction.F32x4Add inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF32x4Sub(Instruction.F32x4Sub inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF32x4Mul(Instruction.F32x4Mul inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF32x4Div(Instruction.F32x4Div inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF32x4Min(Instruction.F32x4Min inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF32x4Max(Instruction.F32x4Max inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF32x4PMin(Instruction.F32x4PMin inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF32x4PMax(Instruction.F32x4PMax inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF64x2Ceil(Instruction.F64x2Ceil inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF64x2Floor(Instruction.F64x2Floor inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF64x2Trunc(Instruction.F64x2Trunc inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF64x2Nearest(Instruction.F64x2Nearest inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF64x2Abs(Instruction.F64x2Abs inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF64x2Neg(Instruction.F64x2Neg inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF64x2Sqrt(Instruction.F64x2Sqrt inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF64x2Add(Instruction.F64x2Add inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF64x2Sub(Instruction.F64x2Sub inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF64x2Mul(Instruction.F64x2Mul inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF64x2Div(Instruction.F64x2Div inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF64x2Min(Instruction.F64x2Min inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF64x2Max(Instruction.F64x2Max inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF64x2PMin(Instruction.F64x2PMin inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF64x2PMax(Instruction.F64x2PMax inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI32x4TruncSatF32x4S(Instruction.I32x4TruncSatF32x4S inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI32x4TruncSatF32x4U(Instruction.I32x4TruncSatF32x4U inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF32x4ConvertI32x4S(Instruction.F32x4ConvertI32x4S inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF32x4ConvertI32x4U(Instruction.F32x4ConvertI32x4U inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI32x4TruncSatF64x2SZero(Instruction.I32x4TruncSatF64x2SZero inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitI32x4TruncSatF64x2UZero(Instruction.I32x4TruncSatF64x2UZero inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF64x2ConvertLowI32x4S(Instruction.F64x2ConvertLowI32x4S inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF64x2ConvertLowI32x4U(Instruction.F64x2ConvertLowI32x4U inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF32x4DemoteF64x2Zero(Instruction.F32x4DemoteF64x2Zero inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public SimpleInstruction visitF64x2PromoteLowF32x4(Instruction.F64x2PromoteLowF32x4 inst) throws Validator.ValidationException {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
