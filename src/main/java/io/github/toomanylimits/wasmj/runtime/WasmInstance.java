package io.github.toomanylimits.wasmj.runtime;

import io.github.toomanylimits.wasmj.compiler.Compile;
import io.github.toomanylimits.wasmj.parsing.module.WasmModule;
import io.github.toomanylimits.wasmj.runtime.reflect.JavaModuleData;
import io.github.toomanylimits.wasmj.runtime.reflect.WasmJImpl;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Modules are instantiated in an instance.
 */
public class WasmInstance {

    private final CustomWasmJLoader loader = new CustomWasmJLoader(new HashMap<>(), ClassLoader.getSystemClassLoader(), false);
    private final Set<String> wasmModuleNames = new HashSet<>();
    private final Map<String, JavaModuleData<?>> javaModuleData = new HashMap<>();

    public WasmInstance() {
        // Add WasmJ impl as a java module TODO: Remove
        addJavaModule("WasmJ", WasmJImpl.class, null);
    }

    public void addWasmModule(String moduleName, WasmModule module) {
        if (wasmModuleNames.contains(moduleName) || javaModuleData.containsKey(moduleName))
            throw new IllegalArgumentException("There is already a module named \"" + moduleName + "\" in this wasm instance");
        wasmModuleNames.add(moduleName);
        // Compile the module and add it to the custom classloader
        byte[] compiled = Compile.compileModule(javaModuleData, moduleName, module);
        loader.classes.put(Compile.getClassName(moduleName), compiled);
    }

    /**
     * All java modules should be added before adding any WASM modules.
     * A Java module is a class. Methods can be annotated in said class
     * with @WasmJAllow to make them callable by WASM code. The instance
     * is nullable; if you pass in a null instance then it's expected that
     * all annotated methods are static.
     */
    public <T> void addJavaModule(String moduleName, Class<T> moduleClass, T nullableInstance) {
        if (javaModuleData.containsKey(moduleName))
            throw new IllegalArgumentException("There is already a module named \"" + moduleName + "\" in this wasm instance");
        if (!wasmModuleNames.isEmpty())
            throw new UnsupportedOperationException("All java modules must be added to an instance before any WASM modules are added");
        javaModuleData.put(moduleName, new JavaModuleData<>(moduleClass, nullableInstance));
    }

    /**
     * Get the jvm class generated from the wasm module with the given name
     */
    public Class<?> getWasmClass(String wasmModuleName) {
        return loader.findClass(Compile.getClassName(wasmModuleName).replace('/', '.'));
    }

    /**
     * The custom class loader which holds all the generated classes
     * for some instance.
     */
    private static final AtomicInteger nextLoaderId = new AtomicInteger();
    private static class CustomWasmJLoader extends ClassLoader {
        public final HashMap<String, byte[]> classes;
        public final ClassLoader deepestCommonChild;
        private final boolean debugBytecode;

        public CustomWasmJLoader(HashMap<String, byte[]> classes, ClassLoader deepestCommonChild, boolean debugBytecode) {
            super("WasmJLoader" + nextLoaderId.getAndIncrement(), deepestCommonChild);
            this.classes = classes;
            this.deepestCommonChild = deepestCommonChild;
            this.debugBytecode = debugBytecode;
        }

        public Class<?> findClass(String name) {
            String runtimeName = name.replace('.', '/');
            byte[] bytes = classes.remove(runtimeName);
            if (bytes == null)
                return null;
            if (debugBytecode)
                new ClassReader(bytes).accept(new TraceClassVisitor(new PrintWriter(System.err)), ClassReader.SKIP_DEBUG);
            return defineClass(name, bytes, 0, bytes.length);
        }

    }
}

