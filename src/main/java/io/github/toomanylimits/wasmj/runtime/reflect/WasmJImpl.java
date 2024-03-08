package io.github.toomanylimits.wasmj.runtime.reflect;

import io.github.toomanylimits.wasmj.runtime.WasmRuntimeError;
import io.github.toomanylimits.wasmj.runtime.reflect.annotations.WasmJAllow;

public class WasmJImpl {

    @WasmJAllow
    public static void print_char(int c) {
        System.out.print(Character.toString(c));
    }

    @WasmJAllow
    public static void err_char(int c) {
        System.err.print(Character.toString(c));
    }

    @WasmJAllow
    public static void err() throws WasmRuntimeError {
        throw new WasmRuntimeError("Program errored!");
    }

    @WasmJAllow
    public static void print_obj(Object o) {
//        System.out.println(o); // Extremely slow, comment it out for any performance testing
    }

    // Counter, testing code

    @WasmJAllow
    public static Object new_counter() {
        return new Counter();
    }

    @WasmJAllow
    public static void inc_counter(Object obj) {
        if (obj instanceof Counter counter) {
            counter.increment();
        } else {
            throw new IllegalArgumentException(">:(");
        }
    }

    static class Counter {
        int value;
        public void increment() {
            this.value += 1;
        }
        public String toString() {
            return "Counter(" + value + ")";
        }
    }

}
