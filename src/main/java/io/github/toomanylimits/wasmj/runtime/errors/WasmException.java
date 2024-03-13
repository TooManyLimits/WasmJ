package io.github.toomanylimits.wasmj.runtime.errors;

/**
 * An exception thrown from inside a WASM instance.
 */
public abstract class WasmException extends Exception {
    public WasmException(String message) {
        super(message);
    }
    public WasmException(String message, Throwable cause) {
        super(message, cause);
    }
}
