package com.opsflow.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An endpoint for every logged-in user, also one who has no role yet. The pending page needs such an
 * endpoint to ask who the user is. See {@link RequiresPermission} for the rule that every endpoint declares
 * who may call it.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AnyAccount {
}
