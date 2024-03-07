package io.github.toomanylimits.wasmj.util;

import io.github.toomanylimits.wasmj.util.funcs.ThrowingFunction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public static <T, K, V, E extends Throwable> Map<K, V> toMap(List<T> list, ThrowingFunction<T, K, E> keyGetter, ThrowingFunction<T, V, E> valueGetter) throws E {
        Map<K, V> result = new HashMap<>(list.size());
        for (T elem : list)
            result.put(keyGetter.accept(elem), valueGetter.accept(elem));
        return result;
    }

}
