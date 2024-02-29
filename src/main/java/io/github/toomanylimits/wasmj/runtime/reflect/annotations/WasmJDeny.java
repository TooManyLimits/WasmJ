package io.github.toomanylimits.wasmj.runtime.reflect.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface WasmJDeny {
    // Currently does nothing, since class-wide allow not yet implemented
}
