package io.github.toomanylimits.wasmj.runtime.errors;

/**
 * An exception thrown by the WASM code itself, using the library function
 * WasmJ.error().
 */
public class WasmCodeException extends WasmException {
    public WasmCodeException(String message) {
        super(message);
    }
}