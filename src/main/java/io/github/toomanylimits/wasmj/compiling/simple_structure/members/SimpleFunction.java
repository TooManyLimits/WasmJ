package io.github.toomanylimits.wasmj.compiling.simple_structure.members;

import io.github.toomanylimits.wasmj.compiling.compiler.Compiler;
import io.github.toomanylimits.wasmj.compiling.helpers.BytecodeHelper;
import io.github.toomanylimits.wasmj.compiling.helpers.Names;
import io.github.toomanylimits.wasmj.compiling.helpers.CallingHelpers;
import io.github.toomanylimits.wasmj.compiling.compiler.CompilingSimpleInstructionVisitor;
import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleInstruction;
import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleModule;
import io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics.ClassGenCallback;
import io.github.toomanylimits.wasmj.parsing.instruction.StackType;
import io.github.toomanylimits.wasmj.parsing.types.ValType;
import io.github.toomanylimits.wasmj.runtime.ExportedFunction;
import io.github.toomanylimits.wasmj.runtime.ExternrefTableAccessor;
import io.github.toomanylimits.wasmj.runtime.reflect.JavaModuleData;
import io.github.toomanylimits.wasmj.runtime.sandbox.InstanceLimiter;
import org.objectweb.asm.*;

import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Keep an array of these in a SimpleModule.
 */
public interface SimpleFunction {

    /**
     * Emit a call to this function into the visitor.
     */
    void emitCall(SimpleModule callingModule, MethodVisitor visitor, CompilingSimpleInstructionVisitor compilingVisitor);

    /**
     * Emit the code needed for calling this function into the class, as well as the function definition if applicable.
     * If this is an exported function, also emit the exports, and add them to the list in the init function.
     */
    void emitFunction(SimpleModule declaringModule, ClassVisitor classWriter, MethodVisitor initFunction, Set<ClassGenCallback> classGenCallbacks);

    /**
     * Get a Handle object from ASM referring to this function
     */
    Handle getHandle(SimpleModule declaringModule);

    /**
     * Get the stack type of this function
     */
    StackType funcType();

    /**
     * A WASM function which is defined in the current module.
     */
    record SameFileFunction(int declaredIndex, String debugName, StackType funcType, String/*?*/ exportedAs, List<SimpleInstruction> instructions, int nextLocalSlot) implements SimpleFunction {
        @Override
        public void emitCall(SimpleModule callingModule, MethodVisitor visitor, CompilingSimpleInstructionVisitor compilingVisitor) {
            // No need for ref-count tracking here.
            // When a WASM function calls another WASM function, references don't
            // "disappear", they are instead taken over by the new stack frame.
            String className = Names.className(callingModule.moduleName);
            String methodName = Names.funcName(declaredIndex, debugName);
            String descriptor = funcType.descriptor();
            // BytecodeHelper.debugPrintln(visitor, "Calling " + methodName);
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, className, methodName, descriptor, false);
            // BytecodeHelper.debugPrintln(visitor, "Returned from " + methodName);
            // Return values
            CallingHelpers.unwrapReturnValues(visitor, compilingVisitor, funcType, false); // Never refcount in Wasm -> Wasm
        }
        @Override
        public void emitFunction(SimpleModule declaringModule, ClassVisitor classWriter, MethodVisitor initFunction, Set<ClassGenCallback> classGenCallbacks) {
            // Create the method visitor
            String funcName = Names.funcName(declaredIndex, debugName);
            String descriptor = funcType.descriptor();

            MethodVisitor methodVisitor = classWriter.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, funcName, descriptor, null, null);
            methodVisitor.visitCode();

            // Write the function body into the method visitor, using a CompilingSimpleInstructionVisitor
            CompilingSimpleInstructionVisitor visitor = new CompilingSimpleInstructionVisitor(declaringModule, methodVisitor, nextLocalSlot, classGenCallbacks);
            visitor.emitMultipleInstructions(instructions);

            // End the method
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();

