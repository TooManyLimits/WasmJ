package io.github.toomanylimits.wasmj.util.funcs;

@FunctionalInterface
public interface BiThrowingBiFunction<T1, T2, R, E1 extends Throwable, E2 extends Throwable> {
    R accept(T1 arg1, T2 arg2) throws E1, E2;
}
