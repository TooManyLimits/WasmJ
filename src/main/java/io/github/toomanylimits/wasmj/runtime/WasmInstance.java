package io.github.toomanylimits.wasmj.runtime;

import io.github.toomanylimits.wasmj.compiling.compiler.Compiler;
import io.github.toomanylimits.wasmj.compiling.helpers.Names;
import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleModule;
import io.github.toomanylimits.wasmj.compiling.simplify.Validator;
import io.github.toomanylimits.wasmj.parsing.module.WasmModule;
import io.github.toomanylimits.wasmj.runtime.errors.JvmCodeError;
import io.github.toomanylimits.wasmj.runtime.errors.WasmException;
import io.github.toomanylimits.wasmj.runtime.reflect.JavaModuleData;
import io.github.toomanylimits.wasmj.runtime.sandbox.InstanceLimiter;
import io.github.toomanylimits.wasmj.runtime.sandbox.RefCountable;
import io.github.toomanylimits.wasmj.util.ListUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.PrintWriter;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Modules are instantiated in an instance.
 */
public class WasmInstance {

    private final CustomWasmJLoader loader = new CustomWasmJLoader(new HashMap<>(), WasmInstance.class.getClassLoader(), false);
    public final InstanceLimiter limiter;

    public final List<String> wasmModuleNames = new ArrayList<>();
    public final Map<String, JavaModuleData<?>> instanceJavaModules = new HashMap<>();

    // The parameters to this are just used to create an InstanceLimiter for sandboxing.
    // Check InstanceLimiter for information on them.
    // Use -1 if you don't want to track the variable at all.
    public WasmInstance(long maxInstructions, long maxJvmHeapMemory) {
        limiter = new InstanceLimiter(maxInstructions, maxJvmHeapMemory);
    }

    public void addWasmModule(String moduleName, WasmModule module) throws Validator.ValidationException, WasmException {
        if (wasmModuleNames.contains(moduleName) || instanceJavaModules.containsKey(moduleName))
            throw new IllegalArgumentException("There is already a module named \"" + moduleName + "\" in this wasm instance");
        wasmModuleNames.add(moduleName);
        // Compile the module and add it to the custom classloader
        SimpleModule simple = new SimpleModule(moduleName, module, this);
        Map<String, byte[]> compiled = Compiler.compile(simple);
        loader.classes.putAll(compiled);
        // Get the wasm class and call the init method.
        try {
            Class<?> c = getWasmClass(moduleName);
            c.getDeclaredMethod(Names.initMethodName(), InstanceLimiter.class, Map.class, SimpleModule.class).invoke(null, limiter, instanceJavaModules, simple); // Throws WasmException
        } catch (InvocationTargetException e) {
            // Re-wrap it as a WASM exception if needed
            if (e.getCause() instanceof WasmException ex)
                throw ex;
            throw new JvmCodeError(e.getCause());
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new IllegalStateException("Failed to locate/call init method? Should always succeed!", e);
        }
    }

    /**
     * All java modules should be added before adding any WASM modules.
     * A Java module is a class. Methods can be annotated in said class
     * with @WasmJAllow to make them callable by WASM code.
     * Static methods are invoked as-is.
     * Non-static methods are invoked on the provided T instance.
     */
    public <T> void addGlobalInstanceJavaModule(String moduleName, Class<T> moduleClass, T instance) {
        if (instanceJavaModules.containsKey(moduleName))
            throw new IllegalArgumentException("There is already a module named \"" + moduleName + "\" in this wasm instance");
        if (!wasmModuleNames.isEmpty())
            throw new UnsupportedOperationException("All java modules must be added to an instance before any WASM modules are added");
        instanceJavaModules.put(moduleName, new JavaModuleData<>(moduleClass, instance));
    }

    /**
     * All java modules should be added before adding any WASM modules.
     * A Java module is a class. Methods can be annotated in said class
     * with @WasmJAllow to make them callable by WASM code.
     * Static methods are invoked as-is.
     * Non-static methods are not supported in this method, because there
     * is no instance to call them on.
     */
    public void addStaticJavaModule(String moduleName, Class<?> moduleClass) {
        if (instanceJavaModules.containsKey(moduleName))
            throw new IllegalArgumentException("There is already a module named \"" + moduleName + "\" in this wasm instance");
        if (!wasmModuleNames.isEmpty())
            throw new UnsupportedOperationException("All java modules must be added to an instance before any WASM modules are added");
        instanceJavaModules.put(moduleName, new JavaModuleData<>(moduleClass, null));
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
        if (instanceJavaModules.containsKey(moduleName))
            throw new IllegalArgumentException("There is already a module named \"" + moduleName + "\" in this wasm instance");
        if (!wasmModuleNames.isEmpty())
            throw new UnsupportedOperationException("All java modules must be added to an instance before any WASM modules are added");
        instanceJavaModules.put(moduleName, new JavaModuleData<>(typeToReflect));
    }

    /**
     * Get the jvm class generated from the wasm module with the given name, or null if
     * it doesn't exist.
     */
    public Class<?> getWasmClass(String wasmModuleName) {
        if (!wasmModuleNames.contains(wasmModuleName))
            return null; // throw new IllegalArgumentException("No WASM module with name \"" + wasmModuleName + "\" was added to this instance");
        try {
            return loader.loadClass(Names.className(wasmModuleName).replace('/', '.'));
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("The class \"" + wasmModuleName + "\" was added to the instance, but could not be found in the class loader? Internal bug!", e);
        }
    }

    /**
     * Get all the exported functions in the given module.
     * If the module doesn't exist, returns an empty list.
     */
    public List<ExportedFunction> exportedFunctions(String wasmModuleName) {
        Class<?> wasmClass = getWasmClass(wasmModuleName);
        if (wasmClass == null) return List.of();
        try {
            return (List<ExportedFunction>) MethodHandles.lookup().findStaticGetter(wasmClass, Names.exportedFunctionsFieldName(), List.class).invokeExact();
        } catch (Throwable e) {
            throw new IllegalStateException("Failed to get exportedFunctions field? Bug in WasmJ, please report!", e);
        }
    }

    /**
     * Gets the exported function in the given module with the given name.
     * If there is no such function, returns null.
     */
    public ExportedFunction getExportedFunction(String wasmModuleName, String exportName) {
        return ListUtils.first(exportedFunctions(wasmModuleName), func -> func.name.endsWith(exportName));
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

