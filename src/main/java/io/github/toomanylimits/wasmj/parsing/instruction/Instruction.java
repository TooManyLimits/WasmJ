package io.github.toomanylimits.wasmj.parsing.instruction;

import io.github.toomanylimits.wasmj.compiling.visitor.InstructionVisitor;
import io.github.toomanylimits.wasmj.parsing.ParseHelper;
import io.github.toomanylimits.wasmj.parsing.module.ModuleParseException;
import io.github.toomanylimits.wasmj.parsing.types.ValType;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

//https://webassembly.github.io/spec/core/binary/instructions.html#binary-expr
public sealed interface Instruction {
    <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T;

    //Control Instructions

    //These are fake instructions, not real ones. They're used for returning
    //when grabbing multiple instructions in succession.
    final class End implements Instruction { public static final End INSTANCE = new End(); private End() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitEnd(this); } }
    final class Else implements Instruction { public static final Else INSTANCE = new Else(); private Else() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitElse(this); } }

    final class Unreachable implements Instruction { public static final Unreachable INSTANCE = new Unreachable(); private Unreachable() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitUnreachable(this); } }
    final class Nop implements Instruction { public static final Nop INSTANCE = new Nop(); private Nop() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitNop(this); } }

    record Block(StackType blockType, List<Instruction> inside) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitBlock(this); } }
    record Loop(StackType blockType, List<Instruction> inside) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitLoop(this); } }
    record If(StackType blockType, List<Instruction> inside) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitIf(this); } }
    record IfElse(StackType blockType, List<Instruction> ifTrue, List<Instruction> ifFalse) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitIfElse(this); } }
    record Branch(int index) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitBranch(this); } }
    record BranchIf(int index) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitBranchIf(this); } }
    record BranchTable(List<Integer> indices, int defaultIndex) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitBranchTable(this); } }
    final class Return implements Instruction { public static final Return INSTANCE = new Return(); private Return() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitReturn(this); } }
    record Call(int index) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitCall(this); } }
    record CallIndirect(int typeIndex, int tableIndex) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitCallIndirect(this); } }

    //Reference Instructions
    record RefNull(ValType type) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitRefNull(this); } }
    final class RefIsNull implements Instruction { public static final RefIsNull INSTANCE = new RefIsNull(); private RefIsNull() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitRefIsNull(this); } }
    record RefFunc(int funcIndex) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitRefFunc(this); } }

    //Parametric Instructions
    final class Drop implements Instruction { public static final Drop INSTANCE = new Drop(); private Drop() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitDrop(this); } }
    final class Select implements Instruction { public static final Select INSTANCE = new Select(); private Select() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitSelect(this); } }
    record SelectFrom(List<ValType> types) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitSelectFrom(this); } }

    //Variable Instructions
    record LocalGet(int localIndex) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitLocalGet(this); } }
    record LocalSet(int localIndex) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitLocalSet(this); } }
    record LocalTee(int localIndex) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitLocalTee(this); } }
    record GlobalGet(int globalIndex) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitGlobalGet(this); } }
    record GlobalSet(int globalIndex) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitGlobalSet(this); } }

    //Table Instructions
    record TableGet(int tableIndex) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitTableGet(this); } }
    record TableSet(int tableIndex) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitTableSet(this); } }
    record TableInit(int elemIndex, int tableIndex) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitTableInit(this); } }
    record ElemDrop(int elemIndex) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitElemDrop(this); } }
    record TableGrow(int tableIndex) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitTableGrow(this); } }
    record TableSize(int tableIndex) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitTableSize(this); } }
    record TableCopy(int tableIndex1, int tableIndex2) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitTableCopy(this); } }
    record TableFill(int tableIndex) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitTableFill(this); } }

    //Memory Instructions
    record I32Load(int align, int offset) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32Load(this); } }
    record I64Load(int align, int offset) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64Load(this); } }
    record F32Load(int align, int offset) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32Load(this); } }
    record F64Load(int align, int offset) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64Load(this); } }
    record I32Load8S(int align, int offset) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32Load8S(this); } }
    record I32Load8U(int align, int offset) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32Load8U(this); } }
    record I32Load16S(int align, int offset) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32Load16S(this); } }
    record I32Load16U(int align, int offset) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32Load16U(this); } }
    record I64Load8S(int align, int offset) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64Load8S(this); } }
    record I64Load8U(int align, int offset) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64Load8U(this); } }
    record I64Load16S(int align, int offset) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64Load16S(this); } }
    record I64Load16U(int align, int offset) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64Load16U(this); } }
    record I64Load32S(int align, int offset) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64Load32S(this); } }
    record I64Load32U(int align, int offset) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64Load32U(this); } }
    record I32Store(int align, int offset) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32Store(this); } }
    record I64Store(int align, int offset) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64Store(this); } }
    record F32Store(int align, int offset) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32Store(this); } }
    record F64Store(int align, int offset) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64Store(this); } }
    record I32Store8(int align, int offset) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32Store8(this); } }
    record I32Store16(int align, int offset) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32Store16(this); } }
    record I64Store8(int align, int offset) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64Store8(this); } }
    record I64Store16(int align, int offset) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64Store16(this); } }
    record I64Store32(int align, int offset) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64Store32(this); } }
    final class MemorySize implements Instruction { public static final MemorySize INSTANCE = new MemorySize(); private MemorySize() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitMemorySize(this); } }
    final class MemoryGrow implements Instruction { public static final MemoryGrow INSTANCE = new MemoryGrow(); private MemoryGrow() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitMemoryGrow(this); } }
    record MemoryInit(int dataIndex) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitMemoryInit(this); } }
    record DataDrop(int dataIndex) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitDataDrop(this); } }
    final class MemoryCopy implements Instruction { public static final MemoryCopy INSTANCE = new MemoryCopy(); private MemoryCopy() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitMemoryCopy(this); } }
    final class MemoryFill implements Instruction { public static final MemoryFill INSTANCE = new MemoryFill(); private MemoryFill() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitMemoryFill(this); } }

    //Numeric Instructions
    record I32Const(int n) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32Const(this); } }
    record I64Const(long n) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64Const(this); } }
    record F32Const(float z) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32Const(this); } }
    record F64Const(double z) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64Const(this); } }

    final class I32Eqz implements Instruction { public static final I32Eqz INSTANCE = new I32Eqz(); private I32Eqz() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32Eqz(this); } }
    final class I32Eq implements Instruction { public static final I32Eq INSTANCE = new I32Eq(); private I32Eq() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32Eq(this); } }
    final class I32Ne implements Instruction { public static final I32Ne INSTANCE = new I32Ne(); private I32Ne() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32Ne(this); } }
    final class I32LtS implements Instruction { public static final I32LtS INSTANCE = new I32LtS(); private I32LtS() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32LtS(this); } }
    final class I32LtU implements Instruction { public static final I32LtU INSTANCE = new I32LtU(); private I32LtU() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32LtU(this); } }
    final class I32GtS implements Instruction { public static final I32GtS INSTANCE = new I32GtS(); private I32GtS() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32GtS(this); } }
    final class I32GtU implements Instruction { public static final I32GtU INSTANCE = new I32GtU(); private I32GtU() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32GtU(this); } }
    final class I32LeS implements Instruction { public static final I32LeS INSTANCE = new I32LeS(); private I32LeS() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32LeS(this); } }
    final class I32LeU implements Instruction { public static final I32LeU INSTANCE = new I32LeU(); private I32LeU() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32LeU(this); } }
    final class I32GeS implements Instruction { public static final I32GeS INSTANCE = new I32GeS(); private I32GeS() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32GeS(this); } }
    final class I32GeU implements Instruction { public static final I32GeU INSTANCE = new I32GeU(); private I32GeU() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32GeU(this); } }

    final class I64Eqz implements Instruction { public static final I64Eqz INSTANCE = new I64Eqz(); private I64Eqz() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64Eqz(this); } }
    final class I64Eq implements Instruction { public static final I64Eq INSTANCE = new I64Eq(); private I64Eq() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64Eq(this); } }
    final class I64Ne implements Instruction { public static final I64Ne INSTANCE = new I64Ne(); private I64Ne() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64Ne(this); } }
    final class I64LtS implements Instruction { public static final I64LtS INSTANCE = new I64LtS(); private I64LtS() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64LtS(this); } }
    final class I64LtU implements Instruction { public static final I64LtU INSTANCE = new I64LtU(); private I64LtU() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64LtU(this); } }
    final class I64GtS implements Instruction { public static final I64GtS INSTANCE = new I64GtS(); private I64GtS() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64GtS(this); } }
    final class I64GtU implements Instruction { public static final I64GtU INSTANCE = new I64GtU(); private I64GtU() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64GtU(this); } }
    final class I64LeS implements Instruction { public static final I64LeS INSTANCE = new I64LeS(); private I64LeS() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64LeS(this); } }
    final class I64LeU implements Instruction { public static final I64LeU INSTANCE = new I64LeU(); private I64LeU() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64LeU(this); } }
    final class I64GeS implements Instruction { public static final I64GeS INSTANCE = new I64GeS(); private I64GeS() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64GeS(this); } }
    final class I64GeU implements Instruction { public static final I64GeU INSTANCE = new I64GeU(); private I64GeU() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64GeU(this); } }

    final class F32Eq implements Instruction { public static final F32Eq INSTANCE = new F32Eq(); private F32Eq() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32Eq(this); } }
    final class F32Ne implements Instruction { public static final F32Ne INSTANCE = new F32Ne(); private F32Ne() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32Ne(this); } }
    final class F32Lt implements Instruction { public static final F32Lt INSTANCE = new F32Lt(); private F32Lt() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32Lt(this); } }
    final class F32Gt implements Instruction { public static final F32Gt INSTANCE = new F32Gt(); private F32Gt() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32Gt(this); } }
    final class F32Le implements Instruction { public static final F32Le INSTANCE = new F32Le(); private F32Le() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32Le(this); } }
    final class F32Ge implements Instruction { public static final F32Ge INSTANCE = new F32Ge(); private F32Ge() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32Ge(this); } }

    final class F64Eq implements Instruction { public static final F64Eq INSTANCE = new F64Eq(); private F64Eq() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64Eq(this); } }
    final class F64Ne implements Instruction { public static final F64Ne INSTANCE = new F64Ne(); private F64Ne() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64Ne(this); } }
    final class F64Lt implements Instruction { public static final F64Lt INSTANCE = new F64Lt(); private F64Lt() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64Lt(this); } }
    final class F64Gt implements Instruction { public static final F64Gt INSTANCE = new F64Gt(); private F64Gt() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64Gt(this); } }
    final class F64Le implements Instruction { public static final F64Le INSTANCE = new F64Le(); private F64Le() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64Le(this); } }
    final class F64Ge implements Instruction { public static final F64Ge INSTANCE = new F64Ge(); private F64Ge() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64Ge(this); } }

    final class I32Clz implements Instruction { public static final I32Clz INSTANCE = new I32Clz(); private I32Clz() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32Clz(this); } }
    final class I32Ctz implements Instruction { public static final I32Ctz INSTANCE = new I32Ctz(); private I32Ctz() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32Ctz(this); } }
    final class I32PopCnt implements Instruction { public static final I32PopCnt INSTANCE = new I32PopCnt(); private I32PopCnt() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32PopCnt(this); } }
    final class I32Add implements Instruction { public static final I32Add INSTANCE = new I32Add(); private I32Add() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32Add(this); } }
    final class I32Sub implements Instruction { public static final I32Sub INSTANCE = new I32Sub(); private I32Sub() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32Sub(this); } }
    final class I32Mul implements Instruction { public static final I32Mul INSTANCE = new I32Mul(); private I32Mul() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32Mul(this); } }
    final class I32DivS implements Instruction { public static final I32DivS INSTANCE = new I32DivS(); private I32DivS() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32DivS(this); } }
    final class I32DivU implements Instruction { public static final I32DivU INSTANCE = new I32DivU(); private I32DivU() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32DivU(this); } }
    final class I32RemS implements Instruction { public static final I32RemS INSTANCE = new I32RemS(); private I32RemS() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32RemS(this); } }
    final class I32RemU implements Instruction { public static final I32RemU INSTANCE = new I32RemU(); private I32RemU() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32RemU(this); } }
    final class I32And implements Instruction { public static final I32And INSTANCE = new I32And(); private I32And() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32And(this); } }
    final class I32Or implements Instruction { public static final I32Or INSTANCE = new I32Or(); private I32Or() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32Or(this); } }
    final class I32Xor implements Instruction { public static final I32Xor INSTANCE = new I32Xor(); private I32Xor() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32Xor(this); } }
    final class I32Shl implements Instruction { public static final I32Shl INSTANCE = new I32Shl(); private I32Shl() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32Shl(this); } }
    final class I32ShrS implements Instruction { public static final I32ShrS INSTANCE = new I32ShrS(); private I32ShrS() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32ShrS(this); } }
    final class I32ShrU implements Instruction { public static final I32ShrU INSTANCE = new I32ShrU(); private I32ShrU() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32ShrU(this); } }
    final class I32Rotl implements Instruction { public static final I32Rotl INSTANCE = new I32Rotl(); private I32Rotl() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32Rotl(this); } }
    final class I32Rotr implements Instruction { public static final I32Rotr INSTANCE = new I32Rotr(); private I32Rotr() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32Rotr(this); } }

    final class I64Clz implements Instruction { public static final I64Clz INSTANCE = new I64Clz(); private I64Clz() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64Clz(this); } }
    final class I64Ctz implements Instruction { public static final I64Ctz INSTANCE = new I64Ctz(); private I64Ctz() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64Ctz(this); } }
    final class I64PopCnt implements Instruction { public static final I64PopCnt INSTANCE = new I64PopCnt(); private I64PopCnt() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64PopCnt(this); } }
    final class I64Add implements Instruction { public static final I64Add INSTANCE = new I64Add(); private I64Add() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64Add(this); } }
    final class I64Sub implements Instruction { public static final I64Sub INSTANCE = new I64Sub(); private I64Sub() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64Sub(this); } }
    final class I64Mul implements Instruction { public static final I64Mul INSTANCE = new I64Mul(); private I64Mul() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64Mul(this); } }
    final class I64DivS implements Instruction { public static final I64DivS INSTANCE = new I64DivS(); private I64DivS() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64DivS(this); } }
    final class I64DivU implements Instruction { public static final I64DivU INSTANCE = new I64DivU(); private I64DivU() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64DivU(this); } }
    final class I64RemS implements Instruction { public static final I64RemS INSTANCE = new I64RemS(); private I64RemS() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64RemS(this); } }
    final class I64RemU implements Instruction { public static final I64RemU INSTANCE = new I64RemU(); private I64RemU() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64RemU(this); } }
    final class I64And implements Instruction { public static final I64And INSTANCE = new I64And(); private I64And() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64And(this); } }
    final class I64Or implements Instruction { public static final I64Or INSTANCE = new I64Or(); private I64Or() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64Or(this); } }
    final class I64Xor implements Instruction { public static final I64Xor INSTANCE = new I64Xor(); private I64Xor() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64Xor(this); } }
    final class I64Shl implements Instruction { public static final I64Shl INSTANCE = new I64Shl(); private I64Shl() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64Shl(this); } }
    final class I64ShrS implements Instruction { public static final I64ShrS INSTANCE = new I64ShrS(); private I64ShrS() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64ShrS(this); } }
    final class I64ShrU implements Instruction { public static final I64ShrU INSTANCE = new I64ShrU(); private I64ShrU() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64ShrU(this); } }
    final class I64Rotl implements Instruction { public static final I64Rotl INSTANCE = new I64Rotl(); private I64Rotl() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64Rotl(this); } }
    final class I64Rotr implements Instruction { public static final I64Rotr INSTANCE = new I64Rotr(); private I64Rotr() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64Rotr(this); } }

    final class F32Abs implements Instruction { public static final F32Abs INSTANCE = new F32Abs(); private F32Abs() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32Abs(this); } }
    final class F32Neg implements Instruction { public static final F32Neg INSTANCE = new F32Neg(); private F32Neg() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32Neg(this); } }
    final class F32Ceil implements Instruction { public static final F32Ceil INSTANCE = new F32Ceil(); private F32Ceil() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32Ceil(this); } }
    final class F32Floor implements Instruction { public static final F32Floor INSTANCE = new F32Floor(); private F32Floor() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32Floor(this); } }
    final class F32Trunc implements Instruction { public static final F32Trunc INSTANCE = new F32Trunc(); private F32Trunc() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32Trunc(this); } }
    final class F32Nearest implements Instruction { public static final F32Nearest INSTANCE = new F32Nearest(); private F32Nearest() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32Nearest(this); } }
    final class F32Sqrt implements Instruction { public static final F32Sqrt INSTANCE = new F32Sqrt(); private F32Sqrt() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32Sqrt(this); } }
    final class F32Add implements Instruction { public static final F32Add INSTANCE = new F32Add(); private F32Add() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32Add(this); } }
    final class F32Sub implements Instruction { public static final F32Sub INSTANCE = new F32Sub(); private F32Sub() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32Sub(this); } }
    final class F32Mul implements Instruction { public static final F32Mul INSTANCE = new F32Mul(); private F32Mul() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32Mul(this); } }
    final class F32Div implements Instruction { public static final F32Div INSTANCE = new F32Div(); private F32Div() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32Div(this); } }
    final class F32Min implements Instruction { public static final F32Min INSTANCE = new F32Min(); private F32Min() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32Min(this); } }
    final class F32Max implements Instruction { public static final F32Max INSTANCE = new F32Max(); private F32Max() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32Max(this); } }
    final class F32Copysign implements Instruction { public static final F32Copysign INSTANCE = new F32Copysign(); private F32Copysign() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32Copysign(this); } }

    final class F64Abs implements Instruction { public static final F64Abs INSTANCE = new F64Abs(); private F64Abs() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64Abs(this); } }
    final class F64Neg implements Instruction { public static final F64Neg INSTANCE = new F64Neg(); private F64Neg() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64Neg(this); } }
    final class F64Ceil implements Instruction { public static final F64Ceil INSTANCE = new F64Ceil(); private F64Ceil() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64Ceil(this); } }
    final class F64Floor implements Instruction { public static final F64Floor INSTANCE = new F64Floor(); private F64Floor() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64Floor(this); } }
    final class F64Trunc implements Instruction { public static final F64Trunc INSTANCE = new F64Trunc(); private F64Trunc() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64Trunc(this); } }
    final class F64Nearest implements Instruction { public static final F64Nearest INSTANCE = new F64Nearest(); private F64Nearest() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64Nearest(this); } }
    final class F64Sqrt implements Instruction { public static final F64Sqrt INSTANCE = new F64Sqrt(); private F64Sqrt() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64Sqrt(this); } }
    final class F64Add implements Instruction { public static final F64Add INSTANCE = new F64Add(); private F64Add() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64Add(this); } }
    final class F64Sub implements Instruction { public static final F64Sub INSTANCE = new F64Sub(); private F64Sub() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64Sub(this); } }
    final class F64Mul implements Instruction { public static final F64Mul INSTANCE = new F64Mul(); private F64Mul() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64Mul(this); } }
    final class F64Div implements Instruction { public static final F64Div INSTANCE = new F64Div(); private F64Div() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64Div(this); } }
    final class F64Min implements Instruction { public static final F64Min INSTANCE = new F64Min(); private F64Min() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64Min(this); } }
    final class F64Max implements Instruction { public static final F64Max INSTANCE = new F64Max(); private F64Max() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64Max(this); } }
    final class F64Copysign implements Instruction { public static final F64Copysign INSTANCE = new F64Copysign(); private F64Copysign() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64Copysign(this); } }

    final class I32WrapI64 implements Instruction { public static final I32WrapI64 INSTANCE = new I32WrapI64(); private I32WrapI64() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32WrapI64(this); } }
    final class I32TruncF32S implements Instruction { public static final I32TruncF32S INSTANCE = new I32TruncF32S(); private I32TruncF32S() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32TruncF32S(this); } }
    final class I32TruncF32U implements Instruction { public static final I32TruncF32U INSTANCE = new I32TruncF32U(); private I32TruncF32U() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32TruncF32U(this); } }
    final class I32TruncF64S implements Instruction { public static final I32TruncF64S INSTANCE = new I32TruncF64S(); private I32TruncF64S() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32TruncF64S(this); } }
    final class I32TruncF64U implements Instruction { public static final I32TruncF64U INSTANCE = new I32TruncF64U(); private I32TruncF64U() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32TruncF64U(this); } }

    final class I64ExtendI32S implements Instruction { public static final I64ExtendI32S INSTANCE = new I64ExtendI32S(); private I64ExtendI32S() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64ExtendI32S(this); } }
    final class I64ExtendI32U implements Instruction { public static final I64ExtendI32U INSTANCE = new I64ExtendI32U(); private I64ExtendI32U() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64ExtendI32U(this); } }
    final class I64TruncF32S implements Instruction { public static final I64TruncF32S INSTANCE = new I64TruncF32S(); private I64TruncF32S() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64TruncF32S(this); } }
    final class I64TruncF32U implements Instruction { public static final I64TruncF32U INSTANCE = new I64TruncF32U(); private I64TruncF32U() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64TruncF32U(this); } }
    final class I64TruncF64S implements Instruction { public static final I64TruncF64S INSTANCE = new I64TruncF64S(); private I64TruncF64S() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64TruncF64S(this); } }
    final class I64TruncF64U implements Instruction { public static final I64TruncF64U INSTANCE = new I64TruncF64U(); private I64TruncF64U() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64TruncF64U(this); } }

    final class F32ConvertI32S implements Instruction { public static final F32ConvertI32S INSTANCE = new F32ConvertI32S(); private F32ConvertI32S() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32ConvertI32S(this); } }
    final class F32ConvertI32U implements Instruction { public static final F32ConvertI32U INSTANCE = new F32ConvertI32U(); private F32ConvertI32U() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32ConvertI32U(this); } }
    final class F32ConvertI64S implements Instruction { public static final F32ConvertI64S INSTANCE = new F32ConvertI64S(); private F32ConvertI64S() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32ConvertI64S(this); } }
    final class F32ConvertI64U implements Instruction { public static final F32ConvertI64U INSTANCE = new F32ConvertI64U(); private F32ConvertI64U() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32ConvertI64U(this); } }
    final class F32DemoteF64 implements Instruction { public static final F32DemoteF64 INSTANCE = new F32DemoteF64(); private F32DemoteF64() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32DemoteF64(this); } }

    final class F64ConvertI32S implements Instruction { public static final F64ConvertI32S INSTANCE = new F64ConvertI32S(); private F64ConvertI32S() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64ConvertI32S(this); } }
    final class F64ConvertI32U implements Instruction { public static final F64ConvertI32U INSTANCE = new F64ConvertI32U(); private F64ConvertI32U() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64ConvertI32U(this); } }
    final class F64ConvertI64S implements Instruction { public static final F64ConvertI64S INSTANCE = new F64ConvertI64S(); private F64ConvertI64S() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64ConvertI64S(this); } }
    final class F64ConvertI64U implements Instruction { public static final F64ConvertI64U INSTANCE = new F64ConvertI64U(); private F64ConvertI64U() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64ConvertI64U(this); } }
    final class F64PromoteF32 implements Instruction { public static final F64PromoteF32 INSTANCE = new F64PromoteF32(); private F64PromoteF32() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64PromoteF32(this); } }

    final class I32ReinterpretF32 implements Instruction { public static final I32ReinterpretF32 INSTANCE = new I32ReinterpretF32(); private I32ReinterpretF32() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32ReinterpretF32(this); } }
    final class I64ReinterpretF64 implements Instruction { public static final I64ReinterpretF64 INSTANCE = new I64ReinterpretF64(); private I64ReinterpretF64() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64ReinterpretF64(this); } }
    final class F32ReinterpretI32 implements Instruction { public static final F32ReinterpretI32 INSTANCE = new F32ReinterpretI32(); private F32ReinterpretI32() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32ReinterpretI32(this); } }
    final class F64ReinterpretI64 implements Instruction { public static final F64ReinterpretI64 INSTANCE = new F64ReinterpretI64(); private F64ReinterpretI64() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64ReinterpretI64(this); } }

    final class I32Extend8S implements Instruction { public static final I32Extend8S INSTANCE = new I32Extend8S(); private I32Extend8S() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32Extend8S(this); } }
    final class I32Extend16S implements Instruction { public static final I32Extend16S INSTANCE = new I32Extend16S(); private I32Extend16S() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32Extend16S(this); } }
    final class I64Extend8S implements Instruction { public static final I64Extend8S INSTANCE = new I64Extend8S(); private I64Extend8S() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64Extend8S(this); } }
    final class I64Extend16S implements Instruction { public static final I64Extend16S INSTANCE = new I64Extend16S(); private I64Extend16S() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64Extend16S(this); } }
    final class I64Extend32S implements Instruction { public static final I64Extend32S INSTANCE = new I64Extend32S(); private I64Extend32S() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64Extend32S(this); } }

    final class I32TruncSatF32S implements Instruction { public static final I32TruncSatF32S INSTANCE = new I32TruncSatF32S(); private I32TruncSatF32S() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32TruncSatF32S(this); } }
    final class I32TruncSatF32U implements Instruction { public static final I32TruncSatF32U INSTANCE = new I32TruncSatF32U(); private I32TruncSatF32U() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32TruncSatF32U(this); } }
    final class I32TruncSatF64S implements Instruction { public static final I32TruncSatF64S INSTANCE = new I32TruncSatF64S(); private I32TruncSatF64S() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32TruncSatF64S(this); } }
    final class I32TruncSatF64U implements Instruction { public static final I32TruncSatF64U INSTANCE = new I32TruncSatF64U(); private I32TruncSatF64U() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32TruncSatF64U(this); } }
    final class I64TruncSatF32S implements Instruction { public static final I64TruncSatF32S INSTANCE = new I64TruncSatF32S(); private I64TruncSatF32S() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64TruncSatF32S(this); } }
    final class I64TruncSatF32U implements Instruction { public static final I64TruncSatF32U INSTANCE = new I64TruncSatF32U(); private I64TruncSatF32U() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64TruncSatF32U(this); } }
    final class I64TruncSatF64S implements Instruction { public static final I64TruncSatF64S INSTANCE = new I64TruncSatF64S(); private I64TruncSatF64S() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64TruncSatF64S(this); } }
    final class I64TruncSatF64U implements Instruction { public static final I64TruncSatF64U INSTANCE = new I64TruncSatF64U(); private I64TruncSatF64U() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64TruncSatF64U(this); } }

    record V128Load(int align, int offset) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitV128Load(this); } }
    record V128Load8x8S(int align, int offset) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitV128Load8x8S(this); } }
    record V128Load8x8U(int align, int offset) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitV128Load8x8U(this); } }
    record V128Load16x4S(int align, int offset) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitV128Load16x4S(this); } }
    record V128Load16x4U(int align, int offset) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitV128Load16x4U(this); } }
    record V128Load32x2S(int align, int offset) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitV128Load32x2S(this); } }
    record V128Load32x2U(int align, int offset) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitV128Load32x2U(this); } }
    record V128Load8Splat(int align, int offset) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitV128Load8Splat(this); } }
    record V128Load16Splat(int align, int offset) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitV128Load16Splat(this); } }
    record V128Load32Splat(int align, int offset) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitV128Load32Splat(this); } }
    record V128Load64Splat(int align, int offset) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitV128Load64Splat(this); } }
    record V128Load32Zero(int align, int offset) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitV128Load32Zero(this); } }
    record V128Load64Zero(int align, int offset) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitV128Load64Zero(this); } }
    record V128Store(int align, int offset) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitV128Store(this); } }

    record V128Load8Lane(int align, int offset, byte laneIndex) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitV128Load8Lane(this); } }
    record V128Load16Lane(int align, int offset, byte laneIndex) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitV128Load16Lane(this); } }
    record V128Load32Lane(int align, int offset, byte laneIndex) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitV128Load32Lane(this); } }
    record V128Load64Lane(int align, int offset, byte laneIndex) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitV128Load64Lane(this); } }
    record V128Store8Lane(int align, int offset, byte laneIndex) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitV128Store8Lane(this); } }
    record V128Store16Lane(int align, int offset, byte laneIndex) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitV128Store16Lane(this); } }
    record V128Store32Lane(int align, int offset, byte laneIndex) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitV128Store32Lane(this); } }
    record V128Store64Lane(int align, int offset, byte laneIndex) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitV128Store64Lane(this); } }
    record V128Const(byte[] bytes) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitV128Const(this); } }

    record I8x16Shuffle(byte[] laneIndices) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI8x16Shuffle(this); } }
    record I8x16ExtractLaneS(byte laneIndex ) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI8x16ExtractLaneS(this); } }
    record I8x16ExtractLaneU(byte laneIndex ) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI8x16ExtractLaneU(this); } }
    record I8x16ReplaceLane(byte laneIndex ) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI8x16ReplaceLane(this); } }
    record I16x8ExtractLaneS(byte laneIndex ) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI16x8ExtractLaneS(this); } }
    record I16x8ExtractLaneU(byte laneIndex ) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI16x8ExtractLaneU(this); } }
    record I16x8ReplaceLane(byte laneIndex ) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI16x8ReplaceLane(this); } }
    record I32x4ExtractLane(byte laneIndex ) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32x4ExtractLane(this); } }
    record I32x4ReplaceLane(byte laneIndex ) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32x4ReplaceLane(this); } }
    record I64x2ExtractLane(byte laneIndex ) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64x2ExtractLane(this); } }
    record I64x2ReplaceLane(byte laneIndex ) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64x2ReplaceLane(this); } }
    record F32x4ExtractLane(byte laneIndex ) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32x4ExtractLane(this); } }
    record F32x4ReplaceLane(byte laneIndex ) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32x4ReplaceLane(this); } }
    record F64x2ExtractLane(byte laneIndex ) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64x2ExtractLane(this); } }
    record F64x2ReplaceLane(byte laneIndex ) implements Instruction { public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64x2ReplaceLane(this); } }

    final class I8x16Swizzle implements Instruction { public static final I8x16Swizzle INSTANCE = new I8x16Swizzle(); private I8x16Swizzle() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI8x16Swizzle(this); } }
    final class I8x16Splat implements Instruction { public static final I8x16Splat INSTANCE = new I8x16Splat(); private I8x16Splat() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI8x16Splat(this); } }
    final class I16x8Splat implements Instruction { public static final I16x8Splat INSTANCE = new I16x8Splat(); private I16x8Splat() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI16x8Splat(this); } }
    final class I32x4Splat implements Instruction { public static final I32x4Splat INSTANCE = new I32x4Splat(); private I32x4Splat() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32x4Splat(this); } }
    final class I64x2Splat implements Instruction { public static final I64x2Splat INSTANCE = new I64x2Splat(); private I64x2Splat() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64x2Splat(this); } }
    final class F32x4Splat implements Instruction { public static final F32x4Splat INSTANCE = new F32x4Splat(); private F32x4Splat() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32x4Splat(this); } }
    final class F64x2Splat implements Instruction { public static final F64x2Splat INSTANCE = new F64x2Splat(); private F64x2Splat() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64x2Splat(this); } }

    final class I8x16Eq implements Instruction { public static final I8x16Eq INSTANCE = new I8x16Eq(); private I8x16Eq() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI8x16Eq(this); } }
    final class I8x16Ne implements Instruction { public static final I8x16Ne INSTANCE = new I8x16Ne(); private I8x16Ne() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI8x16Ne(this); } }
    final class I8x16LtS implements Instruction { public static final I8x16LtS INSTANCE = new I8x16LtS(); private I8x16LtS() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI8x16LtS(this); } }
    final class I8x16LtU implements Instruction { public static final I8x16LtU INSTANCE = new I8x16LtU(); private I8x16LtU() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI8x16LtU(this); } }
    final class I8x16GtS implements Instruction { public static final I8x16GtS INSTANCE = new I8x16GtS(); private I8x16GtS() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI8x16GtS(this); } }
    final class I8x16GtU implements Instruction { public static final I8x16GtU INSTANCE = new I8x16GtU(); private I8x16GtU() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI8x16GtU(this); } }
    final class I8x16LeS implements Instruction { public static final I8x16LeS INSTANCE = new I8x16LeS(); private I8x16LeS() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI8x16LeS(this); } }
    final class I8x16LeU implements Instruction { public static final I8x16LeU INSTANCE = new I8x16LeU(); private I8x16LeU() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI8x16LeU(this); } }
    final class I8x16GeS implements Instruction { public static final I8x16GeS INSTANCE = new I8x16GeS(); private I8x16GeS() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI8x16GeS(this); } }
    final class I8x16GeU implements Instruction { public static final I8x16GeU INSTANCE = new I8x16GeU(); private I8x16GeU() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI8x16GeU(this); } }

    final class I16x8Eq implements Instruction { public static final I16x8Eq INSTANCE = new I16x8Eq(); private I16x8Eq() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI16x8Eq(this); } }
    final class I16x8Ne implements Instruction { public static final I16x8Ne INSTANCE = new I16x8Ne(); private I16x8Ne() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI16x8Ne(this); } }
    final class I16x8LtS implements Instruction { public static final I16x8LtS INSTANCE = new I16x8LtS(); private I16x8LtS() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI16x8LtS(this); } }
    final class I16x8LtU implements Instruction { public static final I16x8LtU INSTANCE = new I16x8LtU(); private I16x8LtU() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI16x8LtU(this); } }
    final class I16x8GtS implements Instruction { public static final I16x8GtS INSTANCE = new I16x8GtS(); private I16x8GtS() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI16x8GtS(this); } }
    final class I16x8GtU implements Instruction { public static final I16x8GtU INSTANCE = new I16x8GtU(); private I16x8GtU() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI16x8GtU(this); } }
    final class I16x8LeS implements Instruction { public static final I16x8LeS INSTANCE = new I16x8LeS(); private I16x8LeS() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI16x8LeS(this); } }
    final class I16x8LeU implements Instruction { public static final I16x8LeU INSTANCE = new I16x8LeU(); private I16x8LeU() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI16x8LeU(this); } }
    final class I16x8GeS implements Instruction { public static final I16x8GeS INSTANCE = new I16x8GeS(); private I16x8GeS() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI16x8GeS(this); } }
    final class I16x8GeU implements Instruction { public static final I16x8GeU INSTANCE = new I16x8GeU(); private I16x8GeU() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI16x8GeU(this); } }

    final class I32x4Eq implements Instruction { public static final I32x4Eq INSTANCE = new I32x4Eq(); private I32x4Eq() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32x4Eq(this); } }
    final class I32x4Ne implements Instruction { public static final I32x4Ne INSTANCE = new I32x4Ne(); private I32x4Ne() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32x4Ne(this); } }
    final class I32x4LtS implements Instruction { public static final I32x4LtS INSTANCE = new I32x4LtS(); private I32x4LtS() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32x4LtS(this); } }
    final class I32x4LtU implements Instruction { public static final I32x4LtU INSTANCE = new I32x4LtU(); private I32x4LtU() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32x4LtU(this); } }
    final class I32x4GtS implements Instruction { public static final I32x4GtS INSTANCE = new I32x4GtS(); private I32x4GtS() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32x4GtS(this); } }
    final class I32x4GtU implements Instruction { public static final I32x4GtU INSTANCE = new I32x4GtU(); private I32x4GtU() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32x4GtU(this); } }
    final class I32x4LeS implements Instruction { public static final I32x4LeS INSTANCE = new I32x4LeS(); private I32x4LeS() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32x4LeS(this); } }
    final class I32x4LeU implements Instruction { public static final I32x4LeU INSTANCE = new I32x4LeU(); private I32x4LeU() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32x4LeU(this); } }
    final class I32x4GeS implements Instruction { public static final I32x4GeS INSTANCE = new I32x4GeS(); private I32x4GeS() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32x4GeS(this); } }
    final class I32x4GeU implements Instruction { public static final I32x4GeU INSTANCE = new I32x4GeU(); private I32x4GeU() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32x4GeU(this); } }

    final class I64x2Eq implements Instruction { public static final I64x2Eq INSTANCE = new I64x2Eq(); private I64x2Eq() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64x2Eq(this); } }
    final class I64x2Ne implements Instruction { public static final I64x2Ne INSTANCE = new I64x2Ne(); private I64x2Ne() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64x2Ne(this); } }
    final class I64x2LtS implements Instruction { public static final I64x2LtS INSTANCE = new I64x2LtS(); private I64x2LtS() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64x2LtS(this); } }
    final class I64x2GtS implements Instruction { public static final I64x2GtS INSTANCE = new I64x2GtS(); private I64x2GtS() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64x2GtS(this); } }
    final class I64x2LeS implements Instruction { public static final I64x2LeS INSTANCE = new I64x2LeS(); private I64x2LeS() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64x2LeS(this); } }
    final class I64x2GeS implements Instruction { public static final I64x2GeS INSTANCE = new I64x2GeS(); private I64x2GeS() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64x2GeS(this); } }

    final class F32x4Eq implements Instruction { public static final F32x4Eq INSTANCE = new F32x4Eq(); private F32x4Eq() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32x4Eq(this); } }
    final class F32x4Ne implements Instruction { public static final F32x4Ne INSTANCE = new F32x4Ne(); private F32x4Ne() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32x4Ne(this); } }
    final class F32x4Lt implements Instruction { public static final F32x4Lt INSTANCE = new F32x4Lt(); private F32x4Lt() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32x4Lt(this); } }
    final class F32x4Gt implements Instruction { public static final F32x4Gt INSTANCE = new F32x4Gt(); private F32x4Gt() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32x4Gt(this); } }
    final class F32x4Le implements Instruction { public static final F32x4Le INSTANCE = new F32x4Le(); private F32x4Le() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32x4Le(this); } }
    final class F32x4Ge implements Instruction { public static final F32x4Ge INSTANCE = new F32x4Ge(); private F32x4Ge() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32x4Ge(this); } }

    final class F64x2Eq implements Instruction { public static final F64x2Eq INSTANCE = new F64x2Eq(); private F64x2Eq() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64x2Eq(this); } }
    final class F64x2Ne implements Instruction { public static final F64x2Ne INSTANCE = new F64x2Ne(); private F64x2Ne() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64x2Ne(this); } }
    final class F64x2Lt implements Instruction { public static final F64x2Lt INSTANCE = new F64x2Lt(); private F64x2Lt() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64x2Lt(this); } }
    final class F64x2Gt implements Instruction { public static final F64x2Gt INSTANCE = new F64x2Gt(); private F64x2Gt() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64x2Gt(this); } }
    final class F64x2Le implements Instruction { public static final F64x2Le INSTANCE = new F64x2Le(); private F64x2Le() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64x2Le(this); } }
    final class F64x2Ge implements Instruction { public static final F64x2Ge INSTANCE = new F64x2Ge(); private F64x2Ge() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64x2Ge(this); } }

    final class V128Not implements Instruction { public static final V128Not INSTANCE = new V128Not(); private V128Not() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitV128Not(this); } }
    final class V128And implements Instruction { public static final V128And INSTANCE = new V128And(); private V128And() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitV128And(this); } }
    final class V128AndNot implements Instruction { public static final V128AndNot INSTANCE = new V128AndNot(); private V128AndNot() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitV128AndNot(this); } }
    final class V128Or implements Instruction { public static final V128Or INSTANCE = new V128Or(); private V128Or() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitV128Or(this); } }
    final class V128Xor implements Instruction { public static final V128Xor INSTANCE = new V128Xor(); private V128Xor() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitV128Xor(this); } }
    final class V128Bitselect implements Instruction { public static final V128Bitselect INSTANCE = new V128Bitselect(); private V128Bitselect() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitV128Bitselect(this); } }
    final class V128AnyTrue implements Instruction { public static final V128AnyTrue INSTANCE = new V128AnyTrue(); private V128AnyTrue() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitV128AnyTrue(this); } }

    final class I8x16Abs implements Instruction { public static final I8x16Abs INSTANCE = new I8x16Abs(); private I8x16Abs() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI8x16Abs(this); } }
    final class I8x16Neg implements Instruction { public static final I8x16Neg INSTANCE = new I8x16Neg(); private I8x16Neg() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI8x16Neg(this); } }
    final class I8x16PopCnt implements Instruction { public static final I8x16PopCnt INSTANCE = new I8x16PopCnt(); private I8x16PopCnt() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI8x16PopCnt(this); } }
    final class I8x16AllTrue implements Instruction { public static final I8x16AllTrue INSTANCE = new I8x16AllTrue(); private I8x16AllTrue() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI8x16AllTrue(this); } }
    final class I8x16Bitmask implements Instruction { public static final I8x16Bitmask INSTANCE = new I8x16Bitmask(); private I8x16Bitmask() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI8x16Bitmask(this); } }
    final class I8x16NarrowI16x8S implements Instruction { public static final I8x16NarrowI16x8S INSTANCE = new I8x16NarrowI16x8S(); private I8x16NarrowI16x8S() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI8x16NarrowI16x8S(this); } }
    final class I8x16NarrowI16x8U implements Instruction { public static final I8x16NarrowI16x8U INSTANCE = new I8x16NarrowI16x8U(); private I8x16NarrowI16x8U() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI8x16NarrowI16x8U(this); } }
    final class I8x16Shl implements Instruction { public static final I8x16Shl INSTANCE = new I8x16Shl(); private I8x16Shl() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI8x16Shl(this); } }
    final class I8x16ShrS implements Instruction { public static final I8x16ShrS INSTANCE = new I8x16ShrS(); private I8x16ShrS() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI8x16ShrS(this); } }
    final class I8x16ShrU implements Instruction { public static final I8x16ShrU INSTANCE = new I8x16ShrU(); private I8x16ShrU() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI8x16ShrU(this); } }
    final class I8x16Add implements Instruction { public static final I8x16Add INSTANCE = new I8x16Add(); private I8x16Add() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI8x16Add(this); } }
    final class I8x16AddSatS implements Instruction { public static final I8x16AddSatS INSTANCE = new I8x16AddSatS(); private I8x16AddSatS() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI8x16AddSatS(this); } }
    final class I8x16AddSatU implements Instruction { public static final I8x16AddSatU INSTANCE = new I8x16AddSatU(); private I8x16AddSatU() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI8x16AddSatU(this); } }
    final class I8x16Sub implements Instruction { public static final I8x16Sub INSTANCE = new I8x16Sub(); private I8x16Sub() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI8x16Sub(this); } }
    final class I8x16SubSatS implements Instruction { public static final I8x16SubSatS INSTANCE = new I8x16SubSatS(); private I8x16SubSatS() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI8x16SubSatS(this); } }
    final class I8x16SubSatU implements Instruction { public static final I8x16SubSatU INSTANCE = new I8x16SubSatU(); private I8x16SubSatU() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI8x16SubSatU(this); } }
    final class I8x16MinS implements Instruction { public static final I8x16MinS INSTANCE = new I8x16MinS(); private I8x16MinS() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI8x16MinS(this); } }
    final class I8x16MinU implements Instruction { public static final I8x16MinU INSTANCE = new I8x16MinU(); private I8x16MinU() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI8x16MinU(this); } }
    final class I8x16MaxS implements Instruction { public static final I8x16MaxS INSTANCE = new I8x16MaxS(); private I8x16MaxS() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI8x16MaxS(this); } }
    final class I8x16MaxU implements Instruction { public static final I8x16MaxU INSTANCE = new I8x16MaxU(); private I8x16MaxU() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI8x16MaxU(this); } }
    final class I8x16AvgrU implements Instruction { public static final I8x16AvgrU INSTANCE = new I8x16AvgrU(); private I8x16AvgrU() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI8x16AvgrU(this); } }

    final class I16x8ExtAddPairwiseI8x16S implements Instruction { public static final I16x8ExtAddPairwiseI8x16S INSTANCE = new I16x8ExtAddPairwiseI8x16S(); private I16x8ExtAddPairwiseI8x16S() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI16x8ExtAddPairwiseI8x16S(this); } }
    final class I16x8ExtAddPairwiseI8x16U implements Instruction { public static final I16x8ExtAddPairwiseI8x16U INSTANCE = new I16x8ExtAddPairwiseI8x16U(); private I16x8ExtAddPairwiseI8x16U() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI16x8ExtAddPairwiseI8x16U(this); } }
    final class I16x8Abs implements Instruction { public static final I16x8Abs INSTANCE = new I16x8Abs(); private I16x8Abs() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI16x8Abs(this); } }
    final class I16x8Neg implements Instruction { public static final I16x8Neg INSTANCE = new I16x8Neg(); private I16x8Neg() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI16x8Neg(this); } }
    final class I16x8Q15MulrSatS implements Instruction { public static final I16x8Q15MulrSatS INSTANCE = new I16x8Q15MulrSatS(); private I16x8Q15MulrSatS() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI16x8Q15MulrSatS(this); } }
    final class I16x8AllTrue implements Instruction { public static final I16x8AllTrue INSTANCE = new I16x8AllTrue(); private I16x8AllTrue() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI16x8AllTrue(this); } }
    final class I16x8Bitmask implements Instruction { public static final I16x8Bitmask INSTANCE = new I16x8Bitmask(); private I16x8Bitmask() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI16x8Bitmask(this); } }
    final class I16x8NarrowI32x4S implements Instruction { public static final I16x8NarrowI32x4S INSTANCE = new I16x8NarrowI32x4S(); private I16x8NarrowI32x4S() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI16x8NarrowI32x4S(this); } }
    final class I16x8NarrowI32x4U implements Instruction { public static final I16x8NarrowI32x4U INSTANCE = new I16x8NarrowI32x4U(); private I16x8NarrowI32x4U() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI16x8NarrowI32x4U(this); } }
    final class I16x8ExtendLowI8x16S implements Instruction { public static final I16x8ExtendLowI8x16S INSTANCE = new I16x8ExtendLowI8x16S(); private I16x8ExtendLowI8x16S() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI16x8ExtendLowI8x16S(this); } }
    final class I16x8ExtendHighI8x16S implements Instruction { public static final I16x8ExtendHighI8x16S INSTANCE = new I16x8ExtendHighI8x16S(); private I16x8ExtendHighI8x16S() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI16x8ExtendHighI8x16S(this); } }
    final class I16x8ExtendLowI8x16U implements Instruction { public static final I16x8ExtendLowI8x16U INSTANCE = new I16x8ExtendLowI8x16U(); private I16x8ExtendLowI8x16U() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI16x8ExtendLowI8x16U(this); } }
    final class I16x8ExtendHighI8x16U implements Instruction { public static final I16x8ExtendHighI8x16U INSTANCE = new I16x8ExtendHighI8x16U(); private I16x8ExtendHighI8x16U() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI16x8ExtendHighI8x16U(this); } }
    final class I16x8Shl implements Instruction { public static final I16x8Shl INSTANCE = new I16x8Shl(); private I16x8Shl() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI16x8Shl(this); } }
    final class I16x8ShrS implements Instruction { public static final I16x8ShrS INSTANCE = new I16x8ShrS(); private I16x8ShrS() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI16x8ShrS(this); } }
    final class I16x8ShrU implements Instruction { public static final I16x8ShrU INSTANCE = new I16x8ShrU(); private I16x8ShrU() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI16x8ShrU(this); } }
    final class I16x8Add implements Instruction { public static final I16x8Add INSTANCE = new I16x8Add(); private I16x8Add() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI16x8Add(this); } }
    final class I16x8AddSatS implements Instruction { public static final I16x8AddSatS INSTANCE = new I16x8AddSatS(); private I16x8AddSatS() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI16x8AddSatS(this); } }
    final class I16x8AddSatU implements Instruction { public static final I16x8AddSatU INSTANCE = new I16x8AddSatU(); private I16x8AddSatU() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI16x8AddSatU(this); } }
    final class I16x8Sub implements Instruction { public static final I16x8Sub INSTANCE = new I16x8Sub(); private I16x8Sub() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI16x8Sub(this); } }
    final class I16x8SubSatS implements Instruction { public static final I16x8SubSatS INSTANCE = new I16x8SubSatS(); private I16x8SubSatS() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI16x8SubSatS(this); } }
    final class I16x8SubSatU implements Instruction { public static final I16x8SubSatU INSTANCE = new I16x8SubSatU(); private I16x8SubSatU() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI16x8SubSatU(this); } }
    final class I16x8Mul implements Instruction { public static final I16x8Mul INSTANCE = new I16x8Mul(); private I16x8Mul() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI16x8Mul(this); } }
    final class I16x8MinS implements Instruction { public static final I16x8MinS INSTANCE = new I16x8MinS(); private I16x8MinS() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI16x8MinS(this); } }
    final class I16x8MinU implements Instruction { public static final I16x8MinU INSTANCE = new I16x8MinU(); private I16x8MinU() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI16x8MinU(this); } }
    final class I16x8MaxS implements Instruction { public static final I16x8MaxS INSTANCE = new I16x8MaxS(); private I16x8MaxS() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI16x8MaxS(this); } }
    final class I16x8MaxU implements Instruction { public static final I16x8MaxU INSTANCE = new I16x8MaxU(); private I16x8MaxU() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI16x8MaxU(this); } }
    final class I16x8AvgrU implements Instruction { public static final I16x8AvgrU INSTANCE = new I16x8AvgrU(); private I16x8AvgrU() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI16x8AvgrU(this); } }
    final class I16x8ExtMulLowI8x16S implements Instruction { public static final I16x8ExtMulLowI8x16S INSTANCE = new I16x8ExtMulLowI8x16S(); private I16x8ExtMulLowI8x16S() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI16x8ExtMulLowI8x16S(this); } }
    final class I16x8ExtMulHighI8x16S implements Instruction { public static final I16x8ExtMulHighI8x16S INSTANCE = new I16x8ExtMulHighI8x16S(); private I16x8ExtMulHighI8x16S() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI16x8ExtMulHighI8x16S(this); } }
    final class I16x8ExtMulLowI8x16U implements Instruction { public static final I16x8ExtMulLowI8x16U INSTANCE = new I16x8ExtMulLowI8x16U(); private I16x8ExtMulLowI8x16U() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI16x8ExtMulLowI8x16U(this); } }
    final class I16x8ExtMulHighI8x16U implements Instruction { public static final I16x8ExtMulHighI8x16U INSTANCE = new I16x8ExtMulHighI8x16U(); private I16x8ExtMulHighI8x16U() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI16x8ExtMulHighI8x16U(this); } }

    final class I32x4ExtAddPairwiseI16x8S implements Instruction { public static final I32x4ExtAddPairwiseI16x8S INSTANCE = new I32x4ExtAddPairwiseI16x8S(); private I32x4ExtAddPairwiseI16x8S() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32x4ExtAddPairwiseI16x8S(this); } }
    final class I32x4ExtAddPairwiseI16x8U implements Instruction { public static final I32x4ExtAddPairwiseI16x8U INSTANCE = new I32x4ExtAddPairwiseI16x8U(); private I32x4ExtAddPairwiseI16x8U() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32x4ExtAddPairwiseI16x8U(this); } }
    final class I32x4Abs implements Instruction { public static final I32x4Abs INSTANCE = new I32x4Abs(); private I32x4Abs() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32x4Abs(this); } }
    final class I32x4Neg implements Instruction { public static final I32x4Neg INSTANCE = new I32x4Neg(); private I32x4Neg() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32x4Neg(this); } }
    final class I32x4AllTrue implements Instruction { public static final I32x4AllTrue INSTANCE = new I32x4AllTrue(); private I32x4AllTrue() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32x4AllTrue(this); } }
    final class I32x4Bitmask implements Instruction { public static final I32x4Bitmask INSTANCE = new I32x4Bitmask(); private I32x4Bitmask() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32x4Bitmask(this); } }
    final class I32x4ExtendLowI16x8S implements Instruction { public static final I32x4ExtendLowI16x8S INSTANCE = new I32x4ExtendLowI16x8S(); private I32x4ExtendLowI16x8S() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32x4ExtendLowI16x8S(this); } }
    final class I32x4ExtendHighI16x8S implements Instruction { public static final I32x4ExtendHighI16x8S INSTANCE = new I32x4ExtendHighI16x8S(); private I32x4ExtendHighI16x8S() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32x4ExtendHighI16x8S(this); } }
    final class I32x4ExtendLowI16x8U implements Instruction { public static final I32x4ExtendLowI16x8U INSTANCE = new I32x4ExtendLowI16x8U(); private I32x4ExtendLowI16x8U() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32x4ExtendLowI16x8U(this); } }
    final class I32x4ExtendHighI16x8U implements Instruction { public static final I32x4ExtendHighI16x8U INSTANCE = new I32x4ExtendHighI16x8U(); private I32x4ExtendHighI16x8U() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32x4ExtendHighI16x8U(this); } }
    final class I32x4Shl implements Instruction { public static final I32x4Shl INSTANCE = new I32x4Shl(); private I32x4Shl() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32x4Shl(this); } }
    final class I32x4ShrS implements Instruction { public static final I32x4ShrS INSTANCE = new I32x4ShrS(); private I32x4ShrS() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32x4ShrS(this); } }
    final class I32x4ShrU implements Instruction { public static final I32x4ShrU INSTANCE = new I32x4ShrU(); private I32x4ShrU() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32x4ShrU(this); } }
    final class I32x4Add implements Instruction { public static final I32x4Add INSTANCE = new I32x4Add(); private I32x4Add() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32x4Add(this); } }
    final class I32x4Sub implements Instruction { public static final I32x4Sub INSTANCE = new I32x4Sub(); private I32x4Sub() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32x4Sub(this); } }
    final class I32x4Mul implements Instruction { public static final I32x4Mul INSTANCE = new I32x4Mul(); private I32x4Mul() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32x4Mul(this); } }
    final class I32x4MinS implements Instruction { public static final I32x4MinS INSTANCE = new I32x4MinS(); private I32x4MinS() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32x4MinS(this); } }
    final class I32x4MinU implements Instruction { public static final I32x4MinU INSTANCE = new I32x4MinU(); private I32x4MinU() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32x4MinU(this); } }
    final class I32x4MaxS implements Instruction { public static final I32x4MaxS INSTANCE = new I32x4MaxS(); private I32x4MaxS() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32x4MaxS(this); } }
    final class I32x4MaxU implements Instruction { public static final I32x4MaxU INSTANCE = new I32x4MaxU(); private I32x4MaxU() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32x4MaxU(this); } }
    final class I32x4DotI16x8S implements Instruction { public static final I32x4DotI16x8S INSTANCE = new I32x4DotI16x8S(); private I32x4DotI16x8S() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32x4DotI16x8S(this); } }
    final class I32x4ExtMulLowI16x8S implements Instruction { public static final I32x4ExtMulLowI16x8S INSTANCE = new I32x4ExtMulLowI16x8S(); private I32x4ExtMulLowI16x8S() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32x4ExtMulLowI16x8S(this); } }
    final class I32x4ExtMulHighI16x8S implements Instruction { public static final I32x4ExtMulHighI16x8S INSTANCE = new I32x4ExtMulHighI16x8S(); private I32x4ExtMulHighI16x8S() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32x4ExtMulHighI16x8S(this); } }
    final class I32x4ExtMulLowI16x8U implements Instruction { public static final I32x4ExtMulLowI16x8U INSTANCE = new I32x4ExtMulLowI16x8U(); private I32x4ExtMulLowI16x8U() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32x4ExtMulLowI16x8U(this); } }
    final class I32x4ExtMulHighI16x8U implements Instruction { public static final I32x4ExtMulHighI16x8U INSTANCE = new I32x4ExtMulHighI16x8U(); private I32x4ExtMulHighI16x8U() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32x4ExtMulHighI16x8U(this); } }

    final class I64x2Abs implements Instruction { public static final I64x2Abs INSTANCE = new I64x2Abs(); private I64x2Abs() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64x2Abs(this); } }
    final class I64x2Neg implements Instruction { public static final I64x2Neg INSTANCE = new I64x2Neg(); private I64x2Neg() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64x2Neg(this); } }
    final class I64x2AllTrue implements Instruction { public static final I64x2AllTrue INSTANCE = new I64x2AllTrue(); private I64x2AllTrue() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64x2AllTrue(this); } }
    final class I64x2Bitmask implements Instruction { public static final I64x2Bitmask INSTANCE = new I64x2Bitmask(); private I64x2Bitmask() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64x2Bitmask(this); } }
    final class I64x2ExtendLowI32x4S implements Instruction { public static final I64x2ExtendLowI32x4S INSTANCE = new I64x2ExtendLowI32x4S(); private I64x2ExtendLowI32x4S() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64x2ExtendLowI32x4S(this); } }
    final class I64x2ExtendHighI32x4S implements Instruction { public static final I64x2ExtendHighI32x4S INSTANCE = new I64x2ExtendHighI32x4S(); private I64x2ExtendHighI32x4S() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64x2ExtendHighI32x4S(this); } }
    final class I64x2ExtendLowI32x4U implements Instruction { public static final I64x2ExtendLowI32x4U INSTANCE = new I64x2ExtendLowI32x4U(); private I64x2ExtendLowI32x4U() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64x2ExtendLowI32x4U(this); } }
    final class I64x2ExtendHighI32x4U implements Instruction { public static final I64x2ExtendHighI32x4U INSTANCE = new I64x2ExtendHighI32x4U(); private I64x2ExtendHighI32x4U() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64x2ExtendHighI32x4U(this); } }
    final class I64x2Shl implements Instruction { public static final I64x2Shl INSTANCE = new I64x2Shl(); private I64x2Shl() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64x2Shl(this); } }
    final class I64x2ShrS implements Instruction { public static final I64x2ShrS INSTANCE = new I64x2ShrS(); private I64x2ShrS() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64x2ShrS(this); } }
    final class I64x2ShrU implements Instruction { public static final I64x2ShrU INSTANCE = new I64x2ShrU(); private I64x2ShrU() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64x2ShrU(this); } }
    final class I64x2Add implements Instruction { public static final I64x2Add INSTANCE = new I64x2Add(); private I64x2Add() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64x2Add(this); } }
    final class I64x2Sub implements Instruction { public static final I64x2Sub INSTANCE = new I64x2Sub(); private I64x2Sub() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64x2Sub(this); } }
    final class I64x2Mul implements Instruction { public static final I64x2Mul INSTANCE = new I64x2Mul(); private I64x2Mul() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64x2Mul(this); } }
    final class I64x2ExtMulLowI32x4S implements Instruction { public static final I64x2ExtMulLowI32x4S INSTANCE = new I64x2ExtMulLowI32x4S(); private I64x2ExtMulLowI32x4S() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64x2ExtMulLowI32x4S(this); } }
    final class I64x2ExtMulHighI32x4S implements Instruction { public static final I64x2ExtMulHighI32x4S INSTANCE = new I64x2ExtMulHighI32x4S(); private I64x2ExtMulHighI32x4S() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64x2ExtMulHighI32x4S(this); } }
    final class I64x2ExtMulLowI32x4U implements Instruction { public static final I64x2ExtMulLowI32x4U INSTANCE = new I64x2ExtMulLowI32x4U(); private I64x2ExtMulLowI32x4U() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64x2ExtMulLowI32x4U(this); } }
    final class I64x2ExtMulHighI32x4U implements Instruction { public static final I64x2ExtMulHighI32x4U INSTANCE = new I64x2ExtMulHighI32x4U(); private I64x2ExtMulHighI32x4U() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI64x2ExtMulHighI32x4U(this); } }

    final class F32x4Ceil implements Instruction { public static final F32x4Ceil INSTANCE = new F32x4Ceil(); private F32x4Ceil() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32x4Ceil(this); } }
    final class F32x4Floor implements Instruction { public static final F32x4Floor INSTANCE = new F32x4Floor(); private F32x4Floor() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32x4Floor(this); } }
    final class F32x4Trunc implements Instruction { public static final F32x4Trunc INSTANCE = new F32x4Trunc(); private F32x4Trunc() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32x4Trunc(this); } }
    final class F32x4Nearest implements Instruction { public static final F32x4Nearest INSTANCE = new F32x4Nearest(); private F32x4Nearest() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32x4Nearest(this); } }
    final class F32x4Abs implements Instruction { public static final F32x4Abs INSTANCE = new F32x4Abs(); private F32x4Abs() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32x4Abs(this); } }
    final class F32x4Neg implements Instruction { public static final F32x4Neg INSTANCE = new F32x4Neg(); private F32x4Neg() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32x4Neg(this); } }
    final class F32x4Sqrt implements Instruction { public static final F32x4Sqrt INSTANCE = new F32x4Sqrt(); private F32x4Sqrt() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32x4Sqrt(this); } }
    final class F32x4Add implements Instruction { public static final F32x4Add INSTANCE = new F32x4Add(); private F32x4Add() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32x4Add(this); } }
    final class F32x4Sub implements Instruction { public static final F32x4Sub INSTANCE = new F32x4Sub(); private F32x4Sub() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32x4Sub(this); } }
    final class F32x4Mul implements Instruction { public static final F32x4Mul INSTANCE = new F32x4Mul(); private F32x4Mul() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32x4Mul(this); } }
    final class F32x4Div implements Instruction { public static final F32x4Div INSTANCE = new F32x4Div(); private F32x4Div() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32x4Div(this); } }
    final class F32x4Min implements Instruction { public static final F32x4Min INSTANCE = new F32x4Min(); private F32x4Min() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32x4Min(this); } }
    final class F32x4Max implements Instruction { public static final F32x4Max INSTANCE = new F32x4Max(); private F32x4Max() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32x4Max(this); } }
    final class F32x4PMin implements Instruction { public static final F32x4PMin INSTANCE = new F32x4PMin(); private F32x4PMin() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32x4PMin(this); } }
    final class F32x4PMax implements Instruction { public static final F32x4PMax INSTANCE = new F32x4PMax(); private F32x4PMax() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32x4PMax(this); } }

    final class F64x2Ceil implements Instruction { public static final F64x2Ceil INSTANCE = new F64x2Ceil(); private F64x2Ceil() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64x2Ceil(this); } }
    final class F64x2Floor implements Instruction { public static final F64x2Floor INSTANCE = new F64x2Floor(); private F64x2Floor() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64x2Floor(this); } }
    final class F64x2Trunc implements Instruction { public static final F64x2Trunc INSTANCE = new F64x2Trunc(); private F64x2Trunc() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64x2Trunc(this); } }
    final class F64x2Nearest implements Instruction { public static final F64x2Nearest INSTANCE = new F64x2Nearest(); private F64x2Nearest() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64x2Nearest(this); } }
    final class F64x2Abs implements Instruction { public static final F64x2Abs INSTANCE = new F64x2Abs(); private F64x2Abs() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64x2Abs(this); } }
    final class F64x2Neg implements Instruction { public static final F64x2Neg INSTANCE = new F64x2Neg(); private F64x2Neg() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64x2Neg(this); } }
    final class F64x2Sqrt implements Instruction { public static final F64x2Sqrt INSTANCE = new F64x2Sqrt(); private F64x2Sqrt() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64x2Sqrt(this); } }
    final class F64x2Add implements Instruction { public static final F64x2Add INSTANCE = new F64x2Add(); private F64x2Add() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64x2Add(this); } }
    final class F64x2Sub implements Instruction { public static final F64x2Sub INSTANCE = new F64x2Sub(); private F64x2Sub() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64x2Sub(this); } }
    final class F64x2Mul implements Instruction { public static final F64x2Mul INSTANCE = new F64x2Mul(); private F64x2Mul() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64x2Mul(this); } }
    final class F64x2Div implements Instruction { public static final F64x2Div INSTANCE = new F64x2Div(); private F64x2Div() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64x2Div(this); } }
    final class F64x2Min implements Instruction { public static final F64x2Min INSTANCE = new F64x2Min(); private F64x2Min() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64x2Min(this); } }
    final class F64x2Max implements Instruction { public static final F64x2Max INSTANCE = new F64x2Max(); private F64x2Max() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64x2Max(this); } }
    final class F64x2PMin implements Instruction { public static final F64x2PMin INSTANCE = new F64x2PMin(); private F64x2PMin() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64x2PMin(this); } }
    final class F64x2PMax implements Instruction { public static final F64x2PMax INSTANCE = new F64x2PMax(); private F64x2PMax() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64x2PMax(this); } }

    final class I32x4TruncSatF32x4S implements Instruction { public static final I32x4TruncSatF32x4S INSTANCE = new I32x4TruncSatF32x4S(); private I32x4TruncSatF32x4S() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32x4TruncSatF32x4S(this); } }
    final class I32x4TruncSatF32x4U implements Instruction { public static final I32x4TruncSatF32x4U INSTANCE = new I32x4TruncSatF32x4U(); private I32x4TruncSatF32x4U() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32x4TruncSatF32x4U(this); } }
    final class F32x4ConvertI32x4S implements Instruction { public static final F32x4ConvertI32x4S INSTANCE = new F32x4ConvertI32x4S(); private F32x4ConvertI32x4S() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32x4ConvertI32x4S(this); } }
    final class F32x4ConvertI32x4U implements Instruction { public static final F32x4ConvertI32x4U INSTANCE = new F32x4ConvertI32x4U(); private F32x4ConvertI32x4U() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32x4ConvertI32x4U(this); } }
    final class I32x4TruncSatF64x2SZero implements Instruction { public static final I32x4TruncSatF64x2SZero INSTANCE = new I32x4TruncSatF64x2SZero(); private I32x4TruncSatF64x2SZero() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32x4TruncSatF64x2SZero(this); } }
    final class I32x4TruncSatF64x2UZero implements Instruction { public static final I32x4TruncSatF64x2UZero INSTANCE = new I32x4TruncSatF64x2UZero(); private I32x4TruncSatF64x2UZero() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitI32x4TruncSatF64x2UZero(this); } }
    final class F64x2ConvertLowI32x4S implements Instruction { public static final F64x2ConvertLowI32x4S INSTANCE = new F64x2ConvertLowI32x4S(); private F64x2ConvertLowI32x4S() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64x2ConvertLowI32x4S(this); } }
    final class F64x2ConvertLowI32x4U implements Instruction { public static final F64x2ConvertLowI32x4U INSTANCE = new F64x2ConvertLowI32x4U(); private F64x2ConvertLowI32x4U() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64x2ConvertLowI32x4U(this); } }
    final class F32x4DemoteF64x2Zero implements Instruction { public static final F32x4DemoteF64x2Zero INSTANCE = new F32x4DemoteF64x2Zero(); private F32x4DemoteF64x2Zero() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF32x4DemoteF64x2Zero(this); } }
    final class F64x2PromoteLowF32x4 implements Instruction { public static final F64x2PromoteLowF32x4 INSTANCE = new F64x2PromoteLowF32x4(); private F64x2PromoteLowF32x4() {} public <R, T extends Throwable> R accept(InstructionVisitor<R, T> visitor) throws T { return visitor.visitF64x2PromoteLowF32x4(this); } }


    static Instruction read(InputStream stream, List<StackType> moduleTypes) throws IOException, ModuleParseException {
        int b = stream.read();
        return switch (b) {
            case 0x05 -> Else.INSTANCE; //fake
            case 0x0B -> End.INSTANCE; //fake

            case 0x00 -> Unreachable.INSTANCE;
            case 0x01 -> Nop.INSTANCE;
            case 0x02 -> new Block(StackType.readBlockType(stream, moduleTypes), readMany(stream, moduleTypes, false).instrs());
            case 0x03 -> new Loop(StackType.readBlockType(stream, moduleTypes), readMany(stream, moduleTypes, false).instrs());
            case 0x04 -> {
                StackType blockType = StackType.readBlockType(stream, moduleTypes);
                ManyReadResult firstResult = readMany(stream, moduleTypes, true);
                if (firstResult.foundElse())
                    yield new IfElse(blockType, firstResult.instrs(), readMany(stream, moduleTypes, false).instrs());
                else
                    yield new If(blockType, firstResult.instrs());
            }
            case 0x0C -> new Branch(ParseHelper.readUnsignedWasmInt(stream));
            case 0x0D -> new BranchIf(ParseHelper.readUnsignedWasmInt(stream));
            case 0x0E -> new BranchTable(ParseHelper.readVector(stream, ParseHelper::readUnsignedWasmInt), ParseHelper.readUnsignedWasmInt(stream));
            case 0x0F -> Return.INSTANCE;
            case 0x10 -> new Call(ParseHelper.readUnsignedWasmInt(stream));
            case 0x11 -> new CallIndirect(ParseHelper.readUnsignedWasmInt(stream), ParseHelper.readUnsignedWasmInt(stream));

            case 0xD0 -> new RefNull(ValType.readRefType(stream));
            case 0xD1 -> RefIsNull.INSTANCE;
            case 0xD2 -> new RefFunc(ParseHelper.readUnsignedWasmInt(stream));

            case 0x1A -> Drop.INSTANCE;
            case 0x1B -> Select.INSTANCE;
            case 0x1C -> new SelectFrom(ParseHelper.readVector(stream, ValType::read));

            case 0x20 -> new LocalGet(ParseHelper.readUnsignedWasmInt(stream));
            case 0x21 -> new LocalSet(ParseHelper.readUnsignedWasmInt(stream));
            case 0x22 -> new LocalTee(ParseHelper.readUnsignedWasmInt(stream));
            case 0x23 -> new GlobalGet(ParseHelper.readUnsignedWasmInt(stream));
            case 0x24 -> new GlobalSet(ParseHelper.readUnsignedWasmInt(stream));

            case 0x25 -> new TableGet(ParseHelper.readUnsignedWasmInt(stream));
            case 0x26 -> new TableSet(ParseHelper.readUnsignedWasmInt(stream));
            case 0xFC -> {
                int v = ParseHelper.readUnsignedWasmInt(stream);
                yield switch (v) {
                    case 12 -> new TableInit(ParseHelper.readUnsignedWasmInt(stream), ParseHelper.readUnsignedWasmInt(stream));
                    case 13 -> new ElemDrop(ParseHelper.readUnsignedWasmInt(stream));
                    case 14 -> new TableCopy(ParseHelper.readUnsignedWasmInt(stream), ParseHelper.readUnsignedWasmInt(stream));
                    case 15 -> new TableGrow(ParseHelper.readUnsignedWasmInt(stream));
                    case 16 -> new TableSize(ParseHelper.readUnsignedWasmInt(stream));
                    case 17 -> new TableFill(ParseHelper.readUnsignedWasmInt(stream));

                    case 8 -> {
                        Instruction result = new MemoryInit(ParseHelper.readUnsignedWasmInt(stream));
                        readMemIndex(stream);
                        yield result;
                    }
                    case 9 -> new DataDrop(ParseHelper.readUnsignedWasmInt(stream));
                    case 10 -> {
                        Instruction result = MemoryCopy.INSTANCE;
                        readMemIndex(stream);
                        readMemIndex(stream);
                        yield result;
                    }
                    case 11 -> {
                        Instruction result = MemoryFill.INSTANCE;
                        readMemIndex(stream);
                        yield result;
                    }

                    case 0 -> I32TruncSatF32S.INSTANCE;
                    case 1 -> I32TruncSatF32U.INSTANCE;
                    case 2 -> I32TruncSatF64S.INSTANCE;
                    case 3 -> I32TruncSatF64U.INSTANCE;
                    case 4 -> I64TruncSatF32S.INSTANCE;
                    case 5 -> I64TruncSatF32U.INSTANCE;
                    case 6 -> I64TruncSatF64S.INSTANCE;
                    case 7 -> I64TruncSatF64U.INSTANCE;

                    default -> throw new ModuleParseException("Invalid integer after 0xFC byte: " + v);
                };
            }

            case 0x28, 0x29, 0x2A, 0x2B, 0x2C, 0x2D, 0x2E, 0x2F, 0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x3A, 0x3B, 0x3C, 0x3D, 0x3E -> {
                int align = ParseHelper.readUnsignedWasmInt(stream);
                int offset = ParseHelper.readUnsignedWasmInt(stream);
                yield switch (b) {
                    case 0x28 -> new I32Load(align, offset);
                    case 0x29 -> new I64Load(align, offset);
                    case 0x2A -> new F32Load(align, offset);
                    case 0x2B -> new F64Load(align, offset);
                    case 0x2C -> new I32Load8S(align, offset);
                    case 0x2D -> new I32Load8U(align, offset);
                    case 0x2E -> new I32Load16S(align, offset);
                    case 0x2F -> new I32Load16U(align, offset);
                    case 0x30 -> new I64Load8S(align, offset);
                    case 0x31 -> new I64Load8U(align, offset);
                    case 0x32 -> new I64Load16S(align, offset);
                    case 0x33 -> new I64Load16U(align, offset);
                    case 0x34 -> new I64Load32S(align, offset);
                    case 0x35 -> new I64Load32U(align, offset);
                    case 0x36 -> new I32Store(align, offset);
                    case 0x37 -> new I64Store(align, offset);
                    case 0x38 -> new F32Store(align, offset);
                    case 0x39 -> new F64Store(align, offset);
                    case 0x3A -> new I32Store8(align, offset);
                    case 0x3B -> new I32Store16(align, offset);
                    case 0x3C -> new I64Store8(align, offset);
                    case 0x3D -> new I64Store16(align, offset);
                    case 0x3E -> new I64Store32(align, offset);
                    default -> throw new ModuleParseException("Should be extremely impossible for this to show up");
                };
            }
            case 0x3F -> {
                readMemIndex(stream);
                yield MemorySize.INSTANCE;
            }
            case 0x40 -> {
                readMemIndex(stream);
                yield MemoryGrow.INSTANCE;
            }

            case 0x41 -> new I32Const(ParseHelper.readSignedWasmInt(stream));
            case 0x42 -> new I64Const(ParseHelper.readSignedWasmLong(stream));
            case 0x43 -> new F32Const(ParseHelper.readFloat(stream));
            case 0x44 -> new F64Const(ParseHelper.readDouble(stream));

            case 0x45 -> I32Eqz.INSTANCE;
            case 0x46 -> I32Eq.INSTANCE;
            case 0x47 -> I32Ne.INSTANCE;
            case 0x48 -> I32LtS.INSTANCE;
            case 0x49 -> I32LtU.INSTANCE;
            case 0x4A -> I32GtS.INSTANCE;
            case 0x4B -> I32GtU.INSTANCE;
            case 0x4C -> I32LeS.INSTANCE;
            case 0x4D -> I32LeU.INSTANCE;
            case 0x4E -> I32GeS.INSTANCE;
            case 0x4F -> I32GeU.INSTANCE;

            case 0x50 -> I64Eqz.INSTANCE;
            case 0x51 -> I64Eq.INSTANCE;
            case 0x52 -> I64Ne.INSTANCE;
            case 0x53 -> I64LtS.INSTANCE;
            case 0x54 -> I64LtU.INSTANCE;
            case 0x55 -> I64GtS.INSTANCE;
            case 0x56 -> I64GtU.INSTANCE;
            case 0x57 -> I64LeS.INSTANCE;
            case 0x58 -> I64LeU.INSTANCE;
            case 0x59 -> I64GeS.INSTANCE;
            case 0x5A -> I64GeU.INSTANCE;

            case 0x5B -> F32Eq.INSTANCE;
            case 0x5C -> F32Ne.INSTANCE;
            case 0x5D -> F32Lt.INSTANCE;
            case 0x5E -> F32Gt.INSTANCE;
            case 0x5F -> F32Le.INSTANCE;
            case 0x60 -> F32Ge.INSTANCE;

            case 0x61 -> F64Eq.INSTANCE;
            case 0x62 -> F64Ne.INSTANCE;
            case 0x63 -> F64Lt.INSTANCE;
            case 0x64 -> F64Gt.INSTANCE;
            case 0x65 -> F64Le.INSTANCE;
            case 0x66 -> F64Ge.INSTANCE;

            case 0x67 -> I32Clz.INSTANCE;
            case 0x68 -> I32Ctz.INSTANCE;
            case 0x69 -> I32PopCnt.INSTANCE;
            case 0x6A -> I32Add.INSTANCE;
            case 0x6B -> I32Sub.INSTANCE;
            case 0x6C -> I32Mul.INSTANCE;
            case 0x6D -> I32DivS.INSTANCE;
            case 0x6E -> I32DivU.INSTANCE;
            case 0x6F -> I32RemS.INSTANCE;
            case 0x70 -> I32RemU.INSTANCE;
            case 0x71 -> I32And.INSTANCE;
            case 0x72 -> I32Or.INSTANCE;
            case 0x73 -> I32Xor.INSTANCE;
            case 0x74 -> I32Shl.INSTANCE;
            case 0x75 -> I32ShrS.INSTANCE;
            case 0x76 -> I32ShrU.INSTANCE;
            case 0x77 -> I32Rotl.INSTANCE;
            case 0x78 -> I32Rotr.INSTANCE;

            case 0x79 -> I64Clz.INSTANCE;
            case 0x7A -> I64Ctz.INSTANCE;
            case 0x7B -> I64PopCnt.INSTANCE;
            case 0x7C -> I64Add.INSTANCE;
            case 0x7D -> I64Sub.INSTANCE;
            case 0x7E -> I64Mul.INSTANCE;
            case 0x7F -> I64DivS.INSTANCE;
            case 0x80 -> I64DivU.INSTANCE;
            case 0x81 -> I64RemS.INSTANCE;
            case 0x82 -> I64RemU.INSTANCE;
            case 0x83 -> I64And.INSTANCE;
            case 0x84 -> I64Or.INSTANCE;
            case 0x85 -> I64Xor.INSTANCE;
            case 0x86 -> I64Shl.INSTANCE;
            case 0x87 -> I64ShrS.INSTANCE;
            case 0x88 -> I64ShrU.INSTANCE;
            case 0x89 -> I64Rotl.INSTANCE;
            case 0x8A -> I64Rotr.INSTANCE;

            case 0x8B -> F32Abs.INSTANCE;
            case 0x8C -> F32Neg.INSTANCE;
            case 0x8D -> F32Ceil.INSTANCE;
            case 0x8E -> F32Floor.INSTANCE;
            case 0x8F -> F32Trunc.INSTANCE;
            case 0x90 -> F32Nearest.INSTANCE;
            case 0x91 -> F32Sqrt.INSTANCE;
            case 0x92 -> F32Add.INSTANCE;
            case 0x93 -> F32Sub.INSTANCE;
            case 0x94 -> F32Mul.INSTANCE;
            case 0x95 -> F32Div.INSTANCE;
            case 0x96 -> F32Min.INSTANCE;
            case 0x97 -> F32Max.INSTANCE;
            case 0x98 -> F32Copysign.INSTANCE;

            case 0x99 -> F64Abs.INSTANCE;
            case 0x9A -> F64Neg.INSTANCE;
            case 0x9B -> F64Ceil.INSTANCE;
            case 0x9C -> F64Floor.INSTANCE;
            case 0x9D -> F64Trunc.INSTANCE;
            case 0x9E -> F64Nearest.INSTANCE;
            case 0x9F -> F64Sqrt.INSTANCE;
            case 0xA0 -> F64Add.INSTANCE;
            case 0xA1 -> F64Sub.INSTANCE;
            case 0xA2 -> F64Mul.INSTANCE;
            case 0xA3 -> F64Div.INSTANCE;
            case 0xA4 -> F64Min.INSTANCE;
            case 0xA5 -> F64Max.INSTANCE;
            case 0xA6 -> F64Copysign.INSTANCE;

            case 0xA7 -> I32WrapI64.INSTANCE;
            case 0xA8 -> I32TruncF32S.INSTANCE;
            case 0xA9 -> I32TruncF32U.INSTANCE;
            case 0xAA -> I32TruncF64S.INSTANCE;
            case 0xAB -> I32TruncF64U.INSTANCE;
            case 0xAC -> I64ExtendI32S.INSTANCE;
            case 0xAD -> I64ExtendI32U.INSTANCE;
            case 0xAE -> I64TruncF32S.INSTANCE;
            case 0xAF -> I64TruncF32U.INSTANCE;
            case 0xB0 -> I64TruncF64S.INSTANCE;
            case 0xB1 -> I64TruncF64U.INSTANCE;
            case 0xB2 -> F32ConvertI32S.INSTANCE;
            case 0xB3 -> F32ConvertI32U.INSTANCE;
            case 0xB4 -> F32ConvertI64S.INSTANCE;
            case 0xB5 -> F32ConvertI64U.INSTANCE;
            case 0xB6 -> F32DemoteF64.INSTANCE;
            case 0xB7 -> F64ConvertI32S.INSTANCE;
            case 0xB8 -> F64ConvertI32U.INSTANCE;
            case 0xB9 -> F64ConvertI64S.INSTANCE;
            case 0xBA -> F64ConvertI64U.INSTANCE;
            case 0xBB -> F64PromoteF32.INSTANCE;
            case 0xBC -> I32ReinterpretF32.INSTANCE;
            case 0xBD -> I64ReinterpretF64.INSTANCE;
            case 0xBE -> F32ReinterpretI32.INSTANCE;
            case 0xBF -> F64ReinterpretI64.INSTANCE;

            case 0xC0 -> I32Extend8S.INSTANCE;
            case 0xC1 -> I32Extend16S.INSTANCE;
            case 0xC2 -> I64Extend8S.INSTANCE;
            case 0xC3 -> I64Extend16S.INSTANCE;
            case 0xC4 -> I64Extend32S.INSTANCE;

            case 0xFD -> {
                int v = ParseHelper.readUnsignedWasmInt(stream);
                yield switch (v) {
                    case 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 92, 93 -> {
                        int align = ParseHelper.readUnsignedWasmInt(stream);
                        int offset = ParseHelper.readUnsignedWasmInt(stream);
                        yield switch (v) {
                            case 0 -> new V128Load(align, offset);
                            case 1 -> new V128Load8x8S(align, offset);
                            case 2 -> new V128Load8x8U(align, offset);
                            case 3 -> new V128Load16x4S(align, offset);
                            case 4 -> new V128Load16x4U(align, offset);
                            case 5 -> new V128Load32x2S(align, offset);
                            case 6 -> new V128Load32x2U(align, offset);
                            case 7 -> new V128Load8Splat(align, offset);
                            case 8 -> new V128Load16Splat(align, offset);
                            case 9 -> new V128Load32Splat(align, offset);
                            case 10 -> new V128Load64Splat(align, offset);
                            case 11 -> new V128Store(align, offset);
                            case 92 -> new V128Load32Zero(align, offset);
                            case 93 -> new V128Load64Zero(align, offset);
                            default -> throw new ModuleParseException("Should be impossible");
                        };
                    }
                    case 84, 85, 86, 87, 88, 89, 90, 91 -> {
                        int align = ParseHelper.readUnsignedWasmInt(stream);
                        int offset = ParseHelper.readUnsignedWasmInt(stream);
                        byte laneIndex = (byte) stream.read();
                        yield switch (v) {
                            case 84 -> new V128Load8Lane(align, offset, laneIndex);
                            case 85 -> new V128Load16Lane(align, offset, laneIndex);
                            case 86 -> new V128Load32Lane(align, offset, laneIndex);
                            case 87 -> new V128Load64Lane(align, offset, laneIndex);
                            case 88 -> new V128Store8Lane(align, offset, laneIndex);
                            case 89 -> new V128Store16Lane(align, offset, laneIndex);
                            case 90 -> new V128Store32Lane(align, offset, laneIndex);
                            case 91 -> new V128Store64Lane(align, offset, laneIndex);
                            default -> throw new ModuleParseException("Should be impossible");
                        };
                    }
                    case 12 -> new V128Const(stream.readNBytes(16));
                    case 13 -> new I8x16Shuffle(stream.readNBytes(16));
                    case 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34 -> {
                        byte laneIndex = (byte) stream.read();
                        yield switch (v) {
                            case 21 -> new I8x16ExtractLaneS(laneIndex);
                            case 22 -> new I8x16ExtractLaneU(laneIndex);
                            case 23 -> new I8x16ReplaceLane(laneIndex);
                            case 24 -> new I16x8ExtractLaneS(laneIndex);
                            case 25 -> new I16x8ExtractLaneU(laneIndex);
                            case 26 -> new I16x8ReplaceLane(laneIndex);
                            case 27 -> new I32x4ExtractLane(laneIndex);
                            case 28 -> new I32x4ReplaceLane(laneIndex);
                            case 30 -> new I64x2ReplaceLane(laneIndex);
                            case 29 -> new I64x2ExtractLane(laneIndex);
                            case 31 -> new F32x4ExtractLane(laneIndex);
                            case 32 -> new F32x4ReplaceLane(laneIndex);
                            case 33 -> new F64x2ExtractLane(laneIndex);
                            case 34 -> new F64x2ReplaceLane(laneIndex);
                            default -> throw new ModuleParseException("Should be impossible");
                        };
                    }
                    case 14 -> I8x16Swizzle.INSTANCE;
                    case 15 -> I8x16Splat.INSTANCE;
                    case 16 -> I16x8Splat.INSTANCE;
                    case 17 -> I32x4Splat.INSTANCE;
                    case 18 -> I64x2Splat.INSTANCE;
                    case 19 -> F32x4Splat.INSTANCE;
                    case 20 -> F64x2Splat.INSTANCE;

                    case 35 -> I8x16Eq.INSTANCE;
                    case 36 -> I8x16Ne.INSTANCE;
                    case 37 -> I8x16LtS.INSTANCE;
                    case 38 -> I8x16LtU.INSTANCE;
                    case 39 -> I8x16GtS.INSTANCE;
                    case 40 -> I8x16GtU.INSTANCE;
                    case 41 -> I8x16LeS.INSTANCE;
                    case 42 -> I8x16LeU.INSTANCE;
                    case 43 -> I8x16GeS.INSTANCE;
                    case 44 -> I8x16GeU.INSTANCE;

                    case 45 -> I16x8Eq.INSTANCE;
                    case 46 -> I16x8Ne.INSTANCE;
                    case 47 -> I16x8LtS.INSTANCE;
                    case 48 -> I16x8LtU.INSTANCE;
                    case 49 -> I16x8GtS.INSTANCE;
                    case 50 -> I16x8GtU.INSTANCE;
                    case 51 -> I16x8LeS.INSTANCE;
                    case 52 -> I16x8LeU.INSTANCE;
                    case 53 -> I16x8GeS.INSTANCE;
                    case 54 -> I16x8GeU.INSTANCE;

                    case 55 -> I32x4Eq.INSTANCE;
                    case 56 -> I32x4Ne.INSTANCE;
                    case 57 -> I32x4LtS.INSTANCE;
                    case 58 -> I32x4LtU.INSTANCE;
                    case 59 -> I32x4GtS.INSTANCE;
                    case 60 -> I32x4GtU.INSTANCE;
                    case 61 -> I32x4LeS.INSTANCE;
                    case 62 -> I32x4LeU.INSTANCE;
                    case 63 -> I32x4GeS.INSTANCE;
                    case 64 -> I32x4GeU.INSTANCE;

                    case 214 -> I64x2Eq.INSTANCE;
                    case 215 -> I64x2Ne.INSTANCE;
                    case 216 -> I64x2LtS.INSTANCE;
                    case 217 -> I64x2GtS.INSTANCE;
                    case 218 -> I64x2LeS.INSTANCE;
                    case 219 -> I64x2GeS.INSTANCE;

                    case 65 -> F32x4Eq.INSTANCE;
                    case 66 -> F32x4Ne.INSTANCE;
                    case 67 -> F32x4Lt.INSTANCE;
                    case 68 -> F32x4Gt.INSTANCE;
                    case 69 -> F32x4Le.INSTANCE;
                    case 70 -> F32x4Ge.INSTANCE;

                    case 71 -> F64x2Eq.INSTANCE;
                    case 72 -> F64x2Ne.INSTANCE;
                    case 73 -> F64x2Lt.INSTANCE;
                    case 74 -> F64x2Gt.INSTANCE;
                    case 75 -> F64x2Le.INSTANCE;
                    case 76 -> F64x2Ge.INSTANCE;

                    case 77 -> V128Not.INSTANCE;
                    case 78 -> V128And.INSTANCE;
                    case 79 -> V128AndNot.INSTANCE;
                    case 80 -> V128Or.INSTANCE;
                    case 81 -> V128Xor.INSTANCE;
                    case 82 -> V128Bitselect.INSTANCE;
                    case 83 -> V128AnyTrue.INSTANCE;

                    case 96 -> I8x16Abs.INSTANCE;
                    case 97 -> I8x16Neg.INSTANCE;
                    case 98 -> I8x16PopCnt.INSTANCE;
                    case 99 -> I8x16AllTrue.INSTANCE;
                    case 100 -> I8x16Bitmask.INSTANCE;
                    case 101 -> I8x16NarrowI16x8S.INSTANCE;
                    case 102 -> I8x16NarrowI16x8U.INSTANCE;
                    case 107 -> I8x16Shl.INSTANCE;
                    case 108 -> I8x16ShrS.INSTANCE;
                    case 109 -> I8x16ShrU.INSTANCE;
                    case 110 -> I8x16Add.INSTANCE;
                    case 111 -> I8x16AddSatS.INSTANCE;
                    case 112 -> I8x16AddSatU.INSTANCE;
                    case 113 -> I8x16Sub.INSTANCE;
                    case 114 -> I8x16SubSatS.INSTANCE;
                    case 115 -> I8x16SubSatU.INSTANCE;
                    case 118 -> I8x16MinS.INSTANCE;
                    case 119 -> I8x16MinU.INSTANCE;
                    case 120 -> I8x16MaxS.INSTANCE;
                    case 121 -> I8x16MaxU.INSTANCE;
                    case 123 -> I8x16AvgrU.INSTANCE;

                    case 124 -> I16x8ExtAddPairwiseI8x16S.INSTANCE;
                    case 125 -> I16x8ExtAddPairwiseI8x16U.INSTANCE;
                    case 128 -> I16x8Abs.INSTANCE;
                    case 129 -> I16x8Neg.INSTANCE;
                    case 130 -> I16x8Q15MulrSatS.INSTANCE;
                    case 131 -> I16x8AllTrue.INSTANCE;
                    case 132 -> I16x8Bitmask.INSTANCE;
                    case 133 -> I16x8NarrowI32x4S.INSTANCE;
                    case 134 -> I16x8NarrowI32x4U.INSTANCE;
                    case 135 -> I16x8ExtendLowI8x16S.INSTANCE;
                    case 136 -> I16x8ExtendHighI8x16S.INSTANCE;
                    case 137 -> I16x8ExtendLowI8x16U.INSTANCE;
                    case 138 -> I16x8ExtendHighI8x16U.INSTANCE;
                    case 139 -> I16x8Shl.INSTANCE;
                    case 140 -> I16x8ShrS.INSTANCE;
                    case 141 -> I16x8ShrU.INSTANCE;
                    case 142 -> I16x8Add.INSTANCE;
                    case 143 -> I16x8AddSatS.INSTANCE;
                    case 144 -> I16x8AddSatU.INSTANCE;
                    case 145 -> I16x8Sub.INSTANCE;
                    case 146 -> I16x8SubSatS.INSTANCE;
                    case 147 -> I16x8SubSatU.INSTANCE;
                    case 149 -> I16x8Mul.INSTANCE;
                    case 150 -> I16x8MinS.INSTANCE;
                    case 151 -> I16x8MinU.INSTANCE;
                    case 152 -> I16x8MaxS.INSTANCE;
                    case 153 -> I16x8MaxU.INSTANCE;
                    case 155 -> I16x8AvgrU.INSTANCE;
                    case 156 -> I16x8ExtMulLowI8x16S.INSTANCE;
                    case 157 -> I16x8ExtMulHighI8x16S.INSTANCE;
                    case 158 -> I16x8ExtMulLowI8x16U.INSTANCE;
                    case 159 -> I16x8ExtMulHighI8x16U.INSTANCE;

                    case 126 -> I32x4ExtAddPairwiseI16x8S.INSTANCE;
                    case 127 -> I32x4ExtAddPairwiseI16x8U.INSTANCE;
                    case 160 -> I32x4Abs.INSTANCE;
                    case 161 -> I32x4Neg.INSTANCE;
                    case 163 -> I32x4AllTrue.INSTANCE;
                    case 164 -> I32x4Bitmask.INSTANCE;
                    case 167 -> I32x4ExtendLowI16x8S.INSTANCE;
                    case 168 -> I32x4ExtendHighI16x8S.INSTANCE;
                    case 169 -> I32x4ExtendLowI16x8U.INSTANCE;
                    case 170 -> I32x4ExtendHighI16x8U.INSTANCE;
                    case 171 -> I32x4Shl.INSTANCE;
                    case 172 -> I32x4ShrS.INSTANCE;
                    case 173 -> I32x4ShrU.INSTANCE;
                    case 174 -> I32x4Add.INSTANCE;
                    case 177 -> I32x4Sub.INSTANCE;
                    case 181 -> I32x4Mul.INSTANCE;
                    case 182 -> I32x4MinS.INSTANCE;
                    case 183 -> I32x4MinU.INSTANCE;
                    case 184 -> I32x4MaxS.INSTANCE;
                    case 185 -> I32x4MaxU.INSTANCE;
                    case 186 -> I32x4DotI16x8S.INSTANCE;
                    case 188 -> I32x4ExtMulLowI16x8S.INSTANCE;
                    case 189 -> I32x4ExtMulHighI16x8S.INSTANCE;
                    case 190 -> I32x4ExtMulLowI16x8U.INSTANCE;
                    case 191 -> I32x4ExtMulHighI16x8U.INSTANCE;

                    case 192 -> I64x2Abs.INSTANCE;
                    case 193 -> I64x2Neg.INSTANCE;
                    case 195 -> I64x2AllTrue.INSTANCE;
                    case 196 -> I64x2Bitmask.INSTANCE;
                    case 199 -> I64x2ExtendLowI32x4S.INSTANCE;
                    case 200 -> I64x2ExtendHighI32x4S.INSTANCE;
                    case 201 -> I64x2ExtendLowI32x4U.INSTANCE;
                    case 202 -> I64x2ExtendHighI32x4U.INSTANCE;
                    case 203 -> I64x2Shl.INSTANCE;
                    case 204 -> I64x2ShrS.INSTANCE;
                    case 205 -> I64x2ShrU.INSTANCE;
                    case 206 -> I64x2Add.INSTANCE;
                    case 209 -> I64x2Sub.INSTANCE;
                    case 213 -> I64x2Mul.INSTANCE;
                    case 220 -> I64x2ExtMulLowI32x4S.INSTANCE;
                    case 221 -> I64x2ExtMulHighI32x4S.INSTANCE;
                    case 222 -> I64x2ExtMulLowI32x4U.INSTANCE;
                    case 223 -> I64x2ExtMulHighI32x4U.INSTANCE;

                    case 103 -> F32x4Ceil.INSTANCE;
                    case 104 -> F32x4Floor.INSTANCE;
                    case 105 -> F32x4Trunc.INSTANCE;
                    case 106 -> F32x4Nearest.INSTANCE;
                    case 224 -> F32x4Abs.INSTANCE;
                    case 225 -> F32x4Neg.INSTANCE;
                    case 227 -> F32x4Sqrt.INSTANCE;
                    case 228 -> F32x4Add.INSTANCE;
                    case 229 -> F32x4Sub.INSTANCE;
                    case 230 -> F32x4Mul.INSTANCE;
                    case 231 -> F32x4Div.INSTANCE;
                    case 232 -> F32x4Min.INSTANCE;
                    case 233 -> F32x4Max.INSTANCE;
                    case 234 -> F32x4PMin.INSTANCE;
                    case 235 -> F32x4PMax.INSTANCE;

                    case 116 -> F64x2Ceil.INSTANCE;
                    case 117 -> F64x2Floor.INSTANCE;
                    case 122 -> F64x2Trunc.INSTANCE;
                    case 148 -> F64x2Nearest.INSTANCE;
                    case 236 -> F64x2Abs.INSTANCE;
                    case 237 -> F64x2Neg.INSTANCE;
                    case 239 -> F64x2Sqrt.INSTANCE;
                    case 240 -> F64x2Add.INSTANCE;
                    case 241 -> F64x2Sub.INSTANCE;
                    case 242 -> F64x2Mul.INSTANCE;
                    case 243 -> F64x2Div.INSTANCE;
                    case 244 -> F64x2Min.INSTANCE;
                    case 245 -> F64x2Max.INSTANCE;
                    case 246 -> F64x2PMin.INSTANCE;
                    case 247 -> F64x2PMax.INSTANCE;

                    case 248 -> I32x4TruncSatF32x4S.INSTANCE;
                    case 249 -> I32x4TruncSatF32x4U.INSTANCE;
                    case 250 -> F32x4ConvertI32x4S.INSTANCE;
                    case 251 -> F32x4ConvertI32x4U.INSTANCE;
                    case 252 -> I32x4TruncSatF64x2SZero.INSTANCE;
                    case 253 -> I32x4TruncSatF64x2UZero.INSTANCE;
                    case 254 -> F64x2ConvertLowI32x4S.INSTANCE;
                    case 255 -> F64x2ConvertLowI32x4U.INSTANCE;
                    case 94 -> F32x4DemoteF64x2Zero.INSTANCE;
                    case 95 -> F64x2PromoteLowF32x4.INSTANCE;
                    default -> throw new ModuleParseException("Invalid vector instruction integer " + v);
                };

            }
            default -> throw new ModuleParseException("Invalid bytecode, unexpected byte " + b);
        };
    }

    //Reads instructions until encountering a 0x0B (end) or a 0x05 (else).
    //Returns these instructions in a list.
    record ManyReadResult(List<Instruction> instrs, boolean foundElse) {}
    public static ManyReadResult readMany(InputStream stream, List<StackType> moduleTypes, boolean allowElse) throws IOException, ModuleParseException {
        ArrayList<Instruction> result = new ArrayList<>();
        var instr = Instruction.read(stream, moduleTypes);
        while (instr != End.INSTANCE && instr != Else.INSTANCE) {
            result.add(instr);
            instr = Instruction.read(stream, moduleTypes);
        }
        if (!allowElse && instr == Else.INSTANCE)
            throw new ModuleParseException("Unexpected \"else\" token 0x05");
        return new ManyReadResult(result, instr == Else.INSTANCE);
    }
    private static void readMemIndex(InputStream stream) throws IOException, ModuleParseException {
        if (ParseHelper.readUnsignedWasmInt(stream) != 0)
            throw new ModuleParseException("Memory defaultIndex must be 0");
    }
}

