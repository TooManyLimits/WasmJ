package io.github.toomanylimits.wasmj.compiling.visitor;

import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleInstruction;

public abstract class SimpleInstructionVisitor<R, T extends Throwable> {

    public abstract R visitLocalStore(SimpleInstruction.LocalStore inst) throws T;
    public abstract R visitLocalLoad(SimpleInstruction.LocalLoad inst) throws T;
    public abstract R visitLocalTee(SimpleInstruction.LocalTee inst) throws T;
    public abstract R visitBlock(SimpleInstruction.Block inst) throws T;
    public abstract R visitLoop(SimpleInstruction.Loop inst) throws T;
    public abstract R visitIfElse(SimpleInstruction.IfElse inst) throws T;
    public abstract R visitJump(SimpleInstruction.Jump inst) throws T;
    public abstract R visitJumpTable(SimpleInstruction.JumpTable inst) throws T;
    public abstract R visitReturn(SimpleInstruction.Return inst) throws T;
    public abstract R visitCall(SimpleInstruction.Call inst) throws T;
    public abstract R visitCallIndirect(SimpleInstruction.CallIndirect inst) throws T;
    public abstract R visitGlobalSet(SimpleInstruction.GlobalSet inst) throws T;
    public abstract R visitGlobalGet(SimpleInstruction.GlobalGet inst) throws T;
    public abstract R visitConstant(SimpleInstruction.Constant inst) throws T;
    public abstract R visitPop(SimpleInstruction.Pop inst) throws T;
    public abstract R visitSelect(SimpleInstruction.Select inst) throws T;
    public abstract R visitRawBytecode(SimpleInstruction.RawBytecode inst) throws T;
    public abstract R visitIntrinsic(SimpleInstruction.Intrinsic inst) throws T;

}
