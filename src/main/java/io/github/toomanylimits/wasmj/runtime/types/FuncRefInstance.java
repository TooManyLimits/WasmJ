package io.github.toomanylimits.wasmj.runtime.types;

import io.github.toomanylimits.wasmj.runtime.errors.JvmCodeError;
import io.github.toomanylimits.wasmj.runtime.errors.WasmException;
import io.github.toomanylimits.wasmj.runtime.sandbox.InstanceLimiter;
import io.github.toomanylimits.wasmj.runtime.sandbox.RefCountable;

import java.lang.invoke.MethodHandle;

/**
 * Instances of these are created by the "RefFunc" instruction.
 * Probably shouldn't be created by non-bytecode users.
 */
public class FuncRefInstance extends RefCountable {
    // Public so bytecode can use the faster path (invokeExact instead of invoke)
    public final MethodHandle handle;

    // To deter accidental use, mark it as throwing Throwable to create
    public FuncRefInstance(MethodHandle handle) throws Throwable {
        this.handle = handle;
    }

    // If the handle has 0 returns, this gives null.
    // If it has 1 return, it gives the output.
    // If it has multiple returns, it gives an array of the outputs.
    // This function is called by user-facing code.
    public Object call(Object... params) throws WasmException {
        try {
            return handle.invoke(params);
        } catch (WasmException e) {
            throw e; // If it's already a WasmException, propagate it
        } catch (Throwable t) {
            throw new JvmCodeError(t); // Otherwise, wrap in special WasmException
        }
    }

    @Override
    protected void drop(InstanceLimiter limiter) {}
    @Override
    protected long getSize() {
        // Just an estimate. 16 for RefCountable, 8 for handle field?
        return 24;
    }
}
