package io.github.toomanylimits.wasmj.compiler;

import io.github.toomanylimits.wasmj.parsing.instruction.Expression;
import io.github.toomanylimits.wasmj.parsing.instruction.Instruction;
import io.github.toomanylimits.wasmj.parsing.module.*;
import io.github.toomanylimits.wasmj.runtime.sandbox.InstanceLimiter;
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
    public static String getGlueFuncName(int index) { return "func_" + index + "_glue"; }
    public static String getGlobalInstanceName(String javaModuleName) { return "global_instance_" + javaModuleName; }
    public static String getGlobalName(int index) { return "global_" + index; }
    public static String getDataFieldName(int index) { return "data_" + index; }
    public static String getElemFieldName(int index) { return "elem_" + index; }
    public static String getJavaExportFuncName(String exportName) { return "java_export_func_" + exportName; }
    public static String getWasmExportFuncName(String exportName) { return "wasm_export_func_" + exportName; }
    public static String getExportedMemGetterName(String exportName) { return "get_exported_mem_" + exportName; }
    public static String getExportedMemSetterName(String exportName) { return "set_exported_mem_" + exportName; }
    public static String getExportedTableGetterName(String exportName) { return "get_exported_table_" + exportName; }
    public static String getExportedTableSetterName(String exportName) { return "set_exported_table_" + exportName; }


    // Get the name of the limiter field
    public static String getLimiterName() { return "limiter"; }
    public static String getInitMethodName() { return "WasmJ_Init"; }

    // Skip boolean, wasm doesn't use it, and byte, since can't make a var handle of it
    private static final Class<?>[] primitives = new Class[] { short.class, int.class, long.class, float.class, double.class };
    public static String getMemoryName(int index) { return "memory_" + index; }
    public static String getMemoryVarHandleName(String typeDesc) { return "memory_handle_" + typeDesc; } // Remember: LITTLE ENDIAN!

    public static String getTableName(int index) { return "table_" + index; }
    public static final String TABLE_DESCRIPTOR = Type.getDescriptor(RefCountable[].class);
    public static final String FUNCREF_TABLE_DESCRIPTOR = Type.getDescriptor(FuncRefInstance[].class);


    // Instantiates the given module and creates a ByteArray, which is the bytecode for a class
    // implementing ModuleInstance.
    public static byte[] compileModule(Map<String, JavaModuleData<?>> javaModules, InstanceLimiter limiter, String moduleName, WasmModule module) {

        // Create the class writer and fill some data about the class
        ClassVisitor writer = new CheckClassAdapter(new ClassWriter(ClassWriter.COMPUTE_FRAMES + ClassWriter.COMPUTE_MAXS));
        String className = getClassName(moduleName);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, className, null, Type.getInternalName(Object.class), null);

        // Functions and glue
        emitFunctions(writer, javaModules, limiter, moduleName, module);
        emitGlueFunctions(writer, javaModules, moduleName, module);

        // Exports
        emitExportFunctions(writer, javaModules, limiter, moduleName, module);
        emitMemoryExports(writer, javaModules, moduleName, module);
        emitTableExports(writer, javaModules, moduleName, module);

        // Create the initialization method
        MethodVisitor init = beginInitMethod(writer, javaModules, moduleName);
        emitGlobals(writer, javaModules, limiter, moduleName, module, init);
        emitMemory(writer, limiter, moduleName, module, init);
        emitDatas(writer, javaModules, limiter, moduleName, module, init);
        emitTables(writer, limiter, moduleName, module, init);
        emitElements(writer, javaModules, limiter, moduleName, module, init);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();

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
            String funcName = getFuncName(index + module.funcImports().size());
            // Generate the code
            Code code = module.codes.get(index);
            MethodVisitor visitor = writer.visitMethod(access, funcName, descriptor, null, null);
            visitor.visitCode();
            // Initialize all locals to 0/null, since WASM expects this
            for (int localIndex = funcType.args.size(); localIndex < code.locals.size(); localIndex++) {
                ValType localType = code.locals.get(localIndex);
                int mappedIndex = code.localMappings().get(localIndex);
                if (localType == ValType.I32) visitor.visitInsn(Opcodes.ICONST_0);
                else if (localType == ValType.I64) visitor.visitInsn(Opcodes.LCONST_0);
                else if (localType == ValType.F32) visitor.visitInsn(Opcodes.FCONST_0);
                else if (localType == ValType.F64) visitor.visitInsn(Opcodes.DCONST_0);
                else if (localType == ValType.FUNCREF || localType == ValType.EXTERNREF) visitor.visitInsn(Opcodes.ACONST_NULL);
                else throw new UnsupportedOperationException("Cannot initialize local of given type - only int, long, float, double, reftype");
                BytecodeHelper.storeLocal(visitor, mappedIndex, localType);
            }
            // Visit instructions
            MethodWritingVisitor instVisitor = new MethodWritingVisitor(javaModules, limiter, moduleName, module, code, funcType, visitor);
            instVisitor.visitExpr(code.expr);
            // Emit a return
            instVisitor.visitReturn(Instruction.Return.INSTANCE);
            // The code has been generated, now finish the method.
            visitor.visitMaxs(0, 0);
            visitor.visitEnd();
        }
    }

    // Emit glue functions for methods in javaModules, involving specialized reference type params/receivers
    private static void emitGlueFunctions(ClassVisitor writer, Map<String, JavaModuleData<?>> javaModules, String moduleName, WasmModule module) {
        // Iterate over func imports
        for (int i = 0; i < module.funcImports().size(); i++) {
            Import.Func funcImport = module.funcImports().get(i);
            // If this imported func is a java function:
            if (javaModules.containsKey(funcImport.moduleName)) {
                JavaModuleData.MethodData funcData = javaModules.get(funcImport.moduleName).allowedMethods.get(funcImport.elementName);
                if (funcData == null)
                    throw new IllegalArgumentException("Failed to compile - unrecognized function \"" + funcImport.moduleName + "." + funcImport.elementName + "\"");
                // If this imported java function needs glue, then generate glue.
                if (funcData.needsGlue()) {
                    funcData.writeGlue(writer, Compile.getGlueFuncName(i), moduleName, funcImport.moduleName);
                }
            }
        }
    }

    /**
     * Emit 2 functions for each exported function.
     * One is designed to be called from JAVA,
     * the other is called from WASM.
     */
    private static void emitExportFunctions(ClassVisitor writer, Map<String, JavaModuleData<?>> javaModules, InstanceLimiter limiter, String moduleName, WasmModule module) {
        for (Export export : module.exports) {
            if (export.type() != Export.ExportType.FUNC) continue;

            FuncType funcType;
            int funcTypeIndex = 0;
            if (export.index() < module.funcImports().size()) {
                // It's an import
                Import.Func funcImport = module.funcImports().get(export.index());
                if (javaModules.containsKey(funcImport.moduleName)) {
                    // It's a java function
                    JavaModuleData.MethodData funcData = javaModules.get(funcImport.moduleName).allowedMethods.get(funcImport.elementName);
                    if (funcData == null)
                        throw new IllegalArgumentException("Failed to compile - unrecognized function \"" + funcImport.moduleName + "." + funcImport.elementName + "\"");
                    // Generate java glue function, using exported name:
                    funcData.writeGlue(writer, getJavaExportFuncName(export.name()), moduleName, funcImport.moduleName);
                    // The glue function is also callable by wasm:
                    funcData.writeGlue(writer, getWasmExportFuncName(export.name()), moduleName, funcImport.moduleName);
                    // We wrote the glue, continue.
                    continue;
                } else {
                    // It's an imported WASM function:
                    funcType = module.types.get(funcImport.typeIndex);
                }
            } else {
                // Local WASM function:
                funcType = module.types.get(module.functions.get(export.index() - module.funcImports().size()));
            }

            // It's a WASM function. Emit functions to call it:
            // Emit the java function:
            {
                int access = Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC;
                String methodName = getJavaExportFuncName(export.name()); // GetJavaExportName!
                StringBuilder descriptor = new StringBuilder("(");
                for (ValType paramType: funcType.args)
                    descriptor.append(paramType.descriptor);
                descriptor.append(")[Ljava/lang/Object;"); // Returns an object array
                MethodVisitor methodVisitor = writer.visitMethod(access, methodName, descriptor.toString(), null, null);
                methodVisitor.visitCode();
                Code tempCode = new Code(-1, -1, funcType.args, new Expression(List.of()));
                MethodWritingVisitor visitor = new MethodWritingVisitor(javaModules, limiter, moduleName, module, tempCode, funcType, methodVisitor);
                // Get the visitor to load all the locals, do the call, then return.
                for (int i = 0; i < funcType.args.size(); i++)
                    visitor.visitLocalGet(new Instruction.LocalGet(i)); // Load all locals
                visitor.visitCall(new Instruction.Call(export.index())); // Call the function
                visitor.returnArrayToJavaCaller(funcType.results); // Return results as array, to java caller
                // End the methodVisitor
                methodVisitor.visitMaxs(0, 0);
                methodVisitor.visitEnd();
            }
            // Emit the wasm function:
            {
                int access = Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC;
                String methodName = getWasmExportFuncName(export.name()); // GetWasmExportName!
                String descriptor = funcType.descriptor();
                MethodVisitor methodVisitor = writer.visitMethod(access, methodName, descriptor, null, null);
                methodVisitor.visitCode();
                Code tempCode = new Code(-1, -1, funcType.args, new Expression(List.of()));
                MethodWritingVisitor visitor = new MethodWritingVisitor(javaModules, limiter, moduleName, module, tempCode, funcType, methodVisitor);
                // Get the visitor to load all the locals, do the call, then return.
                for (int i = 0; i < funcType.args.size(); i++)
                    visitor.visitLocalGet(new Instruction.LocalGet(i)); // Load all locals
                visitor.visitCall(new Instruction.Call(export.index())); // Call the function
                visitor.visitReturn(Instruction.Return.INSTANCE); // Return value
                // End the methodVisitor
                methodVisitor.visitMaxs(0, 0);
                methodVisitor.visitEnd();
            }
        }
    }

    /**
     * The init method accepts an InstanceLimiter as the first parameter,
     * and a Map<String, JavaModuleData<?>> as its second parameter.
     * The InstanceLimiter will be used to fill the visitor field, and the
     * map will be used to fill all the global fields.
     */
    private static MethodVisitor beginInitMethod(ClassVisitor writer, Map<String, JavaModuleData<?>> javaModules, String moduleName) {
        // Create the MethodVisitor
        int access = Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC;
        String descriptor = "(" + Type.getDescriptor(InstanceLimiter.class) + Type.getDescriptor(Map.class) + Type.getDescriptor(WasmModule.class) + ")V";
        MethodVisitor init = writer.visitMethod(access, getInitMethodName(), descriptor, null, null);

        // Create the limiter field and fill it in
        writer.visitField(Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC, getLimiterName(), Type.getDescriptor(InstanceLimiter.class), null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitFieldInsn(Opcodes.PUTSTATIC, getClassName(moduleName), getLimiterName(), Type.getDescriptor(InstanceLimiter.class));

        // Create the global instance fields and fill them in
        for (Map.Entry<String, JavaModuleData<?>> javaModule : javaModules.entrySet()) {
            // Skip any without a global instance
            if (javaModule.getValue().globalInstance == null) continue;
            // For the rest, create a field and fill it in
            String fieldDesc = Type.getDescriptor(javaModule.getValue().moduleClass);
            writer.visitField(Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC, getGlobalInstanceName(javaModule.getKey()), fieldDesc, null, null);
            init.visitVarInsn(Opcodes.ALOAD, 1); // [Map]
            init.visitLdcInsn(javaModule.getKey()); // [Map, javaModuleName]
            init.visitMethodInsn(Opcodes.INVOKEINTERFACE, Type.getInternalName(Map.class), "get", "(Ljava/lang/Object;)Ljava/lang/Object;", true); // [javaModuleData]
            init.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(JavaModuleData.class)); // [javaModuleData]
            init.visitFieldInsn(Opcodes.GETFIELD, Type.getInternalName(JavaModuleData.class), "globalInstance", "Ljava/lang/Object;"); // [javaModuleData.globalInstance]
            init.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(javaModule.getValue().moduleClass)); // [(InstanceType) javaModuleData.globalInstance]
            init.visitFieldInsn(Opcodes.PUTSTATIC, getClassName(moduleName), getGlobalInstanceName(javaModule.getKey()), fieldDesc); // [], value was stored in field
        }

        // Return the method visitor
        return init;
    }

    private static void emitGlobals(ClassVisitor writer, Map<String, JavaModuleData<?>> javaModules, InstanceLimiter limiter, String moduleName, WasmModule module, MethodVisitor init) {
        // Create the various globals, and initialize them in init.
        String className = getClassName(moduleName);
        for (int index = 0; index < module.globals.size(); index++) {
            Global global = module.globals.get(index);
            int access = Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC;
            // Create field:
            writer.visitField(access, getGlobalName(index), global.globalType().valType().descriptor, null, null);
            // Initialize:
            MethodWritingVisitor instVisitor = new MethodWritingVisitor(javaModules, limiter, moduleName, module, new Code(-1, -1, List.of(ValType.EXTERNREF, ValType.EXTERNREF, ValType.EXTERNREF), global.initializer()), null, init);
            instVisitor.visitExpr(global.initializer());
            init.visitFieldInsn(Opcodes.PUTSTATIC, className, getGlobalName(index), global.globalType().valType().descriptor);
        }
        // TODO: For each export, generate a public function to grab the field
    }

    private static void emitMemory(ClassVisitor writer, InstanceLimiter limiter, String moduleName, WasmModule module, MethodVisitor init) {
        // Create the various "memory" fields, which are byte[], as well as other helper fields.
        // Also initialize these fields in init.
        String className = getClassName(moduleName);

        for (int index = 0; index < module.memories.size(); index++) {
            Limits limits = module.memories.get(index);
            // Create fields and get <init> to fill them with values.
            writer.visitField(Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC, getMemoryName(index), "[B", null, null); // Create memory field
            // Increase memory usage count when doing this, if needed
            // Error out if the requested size is too big
            int initialSize = Math.multiplyExact(limits.min(), WASM_PAGE_SIZE);
            if (limiter.countsMemory) {
                // Increment memory use by the initial size
                init.visitFieldInsn(Opcodes.GETSTATIC, getClassName(moduleName), getLimiterName(), Type.getDescriptor(InstanceLimiter.class));
                BytecodeHelper.constLong(init, initialSize); // [limiter, initialSize]
                init.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(InstanceLimiter.class), "incHeapMemoryUsed", "(J)V", false); // []
            }
            // Fill in the field
            init.visitLdcInsn(limits.min() * WASM_PAGE_SIZE);
            init.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_BYTE);
            init.visitFieldInsn(Opcodes.PUTSTATIC, className, getMemoryName(index), "[B");
        }
        for (Class<?> primitive : primitives) {
            Type arr = Type.getType(primitive.arrayType());
            String desc = Type.getDescriptor(primitive);
            // Create the field
            // TODO: Do these need to be final for best performance? Check later, can probably change if needed
            writer.visitField(Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC, getMemoryVarHandleName(desc), Type.getDescriptor(VarHandle.class), null, null);
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

    private static void emitMemoryExports(ClassVisitor writer, Map<String, JavaModuleData<?>> javaModules, String moduleName, WasmModule module) {
        for (Export export : module.exports) {
            // Get only mem exports
            if (export.type() != Export.ExportType.MEM) continue;
            // Generate the getter and setter for the mem
            if (export.index() < module.memImports().size()) {
                Import.Mem memImport = module.memImports().get(export.index());
                // Re-exporting an imported memory
                if (javaModules.containsKey(memImport.moduleName))
                    throw new UnsupportedOperationException("Cannot import memories from java modules");
                else {
                    // It's re-exporting an imported wasm memory. Delegate the function calls.
                    int access = Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC;
                    // Getter:
                    MethodVisitor getter = writer.visitMethod(access, getExportedMemGetterName(export.name()), "()[B", null,null);
                    getter.visitCode();
                    getter.visitMethodInsn(Opcodes.INVOKESTATIC, getClassName(memImport.moduleName), getExportedMemGetterName(memImport.elementName), "()[B", false);
                    getter.visitInsn(Opcodes.ARETURN);
                    getter.visitMaxs(0, 0);
                    getter.visitEnd();
                    // Setter:
                    MethodVisitor setter = writer.visitMethod(access, getExportedMemSetterName(export.name()), "([B)V", null, null);
                    setter.visitCode();
                    setter.visitVarInsn(Opcodes.ALOAD, 0);
                    setter.visitMethodInsn(Opcodes.INVOKESTATIC, getClassName(memImport.moduleName), getExportedMemSetterName(memImport.elementName), "([B)V", false);
                    setter.visitInsn(Opcodes.RETURN);
                    setter.visitMaxs(0, 0);
                    setter.visitEnd();
                }
            } else {
                int adjustedIndex = export.index() - module.memImports().size();
                // Generate getter and setter
                int access = Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC;
                // Getter:
                MethodVisitor getter = writer.visitMethod(access, getExportedMemGetterName(export.name()), "()[B", null, null);
                getter.visitCode();
                getter.visitFieldInsn(Opcodes.GETSTATIC, getClassName(moduleName), getMemoryName(adjustedIndex), "[B");
                getter.visitInsn(Opcodes.ARETURN);
                getter.visitMaxs(0, 0);
                getter.visitEnd();
                // Setter:
                MethodVisitor setter = writer.visitMethod(access, getExportedMemSetterName(export.name()), "([B)V", null, null);
                setter.visitCode();
                setter.visitVarInsn(Opcodes.ALOAD, 0);
                setter.visitFieldInsn(Opcodes.PUTSTATIC, getClassName(moduleName), getMemoryName(adjustedIndex), "[B");
                setter.visitInsn(Opcodes.RETURN);
                setter.visitMaxs(0, 0);
                setter.visitEnd();
            }
        }
    }

    private static void emitDatas(ClassVisitor writer, Map<String, JavaModuleData<?>> javaModules, InstanceLimiter limiter, String moduleName, WasmModule module, MethodVisitor init) {
        for (int dataIndex = 0; dataIndex < module.datas.size(); dataIndex++) {
            Data data = module.datas.get(dataIndex);

            // Store each data byte[] in a field.
            // Increment memory usage by the byte[]'s size:
            if (limiter.countsMemory) {
                // Increment memory use by the byte array size
                init.visitFieldInsn(Opcodes.GETSTATIC, getClassName(moduleName), getLimiterName(), Type.getDescriptor(InstanceLimiter.class));
                BytecodeHelper.constLong(init, data.init.length); // [limiter, size]
                init.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(InstanceLimiter.class), "incHeapMemoryUsed", "(J)V", false); // []
            }

            // Create the field:
            writer.visitField(Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC, getDataFieldName(dataIndex), "[B", null, null);
            // Fetch the byte[] instance from the module, which is the third parameter to the init method
            init.visitVarInsn(Opcodes.ALOAD, 2); // [module]
            init.visitFieldInsn(Opcodes.GETFIELD, Type.getInternalName(WasmModule.class), "datas", Type.getDescriptor(List.class)); // [module.datas]
            BytecodeHelper.constInt(init, dataIndex); // [module.datas, dataIndex]
            init.visitMethodInsn(Opcodes.INVOKEINTERFACE, Type.getInternalName(List.class), "get", "(I)Ljava/lang/Object;", true); // [module.datas.get(dataIndex)]
            init.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(Data.class)); // [module.datas.get(dataIndex)]
            init.visitFieldInsn(Opcodes.GETFIELD, Type.getInternalName(Data.class), "init", "[B"); // [module.datas.get(dataIndex).init]
            // Store it in the newly created field:
            init.visitFieldInsn(Opcodes.PUTSTATIC, getClassName(moduleName), getDataFieldName(dataIndex), "[B");

            // If the data is active, then "memory.init" it right away, and then "data.drop" it:
            if (data.mode instanceof Data.Mode.Active activeMode) {
                // Create a visitor
                MethodWritingVisitor instVisitor = new MethodWritingVisitor(javaModules, limiter, moduleName, module, new Code(-1, -1, List.of(ValType.EXTERNREF, ValType.EXTERNREF, ValType.EXTERNREF), activeMode.offset()), null, init);
                // Visit the code to init, then drop.
                instVisitor.visitExpr(activeMode.offset()); // Stack = [offset (destination)]
                instVisitor.visitI32Const(new Instruction.I32Const(0)); // [offset, 0 (source)]
                instVisitor.visitI32Const(new Instruction.I32Const(data.init.length)); // [dest, src, len]
                instVisitor.visitMemoryInit(new Instruction.MemoryInit(dataIndex)); // []
                instVisitor.visitDataDrop(new Instruction.DataDrop(dataIndex)); // Drop the data
            }
        }
    }

    private static void emitTables(ClassVisitor writer, InstanceLimiter limiter, String moduleName, WasmModule module, MethodVisitor init) {
        // Create the various "table" fields, which are Object[].
        String className = getClassName(moduleName);
        for (int index = 0; index < module.tables.size(); index++) {
            TableType tableType = module.tables.get(index);
            int initialSize = tableType.limits().min();
            // Increment memory if needed
            if (limiter.countsMemory) {
                // Increment memory use by the initial size
                init.visitFieldInsn(Opcodes.GETSTATIC, getClassName(moduleName), getLimiterName(), Type.getDescriptor(InstanceLimiter.class));
                BytecodeHelper.constLong(init, (long) initialSize * 8L); // [limiter, initialSize * 8]
                init.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(InstanceLimiter.class), "incHeapMemoryUsed", "(J)V", false); // []
            }
            // Create fields and get init to fill them with values
            String descriptor = tableType.elementType() == ValType.EXTERNREF ? TABLE_DESCRIPTOR : FUNCREF_TABLE_DESCRIPTOR;

            writer.visitField(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, getTableName(index), descriptor, null, null); // Create table field
            init.visitLdcInsn(initialSize);
            init.visitTypeInsn(Opcodes.ANEWARRAY, descriptor.substring(2, descriptor.length() - 1));
            init.visitFieldInsn(Opcodes.PUTSTATIC, className, getTableName(index), descriptor);
        }
        // TODO: For each export, generate a public function to grab the Object[]
    }

    private static void emitTableExports(ClassVisitor writer, Map<String, JavaModuleData<?>> javaModules, String moduleName, WasmModule module) {
        for (Export export : module.exports) {
            // Get only table exports
            if (export.type() != Export.ExportType.TABLE) continue;
            // Generate the getter and setter for the table
            if (export.index() < module.tableImports().size()) {
                Import.Table tableImport = module.tableImports().get(export.index());
                // Re-exporting an imported memory
                if (javaModules.containsKey(tableImport.moduleName))
                    throw new UnsupportedOperationException("Cannot import tables from java modules");
                else {
                    String tableDescriptor = tableImport.type.elementType() == ValType.FUNCREF ? FUNCREF_TABLE_DESCRIPTOR : TABLE_DESCRIPTOR;
                    // It's re-exporting an imported wasm table. Delegate the function calls.
                    int access = Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC;
                    // Getter:
                    MethodVisitor getter = writer.visitMethod(access, getExportedTableGetterName(export.name()), "()" + tableDescriptor, null,null);
                    getter.visitCode();
                    getter.visitMethodInsn(Opcodes.INVOKESTATIC, getClassName(tableImport.moduleName), getExportedTableGetterName(tableImport.elementName), "()" + tableDescriptor, false);
                    getter.visitInsn(Opcodes.ARETURN);
                    getter.visitMaxs(0, 0);
                    getter.visitEnd();
                    // Setter:
                    MethodVisitor setter = writer.visitMethod(access, getExportedTableSetterName(export.name()), "(" + tableDescriptor + ")V", null, null);
                    setter.visitCode();
                    setter.visitVarInsn(Opcodes.ALOAD, 0);
                    setter.visitMethodInsn(Opcodes.INVOKESTATIC, getClassName(tableImport.moduleName), getExportedTableSetterName(tableImport.elementName), "(" + tableDescriptor + ")V", false);
                    setter.visitInsn(Opcodes.RETURN);
                    setter.visitMaxs(0, 0);
                    setter.visitEnd();
                }
            } else {
                int adjustedIndex = export.index() - module.tableImports().size();
                String tableDescriptor = module.tables.get(adjustedIndex).elementType() == ValType.FUNCREF ? FUNCREF_TABLE_DESCRIPTOR : TABLE_DESCRIPTOR;
                // Generate getter and setter
                int access = Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC;
                // Getter:
                MethodVisitor getter = writer.visitMethod(access, getExportedTableGetterName(export.name()), "()" + tableDescriptor, null, null);
                getter.visitCode();
                getter.visitFieldInsn(Opcodes.GETSTATIC, getClassName(moduleName), getTableName(adjustedIndex), tableDescriptor);
                getter.visitInsn(Opcodes.ARETURN);
                getter.visitMaxs(0, 0);
                getter.visitEnd();
                // Setter:
                MethodVisitor setter = writer.visitMethod(access, getExportedTableSetterName(export.name()), "(" + tableDescriptor + ")V", null, null);
                setter.visitCode();
                setter.visitVarInsn(Opcodes.ALOAD, 0);
                setter.visitFieldInsn(Opcodes.PUTSTATIC, getClassName(moduleName), getTableName(adjustedIndex), tableDescriptor);
                setter.visitInsn(Opcodes.RETURN);
                setter.visitMaxs(0, 0);
                setter.visitEnd();
            }
        }
    }

    private static void emitElements(ClassVisitor writer, Map<String, JavaModuleData<?>> javaModules, InstanceLimiter limiter, String moduleName, WasmModule module, MethodVisitor init) {
        for (int elemIndex = 0; elemIndex < module.elements.size(); elemIndex++) {
            Element elem = module.elements.get(elemIndex);

            // After this if-else, we're going to want an array on the stack, followed by an index.
            Code temp = new Code(-1, -1, List.of(ValType.EXTERNREF, ValType.EXTERNREF, ValType.EXTERNREF), null);
            MethodWritingVisitor instVisitor = new MethodWritingVisitor(javaModules, limiter, moduleName, module, temp, null, init);
            String descriptor = elem.type() == ValType.EXTERNREF ? TABLE_DESCRIPTOR : FUNCREF_TABLE_DESCRIPTOR;
            boolean isActive = elem.mode() instanceof Element.Mode.Active;

            // If it's not active, increment memory usage and create a field:
            if (!isActive) {
                // Increment memory usage by the array's size:
                if (limiter.countsMemory) {
                    // Increment memory use by the array size
                    init.visitFieldInsn(Opcodes.GETSTATIC, getClassName(moduleName), getLimiterName(), Type.getDescriptor(InstanceLimiter.class));
                    BytecodeHelper.constLong(init, (long) elem.exprs().size() * 8L); // [limiter, size]
                    init.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(InstanceLimiter.class), "incHeapMemoryUsed", "(J)V", false); // []
                }
                // Create the field and fill it with a new array:
                writer.visitField(Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC, getElemFieldName(elemIndex), descriptor, null, null);
                BytecodeHelper.constInt(init, elem.exprs().size()); // [size]
                init.visitTypeInsn(Opcodes.ANEWARRAY, descriptor.substring(2, descriptor.length() - 1)); // [arr]
                init.visitInsn(Opcodes.DUP); // [arr, arr]
                init.visitFieldInsn(Opcodes.PUTSTATIC, getClassName(moduleName), getElemFieldName(elemIndex), descriptor); // [arr]
                init.visitInsn(Opcodes.ICONST_0); // [arr, 0]. Array + index are on stack.
            } else {
                // This is an active array. Put the table directly on the stack, then compute the offset.
                Element.Mode.Active activeMode = (Element.Mode.Active) elem.mode();
                init.visitFieldInsn(Opcodes.GETSTATIC, getClassName(moduleName), getTableName(activeMode.tableIndex()), descriptor); // Stack = [table]
                instVisitor.visitExpr(activeMode.offset()); // [table, index]
            }

            // Now, repeat for each expression: compute it and store in the array, then increment index.
            for (Expression expr : elem.exprs()) {
                // If needed, decrement the refcount of the element previously in this slot:
                if (limiter.countsMemory && isActive) {
                    init.visitInsn(Opcodes.DUP2); // [table, index, table, index]
                    init.visitInsn(Opcodes.AALOAD); // [table, index, table[index]]
                    instVisitor.decrementRefCountOfTopElement(); // [table, index]
                }
                init.visitInsn(Opcodes.DUP2); // [table, index, table, index]
                instVisitor.visitExpr(expr); // Evaluate the expression. Stack = [table, index, table, index, element]
                init.visitInsn(Opcodes.AASTORE); // [table, index]
                init.visitInsn(Opcodes.ICONST_1); // [table, index, 1]
                init.visitInsn(Opcodes.IADD); // [table, index + 1]
            }
            init.visitInsn(Opcodes.POP2); // []. Removed elements from stack top.
        }
    }

}