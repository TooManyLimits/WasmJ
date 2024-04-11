import io.github.toomanylimits.wasmj.runtime.ExportedFunction;
import io.github.toomanylimits.wasmj.runtime.WasmInstance;
import io.github.toomanylimits.wasmj.parsing.module.WasmModule;
import io.github.toomanylimits.wasmj.runtime.reflect.WasmJImpl;

import java.io.InputStream;


public class Main {
    public static void main(String[] args) throws Throwable {

        InputStream inStream = Main.class.getResourceAsStream("test_dog.wasm");
        if (inStream == null)
            throw new IllegalStateException("could not find wasm file");
        WasmModule module = new WasmModule(inStream);

        WasmInstance instance = new WasmInstance(-1, -1);
//        instance.addJavaModule("WasmJ", WasmJImpl.class, null); // Add WasmJ impl
        instance.addTypeModule("dog", WasmJImpl.Dog.class); // Test dog module
        instance.addStaticJavaModule("WasmJ", WasmJImpl.class); // Add WasmJ
//        instance.addGlobalInstanceJavaModule("WasmJ", WasmJImpl.FancyPrinter.class, new WasmJImpl.FancyPrinter(" xD")); // Test global instance mode
        instance.addWasmModule("aaa", module); // Compiled wasm module

        // Testing code
        ExportedFunction function = instance.getExportedFunction("aaa", "test_dog");
        long start = System.nanoTime();
        function.invoke();
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
