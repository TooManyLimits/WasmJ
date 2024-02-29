package io.github.toomanylimits.wasmj.structure.utils.funcs;

@FunctionalInterface
public interface BiThrowingFunction<T, R, E1 extends Throwable, E2 extends Throwable> {
    R accept(T arg) throws E1, E2;
}
