package io.github.toomanylimits.wasmj.runtime.reflect;

import io.github.toomanylimits.wasmj.compiling.helpers.Names;
import io.github.toomanylimits.wasmj.compiling.helpers.BytecodeHelper;
import io.github.toomanylimits.wasmj.parsing.types.ValType;
import io.github.toomanylimits.wasmj.runtime.reflect.annotations.ByteArrayAccess;
import io.github.toomanylimits.wasmj.runtime.reflect.annotations.LimiterAccess;
import io.github.toomanylimits.wasmj.runtime.reflect.annotations.WasmJAllow;
import io.github.toomanylimits.wasmj.runtime.reflect.annotations.WasmJRename;
import io.github.toomanylimits.wasmj.runtime.sandbox.InstanceLimiter;
import io.github.toomanylimits.wasmj.runtime.sandbox.RefCountable;
import io.github.toomanylimits.wasmj.util.ListUtils;
import org.objectweb.asm.*;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
     * virtual methods (if there are any) will be invoked.
     */
    public final T globalInstance;

    /**
     * The set of methods which are allowed to be called,
     * marked as such via annotations.
     * Keys are the (renamed / mapped) method names.
     */
    public final Map<String, MethodData> allowedMethods;

    /**
     * The basic, "global module" reflection type that's used
     * in "addGlobalInstanceJavaModule" and "addStaticJavaModule".
     * The global instance is null in the second case and T in the
     * first case.
     */
    public JavaModuleData(Class<T> moduleClass, T globalInstance) {
        this.moduleClass = moduleClass;
        this.globalInstance = globalInstance;
        // Get the map of allowed methods
        allowedMethods = ListUtils.associateBy(ListUtils.map(ListUtils.filter(Arrays.asList(moduleClass.getMethods()),
                method -> method.isAnnotationPresent(WasmJAllow.class)),
                method -> {
                    // Get value
                    if (!Modifier.isStatic(method.getModifiers()) && globalInstance == null)
                        throw new IllegalArgumentException("Method \"" + method.getName() + "\" is non-static, and allowed, but the given instance is null!");
                    WasmJRename rename = method.getAnnotation(WasmJRename.class);
                    String wasmName = rename != null ? rename.value() : method.getName();
                    return new MethodData(method, wasmName, true);
                }),
                MethodData::wasmName);
    }

    /**
     * The more advanced reflection type that's used in the "addTypeModule()" method.
     * Adds an argument at the beginning for the virtual receiver of the calls.
     */
    public JavaModuleData(Class<T> typeToReflect) {
        this.moduleClass = typeToReflect;
        this.globalInstance = null;
        // Get the map of allowed methods
        allowedMethods = ListUtils.associateBy(ListUtils.map(ListUtils.filter(Arrays.asList(moduleClass.getMethods()),
                method -> method.isAnnotationPresent(WasmJAllow.class)),
                method -> {
                    // Get value
                    WasmJRename rename = method.getAnnotation(WasmJRename.class);
                    String wasmName = rename != null ? rename.value() : method.getName();
                    return new MethodData(method, wasmName, false);
                }),
                MethodData::wasmName);
    }

    public String className() {
        return Type.getInternalName(moduleClass);
    }

    public record MethodData(Method method, String wasmName, boolean globalInstanceMode) {
        public boolean isStatic() {
            return Modifier.isStatic(method.getModifiers());
        }
        public String javaName() {
            return method.getName();
        }
        public String descriptor() {
            return Type.getMethodDescriptor(method);
        }

        public boolean hasByteArrayAccess() {
            return method.isAnnotationPresent(ByteArrayAccess.class);
        }
        public boolean hasLimiterAccess() {
            return method.isAnnotationPresent(LimiterAccess.class);
        }

        // A function needs glue if it has any reference-type parameters which are more specific than
        // RefCountable.
        // TODO: Take return type into consideration...?
        public boolean needsGlue() {
            if (!isStatic() && globalInstanceMode) return true;
            if (ListUtils.any(Arrays.asList(method.getParameterTypes()), MethodData::isGluedType)) return true;
            if (!globalInstanceMode && !isStatic() && isGluedType(method.getDeclaringClass())) return true;
            return false;
        }

        public String glueDescriptor() {
            return "(" + ListUtils.fold(ListUtils.map(getGlueParams(), MethodData::gluedTypeDescriptor), "", String::concat) + ")" + Type.getDescriptor(method.getReturnType());
        }

        public void writeGlue(ClassVisitor writer, String functionName, String callerModuleName, String javaModuleName, boolean doRefcounting) {
            int access = Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC;
            MethodVisitor visitor = writer.visitMethod(access, functionName, glueDescriptor(), null, null);

            visitor.visitCode();
            if (globalInstanceMode && !isStatic()) {
                String owner = Names.className(callerModuleName); // Field is located in the caller, the wasm module
                String name = Names.globalInstanceFieldName(javaModuleName); // Name is based on the name of the java module
                String desc = Type.getDescriptor(method.getDeclaringClass()); // Receiver is the type of the global instance
                visitor.visitFieldInsn(Opcodes.GETSTATIC, owner, name, desc); // Grab the static global instance
            }

            int paramIndex = 0;
            int localIndex = 0;
            for (Class<?> glueParam : getGlueParams()) {
                // Load the param
                if (glueParam == boolean.class) {
                    // If boolean, ensure it's 0 or 1 before passing to the java function
                    visitor.visitVarInsn(Opcodes.ILOAD, localIndex);
                    BytecodeHelper.test(visitor, Opcodes.IFNE); // Convert to 1 or 0
                    localIndex += 1;
                } else {
                    ValType valtype = BytecodeHelper.wasmType(glueParam);
                    visitor.visitVarInsn(valtype.loadOpcode, localIndex); // Load the local
                    localIndex += valtype.stackSlots;
                }
                if (isGluedType(glueParam)) {
                    // If it's glued, use instanceof to ensure type
                    Label isInstance = new Label();
                    Label isNull = new Label();
                    // Stack = [ ...,  object ]
                    visitor.visitInsn(Opcodes.DUP); // [ ..., object, object ]
                    visitor.visitTypeInsn(Opcodes.INSTANCEOF, Type.getInternalName(glueParam)); // [ ..., object, bool ]
                    visitor.visitJumpInsn(Opcodes.IFNE, isInstance); // [ ..., object ]

                    // Also okay if it's null (TODO: annotation for non-nullable?)
                    visitor.visitInsn(Opcodes.DUP); // [ ..., object, object ]
                    visitor.visitJumpInsn(Opcodes.IFNULL, isNull); // [ ..., object ]

                    // Throw an error with a hopefully nice error message
                    visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;", false);
                    visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getSimpleName", "()Ljava/lang/String;", false);
                    visitor.visitLdcInsn("Method \"" + wasmName + "\" expected type " + glueParam.getSimpleName() + " as param " + paramIndex + ", found ");
                    visitor.visitInsn(Opcodes.SWAP);
                    visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;", false);
                    BytecodeHelper.throwRuntimeError(visitor);

                    visitor.visitLabel(isInstance);

                    // Do refcounting on the type if necessary, since we're calling out of WASM and into java!
                    // Here, the type *is* an instance, and it *is not* null.
                    if (doRefcounting) {
                        visitor.visitInsn(Opcodes.DUP); // [ ..., object, object ]
                        visitor.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(RefCountable.class)); // [ ..., object, object ]
                        visitor.visitFieldInsn(Opcodes.GETSTATIC, Names.className(callerModuleName), Names.limiterFieldName(), Type.getDescriptor(InstanceLimiter.class)); // [ ..., object, object, limiter ]
                        visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(RefCountable.class), "dec", "(" + Type.getDescriptor(InstanceLimiter.class) + ")V", false); // [ ..., object ]
                    }

                    visitor.visitLabel(isNull);

                    // Checkcast at the end. This checkcast will always succeed, but it's necessary to satisfy the verifier.
                    visitor.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(glueParam));
                }
                paramIndex++;
            }

            // Call the original function
            int opcode = isStatic() ? Opcodes.INVOKESTATIC : Opcodes.INVOKEVIRTUAL;
            visitor.visitMethodInsn(opcode, Type.getInternalName(method.getDeclaringClass()), javaName(), descriptor(), false);

            // Return the output of the java function
            if (method.getReturnType() == void.class) {
                visitor.visitInsn(Opcodes.RETURN);
            } else if (method.getReturnType() == Object[].class) {
                visitor.visitInsn(Opcodes.ARETURN);
            } else if (method.getReturnType() == boolean.class) {
                visitor.visitInsn(Opcodes.IRETURN);
            } else {
                ValType wasmType = BytecodeHelper.wasmType(method.getReturnType());
                visitor.visitInsn(wasmType.returnOpcode);
            }

            // And end the visitor
            visitor.visitMaxs(0, 0);
            visitor.visitEnd();
        }

        private List<Class<?>> getGlueParams() {
            List<Class<?>> res = new ArrayList<>(Arrays.asList(method.getParameterTypes()));
            if (!globalInstanceMode && !isStatic())
                res.add(0, method.getDeclaringClass());
            return res;
        }

        // Returns true if the clazz is RefCountable or a subclass
        private static boolean isGluedType(Class<?> clazz) {
            return RefCountable.class.isAssignableFrom(clazz);
//            return !clazz.isPrimitive() && !clazz.isAssignableFrom(RefCountable.class);
        }

        private static String gluedTypeDescriptor(Class<?> clazz) {
            return isGluedType(clazz) ? Type.getDescriptor(Object.class) : Type.getDescriptor(clazz);
        }


    }

}
