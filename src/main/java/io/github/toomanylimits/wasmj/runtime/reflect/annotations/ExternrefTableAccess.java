package io.github.toomanylimits.wasmj.runtime.reflect.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotate a method with this. It signifies that the method
 * needs access to the special externref array of the caller.
 * (For example, converting RefCountable to/from integers).
 *
 * The ExternRefTableAccessor parameter should come after the byte[] parameter
 * from @ByteArrayAccess.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExternrefTableAccess {

}
