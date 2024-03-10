package io.github.toomanylimits.wasmj.runtime.reflect;

import io.github.toomanylimits.wasmj.runtime.sandbox.InstanceLimiter;
import io.github.toomanylimits.wasmj.runtime.errors.WasmRuntimeException;
import io.github.toomanylimits.wasmj.runtime.reflect.annotations.ByteArrayAccess;
import io.github.toomanylimits.wasmj.runtime.reflect.annotations.LimiterAccess;
import io.github.toomanylimits.wasmj.runtime.reflect.annotations.WasmJAllow;
import io.github.toomanylimits.wasmj.runtime.sandbox.RefCountable;

import java.nio.charset.StandardCharsets;

/**
 * This class acts as a JavaModule which can be added into a WasmInstance.
 */
public class WasmJImpl {

    @WasmJAllow
    public static void print_char(int c) {
        System.out.print(Character.toString(c));
    }

    @WasmJAllow
    @ByteArrayAccess
    @LimiterAccess
    public static void print_str(int ptr, int len, byte[] mem, InstanceLimiter limiter) {
        String s = new String(mem, ptr, len, StandardCharsets.UTF_8);
//        System.out.println(s);
    }

    @WasmJAllow
    public static void err_char(int c) {
        System.err.print(Character.toString(c));
    }

    @WasmJAllow
    @ByteArrayAccess
    public static void err_str(int ptr, int len, byte[] mem) {
        String s = new String(mem, ptr, len, StandardCharsets.UTF_8);
        System.err.print(s);
    }

    @WasmJAllow
    public static void err() throws WasmRuntimeException {
        throw new WasmRuntimeException("Program errored!");
    }

    @WasmJAllow
    public static void print_obj(Object o) {
//        System.out.println(o); // Extremely slow, comment it out for any performance testing
    }

    // Counter, testing code

    @WasmJAllow
    public static Counter new_counter() {
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

    static class Counter extends RefCountable {
        int value;
        public void increment() {
            this.value += 1;
        }
        public String toString() {
            return "Counter(" + value + ")";
        }

        @Override
        public void drop(InstanceLimiter limiter) {}

        @Override
        public long getSize() {
            return 16;
        }
    }

}
