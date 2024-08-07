package io.github.toomanylimits.wasmj.runtime.reflect.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotate a method with this. It signifies that the method
 * needs access to the InstanceLimiter of the caller. Here,
 * you may access the limiting variables (such as the number
 * of instructions executed, or JVM heap used) and modify them
 * as necessary.
 *
 * This should come after the ExternrefTableAccessor from the @ExternrefTableAccess annotation, if that one is present.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LimiterAccess {

}
