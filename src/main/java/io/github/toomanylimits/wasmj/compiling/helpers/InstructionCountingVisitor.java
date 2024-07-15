package io.github.toomanylimits.wasmj.compiling.helpers;

import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleInstruction;
import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleModule;
import io.github.toomanylimits.wasmj.compiling.visitor.SimpleInstructionVisitor;
import io.github.toomanylimits.wasmj.runtime.sandbox.InstanceLimiter;
import io.github.toomanylimits.wasmj.util.ListUtils;

import java.util.List;

// This should generally overestimate the number of instructions!
public class InstructionCountingVisitor extends SimpleInstructionVisitor<Void, InstructionCountingVisitor.CountDoneException> {

    private final SimpleModule module;
    private final InstanceLimiter limiter;
    public InstructionCountingVisitor(SimpleModule module) {
        this.module = module;
        this.limiter = module.instance.limiter;
    }

    private int remaining;
    private void add(int amount) throws CountDoneException {
        remaining -= amount;
        if (remaining <= 0) throw new CountDoneException();
    }

    // Returns true if the list's total is UNDER the maximum!
    public boolean check(List<SimpleInstruction> instructions, int maximum) {
        this.remaining = maximum;
        try {
            for (SimpleInstruction instruction : instructions)
                instruction.accept(this);
            return true;
        } catch (CountDoneException e) { return false; }
    }

    @Override
    public Void visitLocalStore(SimpleInstruction.LocalStore inst) throws InstructionCountingVisitor.CountDoneException {
        if (inst.type().isRef() && limiter.countsMemory) {
            add(inst.jvmLocalIndex() > 250 ? 10 : 6);
        } else {
            add(inst.jvmLocalIndex() > 250 ? 4 : 2);
        }
        return null;
    }

    @Override
    public Void visitLocalLoad(SimpleInstruction.LocalLoad inst) throws InstructionCountingVisitor.CountDoneException {
        if (inst.type().isRef() && limiter.countsMemory) {
            add(inst.jvmLocalIndex() > 250 ? 7 : 5);
        } else {
            add(inst.jvmLocalIndex() > 250 ? 4 : 2);
        }
        return null;
    }

    @Override
    public Void visitLocalTee(SimpleInstruction.LocalTee inst) throws InstructionCountingVisitor.CountDoneException {
        if (inst.type().isRef() && limiter.countsMemory) {
            add(inst.jvmLocalIndex() > 250 ? 14 : 10);
        } else {
            add(inst.jvmLocalIndex() > 250 ? 8 : 4);
        }
        return null;
    }

    @Override
    public Void visitBlock(SimpleInstruction.Block inst) throws InstructionCountingVisitor.CountDoneException {
        add(2);
        for (var inner : inst.inner())
            inner.accept(this);
        return null;
    }

    @Override
    public Void visitLoop(SimpleInstruction.Loop inst) throws InstructionCountingVisitor.CountDoneException {
        add(2);
        for (var inner : inst.inner())
            inner.accept(this);
        return null;
    }

    @Override
    public Void visitIfElse(SimpleInstruction.IfElse inst) throws InstructionCountingVisitor.CountDoneException {
        add(3);
        for (var inner : inst.ifTrue())
            inner.accept(this);
        for (var inner : inst.ifFalse())
            inner.accept(this);
        return null;
    }

    @Override
    public Void visitJump(SimpleInstruction.Jump inst) throws InstructionCountingVisitor.CountDoneException {
        add(inst.typesMaintained().size() * 3 + inst.typesPopped().size() * (limiter.countsMemory ? 2 : 1));
        return null;
    }

    @Override
    public Void visitJumpTable(SimpleInstruction.JumpTable inst) throws InstructionCountingVisitor.CountDoneException {
        int amount = (inst.typesMaintained().size() * 3) * (inst.typesPopped().size() + 1);
        for (var popped : inst.typesPopped())
            amount += popped.size() * (limiter.countsMemory ? 2 : 1);
        add(amount);
        return null;
    }

    @Override
    public Void visitReturn(SimpleInstruction.Return inst) throws InstructionCountingVisitor.CountDoneException {
        // 6 + 12 * num boxed values
        int amount = 1;
        if (limiter.countsMemory) {
            amount += inst.restOfStack().size() * 2;
            amount += 4;
        }
        if (inst.typesReturned().size() > 1)
            amount += (6 + inst.typesReturned().size() * 12); // Boxing into array isn't small
        add(amount);
        return null;
    }

    @Override
    public Void visitCall(SimpleInstruction.Call inst) throws InstructionCountingVisitor.CountDoneException {
        int amount = 3;
        int outTypeCount = module.functions[inst.funcIndex()].funcType().outTypes().size();
        if (outTypeCount > 1)
            amount += (6 + outTypeCount * 12);
        add(amount);
        return null;
    }

    @Override
    public Void visitCallIndirect(SimpleInstruction.CallIndirect inst) throws InstructionCountingVisitor.CountDoneException {
        int amount = 10;
        int slots = ListUtils.sumBy(inst.funcType().inTypes(), t -> t.stackSlots);
        if (slots > 2)
            amount += slots * 6;
        add(amount);
        return null;
    }

    @Override
    public Void visitGlobalSet(SimpleInstruction.GlobalSet inst) throws InstructionCountingVisitor.CountDoneException {
        add(3);
        return null;
    }

    @Override
    public Void visitGlobalGet(SimpleInstruction.GlobalGet inst) throws InstructionCountingVisitor.CountDoneException {
        add(3);
        return null;
    }

    @Override
    public Void visitConstant(SimpleInstruction.Constant inst) throws InstructionCountingVisitor.CountDoneException {
        add(5);
        return null;
    }

    @Override
    public Void visitPop(SimpleInstruction.Pop inst) throws InstructionCountingVisitor.CountDoneException {
        add(limiter.countsMemory ? 2 : 1);
        return null;
    }

    @Override
    public Void visitSelect(SimpleInstruction.Select inst) throws InstructionCountingVisitor.CountDoneException {
        add(5);
        return null;
    }

    @Override
    public Void visitRawBytecode(SimpleInstruction.RawBytecode inst) throws InstructionCountingVisitor.CountDoneException {
        add(3); // average-ish
        return null;
    }

    @Override
    public Void visitIntrinsic(SimpleInstruction.Intrinsic inst) throws InstructionCountingVisitor.CountDoneException {
        add(5); // idk could be anything
        return null;
    }

    public static class CountDoneException extends Exception {}

}
