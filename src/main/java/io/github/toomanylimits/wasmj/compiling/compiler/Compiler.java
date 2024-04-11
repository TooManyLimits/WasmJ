package io.github.toomanylimits.wasmj.compiling.compiler;

import io.github.toomanylimits.wasmj.compiling.helpers.Names;
import io.github.toomanylimits.wasmj.compiling.simple_structure.data.SimpleData;
import io.github.toomanylimits.wasmj.compiling.simple_structure.members.SimpleFunction;
import io.github.toomanylimits.wasmj.compiling.simple_structure.members.SimpleGlobal;
import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleModule;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.ClassGenCallback;
import io.github.toomanylimits.wasmj.compiling.simple_structure.members.SimpleTable;
import io.github.toomanylimits.wasmj.parsing.module.Data;
import io.github.toomanylimits.wasmj.runtime.reflect.JavaModuleData;
import io.github.toomanylimits.wasmj.runtime.sandbox.InstanceLimiter;
import org.objectweb.asm.*;
import org.objectweb.asm.util.CheckClassAdapter;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Logic for compiling a SimpleModule into a byte[], stored in its own class
 * for organization.
 */
public class Compiler {

    // The next free local available in the init function.
    public static final int INIT_FUNCTION_LIMITER_LOCAL = 0;
    public static final int INIT_FUNCTION_MAP_LOCAL = 1;
    public static final int INIT_FUNCTION_SIMPLE_MODULE_LOCAL = 2;
    public static final int INIT_FUNCTION_NEXT_LOCAL = 3;
    public static final int WASM_PAGE_SIZE = 65536;

    /**
     * Compile a module into a byte[] which can be given to a ClassLoader.
     */
    public static byte[] compile(SimpleModule module) {
        // Create and begin the class writer
        ClassVisitor classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        classWriter = new CheckClassAdapter(classWriter);
        String className = Names.className(module.moduleName);
        classWriter.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, className, null, Type.getInternalName(Object.class), null);

        // Create callback set
        HashSet<ClassGenCallback> classGenCallbacks = new HashSet<>();
        // Create init function
        MethodVisitor initFunction = beginInitMethod(classWriter, module.instance.instanceJavaModules, module.moduleName);

        // Emit the members
        for (SimpleFunction f : module.functions) f.emitFunction(module, classWriter, classGenCallbacks);
        for (SimpleGlobal g : module.globals) g.emitGlobal(module, classWriter, initFunction, classGenCallbacks);
        for (SimpleTable t : module.tables) t.emitTable(module, classWriter, initFunction, classGenCallbacks);
        module.memory.emitMemory(module, classWriter, initFunction, classGenCallbacks);

        // Emit datas (todo: and elements)
        for (SimpleData d : module.datas) d.emitData(module, classWriter, initFunction, classGenCallbacks);

        // End init function
        initFunction.visitInsn(Opcodes.RETURN);
        initFunction.visitMaxs(0, 0);
        initFunction.visitEnd();

        // Process the callbacks
        HashSet<ClassGenCallback> newCallbacks = new HashSet<>(classGenCallbacks);
        while (!newCallbacks.isEmpty()) {
            for (var callback : newCallbacks) {
                newCallbacks.addAll(callback.accept(module, classWriter));
            }
            newCallbacks.removeAll(classGenCallbacks);
            classGenCallbacks.addAll(newCallbacks);
        }

        // End writer and return
        classWriter.visitEnd();
        return ((ClassWriter) classWriter.getDelegate()).toByteArray();
    }

    /**
     * The init method accepts an InstanceLimiter as the first parameter,
     * and a Map<String, JavaModuleData<?>> as its second parameter.
     * The InstanceLimiter will be used to fill the limiter field, and the
     * map will be used to fill all the global fields.
     */
    private static MethodVisitor beginInitMethod(ClassVisitor writer, Map<String, JavaModuleData<?>> javaModules, String moduleName) {
        // Create the MethodVisitor
        int access = Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC;
        String descriptor = "(" + Type.getDescriptor(InstanceLimiter.class) + Type.getDescriptor(Map.class) + Type.getDescriptor(SimpleModule.class) + ")V";
        MethodVisitor init = writer.visitMethod(access, Names.initMethodName(), descriptor, null, null);

        // Create the limiter field and fill it in
        writer.visitField(Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC, Names.limiterFieldName(), Type.getDescriptor(InstanceLimiter.class), null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, INIT_FUNCTION_LIMITER_LOCAL); // [limiter]
        init.visitFieldInsn(Opcodes.PUTSTATIC, Names.className(moduleName), Names.limiterFieldName(), Type.getDescriptor(InstanceLimiter.class)); // []

        // Create the global instance fields and fill them in
        for (Map.Entry<String, JavaModuleData<?>> javaModule : javaModules.entrySet()) {
            // Skip any without a global instance
            if (javaModule.getValue().globalInstance == null) continue;
            // For the rest, create a field and fill it in
            String fieldDesc = Type.getDescriptor(javaModule.getValue().moduleClass);
            writer.visitField(Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC, Names.globalInstanceFieldName(javaModule.getKey()), fieldDesc, null, null);
            init.visitVarInsn(Opcodes.ALOAD, INIT_FUNCTION_MAP_LOCAL); // [Map]
            init.visitLdcInsn(javaModule.getKey()); // [Map, javaModuleName]
            init.visitMethodInsn(Opcodes.INVOKEINTERFACE, Type.getInternalName(Map.class), "get", "(Ljava/lang/Object;)Ljava/lang/Object;", true); // [javaModuleData]
            init.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(JavaModuleData.class)); // [javaModuleData]
            init.visitFieldInsn(Opcodes.GETFIELD, Type.getInternalName(JavaModuleData.class), "globalInstance", "Ljava/lang/Object;"); // [javaModuleData.globalInstance]
            init.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(javaModule.getValue().moduleClass)); // [(InstanceType) javaModuleData.globalInstance]
            init.visitFieldInsn(Opcodes.PUTSTATIC, Names.className(moduleName), Names.globalInstanceFieldName(javaModule.getKey()), fieldDesc); // [], value was stored in field
        }

        // Return the method visitor
        return init;
    }

    /**
     * Emit the given data.
     */
    private static void emitData(int dataIndex, Data data, SimpleModule module, ClassVisitor classWriter, MethodVisitor initFunction, Set<ClassGenCallback> classGenCallbacks) {

    }

}
