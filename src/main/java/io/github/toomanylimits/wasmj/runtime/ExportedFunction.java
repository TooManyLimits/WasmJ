package io.github.toomanylimits.wasmj.runtime;

import io.github.toomanylimits.wasmj.runtime.errors.JvmCodeError;
import io.github.toomanylimits.wasmj.runtime.errors.WasmException;

/**
 * An interface for an exported function.
 */
@FunctionalInterface
public interface ExportedFunction {

    Object[] invoke_impl(Object... args) throws Throwable;

    default Object[] invoke(Object... args) throws WasmException {
        try {
            return invoke_impl(args);
        } catch (WasmException e) {
            throw e; // Propagate WasmExceptions
        } catch (Throwable t) {
            throw new JvmCodeError(t); // Wrap other exceptions
        }
    }

}
