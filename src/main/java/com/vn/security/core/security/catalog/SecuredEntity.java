package com.vn.security.core.security.catalog;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Opt-in marker for entity types that participate in secured data enforcement.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface SecuredEntity {
    /**
     * Lowercase entity code. Defaults to lowercase simple class name if empty.
     */
    String code() default "";

    /**
     * Fetch-plan codes. Defaults to {code}-list and {code}-detail if empty.
     */
    String[] fetchPlanCodes() default {};

    /**
     * Opt-in flag that allows callers to execute custom JPQL via
     * {@link com.vn.security.core.security.data.SecureDataManager#load(Class)} or
     * {@link com.vn.security.core.security.data.SecureDataManager#loadByQuery}. Defaults to
     * {@code false} so JPQL paths are denied unless explicitly enabled per entity.
     */
    boolean jpqlAllowed() default false;
}
