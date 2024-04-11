package io.github.toomanylimits.wasmj.compiling.simplify;

import io.github.toomanylimits.wasmj.parsing.instruction.Instruction;
import io.github.toomanylimits.wasmj.parsing.types.ValType;
import io.github.toomanylimits.wasmj.util.ListUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;

/**
 * Based on the algorithm from
 * https://webassembly.github.io/spec/core/appendix/algorithm.html#algo-valid.
 */
public class Validator {

    private final Stack<ValType> valStack = new Stack<>();
    private final Stack<ControlFrame> ctrlStack = new Stack<>();
    private boolean baseUnreachable = false;

    private int peekHeight() {
        if (ctrlStack.size() == 0) return 0;
        return ctrlStack.peek().height;
    }
    private boolean peekUnreachable() {
        if (ctrlStack.size() == 0) return baseUnreachable;
        return ctrlStack.peek().unreachable;
    }

    public void pushVal(ValType type) {
        valStack.push(type);
    }
    public ValType popVal() throws ValidationException {
        if (valStack.size() == peekHeight() && peekUnreachable())
            return ValType.UNKNOWN;
        if (valStack.size() == peekHeight())
            throw new ValidationException("Failed to pop value - malformed WASM, or bug in compiler!");
        return valStack.pop();
    }
    public ValType popVal(ValType expected) throws ValidationException {
        ValType actual = popVal();
        if (actual != expected && actual != ValType.UNKNOWN && expected != ValType.UNKNOWN)
            throw new ValidationException("Got unexpected type: expected " + expected + ", got " + actual + ". Malformed WASM or bug in compiler!");
        return actual;
    }
    public void pushVals(List<ValType> types) {
        for (ValType t : types) pushVal(t);
    }
    public void popVals(List<ValType> expected) throws ValidationException {
        ListUtils.iterReverse(expected, this::popVal);
    }
    public void pushControl(boolean isLoop, List<ValType> in, List<ValType> out) {
        ControlFrame frame = new ControlFrame();
        frame.isLoop = isLoop;
        frame.startTypes = in;
        frame.endTypes = out;
        frame.height = valStack.size();
        ctrlStack.push(frame);
        pushVals(in);
    }
    public ControlFrame popControl() throws ValidationException {
        ControlFrame frame = ctrlStack.peek();
        popVals(frame.endTypes);
        if (valStack.size() != frame.height)
            throw new ValidationException("Unexpected value stack height. Malformed WASM or bug in compiler!");
        ctrlStack.pop();
        return frame;
    }
    public ControlFrame peekControl(int offset) throws ValidationException {
        if (offset >= ctrlStack.size())
            throw new ValidationException("Attempt to branch to nonexistent location - branch target is defaultIndex " + offset + ", but control stack is only height " + ctrlStack.size());
        return ctrlStack.get(ctrlStack.size() - 1 - offset);
    }

    // Returns the list of val types that were popped from the stack
    // when this unreachable() was called.
    // First item in the list = was at the top of the stack
    public void unreachable() {
        while (valStack.size() > peekHeight())
            valStack.pop();
        if (ctrlStack.size() > 0)
            ctrlStack.peek().unreachable = true;
        else
            baseUnreachable = true;
    }

    // Return a list of all val types on the stack.
    // These need to be dealt with when returning.
    // First item in the list = the top of the stack.
    public List<ValType> allVals() {
        List<ValType> res = new ArrayList<>(valStack);
        Collections.reverse(res);
        return res;
    }

    // Return a list of all val types at or after the given
    // control frame index.
    // First item in the list = the top of the stack.
    public List<ValType> allValsAfter(int ctrlIndex) throws ValidationException {
        int minHeight = peekControl(ctrlIndex).height;
        List<ValType> res = new ArrayList<>();
        for (int i = valStack.size() - 1; i >= minHeight; i--)
            res.add(valStack.get(i));
        return res;
    }

    public static class ControlFrame {

        public boolean isLoop;
        public List<ValType> startTypes;
        public List<ValType> endTypes;
        public int height;
        public boolean unreachable;

        public List<ValType> labelTypes() {
            if (isLoop)
                return startTypes; // Loop
            return endTypes; // Block
        }
    }

    public static class ValidationException extends Exception {
        public ValidationException(String message) {
            super(message);
        }
    }

}
