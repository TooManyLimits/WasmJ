package io.github.toomanylimits.wasmj.compiler;

import io.github.toomanylimits.wasmj.parsing.instruction.Instruction;
import io.github.toomanylimits.wasmj.runtime.sandbox.InstanceLimiter;
import io.github.toomanylimits.wasmj.parsing.module.Code;
import io.github.toomanylimits.wasmj.parsing.module.Data;
import io.github.toomanylimits.wasmj.parsing.module.Global;
import io.github.toomanylimits.wasmj.parsing.module.WasmModule;
import io.github.toomanylimits.wasmj.parsing.types.FuncType;
import io.github.toomanylimits.wasmj.parsing.types.Limits;
import io.github.toomanylimits.wasmj.parsing.types.TableType;
import io.github.toomanylimits.wasmj.parsing.types.ValType;
import io.github.toomanylimits.wasmj.runtime.reflect.JavaModuleData;
import io.github.toomanylimits.wasmj.runtime.sandbox.RefCountable;
import org.objectweb.asm.*;
import org.objectweb.asm.util.CheckClassAdapter;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;

public class Compile {

    public static final int WASM_PAGE_SIZE = 65536;

    // Name getters. This class acts as a central authority. Other classes come here to decide
    // what names to give things.
    public static String getClassName(String moduleName) { return "module/" + moduleName; }
    public static String getFuncName(int index) { return "func_" + index; }
    public static String getGlobalName(int index) { return "global_" + index; }

    // Get the name of the limiter field
    public static String getLimiterName() { return "limiter"; }
    public static String getInitMethodName() { return "WasmJ_Init"; }


    // Skip boolean, wasm doesn't use it, and byte, since can't make a var handle of it
    private static final Class<?>[] primitives = new Class[] { short.class, int.class, long.class, float.class, double.class };
    public static String getMemoryName(int index) { return "memory_" + index; }
    public static String getMemoryVarHandleName(String typeDesc) { return "memory_handle_" + typeDesc; } // Remember: LITTLE ENDIAN!

    public static String getTableName(int index) { return "table_" + index; }
    public static final String TABLE_DESCRIPTOR = Type.getDescriptor(RefCountable[].class);


    // Instantiates the given module and creates a ByteArray, which is the bytecode for a class
    // implementing ModuleInstance.
    public static byte[] compileModule(Map<String, JavaModuleData<?>> javaModules, InstanceLimiter limiter, String moduleName, WasmModule module) {

        // Create the class writer and fill some data about the class
        ClassVisitor writer = new CheckClassAdapter(new ClassWriter(ClassWriter.COMPUTE_FRAMES + ClassWriter.COMPUTE_MAXS));
        String className = getClassName(moduleName);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, className, null, Type.getInternalName(Object.class), null);

        emitFunctions(writer, javaModules, limiter, moduleName, module);

        // Create the initialization method
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, getInitMethodName(), "()V", null, null);
        init.visitCode();
        emitGlobals(writer, javaModules, limiter, moduleName, module, init);
        emitMemory(writer, moduleName, module, init);
        emitDatas(writer, javaModules, limiter, moduleName, module, init);
        emitTables(writer, moduleName, module, init);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();

        emitLimiterField(writer);

