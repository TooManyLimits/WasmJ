package io.github.toomanylimits.wasmj.runtime.errors;

/**
 * Thrown when a WASM instance executes more instructions than
 * it's allowed to.
 */
public class OutOfInstructionsException extends RuntimeException {
    public OutOfInstructionsException(long maxInstructions) {
        super("WASM instance went beyond the maximum instruction count of " + maxInstructions);
    }
}
