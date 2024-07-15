package io.github.toomanylimits.wasmj.runtime;

import io.github.toomanylimits.wasmj.parsing.instruction.StackType;
import io.github.toomanylimits.wasmj.parsing.types.ValType;
import io.github.toomanylimits.wasmj.runtime.errors.JvmCodeError;
import io.github.toomanylimits.wasmj.runtime.errors.WasmException;
import io.github.toomanylimits.wasmj.runtime.sandbox.InstanceLimiter;
import io.github.toomanylimits.wasmj.runtime.sandbox.RefCountable;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

/**
 * Represents an exported function.
 */
public class ExportedFunction {

    public final String name;
    public final MethodHandle method;
    // Param types can be fetched from the MethodHandle, but multi-return types are unknown (erased to Object[]).
    // This field is null if there are 0 or 1 returns.
    public final List<ValType> returnTypes;
    private final InstanceLimiter limiter;

    public ExportedFunction(String name, MethodHandle method, List<ValType> returnTypes, InstanceLimiter limiter) {
        this.name = name;
        this.method = method;
        this.returnTypes = returnTypes;
        this.limiter = limiter;
    }

    /**
     * Helper that wraps all exceptions into WasmException.
     */
    public Object invoke(Object... args) throws WasmException {
        try {
            // Can return null (zero return values), Object (one return value), or Object[] (multiple return values)
            Object result = method.invokeWithArguments(args);
            // If we count memory, decrement ref counts:
            if (limiter.countsMemory) {
                if (result instanceof RefCountable refCountable)
                    refCountable.dec(limiter);
                else if (result instanceof Object[] multiReturn)
                    for (Object o : multiReturn)
                        if (o instanceof RefCountable refCountable)
                            refCountable.dec(limiter);
            }
            return result; // Return
        } catch (InvocationTargetException e) {
            // Unwrap InvocationTargetException if possible
            if (e.getCause() instanceof WasmException wasmException)
                throw wasmException;
            throw new JvmCodeError(e.getCause());
        } catch (Throwable t) {
            if (t instanceof WasmException wasmException)
                throw wasmException;
            throw new JvmCodeError(t); // Wrap other exceptions
        }
    }
}
