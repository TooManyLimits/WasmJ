package io.github.toomanylimits.wasmj.runtime;

import io.github.toomanylimits.wasmj.runtime.errors.JvmCodeError;
import io.github.toomanylimits.wasmj.runtime.errors.WasmException;

import java.lang.reflect.InvocationTargetException;

/**
 * An interface for an exported function.
 */
@FunctionalInterface
public interface ExportedFunction {

    /**
     * Returns one of:
     * - null (zero return values)
     * - Object (one return value)
     * - Object[] (multiple return values)
     */
    Object invoke_impl(Object... args) throws Throwable;

    /**
     * Helper that wraps all exceptions into WasmException.
     */
    default Object invoke(Object... args) throws WasmException {
        try {
            return invoke_impl(args);
        } catch (WasmException wasmException) {
            throw wasmException; // Propagate WasmException
        } catch (InvocationTargetException e) {
            // Unwrap InvocationTargetException if possible
            if (e.getCause() instanceof WasmException wasmException)
                throw wasmException;
            throw new JvmCodeError(e.getCause());
        } catch (Throwable t) {
            throw new JvmCodeError(t); // Wrap other exceptions
        }
    }

}
