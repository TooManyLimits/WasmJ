import io.github.toomanylimits.wasmj.compiler.Compile;
import io.github.toomanylimits.wasmj.runtime.WasmInstance;
import io.github.toomanylimits.wasmj.parsing.module.Export;
import io.github.toomanylimits.wasmj.parsing.module.WasmModule;
import io.github.toomanylimits.wasmj.runtime.WasmJCallable;
import io.github.toomanylimits.wasmj.runtime.reflect.WasmJImpl;
import io.github.toomanylimits.wasmj.util.ListUtils;

import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;


public class Main {
    public static void main(String[] args) throws Throwable {

        InputStream inStream = Main.class.getResourceAsStream("test_string.wasm");
        if (inStream == null)
            throw new IllegalStateException("could not find wasm file");
        WasmModule module = new WasmModule(inStream);

        WasmInstance instance = new WasmInstance(Long.MAX_VALUE, 100);
//        instance.addJavaModule("WasmJ", WasmJImpl.class, null); // Add WasmJ impl
        instance.addTypeModule("dog", WasmJImpl.Dog.class); // Test dog module
        instance.addJavaModule("WasmJ", WasmJImpl.FancyPrinter.class, new WasmJImpl.FancyPrinter(" xD")); // Test global instance mode

        instance.addWasmModule("aaa", module); // Compiled wasm module

        // Testing code
        WasmJCallable function = instance.getExportedFunction("aaa", "test_string");
        long start = System.nanoTime();
        function.call();
        long end = System.nanoTime();
//        function.call();
        long end2 = System.nanoTime();
        long start3 = System.nanoTime();
//        for (int x = 0; x < 10000000; x++)
//            function.call();
        long end3 = System.nanoTime();
        System.out.println();
        System.out.println("Execution took " + (end - start) / 1_000_000.0 + " ms");
        System.out.println("Execution took " + (end2 - end) / 1_000_000.0 + " ms on second run");
        System.out.println("Executing 10,000,000 times took " + (end3 - start3) / 1_000_000.0 + " ms");

        System.out.println(instance.limiter.getInstructions() + " instructions executed");
    }
}
