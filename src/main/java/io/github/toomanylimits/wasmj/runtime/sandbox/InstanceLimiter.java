package io.github.toomanylimits.wasmj.runtime.sandbox;

import io.github.toomanylimits.wasmj.runtime.errors.TooManyInstructionsException;
import io.github.toomanylimits.wasmj.runtime.errors.TooMuchHeapMemoryException;

/**
 * An object that tracks the usage of JVM heap memory for a
 * WasmInstance, as well as the number of instructions executed
 * (where "instructions" is defined in some arbitrary manner I made up)
 *
 * JavaModule classes can access one of these by using the @LimiterAccess
 * annotation and adding a param of type InstanceLimiter to the method
 * signature at the end. Note that this param may be null if the instance
 * has no limiter.
 */
public class InstanceLimiter {

    // Instruction counting variables
    public final boolean countsInstructions; // Whether this instance counts instructions at all
    public final long maxInstructions; // If it does count instructions, the maximum number of instructions before erroring
    private long instructionsExecuted; // The current number of instructions executed

    // Memory counting variables
    public final boolean countsMemory; // Whether this instance counts memory usage at all
    public final long maxJvmHeapMemory; // The maximum jvm heap memory before this errors
    private long heapMemoryUsed; // The current amount of jvm heap memory used

    // Pass -1 if you want the limiter to not track that variable.
    // Can improve performance, since the code doesn't need to increment
    // variables and make checks all the time.
    public InstanceLimiter(long maxInstructions, long maxJvmHeapMemory) {
        this.countsInstructions = (maxInstructions != -1);
        this.maxInstructions = maxInstructions == -1 ? Long.MAX_VALUE : maxInstructions;
        this.countsMemory = (maxJvmHeapMemory != -1);
        this.maxJvmHeapMemory = maxJvmHeapMemory == -1 ? Long.MAX_VALUE : maxJvmHeapMemory;
    }

    // Getter and setter for the current # of instructions executed.
    // There's also a way to increment the instructions, check if
    // we've gone over the max, and error if so.
    public long getInstructions() {
        return instructionsExecuted;
    }
    public void setInstructions(long instructions) {
        instructionsExecuted = instructions;
    }
    public void incInstructions(long instructions) throws TooManyInstructionsException {
        if (!countsInstructions) return;
        // Increment, and error if we've gone too high. Also, error on overflow.
        // addExact *should* be intrinsified, probably, so this isn't a big performance hit.
        // It's not expected for instructionsExecuted to ever overflow,
        // but if it does, then an error will be thrown,
        // since we can't really know what's going on at that
        // point.
        instructionsExecuted = Math.addExact(instructionsExecuted, instructions);
        if (instructionsExecuted > maxInstructions) {
            throw new TooManyInstructionsException(maxInstructions);
        }
    }

    // Getter and helpers for heap memory and ref counting.
    public long getHeapMemoryUsed() {
        return heapMemoryUsed;
    }
    public void incHeapMemoryUsed(long amount) throws TooMuchHeapMemoryException {
        if (!countsMemory) return;
        heapMemoryUsed = Math.addExact(heapMemoryUsed, amount);
        if (heapMemoryUsed > maxJvmHeapMemory)
            throw new TooMuchHeapMemoryException(maxJvmHeapMemory);
    }
    public void decHeapMemoryUsed(long amount) {
        if (!countsMemory) return;
        heapMemoryUsed = Math.subtractExact(heapMemoryUsed, amount);
        if (heapMemoryUsed < 0)
            throw new IllegalStateException("Heap memory used fell below 0? Should never happen, bug in refcounting");
    }

}
