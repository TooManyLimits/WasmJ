package io.github.toomanylimits.wasmj.compiling.simple_structure;

import io.github.toomanylimits.wasmj.compiling.compiler.CompilingSimpleInstructionVisitor;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.ClassGenCallback;
import io.github.toomanylimits.wasmj.compiling.visitor.SimpleInstructionVisitor;
import io.github.toomanylimits.wasmj.parsing.instruction.StackType;
import io.github.toomanylimits.wasmj.parsing.types.ValType;
import org.objectweb.asm.MethodVisitor;

import java.util.List;
import java.util.function.Consumer;

/**
 * A simplified form of the very large Instruction type.
 * Has fewer different things, so it's easier to process mentally,
 * and contains some jvm-specific information that's helpful as well.
 */
public sealed interface SimpleInstruction {

    <R, T extends Throwable> R accept(SimpleInstructionVisitor<R, T> visitor) throws T;

    // Local variables
    record LocalStore(ValType type, int jvmLocalIndex) implements SimpleInstruction { @Override public <R, T extends Throwable> R accept(SimpleInstructionVisitor<R, T> visitor) throws T { return visitor.visitLocalStore(this); } }
    record LocalLoad(ValType type, int jvmLocalIndex) implements SimpleInstruction { @Override public <R, T extends Throwable> R accept(SimpleInstructionVisitor<R, T> visitor) throws T { return visitor.visitLocalLoad(this); } }
    record LocalTee(ValType type, int jvmLocalIndex) implements SimpleInstruction { @Override public <R, T extends Throwable> R accept(SimpleInstructionVisitor<R, T> visitor) throws T { return visitor.visitLocalTee(this); } }

    // Various control flow
    record Block(StackType stackType, List<SimpleInstruction> inner) implements SimpleInstruction { @Override public <R, T extends Throwable> R accept(SimpleInstructionVisitor<R, T> visitor) throws T { return visitor.visitBlock(this); } }
    record Loop(StackType stackType, List<SimpleInstruction> inner) implements SimpleInstruction { @Override public <R, T extends Throwable> R accept(SimpleInstructionVisitor<R, T> visitor) throws T { return visitor.visitLoop(this); } }
    record IfElse(StackType stackType, List<SimpleInstruction> ifTrue, List<SimpleInstruction> ifFalse) implements SimpleInstruction { @Override public <R, T extends Throwable> R accept(SimpleInstructionVisitor<R, T> visitor) throws T { return visitor.visitIfElse(this); } }
    // First item in list for Jump/Return = top of stack!
    record Jump(int index, List<ValType> typesMaintained, List<ValType> typesPopped) implements SimpleInstruction { @Override public <R, T extends Throwable> R accept(SimpleInstructionVisitor<R, T> visitor) throws T { return visitor.visitJump(this); } }
    record JumpTable(List<Integer> branchIndices, int defaultIndex, List<ValType> typesMaintained, List<ValType> defaultTypesPopped, List<List<ValType>> typesPopped) implements SimpleInstruction { @Override public <R, T extends Throwable> R accept(SimpleInstructionVisitor<R, T> visitor) throws T { return visitor.visitJumpTable(this); } }
    record Return(List<ValType> typesReturned, List<ValType> restOfStack) implements SimpleInstruction { @Override public <R, T extends Throwable> R accept(SimpleInstructionVisitor<R, T> visitor) throws T { return visitor.visitReturn(this); } }

    // Calling a function
    record Call(int funcIndex, List<ValType> paramTypes, List<ValType> returnTypes) implements SimpleInstruction { @Override public <R, T extends Throwable> R accept(SimpleInstructionVisitor<R, T> visitor) throws T { return visitor.visitCall(this); } }
    record CallIndirect(int tableIndex, List<ValType> paramTypes, List<ValType> returnTypes) implements SimpleInstruction { @Override public <R, T extends Throwable> R accept(SimpleInstructionVisitor<R, T> visitor) throws T { return visitor.visitCallIndirect(this); } }
    // Global variables
    record GlobalSet(int globalIndex) implements SimpleInstruction { @Override public <R, T extends Throwable> R accept(SimpleInstructionVisitor<R, T> visitor) throws T { return visitor.visitGlobalSet(this); } }
    record GlobalGet(int globalIndex) implements SimpleInstruction { @Override public <R, T extends Throwable> R accept(SimpleInstructionVisitor<R, T> visitor) throws T { return visitor.visitGlobalGet(this); } }



    // Basic stack manipulation.
    // "Raw Bytecode" encompasses the vast majority of WASM instructions,
    // which are just simple stack manipulation and arithmetic. It's convenient
    // to handle these in the conversion pass, so we don't need to deal with as many
    // instruction types in the future.
    //
    // What are these *not*?
    // Instructions in this category should not have anything to do with things like
    // function calling conventions, control flow, or reference types (because we need
    // to refcount them). These are the higher-level topics which this class is meant
    // to simplify the implementation of (hopefully).
    record Constant(Object value) implements SimpleInstruction { @Override public <R, T extends Throwable> R accept(SimpleInstructionVisitor<R, T> visitor) throws T { return visitor.visitConstant(this); } }
    record Pop(ValType type) implements SimpleInstruction { @Override public <R, T extends Throwable> R accept(SimpleInstructionVisitor<R, T> visitor) throws T { return visitor.visitPop(this); } }
    record Select(ValType type) implements SimpleInstruction { @Override public <R, T extends Throwable> R accept(SimpleInstructionVisitor<R, T> visitor) throws T { return visitor.visitSelect(this); }}
    record RawBytecode(Consumer<MethodVisitor> visitorConsumer) implements SimpleInstruction { @Override public <R, T extends Throwable> R accept(SimpleInstructionVisitor<R, T> visitor) throws T { return visitor.visitRawBytecode(this); } }

    // Various intrinsic operations. These are implemented with 1 visitor type to share code.
    // Each intrinsic is implemented in its own file in the "intrinsics" package.

    // Note: What counts as an "intrinsic" vs. what gets its own SimpleInstruction is pretty
    // arbitrary. Several of the SimpleInstruction could be implemented as intrinsics instead
    // if we wanted, like Call, GlobalSet/Get, etc. or vice versa.
    // Don't look too deep into it, I just separated them in some arbitrary way.
    // The general rule of thumb is: if this operation uses a helper function when it's used,
    // then it should be an intrinsic (for the class gen callback feature).
    non-sealed interface Intrinsic extends SimpleInstruction {
        // Impl
        default <R, T extends Throwable> R accept(SimpleInstructionVisitor<R, T> visitor) throws T {
            return visitor.visitIntrinsic(this);
        }

        /**
         * Run at the call site, when the intrinsic is used as an instruction
         */
        void atCallSite(SimpleModule module, MethodVisitor visitor, CompilingSimpleInstructionVisitor compilingVisitor);

        /**
         * Return a consumer which will be invoked at class generation time.
         * If you return the same instance of BiConsumer from multiple
         * Intrinsic instances, then that BiConsumer will only be run at most
         * once per SimpleModule.
         */
        ClassGenCallback classGenCallback();

    }

}
