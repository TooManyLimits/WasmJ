package io.github.toomanylimits.wasmj.compiler;

import io.github.toomanylimits.wasmj.parsing.instruction.Instruction;

/**
 * Returns 1 for most things, 0 for a few things.
 * Some things can't be counted without additional context.
 * These types of instructions result in null.
 */
public class CostCountingVisitor extends InstructionVisitor<Long> {

    @Override
    public Long visitEnd(Instruction.End inst) {
        return 0L;
    }

    @Override
    public Long visitElse(Instruction.Else inst) {
        return 0L;
    }

    @Override
    public Long visitUnreachable(Instruction.Unreachable inst) {
        return 1L;
    }

    @Override
    public Long visitNop(Instruction.Nop inst) {
        return 0L;
    }

    @Override
    public Long visitBlock(Instruction.Block inst) {
        return null;
    }

    @Override
    public Long visitLoop(Instruction.Loop inst) {
        return null;
    }

    @Override
    public Long visitIf(Instruction.If inst) {
        return null;
    }

    @Override
    public Long visitIfElse(Instruction.IfElse inst) {
        return null;
    }

    @Override
    public Long visitBranch(Instruction.Branch inst) {
        return null;
    }

    @Override
    public Long visitBranchIf(Instruction.BranchIf inst) {
        return null;
    }

    @Override
    public Long visitBranchTable(Instruction.BranchTable inst) {
        return null;
    }

    @Override
    public Long visitReturn(Instruction.Return inst) {
        return null;
    }

    @Override
    public Long visitCall(Instruction.Call inst) {
        return 1L;
    }

    @Override
    public Long visitCallIndirect(Instruction.CallIndirect inst) {
        return 1L; // Idk just randomly decided
    }

    @Override
    public Long visitRefNull(Instruction.RefNull inst) {
        return 1L;
    }

    @Override
    public Long visitRefIsNull(Instruction.RefIsNull inst) {
        return 1L;
    }

    @Override
    public Long visitRefFunc(Instruction.RefFunc inst) {
        return 1L;
    }

    @Override
    public Long visitDrop(Instruction.Drop inst) {
        return 1L;
    }

    @Override
    public Long visitSelect(Instruction.Select inst) {
        return 1L;
    }

    @Override
    public Long visitSelectFrom(Instruction.SelectFrom inst) {
        return 1L;
    }

    @Override
    public Long visitLocalGet(Instruction.LocalGet inst) {
        return 1L;
    }

    @Override
    public Long visitLocalSet(Instruction.LocalSet inst) {
        return 1L;
    }

    @Override
    public Long visitLocalTee(Instruction.LocalTee inst) {
        return 1L;
    }

    @Override
    public Long visitGlobalGet(Instruction.GlobalGet inst) {
        return 1L;
    }

    @Override
    public Long visitGlobalSet(Instruction.GlobalSet inst) {
        return 1L;
    }

    @Override
    public Long visitTableGet(Instruction.TableGet inst) {
        return 1L;
    }

    @Override
    public Long visitTableSet(Instruction.TableSet inst) {
        return 1L;
    }

