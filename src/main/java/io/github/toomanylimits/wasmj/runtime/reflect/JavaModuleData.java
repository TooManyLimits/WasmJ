package io.github.toomanylimits.wasmj.runtime.reflect;

import io.github.toomanylimits.wasmj.runtime.reflect.annotations.WasmJAllow;
import io.github.toomanylimits.wasmj.runtime.reflect.annotations.WasmJRename;
import io.github.toomanylimits.wasmj.util.ListUtils;
import org.objectweb.asm.Type;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;

/**
 * Information about a JavaModule that was reflected.
 * These are passed to the WASM -> JVM bytecode compiler,
 * so it can make proper decisions about how to call JVM
 * functions.
 */
public class JavaModuleData<T> {

    /**
     * The class which was reflected to make this JavaModuleData
     */
    public final Class<T> moduleClass;
    /**
     * The optional instance of the module class, on which
     * virtual methods (if there are any) will be invoked
     */
    public final T nullableInstance;

    /**
     * The set of methods which are allowed to be called,
     * marked as such via annotations.
     * Keys are the (renamed / mapped) method names.
     */
    public final Map<String, MethodData> allowedMethods;

    public JavaModuleData(Class<T> moduleClass, T nullableInstance) {
        this.moduleClass = moduleClass;
        this.nullableInstance = nullableInstance;
        // Get the map of allowed methods
        allowedMethods = ListUtils.toMap(ListUtils.filter(Arrays.asList(moduleClass.getMethods()),
                method -> method.isAnnotationPresent(WasmJAllow.class)),
                method -> {
                    // Get key
                    WasmJRename rename = method.getAnnotation(WasmJRename.class);
                    return rename != null ? rename.value() : method.getName();
                },
                method -> {
                    // Get value
                    boolean isStatic = Modifier.isStatic(method.getModifiers());
                    if (!isStatic && nullableInstance == null)
                        throw new IllegalArgumentException("Method \"" + method.getName() + "\" is non-static, and allowed, but the given instance is null!");
                    String javaName = method.getName();
                    String descriptor = Type.getMethodDescriptor(method);
                    return new MethodData(isStatic, javaName, descriptor);
                });
    }

    public String className() {
        return Type.getInternalName(moduleClass);
    }

    public record MethodData(boolean isStatic, String javaName, String descriptor) {

    }

}