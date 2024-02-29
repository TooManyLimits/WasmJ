package io.github.toomanylimits.wasmj.structure.utils;

import io.github.toomanylimits.wasmj.structure.utils.funcs.ThrowingFunction;

import java.util.ArrayList;
import java.util.List;

public class ListUtils {

    public static <T, R, E extends Throwable> List<R> map(List<T> list, ThrowingFunction<T, R, E> func) throws E {
        ArrayList<R> result = new ArrayList<>(list.size());
        for (T elem : list)
            result.add(func.accept(elem));
        return result;
    }

    public static <T, E extends Throwable> List<T> filter(List<T> list, ThrowingFunction<T, Boolean, E> pred) throws E {
        ArrayList<T> result = new ArrayList<>(list.size());
        for (T elem : list)
            if (pred.accept(elem))
                result.add(elem);
        result.trimToSize();
        return result;
    }

}
