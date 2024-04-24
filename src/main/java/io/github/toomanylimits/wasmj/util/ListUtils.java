package io.github.toomanylimits.wasmj.util;

import io.github.toomanylimits.wasmj.util.funcs.ThrowingBiConsumer;
import io.github.toomanylimits.wasmj.util.funcs.ThrowingBiFunction;
import io.github.toomanylimits.wasmj.util.funcs.ThrowingConsumer;
import io.github.toomanylimits.wasmj.util.funcs.ThrowingFunction;

import java.util.*;

public class ListUtils {

    public static <T, R, E extends Throwable> List<R> map(List<T> list, ThrowingFunction<T, R, E> func) throws E {
        ArrayList<R> result = new ArrayList<>(list.size());
        for (T elem : list)
            result.add(func.accept(elem));
        return result;
    }

    // Function can return null; null results are not added to the list
    public static <T, R, E extends Throwable> ArrayList<R> flatMapNonNull(List<T> list, ThrowingFunction<T, R, E> func) throws E {
        ArrayList<R> result = new ArrayList<>(list.size());
        for (T elem : list) {
            R output = func.accept(elem);
            if (output != null)
                result.add(output);
        }
        return result;
    }

    public static <T, R, E extends Throwable> List<R> mapIndexed(List<T> list, ThrowingBiFunction<Integer, T, R, E> func) throws E {
        ArrayList<R> result = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++)
            result.add(func.accept(i, list.get(i)));
        return result;
    }

    public static <T, E extends Throwable> int sumBy(List<T> list, ThrowingFunction<T, Integer, E> intSelector) throws E, ArithmeticException {
        int sum = 0;
        for (T elem : list)
            sum = Math.addExact(sum, intSelector.accept(elem));
        return sum;
    }

    public static <T, E extends Throwable> void forEachIndexed(List<T> list, ThrowingBiConsumer<Integer, T, E> func) throws E {
        int i = 0;
        for (T elem : list)
            func.accept(i++, elem);
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

    public static <T> List<T> reversed(List<T> list) {
        ArrayList<T> res = new ArrayList<>(list);
        Collections.reverse(res);
        return res;
    }

    public static <T, E extends Throwable> boolean any(List<T> list, ThrowingFunction<T, Boolean, E> pred) throws E {
        return indexOf(list, pred) != -1;
    }
    public static <T, E extends Throwable> boolean all(List<T> list, ThrowingFunction<T, Boolean, E> pred) throws E {
        return !any(list, x -> !pred.accept(x));
    }
    public static <T, E extends Throwable> T first(List<T> list, ThrowingFunction<T, Boolean, E> pred) throws E {
        int index = indexOf(list, pred);
        if (index == -1) return null;
        return list.get(index);
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

    public static <T, K, V, E extends Throwable> Map<K, V> associateByTo(List<T> list, ThrowingFunction<T, K, E> keyGetter, ThrowingFunction<T, V, E> valueGetter) throws E {
        Map<K, V> result = new HashMap<>(list.size());
        for (T elem : list)
            result.put(keyGetter.accept(elem), valueGetter.accept(elem));
        return result;
    }

    public static <T, R, E extends Throwable> R fold(List<T> list, R initialValue, ThrowingBiFunction<R, T, R, E> func) throws E {
        R result = initialValue;
        for (T elem : list)
            result = func.accept(result, elem);
        return result;
    }

}
