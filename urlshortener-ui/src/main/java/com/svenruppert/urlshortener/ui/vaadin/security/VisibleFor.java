package com.svenruppert.urlshortener.ui.vaadin.security;



import eu.jsentinel.jcustos.authorization.annotations.JCustosAnnotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@JCustosAnnotation(AppRoleAccessEvaluator.class)
public @interface VisibleFor {
  AppRole[] value();
}