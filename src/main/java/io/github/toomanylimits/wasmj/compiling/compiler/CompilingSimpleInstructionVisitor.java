package io.github.toomanylimits.wasmj.compiling.compiler;

import io.github.toomanylimits.wasmj.compiling.helpers.CallingHelpers;
import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleInstruction;
import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleModule;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.ClassGenCallback;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.sandbox.DecRefCount;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.sandbox.IncInstructionsBy;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.sandbox.IncRefCount;
import io.github.toomanylimits.wasmj.compiling.visitor.SimpleInstructionVisitor;
import io.github.toomanylimits.wasmj.compiling.helpers.BytecodeHelper;
import io.github.toomanylimits.wasmj.parsing.types.ValType;
import io.github.toomanylimits.wasmj.util.ListUtils;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.*;

public class CompilingSimpleInstructionVisitor extends SimpleInstructionVisitor<Void, RuntimeException> {

    private final SimpleModule module;
    private final MethodVisitor visitor;
    private final Set<ClassGenCallback> classGenCallbacks;
    private final Stack<Label> labelStack = new Stack<>();
    private final int nextLocalSlot; // The next free local slot, unused by the WASM code, used for temporary data.

    public CompilingSimpleInstructionVisitor(SimpleModule module, MethodVisitor visitor, int nextLocalSlot, Set<ClassGenCallback> classGenCallbacks) {
        this.module = module;
        this.visitor = visitor;
        this.nextLocalSlot = nextLocalSlot;
        this.classGenCallbacks = classGenCallbacks;
    }

    public int getNextLocalSlot() {
        return nextLocalSlot; // May change?
    }

    @Override
    public Void visitLocalStore(SimpleInstruction.LocalStore inst) throws RuntimeException {
        // If we're ref-counting and this local is a reference type, then
        // we need to decrement the refcount of the object that was previously
        // stored in this local.
        if (inst.type().isRef() && module.instance.limiter.countsMemory) {
            visitor.visitVarInsn(Opcodes.ALOAD, inst.jvmLocalIndex()); // Load the previous object to the stack
            visitIntrinsic(DecRefCount.INSTANCE); // Drop the ref type, decrementing its refcount
        }
        // In any case, we want to store an object from the stack into the given variable.
        visitor.visitVarInsn(inst.type().storeOpcode, inst.jvmLocalIndex());
        return null;
    }

    @Override
    public Void visitLocalLoad(SimpleInstruction.LocalLoad inst) throws RuntimeException {
        // If we're ref-counting and this local is a reference type, then increment its refcount
        // once we load it.
        if (inst.type().isRef() && module.instance.limiter.countsMemory) {
            visitor.visitVarInsn(Opcodes.ALOAD, inst.jvmLocalIndex()); // Get the object
            visitor.visitInsn(Opcodes.DUP); // Dup it
            visitIntrinsic(IncRefCount.INSTANCE); // Increment its refcount
        } else {
            // Otherwise, just fetch the local normally.
            visitor.visitVarInsn(inst.type().loadOpcode, inst.jvmLocalIndex());
        }
        return null;
    }

    @Override
    public Void visitLocalTee(SimpleInstruction.LocalTee inst) throws RuntimeException {
        // can be broken down into a local store then a local load
        visitLocalStore(new SimpleInstruction.LocalStore(inst.type(), inst.jvmLocalIndex()));
        visitLocalLoad(new SimpleInstruction.LocalLoad(inst.type(), inst.jvmLocalIndex()));
        return null;
    }

    private boolean isControlFlow(SimpleInstruction i) {
        return i instanceof SimpleInstruction.Jump
                || i instanceof SimpleInstruction.Return
                || i instanceof SimpleInstruction.Block
                || i instanceof SimpleInstruction.Loop
                || i instanceof SimpleInstruction.IfElse;
    }

    // Helper for multi-instruction groups with counting
    public void emitMultipleInstructions(List<SimpleInstruction> instructions) {
        // Declare variables
        int emitIndex = 0;
        int instructionCountIndex = 0;
        // Initial counting
        while (emitIndex < instructions.size()) {
            // Go forward and find instructions, counting up costs
            if (emitIndex == instructionCountIndex) {
                long cost = 0;
                while (instructionCountIndex < instructions.size()) {
                    SimpleInstruction i = instructions.get(instructionCountIndex++);
                    if (isControlFlow(i))
                        break;
                    else
                        cost++;
                }
                if (module.instance.limiter.countsMemory)
                    visitIntrinsic(new IncInstructionsBy(cost));
            }
            // Visit the actual instruction
            instructions.get(emitIndex).accept(this);
            emitIndex++;
        }
    }


    @Override
    public Void visitBlock(SimpleInstruction.Block inst) throws RuntimeException {
        // Push a label onto the stack
        Label label = new Label();
        labelStack.push(label);
        // Emit instructions
        emitMultipleInstructions(inst.inner());
        // Pop label
        if (labelStack.pop() != label) throw new IllegalStateException();
        // Emit the label *at the end*
        visitor.visitLabel(label);
        return null;
    }

