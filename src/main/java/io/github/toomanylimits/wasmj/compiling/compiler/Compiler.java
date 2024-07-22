package io.github.toomanylimits.wasmj.compiling.compiler;

import io.github.toomanylimits.wasmj.compiling.helpers.BytecodeHelper;
import io.github.toomanylimits.wasmj.compiling.helpers.Names;
import io.github.toomanylimits.wasmj.compiling.simple_structure.data.SimpleData;
import io.github.toomanylimits.wasmj.compiling.simple_structure.data.SimpleElem;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.table.TableGet;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.table.TableGrow;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.table.TableSet;
import io.github.toomanylimits.wasmj.compiling.simple_structure.members.SimpleFunction;
import io.github.toomanylimits.wasmj.compiling.simple_structure.members.SimpleGlobal;
import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleModule;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.ClassGenCallback;
import io.github.toomanylimits.wasmj.compiling.simple_structure.members.SimpleTable;
import io.github.toomanylimits.wasmj.parsing.module.Data;
import io.github.toomanylimits.wasmj.runtime.ExternrefTableAccessor;
import io.github.toomanylimits.wasmj.runtime.reflect.JavaModuleData;
import io.github.toomanylimits.wasmj.runtime.sandbox.InstanceLimiter;
import io.github.toomanylimits.wasmj.runtime.sandbox.RefCountable;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.util.CheckClassAdapter;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

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
     * Compile a module into a set of byte[]s which can be given to a ClassLoader.
     */
    public static Map<String, byte[]> compile(SimpleModule module) {
        // Create and begin the class writer
        ClassVisitor classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        classWriter = new CheckClassAdapter(classWriter);

        String className = Names.className(module.moduleName);
        classWriter.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, className, null, Type.getInternalName(Object.class), null);

        // Create callback set
        HashSet<ClassGenCallback> classGenCallbacks = new HashSet<>();
        // Create necessary functions
        byte[] tableAccessorImplWriter = createExternRefTableAccessor(module, classGenCallbacks);
        MethodVisitor initFunction = beginInitMethod(classWriter, module.instance.instanceJavaModules, module.moduleName);

        // Emit the members
        for (SimpleFunction f : module.functions) f.emitFunction(module, classWriter, initFunction, classGenCallbacks);
        for (SimpleGlobal g : module.globals) g.emitGlobal(module, classWriter, initFunction, classGenCallbacks);
        for (SimpleTable t : module.tables) t.emitTable(module, classWriter, initFunction, classGenCallbacks);
        module.memory.emitMemory(module, classWriter, initFunction, classGenCallbacks);

        // Emit datas and elements
        for (SimpleData d : module.datas) d.emitData(module, classWriter, initFunction, classGenCallbacks);
        for (SimpleElem e : module.elems) e.emitElem(module, classWriter, initFunction, classGenCallbacks);

        // End init function
        initFunction.visitInsn(Opcodes.RETURN);
        initFunction.visitMaxs(0, 0);
        initFunction.visitEnd();

        // Process the callbacks
        ArrayList<ClassGenCallback> allCallbacks = new ArrayList<>(classGenCallbacks);
        HashSet<ClassGenCallback> alreadyRan = new HashSet<>();
        for (int i = 0; i < allCallbacks.size(); i++) {
            ClassGenCallback callback = allCallbacks.get(i);
            if (callback == null) continue;
            Set<ClassGenCallback> newCallbacks = callback.accept(module, classWriter);
            alreadyRan.add(callback);
            if (newCallbacks == null) continue;
            for (ClassGenCallback newCallback : newCallbacks) {
                if (alreadyRan.contains(newCallback)) continue;
                allCallbacks.add(newCallback);
            }
        }

        // End the writer and return it as a byte array
        classWriter.visitEnd();
        while (!(classWriter instanceof ClassWriter writer))
            classWriter = classWriter.getDelegate();

        // Return the generated classes, keyed by name
        return Map.of(
                Names.className(module.moduleName), writer.toByteArray(),
                Names.externrefTableAccessorImplClassName(module.moduleName), tableAccessorImplWriter
        );
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

        // Create the exportedFunctions field and fill it in
        writer.visitField(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, Names.exportedFunctionsFieldName(), Type.getDescriptor(List.class), null, null);
        BytecodeHelper.createDefaultObject(init, ArrayList.class);
        init.visitFieldInsn(Opcodes.PUTSTATIC, Names.className(moduleName), Names.exportedFunctionsFieldName(), Type.getDescriptor(List.class)); // []

        // Creeate and fill in the externref accessor field
        writer.visitField(Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC, Names.externrefTableAccessorFieldName(), Type.getDescriptor(ExternrefTableAccessor.class), null, null);
        init.visitTypeInsn(Opcodes.NEW, Names.externrefTableAccessorImplClassName(moduleName)); // [uninit accessor impl]
        init.visitInsn(Opcodes.DUP); // [uninit accessor impl, uninit accessor impl]
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, Names.externrefTableAccessorImplClassName(moduleName), "<init>", "()V", false); // [init accessor impl]
        init.visitFieldInsn(Opcodes.PUTSTATIC, Names.className(moduleName), Names.externrefTableAccessorFieldName(), Type.getDescriptor(ExternrefTableAccessor.class)); // []

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
     * Create the externref table accessor class and return its bytes.
     * Returns the ClassWriter for the accessor implementation.
     */
    private static byte[] createExternRefTableAccessor(SimpleModule module, Set<ClassGenCallback> classGenCallbacks) {
        // Create the new class:
        ClassWriter accessorImpl = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        String accessorImplName = Names.externrefTableAccessorImplClassName(module.moduleName);
        accessorImpl.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, accessorImplName, null, Type.getInternalName(Object.class), new String[] {Type.getInternalName(ExternrefTableAccessor.class)} );

        // Give it a basic default constructor:
        MethodVisitor constructor = accessorImpl.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, Type.getInternalName(Object.class), "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();

        // Implement the methods.
        int index = module.getExternrefTableIndex();

        // get():
        MethodVisitor getter = accessorImpl.visitMethod(Opcodes.ACC_PUBLIC, "get", "(I)" + Type.getDescriptor(RefCountable.class), null, null);
        getter.visitCode();
        if (index == -1)
            BytecodeHelper.throwRuntimeError(getter, "No externref table provided! Unable to get() value!");
        else {
            CompilingSimpleInstructionVisitor compilingVisitor = new CompilingSimpleInstructionVisitor(module, getter, 2, classGenCallbacks);
            getter.visitVarInsn(Opcodes.ILOAD, 1); // [index]
            compilingVisitor.visitIntrinsic(new TableGet(index)); // [table[index]], ref count was incremented
            getter.visitInsn(Opcodes.ARETURN);
        }
        getter.visitMaxs(0, 0);
        getter.visitEnd();

        // set():
        MethodVisitor setter = accessorImpl.visitMethod(Opcodes.ACC_PUBLIC, "set", "(I" + Type.getDescriptor(RefCountable.class) + ")V", null, null);
        setter.visitCode();
        if (index == -1)
            BytecodeHelper.throwRuntimeError(setter, "No externref table provided! Unable to set() value!");
        else {
            CompilingSimpleInstructionVisitor compilingVisitor = new CompilingSimpleInstructionVisitor(module, setter, 3, classGenCallbacks);
            setter.visitVarInsn(Opcodes.ILOAD, 1); // [index]
            setter.visitVarInsn(Opcodes.ALOAD, 2); // [index, value]
            compilingVisitor.visitIntrinsic(new TableSet(index)); // [], value was set, ref count of previous item was decremented
            setter.visitInsn(Opcodes.RETURN);
        }
        setter.visitMaxs(0, 0);
        setter.visitEnd();

        // store():
        MethodVisitor store = accessorImpl.visitMethod(Opcodes.ACC_PUBLIC, "store", "(" + Type.getDescriptor(RefCountable.class) + ")I", null, null);
        store.visitCode();
        if (index == -1)
            BytecodeHelper.throwRuntimeError(store, "No externref table provided! Unable to store() value!");
        else {
            // Get the array and look for a null element.
            module.tables[index].getTable(module, store); // [table]
            store.visitInsn(Opcodes.DUP); // [table, table]
            String asListDescriptor = Type.getMethodDescriptor(Type.getType(List.class), Type.getType(Object[].class));
            store.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(Arrays.class), "asList", asListDescriptor, false); // [table, Arrays.asList(table)]
            store.visitInsn(Opcodes.ACONST_NULL); // [table, Arrays.asList(table), null]
            String indexOfDescriptor = Type.getMethodDescriptor(Type.getType(int.class), Type.getType(Object.class));
            store.visitMethodInsn(Opcodes.INVOKEINTERFACE, Type.getInternalName(List.class), "indexOf", indexOfDescriptor, true); // [table, Arrays.asList(table).indexOf(null)]

            // If -1, no element was found, so grow the array.
            store.visitInsn(Opcodes.DUP); // [table, index, index]
            store.visitInsn(Opcodes.ICONST_M1); // [table, index, index, -1]
            BytecodeHelper.writeIfElse(store, Opcodes.IF_ICMPEQ, ifFound -> {
                // [table, index]
                // Count up instructions if necessary
                if (module.instance.limiter.countsInstructions) {
                    ifFound.visitInsn(Opcodes.DUP); // [index, index]
                    ifFound.visitFieldInsn(Opcodes.GETSTATIC, Names.className(module.moduleName), Names.limiterFieldName(), Type.getDescriptor(InstanceLimiter.class)); // [index, index, limiter]
                    ifFound.visitInsn(Opcodes.SWAP); // [index, limiter, index]
                    ifFound.visitInsn(Opcodes.I2L); // [index, limiter, (long) index]
                    ifFound.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(InstanceLimiter.class), "incInstructions", "(J)V", false); // [index]
                }
                // Return the found index
                ifFound.visitInsn(Opcodes.IRETURN);
            }, ifNotFound -> {
                // Double the table size + 1, and return a new index
                CompilingSimpleInstructionVisitor compilingVisitor = new CompilingSimpleInstructionVisitor(module, ifNotFound, 2, classGenCallbacks);
                // [table, -1]
                ifNotFound.visitInsn(Opcodes.POP); // [table]
                ifNotFound.visitInsn(Opcodes.ARRAYLENGTH); // [table.length]
                ifNotFound.visitInsn(Opcodes.ICONST_1); // [table.length, 1]
                ifNotFound.visitInsn(Opcodes.IADD); // [table.length + 1]
                ifNotFound.visitInsn(Opcodes.ACONST_NULL); // [table.length + 1, null]
                ifNotFound.visitInsn(Opcodes.SWAP); // [null, table.length + 1]
                compilingVisitor.visitIntrinsic(new TableGrow(index)); // [oldTable.length]
                // The oldTable.length, conveniently enough, is now the first non-null index, so return it.
                ifNotFound.visitInsn(Opcodes.IRETURN);
            });
        }
        store.visitMaxs(0, 0);
        store.visitEnd();

        // Methods are implemented, end the accessor impl.
        accessorImpl.visitEnd();
        return accessorImpl.toByteArray();
    }

}
