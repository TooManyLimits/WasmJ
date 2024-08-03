package io.github.toomanylimits.wasmj.runtime.types;

import io.github.toomanylimits.wasmj.runtime.errors.JvmCodeError;
import io.github.toomanylimits.wasmj.runtime.errors.TooMuchHeapMemoryException;
import io.github.toomanylimits.wasmj.runtime.errors.WasmException;
import io.github.toomanylimits.wasmj.runtime.sandbox.InstanceLimiter;
import io.github.toomanylimits.wasmj.runtime.sandbox.RefCountable;

/**
 * A callback passed from WASM into Java.
 * Consists of 2 "i32"s:
 * - An index into the special function table exported with the name "__indirect_function_table"
 * - An integer (which works as a void*) which is to be passed as the first arg to the function when called.
 *   this void* contains closure data in the wasm-compiled language (i.e. Rust).
 * This class is specially handled by the glue function generators. An "externref" cannot be passed as this value.
 * TODO Below:
 * There may also be a "Callback Free" function in the implementation.
 * When this is dropped, it will invoke the callback freeing function with its void pointer.
 */
public class WasmCallback extends RefCountable {

    private final FuncRefInstance funcRef;
    private final int dataPointer;

    // To be constructed by bytecode
    public WasmCallback(FuncRefInstance funcRef, int dataPointer, InstanceLimiter limiter) throws TooMuchHeapMemoryException {
        this.funcRef = funcRef;
        this.dataPointer = dataPointer;
        this.funcRef.inc(limiter);
    }

    // Invoke the callback with the given args!
    public Object invoke(Object... args) throws WasmException {
        try {
            Object[] withVoidPtr = new Object[args.length + 1];
            withVoidPtr[0] = dataPointer;
            System.arraycopy(args, 0, withVoidPtr, 1, args.length);
            return this.funcRef.handle.invokeWithArguments(withVoidPtr);
        } catch (WasmException e) {
            throw e;
        } catch (Throwable e) {
            throw new JvmCodeError(e);
        }
    }

    @Override
    protected void drop(InstanceLimiter limiter) {
        this.funcRef.dec(limiter); // Decrement the funcref ref-counter
        // TODO: Invoke the freeing function
    }

    @Override
    protected long getSize() {
        return 16;
    }
}
