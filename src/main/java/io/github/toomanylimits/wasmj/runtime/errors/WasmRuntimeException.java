package io.github.toomanylimits.wasmj.runtime.errors;

public class WasmRuntimeException extends RuntimeException {
    public WasmRuntimeException(String message) {
        super(message);
    }
}