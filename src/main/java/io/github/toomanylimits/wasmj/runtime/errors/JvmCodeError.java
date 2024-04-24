package io.github.toomanylimits.wasmj.runtime.errors;

/**
 * Wrapper for an error that happened in JVM code, which was not explicitly
 * thrown by WASM.
 * For example, a NullPointerException.
 */
public class JvmCodeError extends WasmException {
    public JvmCodeError(Throwable cause) {
        super(null, cause);
    }
}
