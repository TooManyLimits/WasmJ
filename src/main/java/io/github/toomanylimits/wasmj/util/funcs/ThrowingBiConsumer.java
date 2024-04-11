package io.github.toomanylimits.wasmj.util.funcs;

@FunctionalInterface
public interface ThrowingBiConsumer<T1, T2, E extends Throwable> {
    void accept(T1 elem1, T2 elem2) throws E;
}
