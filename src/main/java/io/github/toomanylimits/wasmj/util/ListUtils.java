package io.github.toomanylimits.wasmj.util;

import io.github.toomanylimits.wasmj.util.funcs.ThrowingBiFunction;
import io.github.toomanylimits.wasmj.util.funcs.ThrowingConsumer;
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

    public static <T, R, E extends Throwable> List<R> mapIndexed(List<T> list, ThrowingBiFunction<Integer, T, R, E> func) throws E {
        ArrayList<R> result = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++)
            result.add(func.accept(i, list.get(i)));
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

    public static <T, E extends Throwable> int indexOf(List<T> list, ThrowingFunction<T, Boolean, E> pred) throws E {
        for (int i = 0; i < list.size(); i++) {
            if (pred.accept(list.get(i)))
                return i;
        }
        return -1;
    }

    public static <T, E extends Throwable> boolean any(List<T> list, ThrowingFunction<T, Boolean, E> pred) throws E {
        return indexOf(list, pred) != -1;
    }

    public static <T, E extends Throwable> void iterReverse(List<T> list, ThrowingConsumer<T, E> func) throws E {
        for (int i = list.size() - 1; i >= 0; i--)
            func.accept(list.get(i));
    }

    public static <T, K, V, E extends Throwable> Map<K, V> toMap(List<T> list, ThrowingFunction<T, K, E> keyGetter, ThrowingFunction<T, V, E> valueGetter) throws E {
        Map<K, V> result = new HashMap<>(list.size());
        for (T elem : list)
            result.put(keyGetter.accept(elem), valueGetter.accept(elem));
        return result;
    }

    public static <T, K, E extends Throwable> Map<K, T> associateBy(List<T> list, ThrowingFunction<T, K, E> keyGetter) throws E {
        Map<K, T> result = new HashMap<>(list.size());
        for (T elem : list)
            result.put(keyGetter.accept(elem), elem);
        return result;
    }

    public static <T, R, E extends Throwable> R fold(List<T> list, R initialValue, ThrowingBiFunction<R, T, R, E> func) throws E {
        R result = initialValue;
        for (T elem : list)
            result = func.accept(result, elem);
        return result;
    }

}