//        implementModuleInstance(writer, moduleName, module.start == null ? null : getFuncName(module.start));

        writer.visitEnd();
        return ((ClassWriter) writer.getDelegate()).toByteArray();
    }

    private static void emitFunctions(ClassVisitor writer, Map<String, JavaModuleData<?>> javaModules, InstanceLimiter limiter, String moduleName, WasmModule module) {
        // Create the various functions
        for (int index = 0; index < module.functions.size(); index++) {
            int funcTypeIndex = module.functions.get(index);
            // Get the function type
            FuncType funcType = module.types.get(funcTypeIndex);
            // Get the descriptor for the function
            String descriptor = funcType.descriptor();
            if (funcType.results.size() > 1)
                throw new UnsupportedOperationException("Multi-return functions not yet supported");
            // Get data about the function, check if exported to figure out the name
            int access = Opcodes.ACC_STATIC + Opcodes.ACC_PRIVATE;
            String funcName = getFuncName(index);
            // Generate the code
            Code code = module.codes.get(index);
            MethodVisitor visitor = writer.visitMethod(access, funcName, descriptor, null, null);
            visitor.visitCode();
            // Initialize all locals to 0/null, since WASM expects this
            for (int localIndex = funcType.args.size(); localIndex < code.locals.size(); localIndex++) {
                ValType localType = code.locals.get(localIndex);
                int mappedIndex = code.localMappings().get(localIndex);
                if (localType == ValType.i32) visitor.visitInsn(Opcodes.ICONST_0);
                else if (localType == ValType.i64) visitor.visitInsn(Opcodes.LCONST_0);
                else if (localType == ValType.f32) visitor.visitInsn(Opcodes.FCONST_0);
                else if (localType == ValType.f64) visitor.visitInsn(Opcodes.DCONST_0);
                else if (localType == ValType.funcref || localType == ValType.externref) visitor.visitInsn(Opcodes.ACONST_NULL);
                else throw new UnsupportedOperationException("Cannot initialize local of given type - only int, long, float, double, reftype");
                BytecodeHelper.storeLocal(visitor, mappedIndex, localType);
            }
            // Visit instructions
            MethodWritingVisitor instVisitor = new MethodWritingVisitor(javaModules, limiter, moduleName, module, code, visitor);
            instVisitor.visitExpr(code.expr);
            // Emit a return
            instVisitor.visitReturn(Instruction.Return.INSTANCE);
            // The code has been generated, now finish the method.
            visitor.visitMaxs(0, 0);
            visitor.visitEnd();
        }
        // TODO: For each export, generate a public function that delegates to the exported one
    }

    private static void emitGlobals(ClassVisitor writer, Map<String, JavaModuleData<?>> javaModules, InstanceLimiter limiter, String moduleName, WasmModule module, MethodVisitor init) {
        // Create the various globals, and initialize them in init.
        String className = getClassName(moduleName);
        for (int index = 0; index < module.globals.size(); index++) {
            Global global = module.globals.get(index);
            int access = Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC;
            // Create field:
            writer.visitField(access, getGlobalName(index), global.globalType().valType().desc(), null, null);
            // Initialize:
            MethodWritingVisitor instVisitor = new MethodWritingVisitor(javaModules, limiter, moduleName, module, new Code(-1, -1, List.of(), global.initializer()), init);
            instVisitor.visitExpr(global.initializer());
            init.visitFieldInsn(Opcodes.PUTSTATIC, className, getGlobalName(index), global.globalType().valType().desc());
        }
        // TODO: For each export, generate a public function to grab the field
    }

    private static void emitMemory(ClassVisitor writer, String moduleName, WasmModule module, MethodVisitor init) {
        // Create the various "memory" fields, which are byte[], as well as other helper fields.
        // Also initialize these fields in init.
        String className = getClassName(moduleName);
        int privateStatic = Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC;

        for (int index = 0; index < module.memories.size(); index++) {
            Limits limits = module.memories.get(index);
            // Create fields and get <init> to fill them with values
            writer.visitField(privateStatic, getMemoryName(index), "[B", null, null); // Create memory field
            init.visitLdcInsn(WASM_PAGE_SIZE * 32); // TODO limits.min, make memory grow from byte data
            init.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_BYTE);
            init.visitFieldInsn(Opcodes.PUTSTATIC, className, getMemoryName(index), "[B");
        }
        for (Class<?> primitive : primitives) {
            Type arr = Type.getType(primitive.arrayType());
            String desc = Type.getDescriptor(primitive);
            // Create the field
            writer.visitField(privateStatic, getMemoryVarHandleName(desc), Type.getDescriptor(VarHandle.class), null, null);
            // Fill it in init
            init.visitLdcInsn(arr);
            init.visitFieldInsn(Opcodes.GETSTATIC, Type.getInternalName(ByteOrder.class), "LITTLE_ENDIAN", Type.getDescriptor(ByteOrder.class));
            String methodDesc = "(" + Type.getDescriptor(Class.class) + Type.getDescriptor(ByteOrder.class) + ")" + Type.getDescriptor(VarHandle.class);
            init.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(MethodHandles.class), "byteArrayViewVarHandle", methodDesc, false);
            init.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(VarHandle.class), "withInvokeExactBehavior", "()" + Type.getDescriptor(VarHandle.class), false);
            init.visitFieldInsn(Opcodes.PUTSTATIC, className, getMemoryVarHandleName(desc), Type.getDescriptor(VarHandle.class));
        }
        // TODO: For each export, generate a public function to grab the byte[]
    }

    private static void emitDatas(ClassVisitor writer, Map<String, JavaModuleData<?>> javaModules, InstanceLimiter limiter, String moduleName, WasmModule module, MethodVisitor init) {
        for (int dataIndex = 0; dataIndex < module.datas.size(); dataIndex++) {
            Data data = module.datas.get(dataIndex);
            // TODO: Make this use a more efficient system for initializing bytes, this one is complete garbage lol
            if (data.mode instanceof Data.Mode.Active activeMode) {
                init.visitFieldInsn(Opcodes.GETSTATIC, getClassName(moduleName), getMemoryName(activeMode.memIndex()), "[B");
                MethodWritingVisitor instVisitor = new MethodWritingVisitor(javaModules, limiter, moduleName, module, new Code(-1, -1, List.of(), activeMode.offset()), init);
                instVisitor.visitExpr(activeMode.offset());
                for (byte b : data.init) {
                    // [arr, index]
                    init.visitInsn(Opcodes.DUP2); // [arr, index, arr, index]
                    BytecodeHelper.constInt(init, b); // [arr, index, arr, index, byte]
                    init.visitInsn(Opcodes.BASTORE); // [arr, index]
                    BytecodeHelper.constInt(init, 1); // [arr, index, 1]
                    init.visitInsn(Opcodes.IADD); // [arr, index+1]
                }
            }
        }
    }

    private static void emitTables(ClassVisitor writer, String moduleName, WasmModule module, MethodVisitor init) {
        // Create the various "table" fields, which are Object[].
        String className = getClassName(moduleName);
        int privateStatic = Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC;
        for (int index = 0; index < module.tables.size(); index++) {
            TableType tableType = module.tables.get(index);
            // Create fields and get init to fill them with values
            writer.visitField(privateStatic, getTableName(index), TABLE_DESCRIPTOR, null, null); // Create table field
            init.visitLdcInsn(256); // TODO limits.min, make table grow from element data
            init.visitTypeInsn(Opcodes.ANEWARRAY, TABLE_DESCRIPTOR.substring(2, TABLE_DESCRIPTOR.length() - 1));
            init.visitFieldInsn(Opcodes.PUTSTATIC, className, getTableName(index), TABLE_DESCRIPTOR);
        }
        // TODO: For each export, generate a public function to grab the Object[]
    }

    // Create the limiter field, which is present on all the generated wasm module classes
    private static void emitLimiterField(ClassVisitor writer) {
        int flags = Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC;
        String descriptor = Type.getDescriptor(InstanceLimiter.class);
        writer.visitField(flags, getLimiterName(), descriptor, null, null);
    }

    private static void implementModuleInstance(ClassVisitor writer, String moduleName, String startFuncName) {
        // No real need for any of this
//        // Default constructor
//        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
//        constructor.visitCode();
//        constructor.visitVarInsn(Opcodes.ALOAD, 0);
//        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, Type.getInternalName(Object.class), "<init>", "()V", false);
//        constructor.visitInsn(Opcodes.RETURN);
//        constructor.visitMaxs(0, 0);
//        constructor.visitEnd();
//
//        // name() method: Returns the name that the module was instantiated with
//        MethodVisitor nameMethod = writer.visitMethod(Opcodes.ACC_PUBLIC, "name", "()" + Type.getDescriptor(String.class), null, null);
//        nameMethod.visitCode();
//        nameMethod.visitLdcInsn(moduleName);
//        nameMethod.visitInsn(Opcodes.ARETURN);
//        nameMethod.visitMaxs(0, 0);
//        nameMethod.visitEnd();
//
//        // start() method: Run the start method, if one exists
//        // Start method is assumed to have descriptor ()V
//        MethodVisitor startMethod = writer.visitMethod(Opcodes.ACC_PUBLIC, "start", "()V", null, null);
//        startMethod.visitCode();
//        if (startFuncName != null) {
//            startMethod.visitMethodInsn(Opcodes.INVOKESTATIC, getClassName(moduleName), startFuncName, "()V", false);
//        }
//        startMethod.visitInsn(Opcodes.RETURN);
//        startMethod.visitMaxs(0, 0);
//        startMethod.visitEnd();
    }

}