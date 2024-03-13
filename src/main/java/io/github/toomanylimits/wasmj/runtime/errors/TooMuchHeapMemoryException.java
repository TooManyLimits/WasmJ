package io.github.toomanylimits.wasmj.runtime.errors;

/**
 * Thrown when a WASM instance executes more instructions than
 * it's allowed to.
 */
public class TooMuchHeapMemoryException extends WasmException {
    public TooMuchHeapMemoryException(long maxHeap) {
        super("WASM instance went beyond the maximum jvm heap memory of " + maxHeap);
    }
}