    @Override
    public Void visitLoop(SimpleInstruction.Loop inst) throws RuntimeException {
        // Push a label onto the stack, and emit it *at the beginning*
        Label label = new Label();
        labelStack.push(label);
        visitor.visitLabel(label);
        // Emit instructions
        emitMultipleInstructions(inst.inner());
        // Pop label
        if (labelStack.pop() != label) throw new IllegalStateException();
        return null;
    }

    @Override
    public Void visitIfElse(SimpleInstruction.IfElse inst) throws RuntimeException {
        // Create labels
        Label ifFalse = new Label();
        Label end = new Label();
        // Emit the jump instruction, going to false if the top of stack is 0
        visitor.visitJumpInsn(Opcodes.IFEQ, ifFalse);
        // Emit the true branch:
        labelStack.push(end); // If we jump while in here, we go to the end!
        emitMultipleInstructions(inst.ifTrue()); // Emit true instructions
        if (labelStack.pop() != end) throw new IllegalStateException(); // Pop label
        visitor.visitJumpInsn(Opcodes.GOTO, end);
        // Emit the false branch:
        visitor.visitLabel(ifFalse);
        labelStack.push(end); // If we jump in here, go to the end
        emitMultipleInstructions(inst.ifFalse()); // Emit false instructions
        if (labelStack.pop() != end) throw new IllegalStateException(); // Pop label
        // Emit the end label
        visitor.visitLabel(end);
        return null;
    }


    private void popNonMaintained(List<ValType> typesMaintained, List<ValType> typesPopped) {
        // Pop everything except for the maintained elements
        if (typesPopped.size() > 0) {
            // Add instructions according to our arbitrary formula (maintained + popped) / 4
            if (module.instance.limiter.countsInstructions) {
                visitIntrinsic(new IncInstructionsBy((typesMaintained.size() + typesPopped.size()) / 4));
            }
            // Store all the maintained elements as temporary locals.
            // First element of list = top of stack
            int local = getNextLocalSlot();
            for (ValType maintainedType : typesMaintained) {
                visitor.visitVarInsn(maintainedType.storeOpcode, local);
                local += maintainedType.stackSlots;
            }
            // Drop all the popped elements:
            for (ValType poppedType : typesPopped) {
                visitPop(new SimpleInstruction.Pop(poppedType));
            }
            // Restore the maintained elements to the stack.
            // Iterate in reverse this time.
            for (ValType maintainedType : ListUtils.reversed(typesMaintained)) {
                local -= maintainedType.stackSlots;
                visitor.visitVarInsn(maintainedType.loadOpcode, local);
            }
        }
    }

    @Override
    public Void visitJump(SimpleInstruction.Jump inst) throws RuntimeException {
        // Pop everything except for the maintained elements
        popNonMaintained(inst.typesMaintained(), inst.typesPopped());
        // Now, the maintained/required elements are all that's on the stack.
        // Peek 'defaultIndex' deep on the stack, and jump there.
        Label label = labelStack.get(labelStack.size() - 1 - inst.index());
        visitor.visitJumpInsn(Opcodes.GOTO, label);
        return null;
    }

    public Void visitJumpTable(SimpleInstruction.JumpTable inst) throws RuntimeException {
        // Emit less code in the special case where all the types popped happen to be the same:
        if (ListUtils.all(inst.typesPopped(), popped -> popped.equals(inst.defaultTypesPopped()))) {
            // Pop everything except for the maintained elements
            popNonMaintained(inst.typesMaintained(), inst.defaultTypesPopped());
            // Now, the maintained/required elements are all that's on the stack.
            // Emit the branch table.
            Label defaultLabel = labelStack.get(labelStack.size() - 1 - inst.defaultIndex());
            Label[] labels = new Label[inst.branchIndices().size()];
            for (int i = 0; i < labels.length; i++) {
                labels[i] = labelStack.get(labelStack.size() - 1 - inst.branchIndices().get(i));
            }
            visitor.visitTableSwitchInsn(0, labels.length - 1, defaultLabel, labels);
        } else {
            // Otherwise, decompose into multiple Jump instructions, decided by a table switch.
            Label defaultLabel = new Label();
            Label[] labels = new Label[inst.branchIndices().size()];
            for (int i = 0; i < labels.length; i++)
                labels[i] = new Label();

            visitor.visitTableSwitchInsn(0, labels.length - 1, defaultLabel, labels);
            for (int i = 0; i < labels.length; i++) {
                visitor.visitLabel(labels[i]);
                int index = inst.branchIndices().get(i);
                visitJump(new SimpleInstruction.Jump(index, inst.typesMaintained(), inst.typesPopped().get(i)));
            }
            visitor.visitLabel(defaultLabel);
            visitJump(new SimpleInstruction.Jump(inst.defaultIndex(), inst.typesMaintained(), inst.defaultTypesPopped()));
        }
        return null;
    }

