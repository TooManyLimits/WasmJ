import io.github.toomanylimits.wasmj.compiler.WasmInstance;
import io.github.toomanylimits.wasmj.structure.module.Export;
import io.github.toomanylimits.wasmj.structure.module.WasmModule;
import io.github.toomanylimits.wasmj.structure.utils.ListUtils;

import java.io.InputStream;
import java.lang.reflect.Method;


public class Main {
    public static void main(String[] args) throws Exception {

        InputStream inStream = Main.class.getResourceAsStream("test_counter.wasm");
        if (inStream == null)
            throw new IllegalStateException("could not find wasm file");
        WasmModule module = new WasmModule(inStream);
        System.out.println(module);

        WasmInstance instance = new WasmInstance();
        instance.instantiateModule("aaa", module);

        // Test code
        Class<?> c = instance.getModule("aaa").getClass();
        Method m = c.getDeclaredMethod("func_" + (ListUtils.filter(module.exports, it -> it.type() == Export.ExportType.FUNC).get(0).index() - module.funcImports().size()));
        m.trySetAccessible();
        long start = System.nanoTime();
        m.invoke(null);
        long end = System.nanoTime();
        m.invoke(null);
        long end2 = System.nanoTime();
        System.out.println("Execution took " + (end - start) / 1_000_000.0 + " ms");
        System.out.println("Execution took " + (end2 - end) / 1_000_000.0 + " ms on second run");
    }
}
