package io.github.toomanylimits.wasmj.compiler;

import io.github.toomanylimits.wasmj.runtime.ModuleInstance;
import io.github.toomanylimits.wasmj.runtime.reflect.Reflector;
import io.github.toomanylimits.wasmj.runtime.reflect.WasmJImpl;
import io.github.toomanylimits.wasmj.structure.module.WasmModule;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Modules are instantiated in an instance.
 */
public class WasmInstance {

    // Temporary public for testing code
    private final HashMap<String, ModuleInstance> modules = new HashMap<>();
    private final CustomWasmJLoader loader = new CustomWasmJLoader(new HashMap<>(), ClassLoader.getSystemClassLoader(), false);

    public WasmInstance() {
        // Add WasmJ impl as a module
        instantiateCompiledModule("WasmJ", Reflector.reflectStaticModule("WasmJ", WasmJImpl.class));
    }

    public void instantiateModule(String moduleName, WasmModule module) {
        instantiateCompiledModule(moduleName, Compile.compileModule(moduleName, module));
    }
    void instantiateCompiledModule(String moduleName, byte[] compiledModule) {
        if (modules.containsKey(moduleName))
            throw new IllegalArgumentException("There is already a module named \"$moduleName\" in this wasm instance");
        loader.classes.put(Compile.getClassName(moduleName), compiledModule);
        try {
            modules.put(moduleName, (ModuleInstance) loader.findClass(Compile.getClassName(moduleName).replace('/', '.')).getConstructor().newInstance());
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw new IllegalArgumentException("There should always be a valid constructor?", e);
        }
    }

    public ModuleInstance getModule(String moduleName) {
        return modules.get(moduleName);
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

