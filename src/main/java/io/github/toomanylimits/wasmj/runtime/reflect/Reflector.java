package io.github.toomanylimits.wasmj.runtime.reflect;

import io.github.toomanylimits.wasmj.compiler.BytecodeHelper;
import io.github.toomanylimits.wasmj.compiler.Compile;
import io.github.toomanylimits.wasmj.runtime.ModuleInstance;
import io.github.toomanylimits.wasmj.runtime.reflect.annotations.WasmJAllow;
import io.github.toomanylimits.wasmj.runtime.reflect.annotations.WasmJRename;
import io.github.toomanylimits.wasmj.structure.utils.ListUtils;
import org.objectweb.asm.*;
import org.objectweb.asm.util.CheckClassAdapter;

import java.lang.reflect.Modifier;
import java.util.Arrays;

/**
 * Reflecting a java class's allowed static methods to generate bytes for a ModuleInstance
 */
public class Reflector {

    public static byte[] reflectStaticModule(String moduleName, Class<?> clazz) {
        ClassVisitor writer = new CheckClassAdapter(new ClassWriter(ClassWriter.COMPUTE_FRAMES + ClassWriter.COMPUTE_MAXS));
        String className = Compile.getClassName(moduleName);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, className, null, Type.getInternalName(Object.class), new String[] { Type.getInternalName(ModuleInstance.class) } );

        // Find static, allowed methods
        ListUtils.filter(Arrays.asList(clazz.getDeclaredMethods()), it -> {
            return it.isAnnotationPresent(WasmJAllow.class) && Modifier.isStatic(it.getModifiers());
        }).forEach(it -> {
            int acc = Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC;
            WasmJRename renameAnnotation = it.getAnnotation(WasmJRename.class);
            String name = renameAnnotation != null ? renameAnnotation.value() : it.getName();
            //TODO: Verify descriptor is allowed by WasmJ
            String desc = Type.getMethodDescriptor(it);
            MethodVisitor visitor = writer.visitMethod(acc, name, desc, null, null);
            visitor.visitCode();

            // Load all the locals (params), call the method, and return
            var curLocal = 0;
            for (Class<?> t : it.getParameterTypes()) {
                BytecodeHelper.loadLocal(visitor, curLocal, BytecodeHelper.wasmType(t));
                curLocal += BytecodeHelper.stackSlots(t);
            }
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(it.getDeclaringClass()), it.getName(), desc, false);
            if (it.getReturnType() == void.class)
                visitor.visitInsn(Opcodes.RETURN);
            else
                BytecodeHelper.returnValue(visitor, BytecodeHelper.wasmType(it.getReturnType()));

            visitor.visitMaxs(0, 0);
            visitor.visitEnd();
        });

        Compile.implementModuleInstance(writer, moduleName);
        return ((ClassWriter) writer.getDelegate()).toByteArray();
    }

}