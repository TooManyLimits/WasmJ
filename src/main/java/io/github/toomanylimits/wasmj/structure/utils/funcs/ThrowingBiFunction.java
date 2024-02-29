package io.github.toomanylimits.wasmj.structure.utils.funcs;

@FunctionalInterface
public interface ThrowingBiFunction<T1, T2, R, E extends Throwable> {
    R accept(T1 arg1, T2 arg2) throws E;
}
