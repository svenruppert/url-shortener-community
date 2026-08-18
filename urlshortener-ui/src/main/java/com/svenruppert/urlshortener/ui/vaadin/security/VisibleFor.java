package com.svenruppert.urlshortener.ui.vaadin.security;



import com.svenruppert.jsentinel.authorization.annotations.JSentinelAnnotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@JSentinelAnnotation(AppRoleAccessEvaluator.class)
public @interface VisibleFor {
  AppRole[] value();
}