    // Generated bytecode functions don't decrement the refcounts of objects they return.
    // That's up to the caller to decide.
    @Override
    public Void visitReturn(SimpleInstruction.Return inst) throws RuntimeException {
        // Stack = [..., results]
        if (module.instance.limiter.countsMemory) {
            // Compute the return value, store into the next temp local
            switch (inst.typesReturned().size()) {
                case 0 -> {} // Nothing
                case 1 -> visitor.visitVarInsn(inst.typesReturned().get(0).storeOpcode, getNextLocalSlot());
                default -> {
                    // Wrap returns in an Object[], then store it in the temp local
                    CallingHelpers.wrapReturnValues(visitor, getNextLocalSlot(), inst.typesReturned());
                    visitor.visitVarInsn(Opcodes.ASTORE, getNextLocalSlot());
                }
            }
            // Now that it's in the temp local, let's pop off the remaining types
            for (ValType poppedType : inst.restOfStack()) {
                visitPop(new SimpleInstruction.Pop(poppedType));
            }
            // Load the return value back to the stack, and return
            switch (inst.typesReturned().size()) {
                case 0 -> visitor.visitInsn(Opcodes.RETURN);
                case 1 -> {
                    visitor.visitVarInsn(inst.typesReturned().get(0).loadOpcode, getNextLocalSlot());
                    visitor.visitInsn(inst.typesReturned().get(0).returnOpcode);
                }
                default -> {
                    visitor.visitVarInsn(Opcodes.ALOAD, getNextLocalSlot());
                    visitor.visitInsn(Opcodes.ARETURN);
                }
            }
        } else {
            // We don't count memory, so let's just return normally.
            switch (inst.typesReturned().size()) {
                case 0 -> visitor.visitInsn(Opcodes.RETURN);
                case 1 -> visitor.visitInsn(inst.typesReturned().get(0).returnOpcode);
                default -> {
                    // Wrap returns in an Object[], then return it
                    CallingHelpers.wrapReturnValues(visitor, getNextLocalSlot(), inst.typesReturned());
                    visitor.visitInsn(Opcodes.ARETURN);
                }
            }
        }
        return null;
    }

    @Override
    public Void visitCall(SimpleInstruction.Call inst) throws RuntimeException {
        // Just call the interface method for emitting a call! :)
        module.functions[inst.funcIndex()].emitCall(module, visitor, this);
        return null;
    }

    @Override
    public Void visitCallIndirect(SimpleInstruction.CallIndirect inst) throws RuntimeException {
        BytecodeHelper.throwRuntimeError(visitor, "call_indirect is TODO");
        return null;
    }

    @Override
    public Void visitGlobalSet(SimpleInstruction.GlobalSet inst) throws RuntimeException {
        module.globals[inst.globalIndex()].emitSet(module, visitor, this);
        return null;
    }

    @Override
    public Void visitGlobalGet(SimpleInstruction.GlobalGet inst) throws RuntimeException {
        module.globals[inst.globalIndex()].emitGet(module, visitor, this);
        return null;
    }

    @Override
    public Void visitConstant(SimpleInstruction.Constant inst) throws RuntimeException {
        // Just use the BytecodeHelper function
        BytecodeHelper.constValue(visitor, inst.value());
        return null;
    }

    @Override
    public Void visitPop(SimpleInstruction.Pop inst) throws RuntimeException {
        // If it's a ref type, and we're refcounting, then use the intrinsic to decrement refcount
        if (inst.type().isRef() && module.instance.limiter.countsMemory) {
            visitIntrinsic(DecRefCount.INSTANCE);
        } else {
            // Otherwise, pop according to stack slots
            switch (inst.type().stackSlots) {
                case 1 -> visitor.visitInsn(Opcodes.POP);
                case 2 -> visitor.visitInsn(Opcodes.POP2);
                case 4 -> throw new UnsupportedOperationException("Vectors not yet implemented");
                default -> throw new IllegalStateException("Unexpected # of stack slots?");
            }
        }
        return null;
    }

    @Override
    public Void visitSelect(SimpleInstruction.Select inst) throws RuntimeException {
        // Stack = [value1, value2, int]. If int is 0, pick value2, otherwise, pick value1.
        // In other words: if 0, swap-pop. Otherwise, just pop.
        Label pop = new Label();
        visitor.visitJumpInsn(Opcodes.IFNE, pop);
        switch (inst.type().stackSlots) {
            case 1 -> visitor.visitInsn(Opcodes.SWAP);
            case 2 -> {
                visitor.visitInsn(Opcodes.DUP2_X2);
                visitor.visitInsn(Opcodes.POP2);
            }
            default -> throw new UnsupportedOperationException("Stack slots other than 1 and 2 not supported");
        }
        visitor.visitLabel(pop);
        visitPop(new SimpleInstruction.Pop(inst.type()));
        return null;
    }

    @Override
    public Void visitRawBytecode(SimpleInstruction.RawBytecode inst) throws RuntimeException {
        // Just run the visitor consumer of the instruction
        inst.visitorConsumer().accept(visitor);
        return null;
    }

    @Override
    public Void visitIntrinsic(SimpleInstruction.Intrinsic inst) throws RuntimeException {
        // Run the intrinsic's call-site code
        inst.atCallSite(module, visitor, this);
        // Add the intrinsic's class-gen-time callback to the set
        var callback = inst.classGenCallback();
        if (callback != null)
            classGenCallbacks.add(callback);
        return null;
    }
}
