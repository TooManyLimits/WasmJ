package io.github.toomanylimits.wasmj.runtime.types;

import io.github.toomanylimits.wasmj.runtime.errors.JvmCodeError;
import io.github.toomanylimits.wasmj.runtime.errors.WasmException;
import io.github.toomanylimits.wasmj.runtime.sandbox.InstanceLimiter;
import io.github.toomanylimits.wasmj.runtime.sandbox.RefCountable;

import java.lang.invoke.MethodHandle;

/**
 * Instances of these are created by the "RefFunc" instruction.
 * Intended only to be used by bytecode.
 * For callback purposes (invoking WASM callbacks from Java) see the WasmCallback class.
 */
public class FuncRefInstance extends RefCountable {
    // Public so bytecode can use the faster path (invokeExact instead of invoke)
    public final MethodHandle handle;

    // To deter accidental use, mark it as throwing Throwable to create
    public FuncRefInstance(MethodHandle handle) throws Throwable {
        this.handle = handle;
    }

    @Override
    protected void drop(InstanceLimiter limiter) {}
    @Override
    protected long getSize() {
        // Just an estimate. 16 for RefCountable, 8 for handle field?
        return 24;
    }
}