            // If this is exported, then create the exported function
            if (exportedAs != null) {
                MethodVisitor exported = classWriter.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, Names.exportFuncName(exportedAs), descriptor, null, null);
                exported.visitCode();
                int index = 0;
                for (ValType v : funcType.inTypes()) {
                    exported.visitVarInsn(v.loadOpcode, index);
                    index += v.stackSlots;
                }
                exported.visitMethodInsn(Opcodes.INVOKESTATIC, Names.className(declaringModule.moduleName), funcName, descriptor, false);
                switch (funcType.outTypes().size()) {
                    case 0 -> exported.visitInsn(Opcodes.RETURN);
                    case 1 -> exported.visitInsn(funcType.outTypes().get(0).returnOpcode);
                    default -> exported.visitInsn(Opcodes.ARETURN);
                }
                exported.visitMaxs(0, 0);
                exported.visitEnd();

                // Also, add this exported function to the exported functions list during the init function:
                initFunction.visitFieldInsn(Opcodes.GETSTATIC, Names.className(declaringModule.moduleName), Names.exportedFunctionsFieldName(), Type.getDescriptor(List.class)); // [list]
                initFunction.visitTypeInsn(Opcodes.NEW, Type.getInternalName(ExportedFunction.class)); // [list, uninit exportedFunction]
                initFunction.visitInsn(Opcodes.DUP); // [list, uninit exportedFunction, uninit exportedFunction]
                initFunction.visitLdcInsn(exportedAs); // [list, uninit exportedFunction, uninit exportedFunction, name]
                initFunction.visitLdcInsn(getHandle(declaringModule)); // [list, uninit exportedFunction, uninit exportedFunction, name, handle]
                if (funcType.outTypes().size() > 1) {
                    BytecodeHelper.createDefaultObject(initFunction, ArrayList.class); // [list, uninit exportedFunction, uninit exportedFunction, name, handle, returnlist]
                    for (ValType t : funcType.outTypes()) {
                        initFunction.visitInsn(Opcodes.DUP); // [..., returnlist, returnlist]
                        initFunction.visitFieldInsn(Opcodes.GETSTATIC, Type.getInternalName(ValType.class), t.name(), Type.getDescriptor(ValType.class)); // [..., returnlist, returnlist, field]
                        initFunction.visitMethodInsn(Opcodes.INVOKEINTERFACE, Type.getInternalName(List.class), "add", "(" + Type.getDescriptor(Object.class) + ")Z", true); // [..., returnlist, bool]
                        initFunction.visitInsn(Opcodes.POP); // [..., returnlist]
                    }
                } else {
                    initFunction.visitInsn(Opcodes.ACONST_NULL); // [list, uninit exportedFunction, uninit exportedFunction, name, handle, null]
                }
                initFunction.visitVarInsn(Opcodes.ALOAD, Compiler.INIT_FUNCTION_LIMITER_LOCAL); // [list, uninit exportedFunction, uninit exportedFunction, name, handle, returnlist, limiter]
                String constructorDescriptor = "(" + Type.getDescriptor(String.class) + Type.getDescriptor(MethodHandle.class) + Type.getDescriptor(List.class) + Type.getDescriptor(InstanceLimiter.class) + ")V";
                initFunction.visitMethodInsn(Opcodes.INVOKESPECIAL, Type.getInternalName(ExportedFunction.class), "<init>", constructorDescriptor, false); // [list, init exportedFunction]
                initFunction.visitMethodInsn(Opcodes.INVOKEINTERFACE, Type.getInternalName(List.class), "add", "(" + Type.getDescriptor(Object.class) + ")Z", true); // [bool]
                initFunction.visitInsn(Opcodes.POP); // []
            }
        }

        @Override
        public Handle getHandle(SimpleModule referringModule) {
            return new Handle(Opcodes.H_INVOKESTATIC, Names.className(referringModule.moduleName), Names.funcName(declaredIndex, debugName), funcType.descriptor(), false);
        }
    }

    /**
     * A WASM function defined in a different module from the caller,
     * but was exported from said module and imported to this module.
     */
    record ImportedWasmFunction(String importModuleName, String exportedAs, String functionName, StackType funcType) implements SimpleFunction {
        @Override
        public void emitCall(SimpleModule callingModule, MethodVisitor visitor, CompilingSimpleInstructionVisitor compilingVisitor) {
            // No refcount needed, for same reason as SameFileFunction - it's calling to another WASM function.
            String className = Names.className(importModuleName); // Get the name of the module we're importing from
            String methodName = Names.exportFuncName(functionName); // Get the name they exported it under
            String descriptor = funcType.descriptor();
            visitor.visitMethodInsn(Opcodes.INVOKESTATIC, className, methodName, descriptor, false);
            // Return values
            CallingHelpers.unwrapReturnValues(visitor, compilingVisitor, funcType, false); // Never refcount in Wasm -> Wasm
        }
        @Override
        public void emitFunction(SimpleModule declaringModule, ClassVisitor classWriter, MethodVisitor initFunction, Set<ClassGenCallback> classGenCallbacks) {
            // Do nothing, the function was emitted in another module, unless we need to re-export it
            if (exportedAs != null) {
                throw new UnsupportedOperationException("Re-exporting imported functions is TODO");
            }
        }

        @Override
        public Handle getHandle(SimpleModule declaringModule) {
            return new Handle(Opcodes.H_INVOKESTATIC, Names.className(importModuleName), Names.exportFuncName(functionName), funcType.descriptor(), false);
        }
    }

    /**
     * A function defined in a java module that's been imported
     * to this module
     */
    record ImportedJavaFunction(int funcImportIndex, StackType funcType, String javaModuleName, JavaModuleData<?> moduleData, JavaModuleData.MethodData methodData) implements SimpleFunction {
        @Override
        public void emitCall(SimpleModule callingModule, MethodVisitor visitor, CompilingSimpleInstructionVisitor compilingVisitor) {
            // Fetch additional parameters if the method needs them:
            if (methodData.hasByteArrayAccess()) {
                // If the func has byte array access, put the byte array on the stack
                callingModule.memory.getMemory(callingModule, visitor);
            }
            if (methodData.hasExternrefTableAccess()) {
                // Get the table accessor impl on the stack
                visitor.visitFieldInsn(Opcodes.GETSTATIC, Names.className(callingModule.moduleName), Names.externrefTableAccessorFieldName(), Type.getDescriptor(ExternrefTableAccessor.class));
            }
            if (methodData.hasLimiterAccess()) {
                // Get the limiter and put on the stack
                visitor.visitFieldInsn(Opcodes.GETSTATIC, Names.className(callingModule.moduleName), Names.limiterFieldName(), Type.getDescriptor(InstanceLimiter.class));
            }
            // Call the glue function if it's needed, or the java function directly if not
            if (methodData.needsGlue()) {
                int invokeOpcode = Opcodes.INVOKESTATIC; // Glue is always static
                String className = Names.className(callingModule.moduleName); // Glue is defined locally
                String javaName = Names.glueFuncName(funcImportIndex); // Glue is also named locally
                String desc = methodData.glueDescriptor();
                visitor.visitMethodInsn(invokeOpcode, className, javaName, desc, false);
            } else {
                // In practice, invokeOpcode will always be INVOKESTATIC, since if it would have INVOKEVIRTUAL
                // then it would need glue.
                int invokeOpcode = methodData.isStatic() ? Opcodes.INVOKESTATIC : Opcodes.INVOKEVIRTUAL;
                String className = moduleData.className();
                String javaName = methodData.javaName();
                String desc = methodData.descriptor();
                visitor.visitMethodInsn(invokeOpcode, className, javaName, desc, false);
            }
            // Unwrap return values
            // Increment ref counts if necessary, since we're returning from Java -> Wasm here
            CallingHelpers.unwrapReturnValues(visitor, compilingVisitor, funcType, callingModule.instance.limiter.countsMemory);
        }

        @Override
        public void emitFunction(SimpleModule declaringModule, ClassVisitor classWriter, MethodVisitor initFunction, Set<ClassGenCallback> classGenCallbacks) {
            // Only thing to do is emit glue if necessary.
            if (methodData.needsGlue()) {
                methodData.writeGlue(declaringModule, classWriter, Names.glueFuncName(funcImportIndex), javaModuleName, classGenCallbacks);
            }
        }

        @Override
        public Handle getHandle(SimpleModule declaringModule) {
            throw new UnsupportedOperationException("Taking references to imported java functions is not supported!");
        }
    }

}
