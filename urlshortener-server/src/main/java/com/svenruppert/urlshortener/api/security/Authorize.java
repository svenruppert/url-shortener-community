package com.svenruppert.urlshortener.api.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the permission a handler class requires (operation ids
 * from {@code ApiPermission}). Evaluated by
 * {@link SecurityHttpHandler} — a handler without this annotation is
 * authenticated-only when wrapped, public when registered directly.
 *
 * @deprecated P220 — superseded by the jCustos framework annotation
 *     {@code eu.jsentinel.jcustos.authorization.annotations.RequiresPermission}.
 *     Still honoured by {@link SecurityHttpHandler} as an evolutionary bridge;
 *     remove once no handler references it.
 */
@Deprecated(since = "00.10.00")
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Authorize {
  /** Required permission id, e.g. {@code "createLink"}. */
  String value();
}
