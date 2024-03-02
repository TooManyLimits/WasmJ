package io.github.toomanylimits.wasmj.util.funcs;

@FunctionalInterface
public interface ThrowingFunction<T, R, E extends Throwable> {
    R accept(T arg) throws E;
}
