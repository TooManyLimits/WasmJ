package io.github.toomanylimits.wasmj.runtime;

import io.github.toomanylimits.wasmj.runtime.errors.TooManyInstructionsException;
import io.github.toomanylimits.wasmj.runtime.errors.TooMuchHeapMemoryException;
import io.github.toomanylimits.wasmj.runtime.sandbox.RefCountable;

/**
 * An instance of this is passed to functions with the @ExternrefTableAccess annotation.
 * One implementation is generated and stored in each module class.
 */
public interface ExternrefTableAccessor {
    // Get the value at the given index.
    RefCountable get(int index);
    // Set the value at the given index.
    void set(int index, RefCountable ref);
    // Store the value somewhere in the table, don't care where, and return the index of it.
    int store(RefCountable ref) throws TooManyInstructionsException, TooMuchHeapMemoryException;
}
