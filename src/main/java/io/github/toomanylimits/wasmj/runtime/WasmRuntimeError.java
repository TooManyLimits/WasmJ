package io.github.toomanylimits.wasmj.runtime;

public class WasmRuntimeError extends Exception {
    public WasmRuntimeError(String message) {
        super(message);
    }
}