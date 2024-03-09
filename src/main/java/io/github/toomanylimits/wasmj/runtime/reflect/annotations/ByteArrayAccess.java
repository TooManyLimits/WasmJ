package io.github.toomanylimits.wasmj.runtime.reflect.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotate a method with this. It signifies that the method
 * needs access to the byte array of the caller. (For example,
 * for reading or writing to a pointer).
 *
 * The byte[] parameter should come directly after the "normal"
 * parameters of the method.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ByteArrayAccess {

}
