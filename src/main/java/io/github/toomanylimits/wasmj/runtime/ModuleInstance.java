package io.github.toomanylimits.wasmj.runtime;

public interface ModuleInstance {
    // Get the name the module was instantiated with
    String name();
    // Run the module's start function, if one is defined
    void start();
}

