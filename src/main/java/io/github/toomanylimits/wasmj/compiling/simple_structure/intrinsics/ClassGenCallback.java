package io.github.toomanylimits.wasmj.compiling.simple_structure.intrinsics;

import io.github.toomanylimits.wasmj.compiling.simple_structure.SimpleModule;
import org.objectweb.asm.ClassVisitor;

import java.util.Set;

@FunctionalInterface
public interface ClassGenCallback {
    /**
     * Returns a set of ClassGenCallback which should be invoked after this one.
     * For example, if one Intrinsic relies upon calling another Intrinsic, then it
     * should output the callback of that Intrinsic as a return.
     */
    Set<ClassGenCallback> accept(SimpleModule module, ClassVisitor classWriter);
}
