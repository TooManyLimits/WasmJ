package io.github.toomanylimits.wasmj.runtime.sandbox;

import io.github.toomanylimits.wasmj.runtime.errors.TooMuchHeapMemoryException;
import io.github.toomanylimits.wasmj.runtime.errors.WasmException;

/**
 * Extend this for a type that should be WASM-capable
 */
public abstract class RefCountable {

    /**
     * Tracks the number of references to this object.
     */
    private int references;

    // Increase the number of references to this by 1
    public void inc(InstanceLimiter limiter) throws TooMuchHeapMemoryException {
        references++;
        if (references == 1)
            limiter.incHeapMemoryUsed(getSize());
    }
    // Decrease the number of references to this by 1
    public void dec(InstanceLimiter limiter) throws WasmException {
        references--;
        if (references == 0) {
            limiter.decHeapMemoryUsed(getSize());
            drop(limiter);
        }
    }

    /**
     * Runs when this object's ref count drops to 0.
     * This should decrement the refcounts of the object's
     * reachable children, for instance.
     *
     * Example:
     * record BinaryTree(int value, BinaryTree leftChild, BinaryTree rightChild) implements RefCountable {
     *     public void drop(InstanceLimiter limiter) {
     *         limiter.decrementRefCount(leftChild);
     *         limiter.decrementRefCount(rightChild);
     *     }
     * }
     *
     * Note that this is only necessary if `BinaryTree` exposes to WASM a
     * way to fetch the left or right child.
     * If there is no way for WASM code to get the left child or right child,
     * then drop() can just be a no-op, and instead the size of the children
     * should be included in the getSize() function.
     */
    protected abstract void drop(InstanceLimiter limiter) throws WasmException;

    /**
     * Return the "size" of this object, so that the ref counter knows
     * how much jvm heap memory it takes up (approximate).
     * Child objects should be included in this only if they're not
     * ever exposed to WASM code. If the child objects are exposed to
     * WASM, then they should be ref-counted as well, and this function
     * should not include their size.
     */
    protected abstract long getSize();

}
