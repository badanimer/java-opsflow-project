package com.opsflow.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The permission an endpoint needs: one row of the Permission Matrix in
 * docs/database/database-design.md, written on the controller method.
 *
 * A user may call the endpoint when any one of their roles grants the permission, because the roles of
 * a user add up. Which role grants what is written in {@link Role} and nowhere else, so an endpoint
 * never names a role. A logged-in user with no role at all is answered {@code ACCOUNT_NO_ROLE}, and a
 * user whose roles do not grant the permission is answered {@code FORBIDDEN}.
 *
 * Every endpoint carries exactly one of {@link Public}, {@link AnyAccount}, and this annotation. An
 * endpoint with none is refused for everyone, and a test fails the build for it.
 *
 * This is the first gate only. A rule that depends on the data, such as an operator who may resolve only
 * their own ticket, is checked by the service, inside the transaction of the change.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {

	Permission value();

}
