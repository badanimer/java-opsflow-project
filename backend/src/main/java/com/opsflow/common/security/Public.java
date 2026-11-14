package com.opsflow.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An endpoint for everyone, logged in or not: login, sign-up, and Forgot password. See
 * {@link RequiresPermission} for the rule that every endpoint declares who may call it.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Public {
}
