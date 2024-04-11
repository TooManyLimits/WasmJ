package io.github.toomanylimits.wasmj.util.funcs;

@FunctionalInterface
public interface ThrowingConsumer<T, E extends Throwable> {
    void accept(T elem) throws E;
}
