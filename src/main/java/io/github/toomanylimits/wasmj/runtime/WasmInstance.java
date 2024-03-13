package io.github.toomanylimits.wasmj.runtime;

import io.github.toomanylimits.wasmj.compiler.Compile;
import io.github.toomanylimits.wasmj.parsing.module.WasmModule;
import io.github.toomanylimits.wasmj.runtime.reflect.JavaModuleData;
import io.github.toomanylimits.wasmj.runtime.sandbox.InstanceLimiter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Modules are instantiated in an instance.
 */
public class WasmInstance {

    private final CustomWasmJLoader loader = new CustomWasmJLoader(new HashMap<>(), ClassLoader.getSystemClassLoader(), true);
    public final InstanceLimiter limiter;

    private final Set<String> wasmModuleNames = new HashSet<>();
    private final Map<String, JavaModuleData<?>> javaModuleData = new HashMap<>();

    // The parameters to this are just used to create an InstanceLimiter for sandboxing.
    // Check InstanceLimiter for information on them.
    public WasmInstance(long maxInstructions, long maxJvmHeapMemory) {
        limiter = new InstanceLimiter(maxInstructions, maxJvmHeapMemory);
    }

    public void addWasmModule(String moduleName, WasmModule module) {
        if (wasmModuleNames.contains(moduleName) || javaModuleData.containsKey(moduleName))
            throw new IllegalArgumentException("There is already a module named \"" + moduleName + "\" in this wasm instance");
        wasmModuleNames.add(moduleName);
        // Compile the module and add it to the custom classloader
        byte[] compiled = Compile.compileModule(javaModuleData, limiter, moduleName, module);
        loader.classes.put(Compile.getClassName(moduleName), compiled);
        // Get the wasm class and call the init method
        try {
            Class<?> c = getWasmClass(moduleName);
            c.getDeclaredMethod(Compile.getInitMethodName(), InstanceLimiter.class, Map.class).invoke(null, limiter, javaModuleData);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Failed to locate/call init method? Should always succeed!", e);
        }
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
     * Works the same as addJavaModule, in that all of these must be added
     * before any Wasm modules are added. This works in a different way to
     * addJavaModule(), in that there is no global instance, and instead
     * instances are placed as the first parameter. For example:
     * class TypeToReflect {
     *     private int value;
     *     @WasmJAllow
     *     public int getValue() { return this.value(); }
     *     @WasmJAllow
     *     @WasmJRename("inc_value")
     *     public void incValue() { this.value++; }
     * }
     * Calling addTypeModule("aaa", TypeToReflect.class) will create a JavaModule
     * containing two methods:
     * getValue(TypeToReflect/externref) -> i32
     * inc_value(TypeToReflect/externref) -> void
     */
    public <T> void addTypeModule(String moduleName, Class<T> typeToReflect) {
        if (javaModuleData.containsKey(moduleName))
            throw new IllegalArgumentException("There is already a module named \"" + moduleName + "\" in this wasm instance");
        if (!wasmModuleNames.isEmpty())
            throw new UnsupportedOperationException("All java modules must be added to an instance before any WASM modules are added");
        javaModuleData.put(moduleName, new JavaModuleData<>(typeToReflect));
    }

    /**
     * Get the jvm class generated from the wasm module with the given name
     */
    public Class<?> getWasmClass(String wasmModuleName) {
        if (!wasmModuleNames.contains(wasmModuleName))
            throw new IllegalArgumentException("No WASM module with name \"" + wasmModuleName + "\" was added to this instance");
        try {
            return loader.loadClass(Compile.getClassName(wasmModuleName).replace('/', '.'));
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("The class \"" + wasmModuleName + "\" was added to the instance, but could not be found in the class loader? Internal bug!", e);
        }
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

        protected Class<?> findClass(String name) {
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