    @Override
    public Long visitTableInit(Instruction.TableInit inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitElemDrop(Instruction.ElemDrop inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitTableGrow(Instruction.TableGrow inst) {
        // Memory and table grows require a bit of extra context, because
        // of the arbitrary amount of object/byte copying that happens.
        // This is just a base cost, that extra cost is added by the
        // MethodWritingVisitor when necessary.
        return 5L;
    }

    @Override
    public Long visitTableSize(Instruction.TableSize inst) {
        return 1L;
    }

    @Override
    public Long visitTableCopy(Instruction.TableCopy inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitTableFill(Instruction.TableFill inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI32Load(Instruction.I32Load inst) {
        return 1L;
    }

    @Override
    public Long visitI64Load(Instruction.I64Load inst) {
        return 1L;
    }

    @Override
    public Long visitF32Load(Instruction.F32Load inst) {
        return 1L;
    }

    @Override
    public Long visitF64Load(Instruction.F64Load inst) {
        return 1L;
    }

    @Override
    public Long visitI32Load8S(Instruction.I32Load8S inst) {
        return 1L;
    }

    @Override
    public Long visitI32Load8U(Instruction.I32Load8U inst) {
        return 1L;
    }

    @Override
    public Long visitI32Load16S(Instruction.I32Load16S inst) {
        return 1L;
    }

    @Override
    public Long visitI32Load16U(Instruction.I32Load16U inst) {
        return 1L;
    }

    @Override
    public Long visitI64Load8S(Instruction.I64Load8S inst) {
        return 1L;
    }

    @Override
    public Long visitI64Load8U(Instruction.I64Load8U inst) {
        return 1L;
    }

    @Override
    public Long visitI64Load16S(Instruction.I64Load16S inst) {
        return 1L;
    }

    @Override
    public Long visitI64Load16U(Instruction.I64Load16U inst) {
        return 1L;
    }

    @Override
    public Long visitI64Load32S(Instruction.I64Load32S inst) {
        return 1L;
    }

    @Override
    public Long visitI64Load32U(Instruction.I64Load32U inst) {
        return 1L;
    }

    @Override
    public Long visitI32Store(Instruction.I32Store inst) {
        return 1L;
    }

    @Override
    public Long visitI64Store(Instruction.I64Store inst) {
        return 1L;
    }

    @Override
    public Long visitF32Store(Instruction.F32Store inst) {
        return 1L;
    }

    @Override
    public Long visitF64Store(Instruction.F64Store inst) {
        return 1L;
    }

    @Override
    public Long visitI32Store8(Instruction.I32Store8 inst) {
        return 1L;
    }

    @Override
    public Long visitI32Store16(Instruction.I32Store16 inst) {
        return 1L;
    }

    @Override
    public Long visitI64Store8(Instruction.I64Store8 inst) {
        return 1L;
    }

    @Override
    public Long visitI64Store16(Instruction.I64Store16 inst) {
        return 1L;
    }

    @Override
    public Long visitI64Store32(Instruction.I64Store32 inst) {
        return 1L;
    }

    @Override
    public Long visitMemorySize(Instruction.MemorySize inst) {
        return 1L;
    }

    @Override
    public Long visitMemoryGrow(Instruction.MemoryGrow inst) {
        // Memory and table grows require a bit of extra context, because
        // of the arbitrary amount of object/byte copying that happens.
        // This is just a base cost, that extra cost is added by the
        // MethodWritingVisitor when necessary.
        return 5L;
    }

    @Override
    public Long visitMemoryInit(Instruction.MemoryInit inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitDataDrop(Instruction.DataDrop inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitMemoryCopy(Instruction.MemoryCopy inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitMemoryFill(Instruction.MemoryFill inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI32Const(Instruction.I32Const inst) {
        return 1L;
    }

    @Override
    public Long visitI64Const(Instruction.I64Const inst) {
        return 1L;
    }

    @Override
    public Long visitF32Const(Instruction.F32Const inst) {
        return 1L;
    }

    @Override
    public Long visitF64Const(Instruction.F64Const inst) {
        return 1L;
    }

    @Override
    public Long visitI32Eqz(Instruction.I32Eqz inst) {
        return 1L;
    }

    @Override
    public Long visitI32Eq(Instruction.I32Eq inst) {
        return 1L;
    }

    @Override
    public Long visitI32Ne(Instruction.I32Ne inst) {
        return 1L;
    }

    @Override
    public Long visitI32LtS(Instruction.I32LtS inst) {
        return 1L;
    }

    @Override
    public Long visitI32LtU(Instruction.I32LtU inst) {
        return 1L;
    }

    @Override
    public Long visitI32GtS(Instruction.I32GtS inst) {
        return 1L;
    }

    @Override
    public Long visitI32GtU(Instruction.I32GtU inst) {
        return 1L;
    }

    @Override
    public Long visitI32LeS(Instruction.I32LeS inst) {
        return 1L;
    }

    @Override
    public Long visitI32LeU(Instruction.I32LeU inst) {
        return 1L;
    }

    @Override
    public Long visitI32GeS(Instruction.I32GeS inst) {
        return 1L;
    }

    @Override
    public Long visitI32GeU(Instruction.I32GeU inst) {
        return 1L;
    }

    @Override
    public Long visitI64Eqz(Instruction.I64Eqz inst) {
        return 1L;
    }

    @Override
    public Long visitI64Eq(Instruction.I64Eq inst) {
        return 1L;
    }

    @Override
    public Long visitI64Ne(Instruction.I64Ne inst) {
        return 1L;
    }

    @Override
    public Long visitI64LtS(Instruction.I64LtS inst) {
        return 1L;
    }

    @Override
    public Long visitI64LtU(Instruction.I64LtU inst) {
        return 1L;
    }

    @Override
    public Long visitI64GtS(Instruction.I64GtS inst) {
        return 1L;
    }

    @Override
    public Long visitI64GtU(Instruction.I64GtU inst) {
        return 1L;
    }

    @Override
    public Long visitI64LeS(Instruction.I64LeS inst) {
        return 1L;
    }

    @Override
    public Long visitI64LeU(Instruction.I64LeU inst) {
        return 1L;
    }

    @Override
    public Long visitI64GeS(Instruction.I64GeS inst) {
        return 1L;
    }

    @Override
    public Long visitI64GeU(Instruction.I64GeU inst) {
        return 1L;
    }

    @Override
    public Long visitF32Eq(Instruction.F32Eq inst) {
        return 1L;
    }

    @Override
    public Long visitF32Ne(Instruction.F32Ne inst) {
        return 1L;
    }

    @Override
    public Long visitF32Lt(Instruction.F32Lt inst) {
        return 1L;
    }

    @Override
    public Long visitF32Gt(Instruction.F32Gt inst) {
        return 1L;
    }

    @Override
    public Long visitF32Le(Instruction.F32Le inst) {
        return 1L;
    }

    @Override
    public Long visitF32Ge(Instruction.F32Ge inst) {
        return 1L;
    }

    @Override
    public Long visitF64Eq(Instruction.F64Eq inst) {
        return 1L;
    }

    @Override
    public Long visitF64Ne(Instruction.F64Ne inst) {
        return 1L;
    }

    @Override
    public Long visitF64Lt(Instruction.F64Lt inst) {
        return 1L;
    }

    @Override
    public Long visitF64Gt(Instruction.F64Gt inst) {
        return 1L;
    }

    @Override
    public Long visitF64Le(Instruction.F64Le inst) {
        return 1L;
    }

    @Override
    public Long visitF64Ge(Instruction.F64Ge inst) {
        return 1L;
    }

    @Override
    public Long visitI32Clz(Instruction.I32Clz inst) {
        return 1L;
    }

    @Override
    public Long visitI32Ctz(Instruction.I32Ctz inst) {
        return 1L;
    }

    @Override
    public Long visitI32PopCnt(Instruction.I32PopCnt inst) {
        return 1L;
    }

    @Override
    public Long visitI32Add(Instruction.I32Add inst) {
        return 1L;
    }

    @Override
    public Long visitI32Sub(Instruction.I32Sub inst) {
        return 1L;
    }

    @Override
    public Long visitI32Mul(Instruction.I32Mul inst) {
        return 1L;
    }

    @Override
    public Long visitI32DivS(Instruction.I32DivS inst) {
        return 1L;
    }

    @Override
    public Long visitI32DivU(Instruction.I32DivU inst) {
        return 1L;
    }

    @Override
    public Long visitI32RemS(Instruction.I32RemS inst) {
        return 1L;
    }

    @Override
    public Long visitI32RemU(Instruction.I32RemU inst) {
        return 1L;
    }

    @Override
    public Long visitI32And(Instruction.I32And inst) {
        return 1L;
    }

    @Override
    public Long visitI32Or(Instruction.I32Or inst) {
        return 1L;
    }

    @Override
    public Long visitI32Xor(Instruction.I32Xor inst) {
        return 1L;
    }

    @Override
    public Long visitI32Shl(Instruction.I32Shl inst) {
        return 1L;
    }

    @Override
    public Long visitI32ShrS(Instruction.I32ShrS inst) {
        return 1L;
    }

    @Override
    public Long visitI32ShrU(Instruction.I32ShrU inst) {
        return 1L;
    }

    @Override
    public Long visitI32Rotl(Instruction.I32Rotl inst) {
        return 1L;
    }

    @Override
    public Long visitI32Rotr(Instruction.I32Rotr inst) {
        return 1L;
    }

    @Override
    public Long visitI64Clz(Instruction.I64Clz inst) {
        return 1L;
    }

    @Override
    public Long visitI64Ctz(Instruction.I64Ctz inst) {
        return 1L;
    }

    @Override
    public Long visitI64PopCnt(Instruction.I64PopCnt inst) {
        return 1L;
    }

    @Override
    public Long visitI64Add(Instruction.I64Add inst) {
        return 1L;
    }

    @Override
    public Long visitI64Sub(Instruction.I64Sub inst) {
        return 1L;
    }

    @Override
    public Long visitI64Mul(Instruction.I64Mul inst) {
        return 1L;
    }

    @Override
    public Long visitI64DivS(Instruction.I64DivS inst) {
        return 1L;
    }

    @Override
    public Long visitI64DivU(Instruction.I64DivU inst) {
        return 1L;
    }

    @Override
    public Long visitI64RemS(Instruction.I64RemS inst) {
        return 1L;
    }

    @Override
    public Long visitI64RemU(Instruction.I64RemU inst) {
        return 1L;
    }

    @Override
    public Long visitI64And(Instruction.I64And inst) {
        return 1L;
    }

    @Override
    public Long visitI64Or(Instruction.I64Or inst) {
        return 1L;
    }

    @Override
    public Long visitI64Xor(Instruction.I64Xor inst) {
        return 1L;
    }

    @Override
    public Long visitI64Shl(Instruction.I64Shl inst) {
        return 1L;
    }

    @Override
    public Long visitI64ShrS(Instruction.I64ShrS inst) {
        return 1L;
    }

    @Override
    public Long visitI64ShrU(Instruction.I64ShrU inst) {
        return 1L;
    }

    @Override
    public Long visitI64Rotl(Instruction.I64Rotl inst) {
        return 1L;
    }

    @Override
    public Long visitI64Rotr(Instruction.I64Rotr inst) {
        return 1L;
    }

    @Override
    public Long visitF32Abs(Instruction.F32Abs inst) {
        return 1L;
    }

    @Override
    public Long visitF32Neg(Instruction.F32Neg inst) {
        return 1L;
    }

    @Override
    public Long visitF32Ceil(Instruction.F32Ceil inst) {
        return 1L;
    }

    @Override
    public Long visitF32Floor(Instruction.F32Floor inst) {
        return 1L;
    }

    @Override
    public Long visitF32Trunc(Instruction.F32Trunc inst) {
        return 1L;
    }

    @Override
    public Long visitF32Nearest(Instruction.F32Nearest inst) {
        return 1L;
    }

    @Override
    public Long visitF32Sqrt(Instruction.F32Sqrt inst) {
        return 1L;
    }

    @Override
    public Long visitF32Add(Instruction.F32Add inst) {
        return 1L;
    }

    @Override
    public Long visitF32Sub(Instruction.F32Sub inst) {
        return 1L;
    }

    @Override
    public Long visitF32Mul(Instruction.F32Mul inst) {
        return 1L;
    }

    @Override
    public Long visitF32Div(Instruction.F32Div inst) {
        return 1L;
    }

    @Override
    public Long visitF32Min(Instruction.F32Min inst) {
        return 1L;
    }

    @Override
    public Long visitF32Max(Instruction.F32Max inst) {
        return 1L;
    }

    @Override
    public Long visitF32Copysign(Instruction.F32Copysign inst) {
        return 1L;
    }

    @Override
    public Long visitF64Abs(Instruction.F64Abs inst) {
        return 1L;
    }

    @Override
    public Long visitF64Neg(Instruction.F64Neg inst) {
        return 1L;
    }

    @Override
    public Long visitF64Ceil(Instruction.F64Ceil inst) {
        return 1L;
    }

    @Override
    public Long visitF64Floor(Instruction.F64Floor inst) {
        return 1L;
    }

    @Override
    public Long visitF64Trunc(Instruction.F64Trunc inst) {
        return 1L;
    }

    @Override
    public Long visitF64Nearest(Instruction.F64Nearest inst) {
        return 1L;
    }

    @Override
    public Long visitF64Sqrt(Instruction.F64Sqrt inst) {
        return 1L;
    }

    @Override
    public Long visitF64Add(Instruction.F64Add inst) {
        return 1L;
    }

    @Override
    public Long visitF64Sub(Instruction.F64Sub inst) {
        return 1L;
    }

    @Override
    public Long visitF64Mul(Instruction.F64Mul inst) {
        return 1L;
    }

    @Override
    public Long visitF64Div(Instruction.F64Div inst) {
        return 1L;
    }

    @Override
    public Long visitF64Min(Instruction.F64Min inst) {
        return 1L;
    }

    @Override
    public Long visitF64Max(Instruction.F64Max inst) {
        return 1L;
    }

    @Override
    public Long visitF64Copysign(Instruction.F64Copysign inst) {
        return 1L;
    }

    @Override
    public Long visitI32WrapI64(Instruction.I32WrapI64 inst) {
        return 1L;
    }

    @Override
    public Long visitI32TruncF32S(Instruction.I32TruncF32S inst) {
        return 1L;
    }

    @Override
    public Long visitI32TruncF32U(Instruction.I32TruncF32U inst) {
        return 1L;
    }

    @Override
    public Long visitI32TruncF64S(Instruction.I32TruncF64S inst) {
        return 1L;
    }

    @Override
    public Long visitI32TruncF64U(Instruction.I32TruncF64U inst) {
        return 1L;
    }

    @Override
    public Long visitI64ExtendI32S(Instruction.I64ExtendI32S inst) {
        return 1L;
    }

    @Override
    public Long visitI64ExtendI32U(Instruction.I64ExtendI32U inst) {
        return 1L;
    }

    @Override
    public Long visitI64TruncF32S(Instruction.I64TruncF32S inst) {
        return 1L;
    }

    @Override
    public Long visitI64TruncF32U(Instruction.I64TruncF32U inst) {
        return 1L;
    }

    @Override
    public Long visitI64TruncF64S(Instruction.I64TruncF64S inst) {
        return 1L;
    }

    @Override
    public Long visitI64TruncF64U(Instruction.I64TruncF64U inst) {
        return 1L;
    }

    @Override
    public Long visitF32ConvertI32S(Instruction.F32ConvertI32S inst) {
        return 1L;
    }

    @Override
    public Long visitF32ConvertI32U(Instruction.F32ConvertI32U inst) {
        return 1L;
    }

    @Override
    public Long visitF32ConvertI64S(Instruction.F32ConvertI64S inst) {
        return 1L;
    }

    @Override
    public Long visitF32ConvertI64U(Instruction.F32ConvertI64U inst) {
        return 1L;
    }

    @Override
    public Long visitF32DemoteF64(Instruction.F32DemoteF64 inst) {
        return 1L;
    }

    @Override
    public Long visitF64ConvertI32S(Instruction.F64ConvertI32S inst) {
        return 1L;
    }

    @Override
    public Long visitF64ConvertI32U(Instruction.F64ConvertI32U inst) {
        return 1L;
    }

    @Override
    public Long visitF64ConvertI64S(Instruction.F64ConvertI64S inst) {
        return 1L;
    }

    @Override
    public Long visitF64ConvertI64U(Instruction.F64ConvertI64U inst) {
        return 1L;
    }

    @Override
    public Long visitF64PromoteF32(Instruction.F64PromoteF32 inst) {
        return 1L;
    }

    @Override
    public Long visitI32ReinterpretF32(Instruction.I32ReinterpretF32 inst) {
        return 1L;
    }

    @Override
    public Long visitI64ReinterpretF64(Instruction.I64ReinterpretF64 inst) {
        return 1L;
    }

    @Override
    public Long visitF32ReinterpretI32(Instruction.F32ReinterpretI32 inst) {
        return 1L;
    }

    @Override
    public Long visitF64ReinterpretI64(Instruction.F64ReinterpretI64 inst) {
        return 1L;
    }

    @Override
    public Long visitI32Extend8S(Instruction.I32Extend8S inst) {
        return 1L;
    }

    @Override
    public Long visitI32Extend16S(Instruction.I32Extend16S inst) {
        return 1L;
    }

    @Override
    public Long visitI64Extend8S(Instruction.I64Extend8S inst) {
        return 1L;
    }

    @Override
    public Long visitI64Extend16S(Instruction.I64Extend16S inst) {
        return 1L;
    }

    @Override
    public Long visitI64Extend32S(Instruction.I64Extend32S inst) {
        return 1L;
    }

    @Override
    public Long visitI32TruncSatF32S(Instruction.I32TruncSatF32S inst) {
        return 1L;
    }

    @Override
    public Long visitI32TruncSatF32U(Instruction.I32TruncSatF32U inst) {
        return 1L;
    }

    @Override
    public Long visitI32TruncSatF64S(Instruction.I32TruncSatF64S inst) {
        return 1L;
    }

    @Override
    public Long visitI32TruncSatF64U(Instruction.I32TruncSatF64U inst) {
        return 1L;
    }

    @Override
    public Long visitI64TruncSatF32S(Instruction.I64TruncSatF32S inst) {
        return 1L;
    }

    @Override
    public Long visitI64TruncSatF32U(Instruction.I64TruncSatF32U inst) {
        return 1L;
    }

    @Override
    public Long visitI64TruncSatF64S(Instruction.I64TruncSatF64S inst) {
        return 1L;
    }

    @Override
    public Long visitI64TruncSatF64U(Instruction.I64TruncSatF64U inst) {
        return 1L;
    }

    @Override
    public Long visitV128Load(Instruction.V128Load inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitV128Load8x8S(Instruction.V128Load8x8S inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitV128Load8x8U(Instruction.V128Load8x8U inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitV128Load16x4S(Instruction.V128Load16x4S inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitV128Load16x4U(Instruction.V128Load16x4U inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitV128Load32x2S(Instruction.V128Load32x2S inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitV128Load32x2U(Instruction.V128Load32x2U inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitV128Load8Splat(Instruction.V128Load8Splat inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitV128Load16Splat(Instruction.V128Load16Splat inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitV128Load32Splat(Instruction.V128Load32Splat inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitV128Load64Splat(Instruction.V128Load64Splat inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitV128Load32Zero(Instruction.V128Load32Zero inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitV128Load64Zero(Instruction.V128Load64Zero inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitV128Store(Instruction.V128Store inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitV128Load8Lane(Instruction.V128Load8Lane inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitV128Load16Lane(Instruction.V128Load16Lane inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitV128Load32Lane(Instruction.V128Load32Lane inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitV128Load64Lane(Instruction.V128Load64Lane inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitV128Store8Lane(Instruction.V128Store8Lane inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitV128Store16Lane(Instruction.V128Store16Lane inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitV128Store32Lane(Instruction.V128Store32Lane inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitV128Store64Lane(Instruction.V128Store64Lane inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitV128Const(Instruction.V128Const inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI8x16Shuffle(Instruction.I8x16Shuffle inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI8x16ExtractLaneS(Instruction.I8x16ExtractLaneS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI8x16ExtractLaneU(Instruction.I8x16ExtractLaneU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI8x16ReplaceLane(Instruction.I8x16ReplaceLane inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI16x8ExtractLaneS(Instruction.I16x8ExtractLaneS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI16x8ExtractLaneU(Instruction.I16x8ExtractLaneU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI16x8ReplaceLane(Instruction.I16x8ReplaceLane inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI32x4ExtractLane(Instruction.I32x4ExtractLane inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI32x4ReplaceLane(Instruction.I32x4ReplaceLane inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI64x2ExtractLane(Instruction.I64x2ExtractLane inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI64x2ReplaceLane(Instruction.I64x2ReplaceLane inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF32x4ExtractLane(Instruction.F32x4ExtractLane inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF32x4ReplaceLane(Instruction.F32x4ReplaceLane inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF64x2ExtractLane(Instruction.F64x2ExtractLane inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF64x2ReplaceLane(Instruction.F64x2ReplaceLane inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI8x16Swizzle(Instruction.I8x16Swizzle inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI8x16Splat(Instruction.I8x16Splat inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI16x8Splat(Instruction.I16x8Splat inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI32x4Splat(Instruction.I32x4Splat inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI64x2Splat(Instruction.I64x2Splat inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF32x4Splat(Instruction.F32x4Splat inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF64x2Splat(Instruction.F64x2Splat inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI8x16Eq(Instruction.I8x16Eq inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI8x16Ne(Instruction.I8x16Ne inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI8x16LtS(Instruction.I8x16LtS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI8x16LtU(Instruction.I8x16LtU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI8x16GtS(Instruction.I8x16GtS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI8x16GtU(Instruction.I8x16GtU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI8x16LeS(Instruction.I8x16LeS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI8x16LeU(Instruction.I8x16LeU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI8x16GeS(Instruction.I8x16GeS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI8x16GeU(Instruction.I8x16GeU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI16x8Eq(Instruction.I16x8Eq inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI16x8Ne(Instruction.I16x8Ne inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI16x8LtS(Instruction.I16x8LtS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI16x8LtU(Instruction.I16x8LtU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI16x8GtS(Instruction.I16x8GtS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI16x8GtU(Instruction.I16x8GtU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI16x8LeS(Instruction.I16x8LeS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI16x8LeU(Instruction.I16x8LeU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI16x8GeS(Instruction.I16x8GeS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI16x8GeU(Instruction.I16x8GeU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI32x4Eq(Instruction.I32x4Eq inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI32x4Ne(Instruction.I32x4Ne inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI32x4LtS(Instruction.I32x4LtS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI32x4LtU(Instruction.I32x4LtU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI32x4GtS(Instruction.I32x4GtS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI32x4GtU(Instruction.I32x4GtU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI32x4LeS(Instruction.I32x4LeS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI32x4LeU(Instruction.I32x4LeU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI32x4GeS(Instruction.I32x4GeS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI32x4GeU(Instruction.I32x4GeU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI64x2Eq(Instruction.I64x2Eq inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI64x2Ne(Instruction.I64x2Ne inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI64x2LtS(Instruction.I64x2LtS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI64x2GtS(Instruction.I64x2GtS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI64x2LeS(Instruction.I64x2LeS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI64x2GeS(Instruction.I64x2GeS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF32x4Eq(Instruction.F32x4Eq inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF32x4Ne(Instruction.F32x4Ne inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF32x4Lt(Instruction.F32x4Lt inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF32x4Gt(Instruction.F32x4Gt inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF32x4Le(Instruction.F32x4Le inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF32x4Ge(Instruction.F32x4Ge inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF64x2Eq(Instruction.F64x2Eq inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF64x2Ne(Instruction.F64x2Ne inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF64x2Lt(Instruction.F64x2Lt inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF64x2Gt(Instruction.F64x2Gt inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF64x2Le(Instruction.F64x2Le inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF64x2Ge(Instruction.F64x2Ge inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitV128Not(Instruction.V128Not inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitV128And(Instruction.V128And inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitV128AndNot(Instruction.V128AndNot inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitV128Or(Instruction.V128Or inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitV128Xor(Instruction.V128Xor inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitV128Bitselect(Instruction.V128Bitselect inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitV128AnyTrue(Instruction.V128AnyTrue inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI8x16Abs(Instruction.I8x16Abs inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI8x16Neg(Instruction.I8x16Neg inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI8x16PopCnt(Instruction.I8x16PopCnt inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI8x16AllTrue(Instruction.I8x16AllTrue inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI8x16Bitmask(Instruction.I8x16Bitmask inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI8x16NarrowI16x8S(Instruction.I8x16NarrowI16x8S inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI8x16NarrowI16x8U(Instruction.I8x16NarrowI16x8U inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI8x16Shl(Instruction.I8x16Shl inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI8x16ShrS(Instruction.I8x16ShrS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI8x16ShrU(Instruction.I8x16ShrU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI8x16Add(Instruction.I8x16Add inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI8x16AddSatS(Instruction.I8x16AddSatS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI8x16AddSatU(Instruction.I8x16AddSatU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI8x16Sub(Instruction.I8x16Sub inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI8x16SubSatS(Instruction.I8x16SubSatS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI8x16SubSatU(Instruction.I8x16SubSatU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI8x16MinS(Instruction.I8x16MinS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI8x16MinU(Instruction.I8x16MinU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI8x16MaxS(Instruction.I8x16MaxS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI8x16MaxU(Instruction.I8x16MaxU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI8x16AvgrU(Instruction.I8x16AvgrU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI16x8ExtAddPairwiseI8x16S(Instruction.I16x8ExtAddPairwiseI8x16S inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI16x8ExtAddPairwiseI8x16U(Instruction.I16x8ExtAddPairwiseI8x16U inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI16x8Abs(Instruction.I16x8Abs inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI16x8Neg(Instruction.I16x8Neg inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI16x8Q15MulrSatS(Instruction.I16x8Q15MulrSatS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI16x8AllTrue(Instruction.I16x8AllTrue inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI16x8Bitmask(Instruction.I16x8Bitmask inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI16x8NarrowI32x4S(Instruction.I16x8NarrowI32x4S inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI16x8NarrowI32x4U(Instruction.I16x8NarrowI32x4U inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI16x8ExtendLowI8x16S(Instruction.I16x8ExtendLowI8x16S inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI16x8ExtendHighI8x16S(Instruction.I16x8ExtendHighI8x16S inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI16x8ExtendLowI8x16U(Instruction.I16x8ExtendLowI8x16U inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI16x8ExtendHighI8x16U(Instruction.I16x8ExtendHighI8x16U inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI16x8Shl(Instruction.I16x8Shl inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI16x8ShrS(Instruction.I16x8ShrS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI16x8ShrU(Instruction.I16x8ShrU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI16x8Add(Instruction.I16x8Add inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI16x8AddSatS(Instruction.I16x8AddSatS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI16x8AddSatU(Instruction.I16x8AddSatU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI16x8Sub(Instruction.I16x8Sub inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI16x8SubSatS(Instruction.I16x8SubSatS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI16x8SubSatU(Instruction.I16x8SubSatU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI16x8Mul(Instruction.I16x8Mul inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI16x8MinS(Instruction.I16x8MinS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI16x8MinU(Instruction.I16x8MinU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI16x8MaxS(Instruction.I16x8MaxS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI16x8MaxU(Instruction.I16x8MaxU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI16x8AvgrU(Instruction.I16x8AvgrU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI16x8ExtMulLowI8x16S(Instruction.I16x8ExtMulLowI8x16S inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI16x8ExtMulHighI8x16S(Instruction.I16x8ExtMulHighI8x16S inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI16x8ExtMulLowI8x16U(Instruction.I16x8ExtMulLowI8x16U inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI16x8ExtMulHighI8x16U(Instruction.I16x8ExtMulHighI8x16U inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI32x4ExtAddPairwiseI16x8S(Instruction.I32x4ExtAddPairwiseI16x8S inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI32x4ExtAddPairwiseI16x8U(Instruction.I32x4ExtAddPairwiseI16x8U inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI32x4Abs(Instruction.I32x4Abs inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI32x4Neg(Instruction.I32x4Neg inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI32x4AllTrue(Instruction.I32x4AllTrue inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI32x4Bitmask(Instruction.I32x4Bitmask inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI32x4ExtendLowI16x8S(Instruction.I32x4ExtendLowI16x8S inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI32x4ExtendHighI16x8S(Instruction.I32x4ExtendHighI16x8S inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI32x4ExtendLowI16x8U(Instruction.I32x4ExtendLowI16x8U inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI32x4ExtendHighI16x8U(Instruction.I32x4ExtendHighI16x8U inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI32x4Shl(Instruction.I32x4Shl inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI32x4ShrS(Instruction.I32x4ShrS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI32x4ShrU(Instruction.I32x4ShrU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI32x4Add(Instruction.I32x4Add inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI32x4Sub(Instruction.I32x4Sub inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI32x4Mul(Instruction.I32x4Mul inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI32x4MinS(Instruction.I32x4MinS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI32x4MinU(Instruction.I32x4MinU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI32x4MaxS(Instruction.I32x4MaxS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI32x4MaxU(Instruction.I32x4MaxU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI32x4DotI16x8S(Instruction.I32x4DotI16x8S inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI32x4ExtMulLowI16x8S(Instruction.I32x4ExtMulLowI16x8S inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI32x4ExtMulHighI16x8S(Instruction.I32x4ExtMulHighI16x8S inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI32x4ExtMulLowI16x8U(Instruction.I32x4ExtMulLowI16x8U inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI32x4ExtMulHighI16x8U(Instruction.I32x4ExtMulHighI16x8U inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI64x2Abs(Instruction.I64x2Abs inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI64x2Neg(Instruction.I64x2Neg inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI64x2AllTrue(Instruction.I64x2AllTrue inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI64x2Bitmask(Instruction.I64x2Bitmask inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI64x2ExtendLowI32x4S(Instruction.I64x2ExtendLowI32x4S inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI64x2ExtendHighI32x4S(Instruction.I64x2ExtendHighI32x4S inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI64x2ExtendLowI32x4U(Instruction.I64x2ExtendLowI32x4U inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI64x2ExtendHighI32x4U(Instruction.I64x2ExtendHighI32x4U inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI64x2Shl(Instruction.I64x2Shl inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI64x2ShrS(Instruction.I64x2ShrS inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI64x2ShrU(Instruction.I64x2ShrU inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI64x2Add(Instruction.I64x2Add inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI64x2Sub(Instruction.I64x2Sub inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI64x2Mul(Instruction.I64x2Mul inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI64x2ExtMulLowI32x4S(Instruction.I64x2ExtMulLowI32x4S inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI64x2ExtMulHighI32x4S(Instruction.I64x2ExtMulHighI32x4S inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI64x2ExtMulLowI32x4U(Instruction.I64x2ExtMulLowI32x4U inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI64x2ExtMulHighI32x4U(Instruction.I64x2ExtMulHighI32x4U inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF32x4Ceil(Instruction.F32x4Ceil inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF32x4Floor(Instruction.F32x4Floor inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF32x4Trunc(Instruction.F32x4Trunc inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF32x4Nearest(Instruction.F32x4Nearest inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF32x4Abs(Instruction.F32x4Abs inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF32x4Neg(Instruction.F32x4Neg inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF32x4Sqrt(Instruction.F32x4Sqrt inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF32x4Add(Instruction.F32x4Add inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF32x4Sub(Instruction.F32x4Sub inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF32x4Mul(Instruction.F32x4Mul inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF32x4Div(Instruction.F32x4Div inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF32x4Min(Instruction.F32x4Min inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF32x4Max(Instruction.F32x4Max inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF32x4PMin(Instruction.F32x4PMin inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF32x4PMax(Instruction.F32x4PMax inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF64x2Ceil(Instruction.F64x2Ceil inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF64x2Floor(Instruction.F64x2Floor inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF64x2Trunc(Instruction.F64x2Trunc inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF64x2Nearest(Instruction.F64x2Nearest inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF64x2Abs(Instruction.F64x2Abs inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF64x2Neg(Instruction.F64x2Neg inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF64x2Sqrt(Instruction.F64x2Sqrt inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF64x2Add(Instruction.F64x2Add inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF64x2Sub(Instruction.F64x2Sub inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF64x2Mul(Instruction.F64x2Mul inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF64x2Div(Instruction.F64x2Div inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF64x2Min(Instruction.F64x2Min inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF64x2Max(Instruction.F64x2Max inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF64x2PMin(Instruction.F64x2PMin inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF64x2PMax(Instruction.F64x2PMax inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI32x4TruncSatF32x4S(Instruction.I32x4TruncSatF32x4S inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI32x4TruncSatF32x4U(Instruction.I32x4TruncSatF32x4U inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF32x4ConvertI32x4S(Instruction.F32x4ConvertI32x4S inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF32x4ConvertI32x4U(Instruction.F32x4ConvertI32x4U inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI32x4TruncSatF64x2SZero(Instruction.I32x4TruncSatF64x2SZero inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitI32x4TruncSatF64x2UZero(Instruction.I32x4TruncSatF64x2UZero inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF64x2ConvertLowI32x4S(Instruction.F64x2ConvertLowI32x4S inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF64x2ConvertLowI32x4U(Instruction.F64x2ConvertLowI32x4U inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF32x4DemoteF64x2Zero(Instruction.F32x4DemoteF64x2Zero inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Long visitF64x2PromoteLowF32x4(Instruction.F64x2PromoteLowF32x4 inst) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
